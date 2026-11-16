package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

/**
 * Generic read / write for short settings packets whose first byte is the command code.
 *
 * Used for the DW-H5600 packets decoded from the official app's traffic:
 *  - 0x42: sound / notification mode (6 bytes)
 *  - 0x51: watch-face display item (6 bytes)
 *
 * Read:  1-byte request [code] on the read-request characteristic (GetSetMode.GET, 26eb002c);
 *        the watch answers with a notification on 26eb002d.
 * Write: the full packet on the all-features characteristic (GetSetMode.SET, 26eb002d),
 *        exactly as the official app does (read-modify-write).
 */
object RawSettingIO {
    private const val TIMEOUT_MS = 10_000L

    private val pending = mutableMapOf<Int, CompletableDeferred<IntArray>>()

    /** Reads packet [code] from the watch. Throws on timeout. */
    suspend fun request(code: Int): IntArray {
        val deferred = CompletableDeferred<IntArray>()
        synchronized(pending) {
            pending[code]?.cancel()
            pending[code] = deferred
        }
        IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte()))
        return try {
            withTimeout(TIMEOUT_MS) { deferred.await() }
        } finally {
            synchronized(pending) { if (pending[code] === deferred) pending.remove(code) }
        }
    }

    /** Writes a full packet (first byte = code) to the watch. */
    fun write(packet: IntArray) {
        val bytes = Utils.byteArrayOfIntArray(packet)
        Timber.i("RawSettingIO write: ${bytes.joinToString(" ") { "%02X".format(it) }}")
        IO.writeCmd(GetSetMode.SET, bytes)
    }

    /** The watch answered `FF 81 <code>` (rejected): fail the pending read for that code now. */
    fun onRejected(code: Int) {
        synchronized(pending) { pending.remove(code) }
            ?.completeExceptionally(IllegalStateException("watch rejected 0x%02X (busy?)".format(code)))
    }

    /** Handler registered in WatchProtocol.dataReceivedHandlers for each supported code. */
    fun onReceived(data: String) {
        val ints = runCatching { Utils.toIntArray(data).toIntArray() }.getOrNull() ?: return
        if (ints.isEmpty()) return
        Timber.i("RawSettingIO received: ${ints.joinToString(" ") { "%02X".format(it) }}")
        synchronized(pending) { pending.remove(ints[0]) }?.complete(ints)
    }
}
