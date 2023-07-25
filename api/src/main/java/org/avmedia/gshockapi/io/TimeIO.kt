package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.io.DstWatchStateIO.DTS_VALUE.*
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import java.time.*
import java.util.*
import kotlin.reflect.KSuspendFunction1

/*
We have now incorporated setting the Casio timezone and rules when setting time, in latest version of the API tagged 1.2.8. Here are some notes on the implementation.

When using the API, the app doesn't need to keep a list of world cities to set the time on the watch. It can simply provide the current timezone
and the API will set the timezone and any applicable Daylight Savings Time rules on the watch. This way, when a user travels from one city to another
and adjusts the time accordingly, the timezone and DST rules will be set accordingly.

In order to set timezone, we must provide data for register 0x1E, something like this:

0x1E 00 02 76 10 00 00

From the Gadgetbridge project, we have information on what these values mean:

                   0x1E 00  A  B   OFF DSTOFF DSTRULES
LOS ANGELES                 A1 00  E0  04     01
DENVER                      54 00  E4  04     01
CHICAGO                     42 00  E8  04     01
NEW YORK                    CA 00  EC  04     01
...
The CasioTimeZone class handles the code for setting the values of time-zone, offset, and DST offset on the watch.
It takes a time-zone string in the form of "Pacific/Pago_Pago" and a Casio-specific DST rules code, and
calculates the corresponding offset and DST offset. A table is then created of all possible CasioTimeZone's
that can be set on the watch.

The findTimeZone function will take a standard timezone obtained from the Android Locale and try to find the
Casio-equivalent from the table. If not found, we create a synthetic CasioTimeZone from the Offset and DST Offset
of the passed timezone, and a value of 0 for the Casio Rules. Most of the timezones not in the Casio table have no DST,
so this is a reasonable assumption.

Since are are using ZoneId API to obtain the TZ offset and DST offset, we don't have to worry about changes in the timezones,
since presumably the API will change accordingly.

For setting DST value ON/OFF/AUTO, we update the 0x1D register. The data looks something like this:
0x1D 00 01 00 03 0C 01 0C 01 FF FF FF FF FF FF

From Gadgetbridge, we see:
There are six clocks on the Casio GW-B5600
0 is the main clock
1-5 are the world clocks

0x1d 00 01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
0x1d 02 03 DST2 DST3 TZ2A TZ2B TZ3A TZ3B ff ff ff ff ff
0x1d 04 05 DST4 DST5 TZ4A TZ4B TZ5A TZ5B ff ff ff ff ff
DST: bitwise flags; bit0: DST on, bit1: DST auto
Here again, are are only concerned with the main clock, so we need to update the value of DST0.
If the timezone has DST, we set this flag to ON | AUTO, or 3
If the timezone has no DST, we set the flag to 0
 */
@RequiresApi(Build.VERSION_CODES.O)
object TimeIO {
    init {}

    private var timeZone: String = TimeZone.getDefault().id
    private var casioTimezone = CasioTimeZoneHelper.findTimeZone(timeZone)

    fun setTimezone(timeZone: String) {
        this.timeZone = timeZone
        casioTimezone = CasioTimeZoneHelper.findTimeZone(timeZone)
    }

    suspend fun set() {
        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B2100) {
            initializeForSettingTimeForB2100()
        } else {
            initializeForSettingTimeForB5600()
        }

