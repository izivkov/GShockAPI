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
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import kotlin.experimental.and
import kotlin.experimental.or

// ============================================================================
// Pure Functional Core: Settings Decoding & Encoding
// ============================================================================

/**
 * Pure functional core for settings processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles all bitwise operations and transformations deterministically.
 */
@RequiresApi(Build.VERSION_CODES.O)
object SettingsIOFunctional {
    // Constants for bit masks (all preserved from original)
    private const val MASK_24_HOURS = 0b00000001
    private const val MASK_BUTTON_TONE_OFF = 0b00000010
    private const val MASK_AUTO_LIGHT_OFF = 0b00000100
    private const val POWER_SAVING_MODE = 0b00010000
    private const val DO_NOT_DISTURB_OFF = 0b01000000

    private const val LIGHT_DURATION_LONG = 0b00000001
    private const val RESET_VALUE = 0
    private const val FONT_CLASSIC_MASK = 0x20

    // Button tone and vibration settings (DW-H5600 specific)
    private const val SOUND_AND_VIBRATION = 0b1100 // Both sound and vibration (0xC)
    private const val VIBRATION_ONLY = 0b1000 // Vibration only (0x8)
    private const val SOUND_ONLY = 0b0100 // Sound only (0x4)
    private const val SILENT = 0b0000 // silent (0x0)

    private const val CHIME = 0b00100000

    enum class SettingType {
        SHORT,
        EXTENDED,
    }

    /**
     * Pure decoder: Converts hex array to Settings model.
     *
     * Extracts all settings from the raw byte array using bit masks.
     * No side effects - pure transformation.
     *
     * Protocol format (17 bytes total for extended, 12 for short):
     * [0] - Command code (0x13)
     * [1] - Byte 1 (contains multiple flags)
     * [2] - Light duration and other flags
     * [4] - Date format (1 = DD:MM, 0 = MM:DD)
     * [5] - Language (0-5 for English, Spanish, French, German, Italian, Russian)
     * [8] - Font flags (0x20 = Classic)
     * [12] - Sound/vibration settings (DW-H5600 specific)
     */
    fun decode(settingString: String): Result<Settings> =
        runCatching {
            val settings = Settings()
            val settingArray = Utils.toIntArray(settingString)
            val settingType = if (settingArray.size == 17) SettingType.EXTENDED else SettingType.SHORT

            // Time format (bit 0 of byte 1)
            if (WatchInfo.hasTimeFormat) {
                settings.timeFormat = if (settingArray[1] and MASK_24_HOURS != 0) "24h" else "12h"
            } else {
                settings.timeFormat = "24h" // Default
            }

            // Button tone and vibration
            if (!WatchInfo.vibrate) {
                settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
            } else if (settingType == SettingType.SHORT) {
                settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
            } else {
                settings.buttonTone = settingArray[12] and SOUND_ONLY != 0
                settings.keyVibration = settingArray[12] and VIBRATION_ONLY != 0
                settings.hourlyChime = settingArray[12] and CHIME != 0
            }

            // Flags from byte 1
            if (WatchInfo.hasAutoLight) {
                settings.autoLight = settingArray[1] and MASK_AUTO_LIGHT_OFF == 0
            }
            settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0
            if (WatchInfo.findButtonUserDefined) { // DnD is usually on watches with user defined buttons
                settings.DnD = settingArray[1] and DO_NOT_DISTURB_OFF == 0
            }

            // Date format (byte 4)
            if (WatchInfo.hasDateFormat) {
                settings.dateFormat = if (settingArray[4] == 1) "DD:MM" else "MM:DD"
            }

            // Language (byte 5)
            if (WatchInfo.weekLanguageSupported) {
                settings.language =
                    when (settingArray[5]) {
                        0 -> "English"
                        1 -> "Spanish"
                        2 -> "French"
                        3 -> "German"
                        4 -> "Italian"
                        5 -> "Russian"
                        else -> "English"
                    }
            }

            // Light duration (bit 0 of byte 2)
            val flags = settingArray[2]
            settings.lightDuration = if ((flags and LIGHT_DURATION_LONG) != 0) "4s" else "2s"

            // Font (byte 8)
            if (WatchInfo.hasMultipleFonts) {
                settings.font = if (settingArray[8] and FONT_CLASSIC_MASK != 0) "Classic" else "Standard"
            }

            settings
        }

