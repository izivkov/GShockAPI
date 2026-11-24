package org.avmedia.gshockapi.casio

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.io.AppInfoIO
import org.avmedia.gshockapi.log.TestLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The first OCW-S7000 connection (owner log 2026-11-24 01:27): "request 28 failed: The answer is shorter than the layout
 * needs" for its 4-byte condition answer, reads of timer, alarms and basic settings it does not have (58 s until its name
 * was read), its 0x22 app information at risk of a scratchpad write, and a failed phone notification write that the log
 * showed as "request 20 failed". Answers below are the watch's own from that log.
 */
class OcwFirstConnectionTest {
    init { TestLog.install() }

    private class Transport : WatchTransport {
        val sent = CopyOnWriteArrayList<Pair<WatchChannel, List<Int>>>()
        @Volatile var ok = true

        override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
            sent += channel to frame.map { it.toInt() and 0xFF }
            return ok
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = Transport()
    private lateinit var queue: WatchQueue
    private val allFeatures = CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID

    @Before fun install() {
        queue = WatchQueue(scope, transport, readTimeoutMs = 400)
        WatchLink.queue = queue
        TestLog.clear()
    }

    @After fun restore() {
        WatchInfo.reset()
    }

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun conditionRead(name: String): Outcome = runBlocking {
        WatchInfo.setNameAndModel(name)
        val read = queue.enqueue(GetSetMode.GET, b(0x28))
        while (transport.sent.isEmpty()) delay(5)
        queue.onNotification(allFeatures, b(0x28, 0x13, 0x1B, 0x00))
        read.await()
    }

    @Test fun theOcwsShortConditionAnswerIsNotAFailure() {
        val outcome = conditionRead("CASIO OCW-S7000")
        assertTrue("the OCW's 0x28 answer is as long as the library's decode needs: ${outcome.reason}", outcome.ok)
        assertFalse(TestLog.recentEvents().joinToString("\n"), TestLog.recentEvents().any { it.contains("shorter than the layout") })
    }

    @Test fun theH5600LayoutsStillApplyToTheH5600Family() {
        assertFalse("a GBX-H5600 answers 0x28 with 9 value bytes", conditionRead("CASIO GBX-H5600").ok)
    }

    @Test fun aConnectionOfAnOcwWritesNothingTo0x22() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        // The OCW's own app information: no scratchpad marker (0x95), which on a scratchpad watch means "write it".
        AppInfoIO.onReceived("0x22 70 43 FD DF 79 92 21 D0 E9 00 02")
        Thread.sleep(200)
        assertTrue("written: ${transport.sent}", transport.sent.none { it.second.firstOrNull() == 0x22 })
    }

    @Test fun theOcwIsNotAskedForTimerAlarmsOrBasicSettings() = runBlocking {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        val protocol = WatchInfo.protocol
        withTimeoutOrNull(1_000) {
            protocol.getTimer()
            protocol.getAlarms()
            protocol.getBasicSettings()
        }
        val codes = transport.sent.map { it.second.first() }
        assertTrue("requests sent: $codes", codes.none { it in setOf(0x13, 0x15, 0x16, 0x18) })
    }

    @Test fun theOcwShowsNoPhoneNotificationsEvenWithTheCharacteristic() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        assertFalse(WatchInfo.showsAppNotifications(serviceFound = true))
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertTrue(WatchInfo.showsAppNotifications(serviceFound = true))
        assertFalse(WatchInfo.showsAppNotifications(serviceFound = false))
    }

    @Test fun aFailedNotificationWriteIsLoggedAsOneAndNotAsARequestOfItsFirstByte() = runBlocking {
        transport.ok = false
        // An inverted notification text whose first byte happens to be 0x20.
        assertFalse(queue.enqueue(GetSetMode.NOTIFY, b(0x20, 0xFE, 0xFF, 0xFF)).await().ok)
        val events = TestLog.recentEvents().joinToString("\n")
        assertFalse(events, events.contains("request 20 failed"))
        assertTrue(events, events.contains("notification write failed"))
        assertEquals(1, transport.sent.size)
    }
}
