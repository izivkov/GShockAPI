package org.avmedia.gshockapi.io

/** One thing the connection setup asks the shell to do. */
sealed interface H5600SetupStep {
    /** One-byte read request on the read-request characteristic. */
    data class Read(val code: Int) : H5600SetupStep

    /** A packet written to the all-features characteristic. */
    class Write(val bytes: ByteArray) : H5600SetupStep {
        override fun equals(other: Any?) = other is Write && bytes.contentEquals(other.bytes)

        override fun hashCode() = bytes.contentHashCode()

        override fun toString() = "Write(${bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }})"
    }

    /** Set the watch's time (the watch asked for it with `39 02`). */
    data object SetTime : H5600SetupStep
}

/**
 * Pure core of the per-connection setup of the DW-H5600 family (DW-H5600, GBX-H5600).
 *
 * No I/O: given the kind of connection and the watch's messages, it decides which reads and writes
 * follow, so the sequence can be unit-tested. One instance per connection. Sequence, as the
 * official app runs it on every connection (traffic of the GBX-H5600, November 2026):
 *
 *  - kinds 13 and 14 (the watch reconnected by itself): read `0x23` and write it back, read `0x26`,
 *    `0x28`, `0x20`, `0x28`, `0x39`, then answer the watch's `39` value: `39 00` with `39 03` (time
 *    change allowed by the `0x28` value) or `39 05`, `39 02` with a time update, `39 04` with `39 04`.
 *    `0x11`, `0x3A` and `0x3B` are not written on these kinds.
 *  - other kinds: read `0x11` and write it back with bit 2 of byte 5 cleared; on kinds 9 and 10 also
 *    read `0x3B` (written back unchanged) and `0x3A` (written back with bytes 8 and 9 set to `DC 05`);
 *    then read `0x23` and write it back, and read `0x28`. The app sets the time as for any watch.
 *
 * The watch then notifies `47 01`: its side of the setup is done. On any link it may also notify
 * `49 00` (connection check, answered with `49 01`) and `47 02` (asks for Link Loss alert level 1,
 * written as `00 01`). Without these answers the watch drops the link after about 45 seconds.
 */
class H5600ConnectionSetup {
    private enum class Phase { IDLE, WAIT_11, WAIT_3B, WAIT_3A, WAIT_23, WAIT_39, DONE }

    private var phase = Phase.IDLE
    private var reconnectKind = false
    private var selfConnect = false
    private var condition: ByteArray? = null

    /** True while the setup waits for one of its answers. */
    val isRunning: Boolean
        get() = phase != Phase.IDLE && phase != Phase.DONE

    /** True once the watch sent `47 01` on this connection. */
    var watchSetupDone = false
        private set

    /** Starts the setup for connection [kind] (byte 8 of the `0x10` answer). Only the first call starts it. */
    fun start(kind: Int): List<H5600SetupStep> {
        if (phase != Phase.IDLE) return emptyList()
        reconnectKind = kind in ALWAYS_CONNECT_KINDS
        selfConnect = kind in SELF_CONNECT_KINDS
        if (selfConnect) return readName()
        phase = Phase.WAIT_11
        return listOf(H5600SetupStep.Read(BLE_SETTINGS))
    }

    fun onMessage(data: ByteArray): List<H5600SetupStep> {
        val code = data.firstOrNull()?.let { it.toInt() and 0xFF } ?: return emptyList()
        return when (code) {
            CONNECTION_CHECK -> if (data.u8(1) == 0) listOf(write(CONNECTION_CHECK, 0x01)) else emptyList()
            SETUP_NOTICE -> onSetupNotice(data)
            BLE_SETTINGS -> onBle(data)
            CODE_3B -> on3b(data)
            CODE_3A -> on3a(data)
            WATCH_NAME -> onName(data)
            WATCH_CONDITION -> emptyList<H5600SetupStep>().also { if (selfConnect) condition = data.copyOf() }
            TIME_MANAGER -> on39(data)
            else -> emptyList()
        }
    }

    private fun onSetupNotice(data: ByteArray): List<H5600SetupStep> =
        when (data.u8(1)) {
            1 -> emptyList<H5600SetupStep>().also { watchSetupDone = true }
            2 -> listOf(H5600SetupStep.Write(linkLossFrame(1)))
            else -> emptyList()
        }

    private fun onBle(data: ByteArray): List<H5600SetupStep> {
        if (phase != Phase.WAIT_11 || data.size < 6) return emptyList()
        val echo = H5600SetupStep.Write(writeBackValue(BLE_SETTINGS, data))
        if (!reconnectKind) return listOf(echo) + readName()
        phase = Phase.WAIT_3B
        return listOf(echo, H5600SetupStep.Read(CODE_3B))
    }

