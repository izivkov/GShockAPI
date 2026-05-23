package org.avmedia.gshockapi

import org.junit.Assert.*
import org.junit.Test
import org.avmedia.gshockapi.io.CasioAlertNotificationIO
import org.avmedia.gshockapi.io.TimeIO
import java.time.LocalDateTime

class ExampleUnitTest {
    @Test
    fun currentTimeEncoderUsesCasioCurrentTimeLayout() {
        val encoded = TimeIO.TimeEncoder.prepareCurrentTime(
            LocalDateTime.of(2026, 5, 23, 14, 5, 6, 500_000_000)
        )

        assertArrayEquals(
            byteArrayOf(
                0xEA.toByte(),
                0x07,
                0x05,
                0x17,
                0x0E,
                0x05,
                0x06,
                0x06,
                0x80.toByte(),
                0x01,
            ),
            encoded
        )
    }

    @Test
    fun currentTimeEncoderMapsSundayToSeven() {
        val encoded = TimeIO.TimeEncoder.prepareCurrentTime(
            LocalDateTime.of(2026, 5, 24, 0, 0, 0, 999_999_999)
        )

        assertEquals(7, encoded[7].toInt())
        assertEquals(255.toByte(), encoded[8])
    }

    @Test
    fun watchInfoRecognizesEqb501dAsEqbModel() {
        WatchInfo.setNameAndModel("CASIO EQB-501D")

        assertEquals(WatchInfo.WatchModel.EQB, WatchInfo.model)
        assertEquals("EQB-501D", WatchInfo.shortName)
    }

    @Test
    fun legacyCasioAlertEncodesEmailNotification() {
        val encoded = CasioAlertNotificationIO.encode(
            AppNotification.create(
                type = NotificationType.EMAIL,
                timestamp = "20260523T120000",
                app = "Mail",
                title = "Inbox",
                text = "Long fallback text",
                shortText = "New mail"
            )
        )

        assertArrayEquals(
            byteArrayOf(
                0x07,
                0x01,
                0x01,
                0x4E,
                0x65,
                0x77,
                0x20,
                0x6D,
                0x61,
                0x69,
                0x6C,
            ),
            encoded
        )
    }

    @Test
    fun legacyCasioAlertKeepsUtf8CharactersWholeWhenTruncating() {
        val encoded = CasioAlertNotificationIO.encode(
            AppNotification(
                type = NotificationType.MESSAGE,
                timestamp = "20260523T120000",
                app = "Chat",
                title = "äöüäöüäöüx",
                text = ""
            )
        )

        assertEquals(0x07, encoded[0].toInt())
        assertEquals(0x05, encoded[1].toInt())
        assertEquals(0x01, encoded[2].toInt())
        assertEquals("äöüäöüäöü", encoded.copyOfRange(3, encoded.size).toString(Charsets.UTF_8))
    }
}
