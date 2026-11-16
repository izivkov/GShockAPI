package org.avmedia.gshockapi.model

/** DW-H5600 sound / notification mode: byte[1] of packet 0x42. */
enum class SoundMode(val code: Int) {
    ALL_OFF(0x00),
    SOUND_ONLY(0x02),
    CUSTOM(0x05),
    VIBE_ONLY(0x08),
    ALL_ON(0x0A), // all five confirmed on the user's watch 2026-11-16
    ;

    companion object {
        fun fromCode(code: Int): SoundMode? = entries.firstOrNull { it.code == code }
    }
}

/** DW-H5600 watch-face display item: bytes[2..5] of packet 0x51. */
enum class WatchFaceItem(val code: Int) {
    STEPS(0x00),
    SUNRISE_SUNSET(0x12),
    ;

    companion object {
        fun fromCode(code: Int): WatchFaceItem? = entries.firstOrNull { it.code == code }
    }
}
