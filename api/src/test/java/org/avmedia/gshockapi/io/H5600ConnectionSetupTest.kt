package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-connection setup of the DW-H5600 family, checked step by step without a watch. */
class H5600ConnectionSetupTest {
    private val setup = H5600ConnectionSetup()

    private fun bytes(hex: String): ByteArray = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun read(code: Int) = H5600SetupStep.Read(code)

    private fun write(hex: String) = H5600SetupStep.Write(bytes(hex))

    private fun receive(hex: String) = setup.onMessage(bytes(hex))

    @Test fun aConnectionCheckGetsExactlyOneAnswer() {
        assertEquals(listOf(write("49 01")), receive("49 00"))
        assertEquals(listOf(write("49 01")), receive("49 00"))
    }

    @Test fun anotherCheckValueIsNotAnswered() {
        assertTrue(receive("49 05").isEmpty())
    }

    @Test fun setupNoticeTwoWritesLinkLossLevelOne() {
        assertEquals(listOf(write("00 01")), receive("47 02"))
    }

    @Test fun setupNoticeOneMarksTheWatchSetupDone() {
        assertFalse(setup.watchSetupDone)
        assertTrue(receive("47 01").isEmpty())
        assertTrue(setup.watchSetupDone)
    }

    @Test fun reconnectKindTenRunsTheWholeSetup() {
        assertEquals(listOf(read(0x11)), setup.start(10))
        assertTrue(setup.isRunning)
        assertEquals(
            listOf(write("11 01 02 03 04 0B 06 07 08 09 0A 0B 0C"), read(0x3B)),
            receive("11 01 02 03 04 0F 06 07 08 09 0A 0B 0C"),
        )
        assertEquals(listOf(write("3B 01 02 03"), read(0x3A)), receive("3B 01 02 03"))
        assertEquals(
            listOf(write("3A 00 01 02 03 04 05 06 DC 05 09 10 11"), read(0x23)),
            receive("3A 00 01 02 03 04 05 06 07 08 09 10 11"),
        )
        assertEquals(listOf(write("23 41 42 43"), read(0x28)), receive("23 41 42 43"))
        assertFalse(setup.isRunning)
    }

    @Test fun otherKindsSkipThe3AAnd3BSteps() {
        assertEquals(listOf(read(0x11)), setup.start(1))
        assertEquals(listOf(write("11 01 02 03 04 00 06 07 08"), read(0x23)), receive("11 01 02 03 04 04 06 07 08"))
    }

    private fun kindFourteenUpTo39() {
        assertEquals(listOf(read(0x23)), setup.start(14))
        assertEquals(
            listOf(write("23 41 42 43"), read(0x26), read(0x28), read(0x20), read(0x28), read(0x39)),
            receive("23 41 42 43"),
        )
        assertTrue(setup.isRunning)
    }

    @Test fun kindFourteenNeverWrites0x11Or3A3B() {
        kindFourteenUpTo39()
        assertTrue("a stray answer is not echoed", receive("11 01 02 03 04 04 06 07 08").isEmpty())
        assertTrue(receive("3B 01 02 03").isEmpty())
        assertTrue(receive("3A 00 01 02 03 04 05 06 07 08 09").isEmpty())
    }

    @Test fun thirtyNineZeroAllowedAnswersThreeAndWaitsForTwo() {
        kindFourteenUpTo39()
        receive("28 00 00 00 00 00 00 00 00 00")
        assertEquals(listOf(write("39 03")), receive("39 00"))
        assertTrue("39 03 waits for the watch's 39 02", setup.isRunning)
        assertEquals(listOf(H5600SetupStep.SetTime, read(0x28)), receive("39 02"))
        assertFalse(setup.isRunning)
    }

    @Test fun thirtyNineZeroNotPermittedAnswersFive() {
        kindFourteenUpTo39()
        receive("28 00 00 00 00 00 00 00 04 00")
        assertEquals(listOf(write("39 05")), receive("39 00"))
        assertFalse(setup.isRunning)
    }

    /** Log 2026-11-23f (GBX-H5600, kind 14): 39 04 read, 39 04 written, 47 01 from the watch 0.3 s later. */
    @Test fun thirtyNineFourIsAnsweredWithFourAndNoTimeWrite() {
        kindFourteenUpTo39()
        receive("26 00 00 00 00 00 00 00 00 00")
        receive("28 06 1E 00 00 FA 00 00 00 00")
        receive("20 23 07 00 12 00 00 00 00 00")
        receive("28 06 1E 00 00 FA 00 00 00 00")
        assertEquals(listOf(write("39 04")), receive("39 04"))
        assertFalse(setup.isRunning)
        receive("47 01")
        assertTrue(setup.watchSetupDone)
    }

    @Test fun thirtyNineFiveFromTheWatchIsNotAnswered() {
        kindFourteenUpTo39()
        assertTrue(receive("39 05").isEmpty())
    }

    @Test fun aFinishedSetupDoesNotAnswerASecondTime() {
        setup.start(1)
        receive("11 01 02 03 04 04 06 07 08")
        receive("23 41")
        assertTrue(receive("11 01 02 03 04 04 06 07 08").isEmpty())
        assertTrue(receive("23 41").isEmpty())
        assertTrue("the setup starts once per connection", setup.start(14).isEmpty())
    }

    @Test fun theLifeLogSetupWritesTheSameValuesBack() {
        assertTrue(bytes("11 01 02 03 04 0B 06").contentEquals(H5600ConnectionSetup.writeBackValue(0x11, bytes("11 01 02 03 04 0F 06"))))
        assertTrue(
            bytes("3A 00 01 02 03 04 05 06 DC 05").contentEquals(
                H5600ConnectionSetup.writeBackValue(0x3A, bytes("3A 00 01 02 03 04 05 06 07 08")),
            ),
        )
        assertTrue(bytes("43 10 27").contentEquals(H5600ConnectionSetup.writeBackValue(0x43, bytes("43 10 27"))))
    }
}