    private fun on3b(data: ByteArray): List<H5600SetupStep> {
        if (phase != Phase.WAIT_3B) return emptyList()
        phase = Phase.WAIT_3A
        return listOf(H5600SetupStep.Write(data.copyOf()), H5600SetupStep.Read(CODE_3A))
    }

    private fun on3a(data: ByteArray): List<H5600SetupStep> {
        if (phase != Phase.WAIT_3A || data.size < 10) return emptyList()
        return listOf(H5600SetupStep.Write(writeBackValue(CODE_3A, data))) + readName()
    }

    private fun readName(): List<H5600SetupStep> {
        phase = Phase.WAIT_23
        return listOf(H5600SetupStep.Read(WATCH_NAME))
    }

    private fun onName(data: ByteArray): List<H5600SetupStep> {
        if (phase != Phase.WAIT_23) return emptyList()
        val echo = H5600SetupStep.Write(data.copyOf())
        if (selfConnect) {
            phase = Phase.WAIT_39
            return listOf(echo) + SELF_CONNECT_READS.map { H5600SetupStep.Read(it) }
        }
        phase = Phase.DONE
        return listOf(echo, H5600SetupStep.Read(WATCH_CONDITION))
    }

    /** The watch's `39` value: read during the setup, and notified again by the watch. Kinds 13 and 14 only. */
    private fun on39(data: ByteArray): List<H5600SetupStep> {
        if (!selfConnect) return emptyList()
        return when (data.u8(1)) {
            0 -> answer39(if (timeChangeAllowed()) 3 else 5)
            2 -> {
                phase = Phase.DONE
                listOf(H5600SetupStep.SetTime, H5600SetupStep.Read(WATCH_CONDITION))
            }
            4 -> answer39(4)
            else -> emptyList() // 39 05: the watch aborted; other values are not known
        }
    }

    private fun answer39(value: Int): List<H5600SetupStep> {
        // After 39 03 the watch asks for the time with 39 02; any other answer ends the setup.
        if (value != 3) phase = Phase.DONE
        return listOf(write(TIME_MANAGER, value))
    }

    /** Bit 2 of byte 8 of the `0x28` value set: the watch does not allow a time change now. Unknown: allowed. */
    private fun timeChangeAllowed(): Boolean {
        val value = condition?.u8(8) ?: return true
        return value and 0x04 == 0
    }

    companion object {
        const val CONNECTION_CHECK = 0x49
        const val SETUP_NOTICE = 0x47
        private const val BLE_SETTINGS = 0x11
        private const val CODE_3A = 0x3A
        private const val CODE_3B = 0x3B
        private const val WATCH_NAME = 0x23
        private const val WATCH_CONDITION = 0x28
        private const val TIME_MANAGER = 0x39
        private val ALWAYS_CONNECT_KINDS = setOf(9, 10)
        private val SELF_CONNECT_KINDS = setOf(13, 14)
        private val SELF_CONNECT_READS = listOf(0x26, WATCH_CONDITION, 0x20, WATCH_CONDITION, TIME_MANAGER)

        /** Codes whose answers and notices the setup reads. */
        val CODES: Set<Int> =
            setOf(CONNECTION_CHECK, SETUP_NOTICE, BLE_SETTINGS, CODE_3A, CODE_3B, WATCH_NAME, WATCH_CONDITION, TIME_MANAGER)

        /** Link Loss alert [level] (0 none, 1 mild, 2 high), written to the all-features characteristic. */
        fun linkLossFrame(level: Int): ByteArray = byteArrayOf(0x00, level.toByte())

        /**
         * What the official app writes back after reading [code]: `0x11` with bit 2 of byte 5 cleared,
         * `0x3A` with bytes 8 and 9 set to `DC 05`, any other value unchanged. Used by this setup and by
         * the LifeLog setup, so both write the same bytes.
         */
        fun writeBackValue(
            code: Int,
            reply: ByteArray,
        ): ByteArray {
            val out = reply.copyOf()
            if (code == BLE_SETTINGS && out.size >= 6) out[5] = (out[5].toInt() and 0x04.inv()).toByte()
            if (code == CODE_3A && out.size >= 10) {
                out[8] = 0xDC.toByte()
                out[9] = 0x05
            }
            return out
        }

        private fun write(vararg values: Int) = H5600SetupStep.Write(ByteArray(values.size) { values[it].toByte() })

        private fun ByteArray.u8(index: Int): Int? = getOrNull(index)?.let { it.toInt() and 0xFF }
    }
}
