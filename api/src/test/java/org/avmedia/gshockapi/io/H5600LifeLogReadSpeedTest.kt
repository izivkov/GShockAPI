package org.avmedia.gshockapi.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.ble.GetSetMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A GBX-H5600 on its slow always-connected link, with the timings of its logs (2026-11-23): every
 * answer comes two connection events (0.78 s) after the request. After START it answers three polls
 * busy (`00 01`), is silent while it prepares, and answers ready [prepMs] after the first START.
 * No new data: the transfer is the parameter exchange and END.
 */
private class SlowLinkWatch(private val prepMs: Long = 10_000L) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reads = CopyOnWriteArrayList<Int>()

    @Volatile private var firstStart = 0L

    @Volatile private var busyAnswers = 0

    @Volatile private var sixSeen = 0

    private fun later(block: () -> Unit) {
        scope.launch {
            delay(2 * EVENT_MS)
            block()
        }
    }

    private fun hex(vararg b: Int) = b.joinToString(" ") { "%02X".format(it) }

    private fun status(bytes: IntArray) {
        // H5600Protocol hands STATUS to the transfer only while one of its setup replies is awaited.
        if (H5600HealthIO.isAwaitingSetupReply()) H5600HealthIO.onStatus(hex(*bytes))
    }

    fun write(
        mode: GetSetMode,
        frame: ByteArray,
    ) {
        val f = frame.map { it.toInt() and 0xFF }
        when (mode) {
            GetSetMode.GET -> {
                reads += f[0]
                later { status(IntArray(20).also { it[0] = f[0]; it[1] = 0x40; it[2] = 0x1F }) }
            }

            GetSetMode.DATA_REQUEST ->
                if (f[0] == 0x00 && f[1] == 0x2E && f[2] == 0xFF) {
                    if (sixSeen >= 2) {
                        later { H5600HealthIO.onDrsp(hex(0x04, 0x2E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)) }
                    } else if (firstStart == 0L) {
                        firstStart = System.currentTimeMillis()
                    }
                }

            GetSetMode.CONVOY ->
                when {
                    f.size == 3 && f.all { it == 0 } -> {
                        val ready = firstStart != 0L && System.currentTimeMillis() - firstStart >= prepMs
                        when {
                            ready -> later { H5600HealthIO.onConvoy(hex(0, 0, 0)) }
                            busyAnswers < 3 -> {
                                busyAnswers++
                                later { H5600HealthIO.onConvoy(hex(0, 1, 0)) }
                            }
                            else -> Unit // preparing: silent
                        }
                    }

                    f[0] == 0x04 -> later { H5600HealthIO.onConvoy(hex(0x04, 0x00)) }
                    f[0] == 0x06 -> {
                        sixSeen++
                        later { H5600HealthIO.onConvoy(hex(0x06, 0xFA, 0, 2, 0, 0, 0, 0, 0)) }
                    }
                }

            else -> Unit
        }
    }

    fun close() = scope.cancel()

    companion object {
        const val EVENT_MS = 390L
    }
}

/** LifeLog read speed on the GBX-H5600: 56 and 61 s for a read with no new data (log 2026-11-23f). */
class H5600LifeLogReadSpeedTest {
    private var watch: SlowLinkWatch? = null

    @Before fun install() {
        H5600HealthIO.stepCounterSupported = { true }
        H5600ConnectionIO.onDisconnected()
    }

    @After fun restore() {
        watch?.close()
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        H5600HealthIO.writeCmd = { mode, bytes -> IO.writeCmd(mode, bytes) }
        H5600HealthIO.readyWaitMs = 60_000L
        H5600ConnectionIO.onDisconnected()
    }

    private fun install(w: SlowLinkWatch) {
        watch = w
        H5600HealthIO.writeCmd = w::write
    }

    private fun watchSetupDone() = H5600ConnectionIO.onReceived("0x47 01")

    @Test fun aReadWithNoNewDataOnASetUpLinkTakesOneAttemptAndNoSetupReplay() =
        runBlocking {
            val w = SlowLinkWatch()
            install(w)
            watchSetupDone()
            val started = System.currentTimeMillis()
            H5600HealthIO.request(peek = true)
            val seconds = (System.currentTimeMillis() - started) / 1000.0
            // Before: 12 reads and 6 echoes per attempt, and the first attempt given up before the watch was ready.
            assertEquals("one attempt, only the step goal and the watch condition", listOf(0x43, 0x28), w.reads.toList())
            assertTrue("read took $seconds s", seconds < 22.0)
        }

    @Test fun withoutTheWatchsSetupDoneTheFullSetupIsReplayed() =
        runBlocking {
            val w = SlowLinkWatch(prepMs = 0)
            install(w)
            H5600HealthIO.request(peek = true)
            assertEquals(H5600LifeLogSync.SETUP_COMMANDS, w.reads.toList())
        }

    @Test fun aWatchThatNeverGetsReadyStillEndsTheRead() =
        runBlocking {
            H5600HealthIO.readyWaitMs = 1_500L
            val w = SlowLinkWatch(prepMs = Long.MAX_VALUE)
            install(w)
            watchSetupDone()
            val started = System.currentTimeMillis()
            val data = H5600HealthIO.request(peek = true)
            assertNull(data.h5600)
            assertTrue(H5600HealthIO.lastError.orEmpty(), H5600HealthIO.lastError.orEmpty().contains("could not be read"))
            assertTrue(System.currentTimeMillis() - started < 30_000)
        }
}
