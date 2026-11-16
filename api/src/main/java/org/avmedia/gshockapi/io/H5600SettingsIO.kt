package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem
import timber.log.Timber

/**
 * DW-H5600 (module 3516 family) settings decoded from the official app's BLE traffic.
 * See FINDINGS.md in the h5600-probe folder for the raw captures.
 *
 * 0x42 (6 bytes): [0]=0x42, [1]=sound mode, [2..5]=per-function CUSTOM table (kept as-is).
 * 0x51 (6 bytes): [0]=0x51, [1]=0x02 (unknown, kept), [2..5]=display item (all four set).
 *
 * Writes are read-modify-write and are refused unless the watch is a DW-H5600 family model
 * and the packet read back has the expected length and code.
 */
object H5600SettingsIO {
    const val SOUND_MODE_CODE = 0x42
    const val WATCH_FACE_CODE = 0x51
    const val SMART_ALARM_CODE = 0x50

    /** CASIO_CURRENT_TIME_MANAGER. The official app requests it after connecting and writes the reply back. */
    const val TIME_MANAGER_CODE = 0x39

    /**
     * Connection handshake copied from the official CASIO app (capture 2026-11-16 17:57:06):
     *   app -> 39 (request), watch -> 39 04, app -> 39 04 (written back unchanged), watch -> 47 01.
     * Without it the watch drops Smart Sync's connection after ~45 s; with the official app it stays
     * connected. Echoes exactly what the watch sent; nothing else is written.
     */
    /**
     * Home location for sunrise/sunset: `24 00 01 <lat f64 BE> <lon f64 BE> 04`. Trailing byte 0x04
     * as the current official app writes it (user's capture 2026-11-16 23:08; izivkov's older capture
     * had 0x01). The watch computes sunrise/sunset from it; without it the watch shows dashes.
     */
    /** Last home location given by the app; re-sent after each time/city sync ([resendHomeLocation]). */
    @Volatile
    private var homeLocation: Pair<Double, Double>? = null

    fun rememberHomeLocation(latitude: Double, longitude: Double) {
        if (latitude in -90.0..90.0 && longitude in -180.0..180.0) homeLocation = latitude to longitude
    }

    /**
     * Supplied by the app: the location to send right now (real or "weather" coordinates). Asked at
     * re-send time so a time sync never goes out without a location, even before the app has
     * remembered one in this process.
     */
    @Volatile
    var homeLocationProvider: (() -> Pair<Double, Double>?)? = null

    /**
     * Called by TimeIO in the middle of a time sync, after the DST/world-city writes (1D, 1E) and
     * before the city names (1F) and the time (09): the exact place the official app writes 0x24,
     * so the watch recalculates sunrise/sunset with the location already in place.
     */
    fun writeLocationInTimeSync() {
        if (!isSupported()) return
        val loc = runCatching { homeLocationProvider?.invoke() }.getOrNull() ?: homeLocation ?: return
        setHomeLocation(loc.first, loc.second)
    }

    /** Re-sends the location after a time/city sync (no-op if none or not a DW-H5600). */
    fun resendHomeLocation() {
        val loc = runCatching { homeLocationProvider?.invoke() }.getOrNull() ?: homeLocation ?: return
        if (isSupported()) setHomeLocation(loc.first, loc.second)
    }

