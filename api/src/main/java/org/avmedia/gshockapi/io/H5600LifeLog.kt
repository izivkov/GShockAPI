package org.avmedia.gshockapi.io

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime

/**
 * DW-H5600 LifeLog (steps) transfer and decoding, ported from izivkov's Python
 * `src/examples/h5600_health.py` / `H5600_LIFELOG.md` (gshock_api, h5600_health branch).
 *
 * Pure code only (no BLE): [Sync] decides what to write for each notification, [Decoder] turns
 * the collected CONVOY fragments into steps. The BLE driver is [H5600LifeLogIO].
 *
 * Confirmed in the Python version: replay of capture DW-H5600-4 gives 624 steps, matching the
 * official app, with the same hourly split. Energy (kcal) scale and the other frames are not decoded.
 */
object H5600LifeLog {
    // One-byte setup requests the official app sends before its first poll; the replies to
    // ECHO_REPLY_COMMANDS are written back unchanged, as the official app does.
    val SETUP_COMMANDS = intArrayOf(0x23, 0x10, 0x11, 0x3B, 0x3A, 0x26, 0x28, 0x20, 0x2F, 0x43, 0x13, 0x45)
    val ECHO_REPLY_COMMANDS = setOf(0x11, 0x3B, 0x3A, 0x2F, 0x13, 0x45)

    val POLL = byteArrayOf(0x00, 0x00, 0x00)
    val SETUP_1 = ByteArray(10).also { it[0] = 0x04 }
    val SETUP_2 = hex("0401180018000000dc05")
    val SETUP_3 = ByteArray(14).also { it[0] = 0x06 }
    val ACK_FRAGMENT = ByteArray(15).also { it[0] = 0x07 }
    val START = hex("002effffffffff")

    const val POLL_RETRY_MS = 3_500L
    const val OVERALL_TIMEOUT_MS = 180_000L

    enum class Target { DRSP, CONVOY }
    data class Write(val target: Target, val data: ByteArray)

    /** Port of `LifeLogSync`: feed every DRSP/CONVOY notification, perform the returned writes. */
    class Sync {
        var phase = "idle"; private set
        var done = false; private set
        private var echo06 = 0
        private var sentSetup2 = false

        fun start(): List<Write> {
            phase = "polling"
            return listOf(Write(Target.CONVOY, POLL))
        }

        fun onConvoy(data: ByteArray): List<Write> {
            val out = mutableListOf<Write>()
            when {
                phase == "polling" ->
                    if (data.contentEquals(POLL)) {
                        phase = "setup"; out += Write(Target.CONVOY, SETUP_1)
                    } else {
                        out += Write(Target.CONVOY, POLL) // busy: poll again
                    }
                phase == "setup" && data.size >= 2 && data[0] == 0x04.toByte() && data[1] == 0x00.toByte() ->
                    if (!sentSetup2) {
                        sentSetup2 = true; out += Write(Target.CONVOY, SETUP_2)
                    } else {
                        out += Write(Target.CONVOY, SETUP_3); phase = "setup_wait"
                    }
                phase == "setup_wait" && data.size == 9 && data[0] == 0x06.toByte() -> {
                    // 2nd byte varies between sessions; echo the first reply, start on the second
                    echo06++
                    if (echo06 == 1) out += Write(Target.CONVOY, data.copyOf())
                    else { out += Write(Target.DRSP, START); phase = "transfer" }
                }
                phase == "transfer" ->
                    if (data.isNotEmpty() && data[0] == 0x05.toByte()) out += Write(Target.CONVOY, ACK_FRAGMENT)
                    else if (data.size >= 2 && data[0] == 0x03.toByte() && data[1] == 0x01.toByte()) {
                        phase = "post_end"; out += Write(Target.CONVOY, POLL)
                    }
            }
            return out
        }

        fun onDrsp(data: ByteArray): List<Write> {
            if (phase != "transfer" || data.size < 2) return emptyList()
            val b0 = data[0].toInt() and 0xFF
            val b1 = data[1].toInt() and 0xFF
            return when {
                data.size >= 3 && b1 == 0x2E && (b0 == 0x00 || b0 == 0x07) -> listOf(Write(Target.DRSP, data.copyOf())) // echo announce / finish
                b0 == 0x04 && b1 == 0x2E -> { done = true; phase = "done"; listOf(Write(Target.DRSP, data.copyOf())) } // echo END
                else -> emptyList()
            }
        }
    }

