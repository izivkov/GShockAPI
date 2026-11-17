package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.Alarms
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Command Pattern & Data Structures
// ============================================================================

/**
 * Sealed hierarchy for BLE operations as data structures.
 * These represent the 'intent' of operations without executing them.
 */
sealed class BLEAction {
    data class Write(
        val mode: GetSetMode,
        val data: ByteArray,
    ) : BLEAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Write) return false
            return mode == other.mode && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return 31 * mode.hashCode() + data.contentHashCode()
        }
    }
}

/**
 * Pure functional core for alarm processing logic.
 *
 * All methods are pure: no mutable state, no side effects.
 * Input -> Deterministic Output transformation.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AlarmsIOFunctional {
    /**
     * Pure parser: Decodes hex string to alarm list.
     *
     * No side effects - returns parsed alarms for the IO shell to handle.
     */
    fun parseReceivedAlarms(data: String): List<Alarm> =
        runCatching {
            val decoded = AlarmDecoder.toJson(data).get("ALARMS")
            val gson = Gson()
            val alarmArr = gson.fromJson(decoded.toString(), Array<Alarm>::class.java)
            alarmArr.toList()
        }.getOrElse { error ->
            Timber.e("Failed to parse received alarms: ${error.message}")
            emptyList()
        }

    /**
     * Pure command builder: Creates the sequence of commands to fetch alarms.
     *
     * Returns data structures (commands) instead of executing them.
     */
    fun buildFetchCommands(alarmCount: Int): List<BLEAction> {
        if (alarmCount == 1) {
            return listOf(
                BLEAction.Write(
                    GetSetMode.GET,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte()),
                ),
            )
        }
        return listOf(
            // Get alarm 1 (must complete before ALM2)
            BLEAction.Write(
                GetSetMode.GET,
                Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte()),
            ),
            // Get the rest of the alarms (only after ALM1 response received)
            BLEAction.Write(
                GetSetMode.GET,
                Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte()),
            ),
        )
    }

    /**
     * Pure command builder: Creates the sequence of commands to set alarms.
     *
     * Parses JSON and builds write commands without side effects.
     */
    fun buildSetCommand(
        message: String,
        alarmCount: Int,
    ): Result<List<BLEAction>> =
        runCatching {
            JSONObject(message).get("value").let { it as JSONArray }.let { jsonArray ->
                if (alarmCount == 1) {
                    val firstAlarm = AlarmsIO.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0))
                    listOf(
                        BLEAction.Write(GetSetMode.SET, firstAlarm),
                    )
                } else {
                    val firstAlarm = AlarmsIO.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0))
                    val secondaryAlarms = AlarmsIO.fromJsonAlarmSecondaryAlarms(jsonArray)
                    listOf(
                        BLEAction.Write(GetSetMode.SET, firstAlarm),
                        BLEAction.Write(GetSetMode.SET, secondaryAlarms),
                    )
                }
            }
        }

    /**
     * Pure validation: Checks if all alarms have been received.
     */
    fun isAlarmCountComplete(
        receivedCount: Int,
        expectedCount: Int,
    ): Boolean = receivedCount >= expectedCount
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

@RequiresApi(Build.VERSION_CODES.O)
object AlarmsIO {
    data class AlarmIOState(
        val deferredResult: CompletableDeferred<ArrayList<Alarm>>? = null,
        val alarms: ArrayList<Alarm> = ArrayList(),
        val isProcessing: Boolean = false,
        val error: String? = null,
    )

    private var state = AlarmIOState()
    private var receivedAlarms = ArrayList<Alarm>()

    // The two alarm packets of the current read (0x15 = alarm 1, 0x16 = alarms 2..5), latest copy of
    // each: the watch sometimes sends a packet twice, and they can arrive in either order.
    private var firstAlarms: List<Alarm>? = null
    private var secondaryAlarms: List<Alarm>? = null

    private fun clearReceivedAlarms() {
        receivedAlarms.clear()
        firstAlarms = null
        secondaryAlarms = null
    }

