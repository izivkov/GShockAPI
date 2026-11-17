package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.RawNotifications
import timber.log.Timber
import java.util.UUID

/**
 * BLE driver for the DW-H5600 LifeLog sync (see [H5600LifeLog] and izivkov's `download_live`).
 * Writes go through the normal queue ([IO.writeCmd]) so ordering is kept; DRSP / CONVOY
 * notifications are taken from [RawNotifications] for the duration of the sync.
 */
object H5600LifeLogIO {
    private val DRSP: UUID = UUID.fromString("26eb0023-b012-49a8-b1f8-394fb2032b0f")
    private val CONVOY: UUID = UUID.fromString("26eb0024-b012-49a8-b1f8-394fb2032b0f")
    private val STATUS: UUID = UUID.fromString("26eb002d-b012-49a8-b1f8-394fb2032b0f")
    private const val SETUP_REPLY_TIMEOUT_MS = 5_000L

    private val mutex = Mutex()

    suspend fun download(): H5600LifeLog.Result? = mutex.withLock {
        check(H5600SettingsIO.isSupported()) { "Not a DW-H5600 family watch" }
        val inbox = Channel<Pair<UUID, ByteArray>>(Channel.UNLIMITED)
        val waiting = java.util.concurrent.atomic.AtomicReference<Pair<Int, CompletableDeferred<ByteArray>>?>(null)

        RawNotifications.listener = { uuid, data ->
            when (uuid) {
                DRSP, CONVOY -> { inbox.trySend(uuid to data.copyOf()); true } // ours during the sync
                STATUS -> {
                    waiting.get()?.let { (code, d) ->
                        if (data.isNotEmpty() && (data[0].toInt() and 0xFF) == code) d.complete(data.copyOf())
                    }
                    false // still dispatched to the normal handlers
                }
                else -> false
            }
        }
        try {
            withTimeoutOrNull(H5600LifeLog.OVERALL_TIMEOUT_MS) { // null = timed out (not a cancellation)
                // 1. Setup requests, echoing some replies back like the official app.
                for (cmd in H5600LifeLog.SETUP_COMMANDS) {
                    val d = CompletableDeferred<ByteArray>()
                    waiting.set(cmd to d)
                    IO.writeCmd(GetSetMode.GET, byteArrayOf(cmd.toByte()))
                    val reply = withTimeoutOrNull(SETUP_REPLY_TIMEOUT_MS) { d.await() }
                    if (reply == null) Timber.w("LifeLog setup: no reply to %02X".format(cmd))
                    else if (cmd in H5600LifeLog.ECHO_REPLY_COMMANDS) IO.writeCmd(GetSetMode.SET, reply)
                }
                waiting.set(null)

                // 2. Early START on DRSP (the watch acknowledges it once ready), then poll.
                IO.writeCmd(GetSetMode.DATA_REQUEST, H5600LifeLog.START)
                val sync = H5600LifeLog.Sync()
                perform(sync.start())

                val convoy = mutableListOf<ByteArray>()
                val announced = linkedMapOf<Int, Int>()
                while (!sync.done) {
                    val msg = withTimeoutOrNull(H5600LifeLog.POLL_RETRY_MS + 5_000) { inbox.receive() }
                    if (msg == null) {
                        if (sync.phase == "polling") perform(sync.onConvoy(byteArrayOf(0x00, 0x01, 0x04)))
                        continue
                    }
                    val (uuid, data) = msg
                    if (uuid == CONVOY) {
                        convoy += data
                        if (data.size >= 2 && data[0] == 0x00.toByte() && data[1] == 0x01.toByte()) {
                            delay(H5600LifeLog.POLL_RETRY_MS) // busy: the official app waits ~3.5 s
                        }
                        perform(sync.onConvoy(data))
                    } else {
                        H5600LifeLog.announcement(data)?.let { (i, len) -> announced.putIfAbsent(i, len) }
                        perform(sync.onDrsp(data))
                    }
                }
                Timber.i("LifeLog: transfer done, ${convoy.size} convoy messages, frames ${announced.keys}")
                H5600LifeLog.decode(convoy, announced)
            }
        } finally {
            RawNotifications.listener = null
            inbox.close()
        }
    }

    private fun perform(writes: List<H5600LifeLog.Write>) = writes.forEach {
        IO.writeCmd(if (it.target == H5600LifeLog.Target.DRSP) GetSetMode.DATA_REQUEST else GetSetMode.CONVOY, it.data)
    }
}
