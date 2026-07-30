package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object StandardProtocol : WatchProtocol {
    override fun extractKey(data: String): Int? {
        return runCatching { Utils.toIntArray(data)[0] }.getOrNull()
    }

    override fun unwrapPayload(data: String, key: Int): String {
        return data
    }

    override fun getWatchConditionRequest(): String {
        return "28"
    }

    override suspend fun setTime(timeMs: Long?) {
        org.avmedia.gshockapi.io.TimeIO.apply {
            writeDST()
            writeDSTForWorldCities()
            writeWorldCities()
            set(timeMs)
        }
    }

    override suspend fun getTimer(): Int {
        return org.avmedia.gshockapi.io.TimerIO.request(getTimerRequest())
    }

    override fun setTimer(timerValue: Int) {
        org.avmedia.gshockapi.io.TimerIO.set(timerValue)
    }

    override fun getTimerRequest(): String {
        return "18"
    }

    override fun getTimerSize(): Int {
        return 7
    }

    override suspend fun getHomeTime(): String {
        val raw = org.avmedia.gshockapi.io.HomeTimeIO.requestRaw(0, "1F")
        return org.avmedia.gshockapi.io.HomeTimeIOFunctional.parseHomeCity(raw, 2)
    }
}
