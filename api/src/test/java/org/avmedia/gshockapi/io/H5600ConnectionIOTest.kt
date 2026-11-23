package org.avmedia.gshockapi.io

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The setup's shell: routing through [H5600Protocol], ordered writes, and reads that wait for the setup. */
class H5600ConnectionIOTest {
    private val writes = CopyOnWriteArrayList<Pair<GetSetMode, List<Int>>>()
    private val rawRequests = CopyOnWriteArrayList<Int>()

    @Before fun install() {
        H5600ConnectionIO.send = { mode, bytes -> writes += mode to bytes.map { it.toInt() and 0xFF } }
        H5600ConnectionIO.setTime = {}
        H5600ConnectionIO.onDisconnected()
        RawSettingIO.sendRequest = { rawRequests += it }
    }

    @After fun restore() {
        H5600ConnectionIO.send = { mode, bytes -> Connection.writeInOrder(mode, bytes) }
        H5600ConnectionIO.setTime = { TimeIO.set() }
        H5600ConnectionIO.onDisconnected()
        RawSettingIO.sendRequest = { code -> IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte())) }
    }

    private fun receive(data: String) = MessageDispatcher.onReceived(data, H5600Protocol)

    private fun waitForWrites(count: Int) =
        runBlocking { withTimeout(2_000) { while (writes.size < count) delay(10) } }

    @Test fun theConnectionCheckIsAnsweredThroughTheProtocol() {
        receive("0x49 00")
        waitForWrites(1)
        assertEquals(listOf(GetSetMode.SET to listOf(0x49, 0x01)), writes.toList())
    }

    @Test fun linkLossLevelOneOnSetupNoticeTwo() {
        receive("0x47 02")
        waitForWrites(1)
        assertEquals(listOf(GetSetMode.SET to listOf(0x00, 0x01)), writes.toList())
    }

    @Test fun reconnectKindTenAnswersThe3Aand3BThroughTheProtocol() {
        H5600ConnectionIO.start(10)
        waitForWrites(1)
        H5600ConnectionIO.onReceived("0x11 01 02 03 04 0F 06 07 08 09 0A 0B 0C")
        waitForWrites(3)
        receive("0x3B 01 02 03")
        waitForWrites(5)
        receive("0x3A 00 01 02 03 04 05 06 07 08 09 10 11")
        waitForWrites(7)
        assertEquals(
            listOf(
                GetSetMode.GET to listOf(0x11),
                GetSetMode.SET to listOf(0x11, 0x01, 0x02, 0x03, 0x04, 0x0B, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C),
                GetSetMode.GET to listOf(0x3B),
                GetSetMode.SET to listOf(0x3B, 0x01, 0x02, 0x03),
                GetSetMode.GET to listOf(0x3A),
                GetSetMode.SET to listOf(0x3A, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0xDC, 0x05, 0x09, 0x10, 0x11),
                GetSetMode.GET to listOf(0x23),
            ),
            writes.toList(),
        )
    }

    /**
     * Log 2026-11-23 15:44:24 (GBX-H5600): a settings read started with the kind-14 setup, took its answers,
     * and the setup never finished (no 47 01). The setup runs first; the read waits for it.
     */
    @Test fun aSettingsReadWaitsUntilTheSetupHasItsAnswers() =
        runBlocking {
            H5600ConnectionIO.start(14)
            assertTrue(H5600ConnectionIO.isRunning)
            val read = async { RawSettingIO.request(0x45) }
            delay(300)
            assertTrue("nothing may be sent in front of the setup's requests", rawRequests.isEmpty())
            H5600ConnectionIO.onReceived("0x23 41 42 43")
            H5600ConnectionIO.onReceived("0x39 04")
            assertFalse(H5600ConnectionIO.isRunning)
            withTimeout(2_000) { while (rawRequests.isEmpty()) delay(10) }
            RawSettingIO.onReceived("0x45 01 02 03")
            assertEquals(listOf(0x45, 1, 2, 3), read.await().toList())
        }

    @Test fun theWaitEndsAtTheTimeout() =
        runBlocking {
            H5600ConnectionIO.start(14)
            assertFalse(H5600ConnectionIO.awaitSetup(timeoutMs = 150, pollMs = 10))
        }

    @Test fun aNewLinkForgetsTheWatchSetupDone() {
        H5600ConnectionIO.start(14)
        receive("0x47 01")
        assertTrue(H5600ConnectionIO.watchSetupDone)
        H5600ConnectionIO.onDisconnected()
        assertFalse(H5600ConnectionIO.watchSetupDone)
        assertFalse(H5600ConnectionIO.isRunning)
    }
}
