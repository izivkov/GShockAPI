package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.WatchInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MTGB3000Test {

    @Before
    fun setup() {
        WatchInfo.setNameAndModel("CASIO MTG-B3000")
    }

    @Test
    fun testTimerEncoding() {
        val timerState = TimerIOFunctional.TimerState(0, 3, 0, 180)
        val encoded = TimerIOFunctional.encode(timerState)
        assertEquals(15, encoded.size)
        assertEquals(0x18.toByte(), encoded[0])
        assertEquals(0.toByte(), encoded[1])
        assertEquals(3.toByte(), encoded[2])
        assertEquals(0.toByte(), encoded[3])
    }

    @Test
    fun testSettingsEncoding() {
        val settings = JSONObject().apply {
            put("timeFormat", "24h")
            put("buttonTone", true)
            put("autoLight", false)
            put("powerSavingMode", true)
            put("lightDuration", "3s")
            put("dateFormat", "MM:DD")
            put("language", "English")
        }
        val encoded = SettingsIOFunctional.encode(settings)
        assertEquals(12, encoded.size)
        assertEquals(0x13.toByte(), encoded[0])
        // arr[1] should have 24h bit set (1)
        assertEquals(1.toByte(), encoded[1])
        // arr[2] should have light duration bit set (1 for 3s/4s)
        assertEquals(1.toByte(), encoded[2])
    }
}
