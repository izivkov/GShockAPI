package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.H5600Channel
import org.avmedia.gshockapi.io.H5600ConnectionIO
import org.avmedia.gshockapi.io.H5600ConnectionSetup
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.model.StepCounterData

/**
 * Protocol for the DW-H5600 (module 3516 family).
 *
 * Steps and the other LifeLog data come from the LifeLog transfer. All other features use the
 * standard protocol. While a transfer is running, notifications are routed to the transfer by
 * channel; otherwise the standard routing applies.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600Protocol : StandardProtocol() {
    /** The watch's connection check and setup notice are answered at any time, also during a transfer. */
    private val ALWAYS_ROUTED = setOf(H5600ConnectionSetup.CONNECTION_CHECK, H5600ConnectionSetup.SETUP_NOTICE)

    override fun extractKey(data: String): Int? {
        val first = super.extractKey(data)
        if (first in ALWAYS_ROUTED) return first
        if (!H5600HealthIO.isActive) return first
        val channel = H5600HealthIO.channelOf(data)
        // A status notification belongs to the transfer only while one of its setup requests is waiting.
        // Anything else on status (such as the button info reply) goes through the normal routing.
        return if (channel == H5600Channel.STATUS && !H5600HealthIO.isAwaitingSetupReply()) {
            first
        } else {
            channel.key
        }
    }

    /** Every connection (automatic reconnects of kind 13 and 14 included) runs the watch's setup. */
    override suspend fun onInitialized() = H5600ConnectionIO.start(ButtonPressedIO.getConnectionKind())

    override suspend fun getStepCount(peek: Boolean): StepCounterData = H5600HealthIO.request(peek)

    override val dataReceivedHandlers: Map<Int, (String) -> Unit>
        get() =
            withSetup(
                super.dataReceivedHandlers -
                    CasioConstants.CHARACTERISTICS.CASIO_ACTIVITY_RECORD.code +
                    mapOf(
                        H5600Channel.DRSP.key to H5600HealthIO::onDrsp,
                        H5600Channel.CONVOY.key to H5600HealthIO::onConvoy,
                        H5600Channel.STATUS.key to H5600HealthIO::onStatus,
                    ),
            )

    /** The connection setup sees its codes too; the standard handler of a code, if any, still runs first. */
    private fun withSetup(handlers: Map<Int, (String) -> Unit>): Map<Int, (String) -> Unit> =
        handlers +
            H5600ConnectionSetup.CODES.associateWith { code ->
                // 0x47 had no handler of its own in the standard routing (logged as unknown).
                val standard = if (code == H5600ConnectionSetup.SETUP_NOTICE) null else handlers[code]
                val handler: (String) -> Unit = { data ->
                    standard?.invoke(data)
                    H5600ConnectionIO.onReceived(data)
                }
                handler
            }
}
