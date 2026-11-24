package org.avmedia.gshockapi.log

import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLogTest {
    private val got = ArrayList<Pair<LibraryLog.Source, String>>()

    @After fun restore() = TestLog.install()

    @Test fun everyLineGoesToTheSinkWithItsSource() {
        LibraryLog.sink = LibraryLogSink { s, t -> got += s to t }
        ConnectionKeepAlive.log("49 00 received, 49 01 written")
        LibraryLog.log(LibraryLog.Source.Workouts, "Workout read: 1C the watch is ready")
        assertEquals(
            listOf(LibraryLog.Source.Watch to "49 00 received, 49 01 written", LibraryLog.Source.Workouts to "Workout read: 1C the watch is ready"),
            got,
        )
    }

    @Test fun oneLineEvenWithNewlines() {
        LibraryLog.sink = LibraryLogSink { s, t -> got += s to t }
        LibraryLog.log(LibraryLog.Source.Health, "a\nb\rc")
        assertEquals(listOf(LibraryLog.Source.Health to "a b c"), got)
    }

    @Test fun aFailingSinkDoesNotBreakTheCaller() {
        LibraryLog.sink = LibraryLogSink { _, _ -> throw IllegalStateException("disk full") }
        ConnectionKeepAlive.log("still running")
    }

    @Test fun withoutASinkLinesGoToTimber() {
        LibraryLog.sink = LibraryLog.TIMBER
        LibraryLog.log(LibraryLog.Source.Watch, "no sink set")
    }
}
