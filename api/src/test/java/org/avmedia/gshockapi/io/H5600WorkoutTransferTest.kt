package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scripted watch holding one running workout in slot 0 (header at 4096, one data block at 8192, one lap block at 12288).
 * It echoes the session request `00 1C` + 8 zero as the real watch does (`001c0000000000000000`).
 */
internal class FakeWatch(
    private val inbox: Channel<WorkoutMsg>,
    private val corruptFirstHeader: Boolean = false,
    /** Like the watch: a fetched mark sets the transferred bits of the group header (from byte 525), so the next read skips the slot. */
    private val keepsMarks: Boolean = false,
    /** A second workout in slot 1 (header at 4352, data block at 16384, lap block at 20480). */
    twoWorkouts: Boolean = false,
    /** Sent instead of the answer to the first read request after a fetched mark (or to [replayOn]): the watch replaying its last read. */
    private val staleReplay: List<WorkoutMsg>? = null,
    /** The read (`read cat addr`, decimal) answered by [staleReplay] instead of the first read after a mark. */
    private val replayOn: String? = null,
    /** The test clock (virtual time), to see how long after the mark answer the next request came. */
    private val now: () -> Long = { 0L },
    /**
     * As the real watch on 2026-11-24 17:20: a request of another category while the last block read (1F, 20) was not
     * closed with `04 cat` is answered with these frames (a replay of that block read), every time.
     */
    private val replaysUnclosed: List<WorkoutMsg>? = null,
) : WorkoutLink {
    val events = mutableListOf<String>()
    val marks = mutableListOf<ByteArray>()
    /** Per mark: the time from its answer to the next read request. */
    val gapsAfterMark = mutableListOf<Long>()
    private var markAnsweredAt: Long? = null
    /** Per block read request: the time since the last block read ended (its `07`). */
    val gapsAfterBlockEnd = mutableListOf<Long>()
    private var blockEndAt: Long? = null
    private var replayed = false
    /** The block category whose last read ended with its sector end and got no `04 cat` yet. */
    private var openBlock: Int? = null
    private var corrupted = false
    private val store = HashMap<Pair<Int, Long>, ByteArray>()

    init {
        val samples = (0 until 3).fold(ByteArray(0)) { acc, i -> acc + WorkoutTestData.runningSample(i, 120 + i) }
        val lap = WorkoutTestData.runningLap(0)
        val group = ByteArray(1024) { 0xFF.toByte() }
        group[0] = (if (twoWorkouts) 0xFC else 0xFE).toByte()
        for (i in 0 until 4) store[0x1D to i * 256L] = group.copyOfRange(i * 256, i * 256 + 256)
        store[0x1E to 4096L] = WorkoutTestData.header(dataBytes = samples.size.toLong(), lapBytes = lap.size.toLong(), dataAddr = 8192, lapAddr = 12288)
        store[0x1F to 8192L] = WorkoutTestData.block(samples)
        store[0x20 to 12288L] = WorkoutTestData.block(lap)
        if (twoWorkouts) {
            store[0x1E to 4352L] = WorkoutTestData.header(dataBytes = samples.size.toLong(), lapBytes = lap.size.toLong(), dataAddr = 16384, lapAddr = 20480)
            store[0x1F to 16384L] = WorkoutTestData.block(samples)
            store[0x20 to 20480L] = WorkoutTestData.block(lap)
        }
    }

    private fun send(channel: H5600Channel, vararg b: Int) = inbox.trySend(WorkoutMsg(channel, ByteArray(b.size) { b[it].toByte() }))

    override fun bulk(data: ByteArray) {
        when (data[0].toInt()) {
            0 -> send(H5600Channel.CONVOY, 0, 0, 0)
            4 -> send(H5600Channel.CONVOY, 4, 0, 0)
            6 -> if (data.size == 14 && data[1].toInt() == 0) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(6, 0, 2, 0, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0))) else send(H5600Channel.CONVOY, 6, 1)
            7 -> {
                marks += data
                events += "mark"
                if (keepsMarks) {
                    val part = store.getValue(0x1D to 512L).copyOf()
                    for (i in 0 until 13) part[525 - 512 + i] = data[2 + i]
                    store[0x1D to 512L] = part
                }
                inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(7, 0)))
                markAnsweredAt = now()
            }
        }
    }

    override fun drsp(data: ByteArray) {
        if (data.size == 2 && data[0].toInt() == 4) { events += "close ${data[1]}"; if (data[1].toInt() == openBlock) openBlock = null; return }
        if (data.size != 10 || data[0].toInt() != 0) { events += "drsp ${data[0]}"; return }
        val cat = data[1].toInt()
        val addr = (0 until 4).sumOf { (data[2 + it].toLong() and 0xFF) shl (8 * it) }
        val len = (0 until 4).sumOf { (data[6 + it].toInt() and 0xFF) shl (8 * it) }
        if (cat == 0x1C) { inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, data.copyOf())); return }
        events += "read $cat $addr"
        markAnsweredAt?.let { gapsAfterMark += now() - it }
        if (len != 256) blockEndAt?.let { gapsAfterBlockEnd += now() - it }
        if (replaysUnclosed != null && openBlock != null && openBlock != cat) {
            events += "unclosed replay"
            for (m in replaysUnclosed) inbox.trySend(WorkoutMsg(m.channel, m.data.copyOf()))
            return
        }
        val afterMark = markAnsweredAt != null
        markAnsweredAt = null
        val replayHere = if (replayOn != null) "read $cat $addr" == replayOn else afterMark
        if (replayHere && staleReplay != null && !replayed) {
            replayed = true
            events += "stale replay"
            for (m in staleReplay) inbox.trySend(WorkoutMsg(m.channel, m.data.copyOf()))
            return
        }
        var raw = WorkoutTestData.chunked(store.getValue(cat to addr), if (len == 256) 256 else 512)
        if (corruptFirstHeader && cat == 0x1D && addr == 0L && !corrupted) {
            corrupted = true
            raw = raw.copyOf().also { it[3] = (it[3].toInt() xor 0x40).toByte() }
        }
        var pos = 0
        while (pos < raw.size) {
            val n = minOf(19, raw.size - pos)
            inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(5) + raw.copyOfRange(pos, pos + n)))
            pos += n
        }
        // As the real watch: a 256-byte read (1D, 1E) ends with `09`; a block read (1F, 20) ends
        // with its sector end `07`, and then the watch waits for the next request (no `09`).
        if (len == 256) send(H5600Channel.DRSP, 0x09) else { send(H5600Channel.DRSP, 0x07); blockEndAt = now(); openBlock = cat }
    }
}

