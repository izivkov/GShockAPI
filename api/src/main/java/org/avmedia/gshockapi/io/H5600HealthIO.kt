package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

/**
 * Imperative shell for the DW-H5600 LifeLog transfer.
 *
 * Runs one transfer at a time. Notifications arrive through [onDrsp], [onConvoy] and [onStatus],
 * which [org.avmedia.gshockapi.protocols.H5600Protocol] registers. The sequencing lives in
 * [H5600LifeLogSync]; this object performs its writes and collects the result for
 * [H5600HealthDecoder].
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600HealthIO {
    private const val SETUP_REPLY_TIMEOUT_MS = 5_000L
    private const val OVERALL_TIMEOUT_MS = 180_000L
    private const val STEP_GOAL_COMMAND = 0x43
    private const val WATCH_CONDITION_COMMAND = 0x28
    private const val ANNOUNCEMENT_COMMAND = 0x00
    private const val ANNOUNCEMENT_BYTES = 6

    private class Transfer(
        val sync: H5600LifeLogSync,
        val zone: ZoneId,
    ) {
        val notifications = mutableListOf<ByteArray>()
        val announcements = mutableMapOf<Int, Int>()
        val setupReplies = Channel<ByteArray>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()
        var watchCondition: ByteArray? = null
    }

    private val lock = Any()
    private var active: Transfer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isActive: Boolean
        get() = synchronized(lock) { active != null }

    fun channelOf(hex: String): H5600Channel = H5600LifeLogSync.channelOf(bytesOf(hex))

    fun onDrsp(hex: String) = onNotification(H5600Channel.DRSP, hex)

    fun onConvoy(hex: String) = onNotification(H5600Channel.CONVOY, hex)

    fun onStatus(hex: String) = onNotification(H5600Channel.STATUS, hex)

    /**
     * Runs one transfer and returns the decoded data. The watch always finishes the transfer
     * with END, so [peek] does not change the sequence.
     */
    suspend fun request(peek: Boolean = true): StepCounterData {
        if (!WatchInfo.hasStepCounter) {
            Timber.i("H5600 LifeLog not supported on watch model: ${WatchInfo.model}")
            return StepCounterData.unavailable()
        }

        val transfer = Transfer(H5600LifeLogSync(), ZoneId.systemDefault())
        val started = synchronized(lock) {
            if (active != null) {
                false
            } else {
                active = transfer
                true
            }
        }
        if (!started) {
            Timber.w("H5600 LifeLog transfer already running")
            return StepCounterData.unavailable()
        }

        try {
            val stepGoal = runSetup(transfer)

            for (write in synchronized(lock) { transfer.sync.start() }) {
                send(write)
            }

            if (withTimeoutOrNull(OVERALL_TIMEOUT_MS.milliseconds) { transfer.finished.await() } == null) {
                Timber.w("H5600 LifeLog transfer timed out; decoding what was received")
            }

            val snapshot = synchronized(lock) { transfer.notifications.toList() to transfer.announcements.toMap() }
            return H5600HealthDecoder.decode(
                notifications = snapshot.first,
                announcedLengths = snapshot.second,
                zone = transfer.zone,
                stepGoal = stepGoal,
                watchCondition = transfer.watchCondition,
            ) ?: StepCounterData.unavailable()
        } finally {
            synchronized(lock) { active = null }
            transfer.setupReplies.close()
        }
    }

    /** Sends each one-byte setup request and waits for its reply. Returns the step goal, if read. */
    private suspend fun runSetup(transfer: Transfer): Int? {
        var stepGoal: Int? = null
        for (command in H5600LifeLogSync.SETUP_COMMANDS) {
            IO.writeCmd(GetSetMode.GET, byteArrayOf(command.toByte()))
            val reply = awaitSetupReply(transfer, command)
            if (reply == null) {
                Timber.w("H5600 setup 0x${command.toString(16)}: no reply")
                continue
            }
            if (command == STEP_GOAL_COMMAND && reply.size >= 3) {
                stepGoal = (reply[1].toInt() and 0xFF) or ((reply[2].toInt() and 0xFF) shl 8)
            }
            if (command in H5600LifeLogSync.ECHO_REPLY_COMMANDS) {
                IO.writeCmd(GetSetMode.SET, reply)
            }
        }
        return stepGoal
    }

    private suspend fun awaitSetupReply(
        transfer: Transfer,
        command: Int,
    ): ByteArray? =
        withTimeoutOrNull(SETUP_REPLY_TIMEOUT_MS.milliseconds) {
            var reply: ByteArray? = null
            while (reply == null) {
                val candidate = transfer.setupReplies.receive()
                if (candidate.firstOrNull() == command.toByte()) reply = candidate
            }
            reply
        }

    private fun onNotification(
        channel: H5600Channel,
        hex: String,
    ) {
        val transfer = synchronized(lock) { active } ?: return
        val bytes = bytesOf(hex)
        val writes =
            synchronized(lock) {
                when (channel) {
                    H5600Channel.CONVOY -> transfer.notifications += bytes
                    H5600Channel.DRSP -> announcement(bytes)?.let { (index, length) -> transfer.announcements[index] = length }
                    H5600Channel.STATUS -> {
                        if (bytes.firstOrNull() == WATCH_CONDITION_COMMAND.toByte()) transfer.watchCondition = bytes
                        transfer.setupReplies.trySend(bytes)
                    }
                }
                val next = transfer.sync.onMessage(channel, bytes)
                if (transfer.sync.isFinished) transfer.finished.complete(Unit)
                next
            }
        scope.launch {
            for (write in writes) send(write)
        }
    }

    private suspend fun send(write: H5600Write) {
        if (write.delayMs > 0) delay(write.delayMs)
        when (write.channel) {
            H5600Channel.CONVOY -> IO.writeCmd(GetSetMode.CONVOY, write.data)
            H5600Channel.DRSP -> IO.writeCmd(GetSetMode.DATA_REQUEST, write.data)
            H5600Channel.STATUS -> IO.writeCmd(GetSetMode.SET, write.data)
        }
    }

    /** Index announcement `00 2E <index> <length, 3 bytes LE>` on DRSP. */
    private fun announcement(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < ANNOUNCEMENT_BYTES || bytes[0] != ANNOUNCEMENT_COMMAND.toByte() || bytes[1] != 0x2E.toByte()) {
            return null
        }
        if (bytes[2] == 0xFF.toByte()) return null
        val length =
            (bytes[3].toInt() and 0xFF) or
                ((bytes[4].toInt() and 0xFF) shl 8) or
                ((bytes[5].toInt() and 0xFF) shl 16)
        return (bytes[2].toInt() and 0xFF) to length
    }

    private fun bytesOf(hex: String): ByteArray {
        val intArray = Utils.toIntArray(hex)
        return Utils.byteArrayOfIntArray(intArray.toIntArray())
    }
}
