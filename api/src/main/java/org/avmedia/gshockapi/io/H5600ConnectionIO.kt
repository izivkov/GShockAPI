package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

/**
 * Imperative shell for [H5600ConnectionSetup]: runs the setup once per connection, answers the
 * watch's connection checks, and lets LifeLog and settings reads wait until the setup is done, so
 * their requests do not queue in front of the setup's and take its answers.
 *
 * [H5600Protocol][org.avmedia.gshockapi.protocols.H5600Protocol] starts it from `onInitialized`
 * (every connection, automatic reconnects of kind 13 and 14 included) and hands it the answers.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600ConnectionIO {
    const val SETUP_WAIT_MS = 20_000L

    // Seams for unit tests; production values below.
    internal var send: suspend (GetSetMode, ByteArray) -> Unit = { mode, bytes -> Connection.writeInOrder(mode, bytes) }
    internal var setTime: suspend () -> Unit = { TimeIO.set() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** All writes go out in order, one after the other. */
    private val outbox = Channel<H5600SetupStep>(Channel.UNLIMITED)

    @Volatile
    private var setup = H5600ConnectionSetup()

    init {
        scope.launch { for (step in outbox) perform(step) }
    }

    /** True while the setup of this connection waits for the watch's answers. */
    val isRunning: Boolean
        get() = setup.isRunning

    /** True once the watch reported its side of the setup done (`47 01`) on this connection. */
    val watchSetupDone: Boolean
        get() = setup.watchSetupDone

    /** A new connection of [kind]: starts its setup. */
    fun start(kind: Int?) {
        val next = H5600ConnectionSetup()
        setup = next
        if (kind == null) {
            Timber.w("H5600 setup: connection kind unknown, setup not run")
            return
        }
        Timber.i("H5600 setup: connection kind $kind, setup starts")
        enqueue(synchronized(next) { next.start(kind) })
    }

    /** The link is gone: the next connection starts from scratch. */
    fun onDisconnected() {
        setup = H5600ConnectionSetup()
    }

    /** Handler for the codes in [H5600ConnectionSetup.CODES]. */
    fun onReceived(data: String) {
        val bytes = runCatching { Utils.byteArrayOfIntArray(Utils.toIntArray(data).toIntArray()) }.getOrNull() ?: return
        val current = setup
        val steps = synchronized(current) { current.onMessage(bytes) }
        if (steps.isNotEmpty()) Timber.i("H5600 setup: ${hex(bytes)} -> $steps")
        enqueue(steps)
    }

    /**
     * Waits at most [timeoutMs] until the setup is not running. Returns false when it still ran at
     * the timeout; the caller goes on.
     */
    suspend fun awaitSetup(
        timeoutMs: Long = SETUP_WAIT_MS,
        pollMs: Long = 100L,
    ): Boolean {
        if (!isRunning) return true
        Timber.i("H5600 setup: waiting for the connection setup before starting")
        val done =
            withTimeoutOrNull(timeoutMs) {
                while (isRunning) delay(pollMs)
                true
            } ?: false
        if (!done) Timber.w("H5600 setup: still running after $timeoutMs ms, going on")
        return done
    }

    private fun enqueue(steps: List<H5600SetupStep>) {
        for (step in steps) outbox.trySend(step)
    }

    private suspend fun perform(step: H5600SetupStep) {
        try {
            when (step) {
                is H5600SetupStep.Read -> send(GetSetMode.GET, byteArrayOf(step.code.toByte()))
                is H5600SetupStep.Write -> send(GetSetMode.SET, step.bytes)
                // The time write is a sequence of its own; the connection checks must not wait for it.
                H5600SetupStep.SetTime -> scope.launch { runCatching { setTime() }.onFailure { Timber.e(it, "H5600 setup: time write failed") } }
            }
        } catch (e: Exception) {
            Timber.e(e, "H5600 setup: $step failed")
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