    fun setHomeLocation(latitude: Double, longitude: Double) {
        check(isSupported()) { "Not a DW-H5600 family watch (model=${WatchInfo.model})" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Bad coordinates" }
        homeLocation = latitude to longitude
        val buf = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(0x24.toByte()).put(0x00).put(0x01).putDouble(latitude).putDouble(longitude).put(0x04)
        RawSettingIO.write(buf.array().map { it.toInt() and 0xFF }.toIntArray())
    }

    /** Read-only: reads packets the official app reads on every connect, for logging/comparison. */
    suspend fun readDiagnostics(): Map<Int, String> =
        listOf(0x45, 0x4F).associateWith { code ->
            runCatching { RawSettingIO.request(code).joinToString(" ") { "%02X".format(it) } }
                .getOrElse { "error: ${it.message}" }
        }

    suspend fun connectionHandshake() {
        if (!isSupported()) return
        val reply = RawSettingIO.request(TIME_MANAGER_CODE)
        check(reply.isNotEmpty() && reply[0] == TIME_MANAGER_CODE && reply.size <= 4) {
            "Unexpected 0x39 reply: ${reply.joinToString(" ") { "%02X".format(it) }}"
        }
        RawSettingIO.write(reply)
    }
    private const val PACKET_SIZE = 6

    fun isSupported(): Boolean = WatchInfo.model == WatchInfo.WatchModel.DW_H5600

    private suspend fun readChecked(code: Int): IntArray {
        check(isSupported()) { "Not a DW-H5600 family watch (model=${WatchInfo.model})" }
        val packet = RawSettingIO.request(code)
        check(packet.size == PACKET_SIZE && packet[0] == code) {
            "Unexpected 0x%02X packet: %s".format(code, packet.joinToString(" ") { "%02X".format(it) })
        }
        return packet
    }

    /** Raw sound packet as hex, for mapping the per-function CUSTOM bits. */
    suspend fun getSoundPacketHex(): String =
        readChecked(SOUND_MODE_CODE).joinToString(" ") { "%02X".format(it) }

    suspend fun getSoundMode(): SoundMode? =
        readChecked(SOUND_MODE_CODE).let { SoundMode.fromCode(it[1]) }

    suspend fun setSoundMode(mode: SoundMode) {
        val packet = readChecked(SOUND_MODE_CODE)
        if (packet[1] == mode.code) {
            Timber.i("Sound mode already $mode, not writing")
            return
        }
        RawSettingIO.write(packet.copyOf().also { it[1] = mode.code })
    }

    /** Returns null if the four item bytes disagree or hold an unknown code. */
    suspend fun getWatchFaceItem(): WatchFaceItem? = getWatchFaceCode()?.let { WatchFaceItem.fromCode(it) }

    /** Raw display-item code (also for values not yet in [WatchFaceItem]); null if the four bytes disagree. */
    suspend fun getWatchFaceCode(): Int? =
        readChecked(WATCH_FACE_CODE).let { p ->
            val items = p.copyOfRange(2, PACKET_SIZE).distinct()
            if (items.size == 1) items[0] else null
        }

    /**
     * Writes a full 17-byte 0x13 (basic settings) packet as-is. Intended only for restoring a
     * packet previously read from this same watch (e.g. after stock Smart Sync zeroed bytes 12-16).
     */
    fun restoreBasicSettings(packet: IntArray) {
        check(isSupported()) { "Not a DW-H5600 family watch (model=${WatchInfo.model})" }
        check(packet.size == 17 && packet[0] == 0x13) { "Not a 17-byte 0x13 packet" }
        RawSettingIO.write(packet)
        SettingsIO.rememberRaw(org.avmedia.gshockapi.utils.Utils.byteArrayOfIntArray(packet))
    }

    /** Watch face: byte 1 of 0x51, 1 = simple face (weekday), 2 = customisable face (item). */
    suspend fun getWatchFaceNumber(): Int = readChecked(WATCH_FACE_CODE)[1]

    suspend fun setWatchFaceNumber(face: Int) {
        require(face == 1 || face == 2) { "face must be 1 or 2" }
        val packet = readChecked(WATCH_FACE_CODE)
        if (packet[1] == face) return
        RawSettingIO.write(packet.copyOf().also { it[1] = face })
    }

    suspend fun getSmartAlarm(): org.avmedia.gshockapi.model.SmartAlarm {
        check(isSupported()) { "Not a DW-H5600 family watch (model=${WatchInfo.model})" }
        val p = RawSettingIO.request(SMART_ALARM_CODE)
        check(p.size == 5 && p[0] == SMART_ALARM_CODE) { "Unexpected 0x50 packet: ${p.joinToString(" ") { "%02X".format(it) }}" }
        return org.avmedia.gshockapi.model.SmartAlarm(p[1], p[2], p[3] and 0x01 != 0, p[3] and 0x02 != 0, p[4])
    }

    /** Writes the smart-alarm slot; other flag bits read from the watch are kept. */
    suspend fun setSmartAlarm(a: org.avmedia.gshockapi.model.SmartAlarm) {
        val p = RawSettingIO.request(SMART_ALARM_CODE)
        check(p.size == 5 && p[0] == SMART_ALARM_CODE) { "Unexpected 0x50 packet" }
        require(a.hour in 0..23 && a.minute in 0..59 && a.windowMin in 0..120) { "Bad smart alarm" }
        var flags = p[3] and 0x03.inv()
        if (a.enabled) flags = flags or 0x01
        if (a.snooze) flags = flags or 0x02
        val wanted = intArrayOf(SMART_ALARM_CODE, a.hour, a.minute, flags, a.windowMin)
        if (!wanted.contentEquals(p)) RawSettingIO.write(wanted)
    }

    suspend fun setWatchFaceItem(item: WatchFaceItem) {
        val packet = readChecked(WATCH_FACE_CODE)
        val updated = packet.copyOf().also { for (i in 2 until PACKET_SIZE) it[i] = item.code }
        if (updated.contentEquals(packet)) {
            Timber.i("Watch face item already $item, not writing")
            return
        }
        RawSettingIO.write(updated)
    }
}