class H5600WorkoutTransferTest {
    @Test
    fun oneWorkoutIsStoredThenMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val got = mutableListOf<org.avmedia.gshockapi.model.WorkoutSession>()
        val result = WorkoutTransfer(watch, inbox, sink = { got += it; watch.events += "stored"; true }).run()

        assertNull(result.error)
        assertEquals(1, result.downloaded)
        assertEquals(1, got.size)
        assertEquals(3, got[0].samples.size)
        assertEquals(1, got[0].laps.size)
        assertEquals(listOf("stored", "mark"), watch.events.filter { it == "stored" || it == "mark" })
        assertEquals(1, watch.marks.size)
        assertEquals(0x07, watch.marks[0][0].toInt())
        assertEquals(1, watch.marks[0][1].toInt())
        assertEquals(0xFE, watch.marks[0][2].toInt() and 0xFF)
    }

    /** A workout read left no line at all. Every category and the stored mark are logged now. */
    @Test
    fun eachCategoryAndSlotIsLogged() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(FakeWatch(inbox), inbox, sink = { true }, log = { lines += it }).run()
        assertNull(result.error)
        for (want in listOf("1C requested", "1C the watch is ready", "1D group header read, 1 on the watch, 1 new (slots [0])", "1E 1 workout headers",
            "slot 0 1F 1 data blocks", "slot 0 20 1 lap blocks", "slot 0 stored and marked")) {
            assertTrue("$want in $lines", lines.any { it.contains(want) })
        }
    }

    @Test
    fun aWorkoutTheSinkRefusesIsNeverMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val result = WorkoutTransfer(watch, inbox, sink = { false }).run()
        assertNotNull(result.error)
        assertEquals(0, result.downloaded)
        assertTrue(watch.marks.isEmpty())
    }

    @Test
    fun aSinkThatThrowsIsNeverMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val result = WorkoutTransfer(watch, inbox, sink = { error("disk full") }).run()
        assertNotNull(result.error)
        assertTrue(watch.marks.isEmpty())
    }

    @Test
    fun aBadChunkIsReadAgainAndTheTransferGoesOn() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox, corruptFirstHeader = true)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertNull(result.error)
        assertEquals(1, result.downloaded)
        assertEquals(2, watch.events.count { it == "read 29 0" })
    }

    /** Answers only the start message, with the given state and reason; records what the transfer writes. */
    private class StartOnlyWatch(private val inbox: Channel<WorkoutMsg>, private val state: Int, private val reason: Int) : WorkoutLink {
        val bulks = mutableListOf<ByteArray>()

        override fun drsp(data: ByteArray) {
            if (data.size == 10 && data[1].toInt() == 0x1C) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, data.copyOf()))
        }

        override fun bulk(data: ByteArray) {
            bulks += data
            if (data[0].toInt() == 0) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(0, state.toByte(), reason.toByte())))
        }
    }

    // On the tide path start state 0 means "ok, go on"; an earlier reading of the workout start took 0 as "cannot". Our code treats 0 as go
    // on (the one real watch run worked that way); this pins it, and pins that a non-zero state with reason 1 or 2 is refused.
    @Test
    fun startStateZeroGoesOnToTheConnectionParameters() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = StartOnlyWatch(inbox, state = 0, reason = 0)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertTrue(watch.bulks.any { it[0].toInt() == 4 })
        assertTrue(result.error!!.contains("connection parameters"))
        assertTrue(!result.refused)
    }

    @Test
    fun aNonZeroStartStateWithReasonOneIsARefusalForTheBattery() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = StartOnlyWatch(inbox, state = 1, reason = 1)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertTrue(result.refused)
        assertTrue(result.error!!.contains("battery"))
    }

    /** The frames the watch sent instead of an answer, from a resource like `h5600_workout_stale_replay.txt`. */
    private fun staleReplayFrames(resource: String = "/h5600_workout_stale_replay.txt"): List<WorkoutMsg> =
        javaClass.getResourceAsStream(resource)!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (_, ch, hex) = line.trim().split(" ")
                WorkoutMsg(if (ch == "BULK") H5600Channel.CONVOY else H5600Channel.DRSP, hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }

    /**
     * Slot 0 was read and marked; our request for slot 1 went out 12 ms after the mark answer and
     * the watch replayed its previous read (echo `00 20 00 60 ...`, 512 bytes, `07 20`). The stale sector is answered, our
     * request goes out once more and slot 1 is read; the next request waits at least 1 s after the mark answer.
     */
    @Test
    fun aStaleReplayAfterTheMarkIsAnsweredAndTheRequestSentAgain() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val writes = mutableListOf<String>()
        val watch = FakeWatch(inbox, twoWorkouts = true, staleReplay = staleReplayFrames(), now = { testScheduler.currentTime })
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(watch, inbox, sink = { true }, log = { lines += it; writes += it }).run()
        val why = "error ${result.error}, events ${watch.events.takeLast(12)}, log ${lines.takeLast(8)}"

        assertNull(why, result.error)
        assertEquals(why, 2, result.downloaded)
        assertEquals(why, 2, watch.marks.size)
        assertEquals("the stale read was replayed once: $why", 1, watch.events.count { it == "stale replay" })
        assertEquals("slot 1 data asked for twice (once replayed, once again): $why", 2, watch.events.count { it == "read 31 16384" })
        assertTrue("its whole end was answered: $why", lines.any { it.endsWith("tx 07 20 00 00 00 00 00") })
        assertTrue("one log line: $why", lines.count { it.contains("stale replay of 20 00016000, request sent again") } == 1)
        assertTrue("the request after the mark waits 1 s: ${watch.gapsAfterMark}", watch.gapsAfterMark.isNotEmpty() && watch.gapsAfterMark.all { it >= 1_000L })
    }

    /**
     * Between two block reads of one workout (data 1F, then laps 20) the watch replayed the
     * previous read (echo `00 1F 00 70 01 ...`, 512 bytes, `07 1F`). Handled the same way, and each block request waits a
     * short pause after the previous block's sector end.
     */
    @Test
    fun aStaleReplayBetweenBlockReadsIsAnsweredAndTheRequestSentAgain() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox, staleReplay = staleReplayFrames("/h5600_workout_stale_replay_block.txt"), replayOn = "read 32 12288", now = { testScheduler.currentTime })
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(watch, inbox, sink = { true }, log = { lines += it }).run()
        val why = "error ${result.error}, events ${watch.events.takeLast(12)}, log ${lines.takeLast(8)}"

        assertNull(why, result.error)
        assertEquals(why, 1, result.downloaded)
        assertEquals(why, 1, watch.events.count { it == "stale replay" })
        assertEquals("lap block asked for twice: $why", 2, watch.events.count { it == "read 32 12288" })
        assertTrue("its whole end was answered: $why", lines.any { it.endsWith("tx 07 1F 00 00 00 00 00") })
        assertEquals("one log line: $why", 1, lines.count { it.contains("stale replay of 1F 00017000, request sent again") })
        assertTrue("each block request waits after the last block end: ${watch.gapsAfterBlockEnd}", watch.gapsAfterBlockEnd.isNotEmpty() && watch.gapsAfterBlockEnd.all { it >= 300L })
    }

    /**
     * 2026-11-24 17:20: the data chain (1F) ended with our `07 1F` echo and the laps request (`00 20`) came next. The watch
     * replayed its last 1F read twice and the workout was not stored. The 17:21 retry, with the data blocks from the cache,
     * asked `00 20` right after the closed 1E read and the watch served it. The last block of each chain is closed with
     * `04 cat`, as `09 cat` is followed by `04 cat`; the blocks inside a chain are not.
     */
    @Test
    fun eachBlockChainIsClosedBeforeTheNextCategory() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox, twoWorkouts = true, replaysUnclosed = staleReplayFrames("/h5600_workout_lap_replay_1f.txt"))
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(watch, inbox, sink = { true }, log = { lines += it }).run()
        val why = "error ${result.error}, events ${watch.events.takeLast(14)}, log ${lines.takeLast(8)}"

        assertEquals("no request met an unclosed block read: $why", 0, watch.events.count { it == "unclosed replay" })
        assertNull(why, result.error)
        assertEquals(why, 2, result.downloaded)
        val e = watch.events.filter { it.startsWith("read 31") || it.startsWith("read 32") || it == "close 31" || it == "close 32" || it == "mark" }
        assertEquals(why, listOf("read 31 8192", "close 31", "read 32 12288", "close 32", "mark",
            "read 31 16384", "close 31", "read 32 20480", "close 32", "mark"), e)
    }
}
