package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.io.CasioIO
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.util.TimeZone

object CasioTimeZone {
    class WorldCity(private val city: String, val index: Int) {
        fun createCasioString(): String {
            return ("1F" + "%02x".format(index) + Utils.toHexStr(city.take(18))
                .padEnd(36, '0')) // pad to 40 chars
        }
    }

    object TimeZoneHelper {
        fun parseCity(timeZone: String): String? {

            // validate
            if (!timeZone.contains("/"))
                return null

            return try {
                val city = timeZone.split('/')[1]
                city.uppercase().replace('_', ' ')
            } catch (e: Error) {
                null
            }
        }
    }

    fun setHomeTime(timeZone: String) {
        val city = TimeZoneHelper.parseCity(timeZone) ?: return

        var worldCity = WorldCity(city, 0)

        Timber.i("----> Setting Home time")
        val encodedHomeCity = worldCity.createCasioString()
        CasioIO.writeCmd(
            0xE, encodedHomeCity
        )
    }
}