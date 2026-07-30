package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object AnalogueProtocol : WatchProtocol {
    override fun extractKey(data: String): Int? {
        return runCatching {
            val ints = Utils.toIntArray(data)
            val firstByte = ints[0]
            if (firstByte == 0x28 && ints.size > 4) {
                // Heuristic: check if this is a wrapped packet with a known key
                if (ints[1] == 0x01 && dataReceivedHandlers.containsKey(ints[4])) {
                    ints[4]
                } else if (ints[1] == 0x00 && dataReceivedHandlers.containsKey(ints[3])) {
                    // Standard envelope (non-bundle)
                    ints[3]
                } else {
                    0x28 // Fall back to WatchCondition
                }
            } else {
                firstByte
            }
        }.getOrNull()
    }

    override fun unwrapPayload(data: String, key: Int): String {
        val ints = Utils.toIntArray(data)
        if (ints.isNotEmpty() && ints[0] == 0x28 && key != 0x28) {
            val skip = if (ints.getOrNull(1) == 0x01) 4 else 3
            return Utils.fromByteArrayToHexStrWithSpaces(
                Utils.byteArrayOfIntArray(
                    ints.drop(skip).toIntArray()
                )
            )
        }
        return data
    }

    override fun getWatchConditionRequest(): String {
        return "280000"
    }

    override suspend fun setTime(timeMs: Long?) {
        org.avmedia.gshockapi.io.TimeIO.apply {
            writeDST()
            writeDSTForWorldCities()
            writeHomeTimes()
            set(timeMs)
        }

        if (org.avmedia.gshockapi.WatchInfo.hasSecondDial) {
            org.avmedia.gshockapi.io.MtgB1000TimeIO.setSecondDial()
        }
    }

    override suspend fun getTimer(): Int {
        return org.avmedia.gshockapi.io.TimerIO.request(getTimerRequest())
    }

    override fun setTimer(timerValue: Int) {
        org.avmedia.gshockapi.io.TimerIO.set(timerValue)
    }

    override fun getTimerRequest(): String {
        return "182000"
    }

    override fun getTimerSize(): Int {
        return 15
    }

    override suspend fun getHomeTime(): String {
        val raw = org.avmedia.gshockapi.io.HomeTimeIO.requestRaw(0, "24")
        return org.avmedia.gshockapi.io.HomeTimeIOFunctional.parseHomeCity(raw, 4)
    }
}