    /**
     * Pure encoder: Converts Settings model to byte array.
     *
     * Constructs the raw byte array with all necessary bit flags.
     * No side effects - pure transformation.
     *
     * Returns 17-byte array (or 12-byte for shorter modules) containing all settings encoded per protocol.
     */
    fun encode(settings: JSONObject, base: ByteArray? = null): ByteArray {
        val size = WatchInfo.settingsSize
        // Start from the packet last read from the watch (if any) so bytes this encoder does not
        // manage are preserved. Starting from zeros wiped e.g. DW-H5600 bytes 12-16 (sunrise/sunset
        // display data). Only the bits handled below are cleared and re-set.
        val arr = base?.takeIf { it.size == size }?.copyOf() ?: ByteArray(size)
        if (base != null && base.size == size) {
            var b1Mask = MASK_24_HOURS or MASK_BUTTON_TONE_OFF or POWER_SAVING_MODE
            if (WatchInfo.hasAutoLight) b1Mask = b1Mask or MASK_AUTO_LIGHT_OFF
            if (WatchInfo.findButtonUserDefined) b1Mask = b1Mask or DO_NOT_DISTURB_OFF
            arr[1] = (arr[1].toInt() and b1Mask.inv()).toByte()
            arr[2] = (arr[2].toInt() and LIGHT_DURATION_LONG.inv()).toByte()
            if (size == 17) {
                arr[12] = (arr[12].toInt() and (SOUND_ONLY or VIBRATION_ONLY or CHIME).inv()).toByte()
                if (WatchInfo.hasMultipleFonts) arr[8] = (arr[8].toInt() and FONT_CLASSIC_MASK.inv()).toByte()
            }
            if (WatchInfo.hasDateFormat) arr[4] = 0
        }

        // Command code
        arr[0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()

        // Byte 1 - Multiple flags
        if (!WatchInfo.hasTimeFormat) {
            arr[1] = (arr[1] or MASK_24_HOURS.toByte()) // Always 24h if no format setting
        } else if (settings.get("timeFormat") == "24h") {
            arr[1] = (arr[1] or MASK_24_HOURS.toByte())
        }

        // Button tone and vibration settings
        if (settings.get("buttonTone") == false) {
            arr[1] = (arr[1] or MASK_BUTTON_TONE_OFF.toByte())
            if (size == 17) {
                arr[12] = (arr[12] and SOUND_ONLY.inv().toByte())
            }
        } else {
            if (size == 17) {
                arr[12] = (arr[12] or SOUND_ONLY.toByte())
            }
        }

        if (size == 17) {
            if (settings.get("keyVibration") == true) {
                arr[12] = (arr[12] or VIBRATION_ONLY.toByte())
            }

            if (settings.get("hourlyChime") == true) {
                arr[12] = (arr[12] or CHIME.toByte())
            }
        }

        // Additional byte 1 flags
        if (WatchInfo.hasAutoLight) {
            if (settings.get("autoLight") == false) {
                arr[1] = (arr[1] or MASK_AUTO_LIGHT_OFF.toByte())
            }
        }

        if (settings.get("powerSavingMode") == false) {
            arr[1] = (arr[1] or POWER_SAVING_MODE.toByte())
        }

        if (WatchInfo.findButtonUserDefined) {
            if (settings.get("DnD") == false) {
                arr[1] = (arr[1] or DO_NOT_DISTURB_OFF.toByte())
            }
        }

        // Byte 2 - Light duration flag (other bits of the byte are preserved)
        if (settings["lightDuration"] == "4s" || settings["lightDuration"] == "3s") {
            arr[2] = (arr[2].toInt() or LIGHT_DURATION_LONG).toByte()
        }

        // Byte 8 - Font flag (other bits preserved)
        if (size == 17 && WatchInfo.hasMultipleFonts && settings["font"] == "Classic") {
            arr[8] = (arr[8].toInt() or FONT_CLASSIC_MASK).toByte()
        }

        // Byte 4 - Date format
        if (WatchInfo.hasDateFormat) {
            if (settings.get("dateFormat") == "DD:MM") arr[4] = 1
        }

        // Byte 5 - Language
        if (WatchInfo.weekLanguageSupported) {
            arr[5] =
                when (settings.get("language")) {
                    "English" -> 0
                    "Spanish" -> 1
                    "French" -> 2
                    "German" -> 3
                    "Italian" -> 4
                    "Russian" -> 5
                    else -> 0
                }.toByte()
        }

        return arr
    }

    /**
     * Pure command builder: Creates fetch command for settings.
     */
    fun buildFetchCommand(): ByteArray =
        Utils.byteArray(
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte(),
        )
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Settings IO handler with state management.
 *
 * Manages the asynchronous request/response cycle for settings data.
 * Uses pure functional core for all transformations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object SettingsIO {
    private data class State(val deferredResult: CompletableDeferred<Settings>? = null)

    private var state = State()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings = CachedIO.request("GET_SETTINGS") { key -> getBasicSettings(key) }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun getBasicSettings(key: String): Settings {
        // Concurrent readers share the pending request: replacing it (as before) left the first
        // caller waiting forever, e.g. a toggle and a screen refresh reading 0x13 at the same time.
        val (deferred, isNew) = synchronized(this) {
            state.deferredResult?.takeIf { it.isActive }?.let { it to false }
                ?: CompletableDeferred<Settings>().also { state = state.copy(deferredResult = it) } to true
        }
        if (isNew) Connection.sendMessage("{ action: '$key'}")
        return deferred.await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun set(settings: Settings) {
        settings.let { Gson().toJson(it) }.let { settingJson ->
            CachedIO.set("GET_SETTINGS") {
                Connection.sendMessage("{action: \"SET_SETTINGS\", value: $settingJson}")
            }
        }
    }

    /** Raw 0x13 packet last received from the watch; base for the next encode. */
    @Volatile
    private var lastRaw: ByteArray? = null

    /** Bumped on every 0x13 packet from the watch, so a fresh read can tell a new answer from stale lastRaw. */
    @Volatile
    private var rawSeq = 0

    /**
     * Fresh 0x13 read (bypassing the cache), retried. The watch sometimes drops a GET sent right
     * after a SET; the unanswered request must then be discarded, otherwise later readers join it
     * (see getBasicSettings) and never send a new one.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun freshRaw(): ByteArray {
        repeat(3) { attempt ->
            val seq = rawSeq
            CachedIO.remove("GET_SETTINGS")
            kotlinx.coroutines.withTimeoutOrNull(3_000) { request() }
            if (rawSeq != seq) lastRaw?.let { return it.copyOf() }
            synchronized(this) {
                state.deferredResult?.completeExceptionally(IllegalStateException("no 0x13 answer"))
                state = state.copy(deferredResult = null)
            }
            CachedIO.remove("GET_SETTINGS")
            Timber.w("0x13 read attempt ${attempt + 1} got no answer; retrying")
            kotlinx.coroutines.delay(400L * (attempt + 1))
        }
        error("watch did not answer the settings read")
    }

    private const val CODE = 0x13

    private fun is24h(raw: ByteArray) = (raw[1].toInt() and 0x01) != 0
    private fun chime(raw: ByteArray) = raw.size > 12 && (raw[12].toInt() and 0x20) != 0

    /** Fresh read of 0x13 (bypassing the cache); true = 24-hour format. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readTimeFormat24h(): Boolean = is24h(freshRaw())

    /** Hourly time signal: byte 12 bit 0x20 of a fresh 0x13 read (confirmed on the DW-H5600). */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readHourlyChime(): Boolean = chime(freshRaw())

    /** Both bits from ONE fresh read: (24-hour format, hourly signal). */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readTimeFormatAndChime(): Pair<Boolean, Boolean> = freshRaw().let { is24h(it) to chime(it) }

    /**
     * Read-modify-write of the raw 0x13 packet, under the per-code lock shared with [update], so
     * concurrent changes (Time tab, Settings tab, scheduler) cannot undo each other.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun modifyRaw(change: (ByteArray) -> Unit) = RawSettingIO.withLock(CODE) {
        val b = freshRaw()
        change(b)
        IO.writeCmd(GetSetMode.SET, b)
        rememberRaw(b)
    }

    /** Flips only the 24-hour bit of a freshly read 0x13 packet; every other byte is kept. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun writeTimeFormat24h(is24h: Boolean) = modifyRaw { b ->
        b[1] = (if (is24h) b[1].toInt() or 0x01 else b[1].toInt() and 0x01.inv()).toByte()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun writeHourlyChime(on: Boolean) = modifyRaw { b ->
        check(b.size > 12) { "short 0x13 packet" }
        b[12] = (if (on) b[12].toInt() or 0x20 else b[12].toInt() and 0x20.inv()).toByte()
    }

    /** Fresh read, [change] on the decoded settings, encoded onto that same fresh packet and written. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(change: (Settings) -> Unit) = RawSettingIO.withLock(CODE) {
        val raw = freshRaw()
        val settings = SettingsIOFunctional.decode(Utils.fromByteArrayToHexStrWithSpaces(raw)).getOrThrow()
        change(settings)
        val bytes = SettingsIOFunctional.encode(JSONObject(Gson().toJson(settings)), raw)
        IO.writeCmd(GetSetMode.SET, bytes)
        rememberRaw(bytes)
    }

    /** Used after writing a raw 0x13 packet directly, so the next encode starts from it. */
    fun rememberRaw(bytes: ByteArray) {
        lastRaw = bytes.copyOf()
        CachedIO.remove("GET_SETTINGS")
    }

    fun onReceived(data: String) {
        runCatching { Utils.byteArrayOfIntArray(Utils.toIntArray(data).toIntArray()) }
            .getOrNull()?.let { lastRaw = it; rawSeq++ }
        // Use pure function to decode
        SettingsIOFunctional.decode(data)
            .fold(
                onSuccess = { model ->
                    synchronized(this) {
                        state.deferredResult?.complete(model)
                        state = state.copy(deferredResult = null)
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to decode settings: ${error.message}")
                    synchronized(this) {
                        state.deferredResult?.completeExceptionally(error)
                        state = state.copy(deferredResult = null)
                    }
                },
            )
    }

    fun onRunError() {
        synchronized(this) {
            state.deferredResult?.complete(Settings())
            state = state.copy(deferredResult = null)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use pure function to build command, then execute
        IO.writeCmd(
            GetSetMode.GET,
            SettingsIOFunctional.buildFetchCommand(),
        )
    }

    fun sendToWatchSet(message: String) {
        // Use pure function to encode, then execute
        val settings = JSONObject(message).get("value") as JSONObject
        IO.writeCmd(GetSetMode.SET, SettingsIOFunctional.encode(settings, lastRaw))
    }
}