    private fun addSortedReceivedAlarms(source: Array<Alarm>) {
        val position = if (source.size == 1) 0 else receivedAlarms.size
        receivedAlarms.addAll(position, source.toList())
    }

    private fun getReceivedAlarms(): ArrayList<Alarm> = receivedAlarms

    private fun updateState(transform: (AlarmIOState) -> AlarmIOState): AlarmIOState =
        synchronized(this) {
            state = transform(state)
            state
        }

    fun fromJsonAlarmFirstAlarm(alarmJson: JSONObject): ByteArray {
        val gson = Gson()
        val alarm: Alarm = gson.fromJson(alarmJson.toString(), Alarm::class.java)
        return createFirstAlarm(alarm)
    }

    fun fromJsonAlarmSecondaryAlarms(alarmsJson: JSONArray): ByteArray {
        if (alarmsJson.length() < 2) {
            return ByteArray(0)
        }
        val gson = Gson()
        val allAlarms: Array<Alarm> = gson.fromJson(alarmsJson.toString(), Array<Alarm>::class.java)
        val alarms = allAlarms.toList().subList(1, allAlarms.size)
        return createSecondaryAlarm(alarms)
    }

    // Raw 4-byte entries last read from the watch (0x15 = alarm 1, 0x16 = alarms 2..5).
    // Encoding starts from these so bits this code does not manage (e.g. 0x10 on the DW-H5600,
    // and the watch's own byte[1]) are preserved instead of being rebuilt from scratch.
    @Volatile private var rawFirst: IntArray? = null
    @Volatile private var rawSecondary: List<IntArray> = emptyList()
    private const val MANAGED_FLAGS = Alarms.ENABLED_MASK or Alarms.ONE_TIME_MASK or Alarms.HOURLY_CHIME_MASK

    private fun rememberRaw(data: String) {
        val ints = runCatching { Utils.toIntArray(data) }.getOrNull() ?: return
        when (ints.firstOrNull()) {
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code ->
                if (ints.size >= 5) rawFirst = ints.subList(1, 5).toIntArray()
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code ->
                rawSecondary = ints.drop(1).chunked(4).filter { it.size == 4 }.map { it.toIntArray() }
        }
    }

    private fun baseFlags(raw: IntArray?): Int = (raw?.get(0) ?: 0) and MANAGED_FLAGS.inv()
    private fun baseConstant(raw: IntArray?): Int = raw?.get(1) ?: Alarms.ALARM_CONSTANT_VALUE

    /** Applies an explicit snooze choice; null keeps the bit read from the watch. */
    private fun withSnooze(flag: Int, alarm: Alarm): Int = when (alarm.snooze) {
        true -> flag or Alarms.SNOOZE_MASK
        false -> flag and Alarms.SNOOZE_MASK.inv()
        null -> flag
    }

    private fun createFirstAlarm(alarm: Alarm): ByteArray {
        val raw = rawFirst
        var flag = baseFlags(raw)
        if (alarm.enabled) {
            flag = flag or Alarms.ENABLED_MASK
        }
        if (alarm.hasHourlyChime) flag = flag or Alarms.HOURLY_CHIME_MASK
        flag = withSnooze(flag, alarm)

        return Utils.byteArrayOfInts(
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code,
            flag,
            baseConstant(raw),
            alarm.hour,
            alarm.minute,
        )
    }

    private fun createSecondaryAlarm(alarms: List<Alarm>): ByteArray {
        var allAlarms =
            Utils.byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code)

        for ((index, alarm) in alarms.withIndex()) {
            val raw = rawSecondary.getOrNull(index)
            var flag = baseFlags(raw)
            if (alarm.enabled) {
                flag = flag or Alarms.ENABLED_MASK
                if (alarm.oneTime) {
                    flag = flag or Alarms.ONE_TIME_MASK
                }
            }
            if (alarm.hasHourlyChime) flag = flag or Alarms.HOURLY_CHIME_MASK
            flag = withSnooze(flag, alarm)

            allAlarms +=
                Utils.byteArrayOfInts(
                    flag,
                    baseConstant(raw),
                    alarm.hour,
                    alarm.minute,
                )
        }

