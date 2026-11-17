package org.avmedia.gshockapi

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.io.RawSettingIO
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * RawSettingIO against a fake watch. Covers the behaviours behind the DW-H5600 fixes: shared
 * requests, retry of unanswered GETs, fast failure on "FF 81 xx", serialized read-modify-write.
 */
class RawSettingIOTest {
    private val code = 0x42
    private val requests = CopyOnWriteArrayList<Int>()
    private val writes = CopyOnWriteArrayList<IntArray>()

    /** What the fake watch holds for [code]; null = it ignores the request. */
    @Volatile private var packet: IntArray? = intArrayOf(0x42, 0x05, 0x3D, 0x7E, 0x27, 0x7F)
    @Volatile private var ignoreNext = 0
    @Volatile private var rejectNext = 0

    private fun hex(p: IntArray) = p.joinToString(" ") { "%02X".format(it) }

    @Before
    fun setUp() {
        RawSettingIO.tryTimeoutMs = 150
        RawSettingIO.retryBaseDelayMs = 20
        RawSettingIO.sendRequest = { c ->
            requests += c
            Thread {
                Thread.sleep(30)
                when {
                    ignoreNext > 0 -> ignoreNext--
                    rejectNext > 0 -> { rejectNext--; RawSettingIO.onRejected(c) }
                    else -> packet?.let { RawSettingIO.onReceived(hex(it)) }
                }
            }.start()
        }
        RawSettingIO.sendPacket = { p -> writes += p.copyOf(); packet = p.copyOf() }
    }

    @After
    fun tearDown() {
        RawSettingIO.tryTimeoutMs = 3_000L
        RawSettingIO.retryBaseDelayMs = 400L
    }

    @Test
    fun concurrentReadersShareOneRequest() = runBlocking {
        val results = (1..3).map { async { RawSettingIO.request(code) } }.awaitAll()
        assertEquals("one GET for three readers", 1, requests.size)
        results.forEach { assertArrayEquals(packet, it) }
    }

    @Test
    fun unansweredRequestIsRetriedWithANewOne() = runBlocking {
        ignoreNext = 1
        val p = RawSettingIO.request(code)
        assertEquals(2, requests.size)
        assertArrayEquals(packet, p)
    }

    @Test
    fun rejectionIsRetriedWithoutWaitingForTheTimeout() = runBlocking {
        rejectNext = 1
        val start = System.currentTimeMillis()
        RawSettingIO.request(code)
        assertEquals(2, requests.size)
        assertTrue("retried before the 150 ms try timeout", System.currentTimeMillis() - start < 150 + 100)
    }

    @Test
    fun throwsAfterAllTriesFail() = runBlocking {
        ignoreNext = 10
        try {
            RawSettingIO.request(code, tries = 3)
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertEquals(3, requests.size)
        }
    }

    @Test
    fun modifySkipsTheWriteWhenNothingChanges() = runBlocking {
        val before = RawSettingIO.modify(code) { it } // same packet back
        assertArrayEquals(packet, before)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun concurrentModifiesDoNotUndoEachOther() = runBlocking {
        val a = launch { RawSettingIO.modify(code) { p -> p.also { it[1] = 0x08 } } }
        val b = launch { delay(5); RawSettingIO.modify(code) { p -> p.also { it[5] = 0x6F } } }
        a.join(); b.join()
        assertEquals(2, writes.size)
        assertEquals("first change kept", 0x08, packet!![1])
        assertEquals("second change applied", 0x6F, packet!![5])
    }
}
