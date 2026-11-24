package org.avmedia.gshockapi.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.avmedia.gshockapi.casio.ConnectionKeepAlive

/**
 * The tests' log sink: the last lines in memory, in the app's line format (`time [Source] text`), time from
 * [ConnectionKeepAlive.clock]. A test class that reads lines calls [install] in its constructor.
 */
object TestLog : LibraryLogSink {
    private const val KEEP = 300
    private val lines = ArrayDeque<String>()

    fun install() {
        LibraryLog.sink = this
    }

    override fun log(source: LibraryLog.Source, text: String) {
        val line = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ConnectionKeepAlive.clock())) +
            " [" + source.name + "] " + text
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > KEEP) lines.removeFirst()
        }
    }

    fun recentEvents(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }
}