    // ---- decoding ----

    data class Slot(val timeUtc: LocalDateTime, val steps: Int, val energyRaw: Int?)
    data class Result(
        val headerTimeUtc: LocalDateTime,
        val headerSteps: Int,
        val headerEnergyRaw: Int,
        /** Newest first; slot i is 5*i minutes before the header time. Invalid slots dropped. */
        val slots: List<Slot>,
    )

    private val FRAME_ORDER = intArrayOf(0x65, 0x50, 0x51, 0x52, 0x53, 0x56, 0x58, 0x59, 0x5A, 0x5B, 0x63, 0x72)
    private val HEADER_MAGIC = byteArrayOf(0x00, 0x0F, 0x00, 0x00, 0x00)
    private const val MAX_STEPS_PER_SLOT = 1000
    private const val MAX_ENERGY_RAW_PER_SLOT = 5000

    /** Announcement `00 2e <idx> <len 3 bytes LE>` on DRSP -> (idx, len). Null for START/END. */
    fun announcement(drsp: ByteArray): Pair<Int, Int>? {
        if (drsp.size < 6 || drsp[0] != 0x00.toByte() || drsp[1] != 0x2E.toByte() || drsp[2] == 0xFF.toByte()) return null
        val len = (drsp[3].toInt() and 0xFF) or ((drsp[4].toInt() and 0xFF) shl 8) or ((drsp[5].toInt() and 0xFF) shl 16)
        return (drsp[2].toInt() and 0xFF) to len
    }

    /** CONVOY `05 …` notifications -> LifeLog byte stream (drop 0x05 and 2-byte checksum, XOR 0xFF). */
    fun stream(convoy: List<ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        convoy.filter { it.size >= 3 && it[0] == 0x05.toByte() }.forEach { out.write(it, 1, it.size - 3) }
        return out.toByteArray().map { (it.toInt() xor 0xFF).toByte() }.toByteArray()
    }

    fun decode(convoy: List<ByteArray>, announced: Map<Int, Int>): Result? {
        val s = stream(convoy)
        if (s.size < 20 || !s.copyOfRange(0, 5).contentEquals(HEADER_MAGIC)) return null
        val u = { i: Int -> s[i].toInt() and 0xFF }
        val tUtc = LocalDateTime.of(2000 + u(5), u(6), u(7), u(8), u(9))
        val le = ByteBuffer.wrap(s).order(ByteOrder.LITTLE_ENDIAN)
        val headerSteps = le.getInt(14)
        val headerEnergy = le.getShort(18).toInt() and 0xFFFF

        // Find each frame by its exact header (type + announced length as int32 LE), in order.
        val spans = linkedMapOf<Int, Int>()
        var pos = 20
        for (t in FRAME_ORDER) {
            val len = announced[t] ?: continue
            val needle = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put(t.toByte()).putInt(len).array()
            val p = indexOf(s, needle, pos)
            if (p >= 0) { spans[t] = p; pos = p + 5 }
        }
        val order = spans.entries.sortedBy { it.value }
        val ends = order.mapIndexed { i, e -> e.key to (if (i + 1 < order.size) order[i + 1].value else s.size) }.toMap()

        val slots = mutableListOf<Slot>()
        val stepsStart = spans[0x50]; val energyStart = spans[0x51]
        if (stepsStart != null && energyStart != null) {
            val steps = records4(s, stepsStart, ends.getValue(0x50))
            val energy = records4(s, energyStart, ends.getValue(0x51))
            for (i in 0 until minOf(steps.size, energy.size)) {
                val st = steps[i]
                if (st > MAX_STEPS_PER_SLOT) continue
                val en = energy[i]
                slots += Slot(tUtc.minusMinutes(5L * i), st, en.takeIf { it <= MAX_ENERGY_RAW_PER_SLOT })
            }
        }
        return Result(tUtc, headerSteps, headerEnergy, slots)
    }

    /** First uint16 of each 4-byte record after the 5-byte frame header. */
    private fun records4(s: ByteArray, start: Int, end: Int): List<Int> {
        val out = mutableListOf<Int>()
        var i = start + 5
        while (i + 3 < end) {
            out += (s[i].toInt() and 0xFF) or ((s[i + 1].toInt() and 0xFF) shl 8)
            i += 4
        }
        return out
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