        Connection.sendMessage(
            "{action: \"SET_TIME\", value: ${
                Clock.systemDefaultZone().millis()
            }}"
        )
    }

    private suspend fun getDSTWatchState(state: CasioIO.DTS_STATE): String {
        return DstWatchStateIO.request(state)
    }

    private suspend fun getDSTWatchStateWithTZ(state: CasioIO.DTS_STATE): String {
        val origDTS = getDSTWatchState(state)
        CasioIO.removeFromCache(origDTS)
        val hasDST = if (casioTimezone.dstOffset > 0) ON_AND_AUTO else OFF
        return DstWatchStateIO.setDST(origDTS, hasDST)
    }

    private suspend fun getDSTForWorldCities(cityNum: Int): String {
        return DstForWorldCitiesIO.request(cityNum)
    }

    private suspend fun getDSTForWorldCitiesWithTZ(cityNum: Int): String {
        var origDSTForCity = getDSTForWorldCities(cityNum)
        CasioIO.removeFromCache(origDSTForCity)
        return DstForWorldCitiesIO.setDST(origDSTForCity, casioTimezone)
    }

    private suspend fun getWorldCities(cityNum: Int): String {
        return WorldCitiesIO.request(cityNum)
    }

    private suspend fun getWorldCitiesWithTZ(cityNum: Int): String {
        val newCity = WorldCitiesIO.parseCity(timeZone)
        val encoded = WorldCitiesIO.encodeAndPad(newCity!!, cityNum)
        CasioIO.removeFromCache(encoded)
        return encoded
    }

    /**
     * This function is internally called by [setTime] to initialize some values.
     */
    private suspend fun initializeForSettingTimeForB5600() {
        // Before we can set time, we must read and write back these values.
        // Why? Not sure, ask Casio

        suspend fun <T> readAndWrite(function: KSuspendFunction1<T, String>, param: T) {
            val ret: String = function(param)
            val shortStr = Utils.toCompactString(ret)
            CasioIO.writeCmd(0xE, shortStr)
        }

        readAndWrite(::getDSTWatchStateWithTZ, CasioIO.DTS_STATE.ZERO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.TWO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.FOUR)

        readAndWrite(::getDSTForWorldCitiesWithTZ, 0)
        readAndWrite(::getDSTForWorldCities, 1)
        readAndWrite(::getDSTForWorldCities, 2)
        readAndWrite(::getDSTForWorldCities, 3)
        readAndWrite(::getDSTForWorldCities, 4)
        readAndWrite(::getDSTForWorldCities, 5)

        readAndWrite(::getWorldCitiesWithTZ, 0)
        readAndWrite(::getWorldCities, 1)
        readAndWrite(::getWorldCities, 2)
        readAndWrite(::getWorldCities, 3)
        readAndWrite(::getWorldCities, 4)
        readAndWrite(::getWorldCities, 5)
    }

    private suspend fun initializeForSettingTimeForB2100() {
        // Before we can set time, we must read and write back these values.
        // Why? Not sure, ask Casio

        suspend fun <T> readAndWrite(function: KSuspendFunction1<T, String>, param: T) {
            val ret: String = function(param)
            val shortStr = Utils.toCompactString(ret)
            CasioIO.writeCmd(0xE, shortStr)
        }

        readAndWrite(::getDSTWatchStateWithTZ, CasioIO.DTS_STATE.ZERO)

        readAndWrite(::getDSTForWorldCitiesWithTZ, 0)
        readAndWrite(::getDSTForWorldCities, 1)

        readAndWrite(::getWorldCitiesWithTZ, 0)
        readAndWrite(::getWorldCities, 1)
    }

    fun sendToWatchSet(message: String) {
        val dateTimeMs: Long = JSONObject(message).get("value") as Long

        val dateTime =
            Instant.ofEpochMilli(dateTimeMs).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val timeData = TimeEncoder.prepareCurrentTime(dateTime)
        var timeCommand =
            Utils.byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_CURRENT_TIME.code) + timeData

        CasioIO.writeCmd(0x000e, timeCommand)
    }

    object TimeEncoder {
        fun prepareCurrentTime(date: LocalDateTime): ByteArray {
            val arr = ByteArray(10)
            val year = date.year
            arr[0] = (year ushr 0 and 0xff).toByte()
            arr[1] = (year ushr 8 and 0xff).toByte()
            arr[2] = date.month.value.toByte()
            arr[3] = date.dayOfMonth.toByte()
            arr[4] = date.hour.toByte()
            arr[5] = date.minute.toByte()
            arr[6] = date.second.toByte()
            arr[7] = date.dayOfWeek.value.toByte()
            arr[8] = (date.nano / 1000000).toByte()
            arr[9] = 1 // or 0?
            return arr
        }
    }
}