        return allAlarms
    }

    /**
     * Reads all alarms (0x15 + 0x16). Concurrent callers share one request (a second caller used to
     * replace the first one's request and wipe its half-received alarms, leaving it waiting forever);
     * each try times out and is retried, and an `FF 81 15/16` rejection fails the try at once.
     * Callers get a copy, never the list the next read clears.
     */
    suspend fun request(): ArrayList<Alarm> =
        CachedIO.request("GET_ALARMS") fetch@{ key ->
            var last: Throwable = IllegalStateException("no answer to the alarm read")
            for (attempt in 0 until 3) {
                val (deferred, isNew) = synchronized(this) {
                    state.deferredResult?.takeIf { it.isActive }?.let { it to false }
                        ?: CompletableDeferred<ArrayList<Alarm>>().also {
                            state = state.copy(deferredResult = it, isProcessing = true)
                            clearReceivedAlarms()
                        }.let { it to true }
                }
                if (isNew) Connection.sendMessage("{ action: '$key'}")
                try {
                    // 0x15 and 0x16 can arrive seconds apart while the watch is busy (e.g. right after a time sync).
                    kotlinx.coroutines.withTimeoutOrNull(8_000) { deferred.await() }?.let { return@fetch ArrayList(it) }
                    last = IllegalStateException("no answer to the alarm read")
                    synchronized(this) {
                        if (state.deferredResult === deferred) state = state.copy(deferredResult = null, isProcessing = false)
                    }
                    deferred.completeExceptionally(last)
                } catch (e: IllegalStateException) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    last = e // rejected, or another caller's try timed out on the shared request
                }
                Timber.w("Alarm read try ${attempt + 1} failed: ${last.message}")
                kotlinx.coroutines.delay(500L * (attempt + 1))
            }
            throw last
        }

    /** The watch answered `FF 81 15` / `FF 81 16` (busy): fail the pending read now. */
    fun onRejected() {
        val d = synchronized(this) {
            state.deferredResult.also { state = state.copy(deferredResult = null, isProcessing = false) }
        }
        d?.completeExceptionally(IllegalStateException("watch rejected the alarm read (busy?)"))
    }

    fun set(alarms: ArrayList<Alarm>) {
        if (alarms.isEmpty()) {
            updateState { it.copy(error = "Alarm model not initialised!") }
            return
        }

        CachedIO.set("GET_ALARMS") {
            val json = Gson().toJson(alarms)
            Connection.sendMessage("{action: \"SET_ALARMS\", value: $json }")
        }
    }

    fun onReceived(data: String) {
        rememberRaw(data)
        val parsedAlarms = AlarmsIOFunctional.parseReceivedAlarms(data)
        synchronized(this) {
            when (runCatching { Utils.toIntArray(data).firstOrNull() }.getOrNull()) {
                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code -> firstAlarms = parsedAlarms
                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code -> secondaryAlarms = parsedAlarms
                else -> return
            }
            receivedAlarms.clear()
            receivedAlarms.addAll(firstAlarms.orEmpty() + secondaryAlarms.orEmpty())
            // Complete only when both packets are in. Counting entries is not enough: the DW-H5600
            // (alarmCount 4) sends 4 entries in 0x16 alone, so when 0x16 arrived before 0x15 the
            // read finished without alarm 1 and every slot looked shifted (-> needless rewrites).
            val complete = firstAlarms != null && (WatchInfo.alarmCount <= 1 || secondaryAlarms != null)
            if (complete && AlarmsIOFunctional.isAlarmCountComplete(getReceivedAlarms().size, WatchInfo.alarmCount)) {
                val snapshot = ArrayList(getReceivedAlarms()) // never hand out the buffer the next read clears
                state.deferredResult?.complete(snapshot)
                state = state.copy(alarms = snapshot, isProcessing = false, deferredResult = null)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use protocol to build commands, then execute them sequentially
        val commands = AlarmsIOFunctional.buildFetchCommands(WatchInfo.alarmCount)
        commands.forEach { action ->
            when (action) {
                is BLEAction.Write -> IO.writeCmd(action.mode, action.data)
            }
        }
    }

    fun sendToWatchSet(message: String) {
        // Use protocol to build commands, then execute them
        AlarmsIOFunctional.buildSetCommand(message, WatchInfo.alarmCount)
            .onSuccess { commands ->
                commands.forEach { action ->
                    when (action) {
                        is BLEAction.Write -> IO.writeCmd(action.mode, action.data)
                    }
                }
            }
            .onFailure { error ->
                Timber.e("Failed to set alarms: ${error.message}")
            }
    }
}

// ============================================================================
// Pure Decoder: Alarm Protocol Decoding
// ============================================================================

/**
 * Pure decoder for alarm protocol data.
 * No mutable state or side effects - pure transformations only.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AlarmDecoder {
    private const val HOURLY_CHIME_MASK = 0b10000000

    /**
     * Converts a command string to a JSON object containing alarm data.
     *
     * Command buffer structure:
     * [0] - Command type:
     *     - 0x2A: Single alarm (CASIO_SETTING_FOR_ALM)
     *     - 0x2B: Multiple alarms (CASIO_SETTING_FOR_ALM2)
     *
     * For single alarm (0x2A):
     * [1..4] - One 4-byte alarm entry
     *
     * For multiple alarms (0x2B):
     * [1..16] - Four 4-byte alarm entries
     *
     * Each alarm entry is 4 bytes:
     * [0] - Flags byte:
     *     - bit 7 (0x80): Hourly chime enabled
     *     - bit 0 (0x01): Alarm enabled
     * [1] - Constant value (0x40)
     * [2] - Hour (0-23)
     * [3] - Minute (0-59)
     *
     * @param command The raw command string containing alarm data
     * @return JSONObject with format: {"ALARMS": [array of alarm objects]}
     */
    fun toJson(command: String): JSONObject =
        runCatching {
            Utils.toIntArray(command).let { intArray ->
                JSONArray().apply {
                    when (intArray.firstOrNull()) {
                        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code ->
                            ArrayList(
                                intArray.drop(1),
                            ).let(::createJsonAlarm).let(::put)

                        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code ->
                            intArray.drop(
                                1,
                            ).chunked(4).map { ArrayList(it) }.forEach { put(createJsonAlarm(it)) }

                        else -> Timber.d("Unhandled Command [$command]")
                    }
                }
            }.let { alarms -> JSONObject().put("ALARMS", alarms) }
        }.getOrElse { error ->
            Timber.e("Failed to parse command: ${error.message}")
            JSONObject()
        }

    /**
     * Pure parser: Creates a JSON object from a 4-byte alarm data buffer.
     *
     * Buffer structure:
     * [0] - Flags byte:
     *     - bit 7 (0x80): Hourly chime enabled
     *     - bit 0 (0x01): Alarm enabled
     * [1] - Constant value (typically 0x40)
     * [2] - Hour (0-23)
     * [3] - Minute (0-59)
     *
     * @param intArray ArrayList containing 4 bytes of alarm data
     * @return JSONObject containing the parsed alarm data
     */
    private fun createJsonAlarm(intArray: ArrayList<Int>): JSONObject =
        runCatching {
            Alarm(
                hour = intArray[2],
                minute = intArray[3],
                enabled = intArray[0] and Alarms.ENABLED_MASK != 0,
                hasHourlyChime = if (WatchInfo.hasHourlyChime) (intArray[0] and HOURLY_CHIME_MASK != 0) else false,
                oneTime = intArray[0] and Alarms.ONE_TIME_MASK != 0,
                snooze = intArray[0] and Alarms.SNOOZE_MASK != 0,
            ).let { alarm ->
                JSONObject(Gson().toJson(alarm))
            }
        }.getOrElse { error ->
            Timber.e("Failed to create alarm: ${error.message}")
            JSONObject()
        }
}
