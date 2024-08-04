package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GET_SET_MODE
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

object SettingsIO {
    val MASK_24_HOURS = 0b00000001
    val MASK_BUTTON_TONE_OFF = 0b00000010
    val MASK_AUTO_LIGHT_OFF = 0b00000100
    val POWER_SAVING_MODE = 0b00010000
    val DO_NOT_DISTURB_OFF = 0b01000000

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<Settings>
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings {
        return CachedIO.request("GET_SETTINGS", ::getBasicSettings) as Settings
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun getBasicSettings(key: String): Settings {
        DeferredValueHolder.deferredResult = CompletableDeferred<Settings>()
        Connection.sendMessage("{ action: '$key'}")
        return DeferredValueHolder.deferredResult.await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun set(settings: Settings) {
        val settingJson = Gson().toJson(settings)
        fun setFunc () {Connection.sendMessage("{action: \"SET_SETTINGS\", value: ${settingJson}}")}
        CachedIO.set("GET_SETTINGS", ::setFunc)
    }

    fun onReceived(data: String) {
        val jsonData = decodeToJson(data).toString()
        val model = Gson().fromJson(jsonData, Settings::class.java)
        DeferredValueHolder.deferredResult.complete(model)
    }

    /*
Time Format:
24 h:   13 05 00 00 00 00 00 00 00 00 00 00
12 h:   13 04 00 00 00 00 00 00 00 00 00 00

Date format:
mm:dd   13 04 00 00 00 00 00 00 00 00 00 00
dd:mm   13 04 00 00 01 00 00 00 00 00 00 00

Languages:
english:13 04 00 00 00 00 00 00 00 00 00 00
spanish:13 04 00 00 00 01 00 00 00 00 00 00
fr:     13 04 00 00 00 02 00 00 00 00 00 00
german: 13 04 00 00 00 03 00 00 00 00 00 00
italian:13 04 00 00 00 04 00 00 00 00 00 00
russian:13 04 00 00 00 05 00 00 00 00 00 00

Button Tone:
on:     13 04 00 00 00 00 00 00 00 00 00 00
off:    13 06 00 00 00 00 00 00 00 00 00 00

Auto Light: ?????
on:     13 00 00 00 00 00 00 00 00 00 00 00
off:    13 04 00 00 00 00 00 00 00 00 00 00

Light Duration:
2s      13 04 00 00 00 00 00 00 00 00 00 00
4s      13 04 01 00 00 00 00 00 00 00 00 00

Power Saving:
on      13 04 00 00 00 00 00 00 00 00 00 00
off     13 14 00 00 00 00 00 00 00 00 00 00

Combination:
auto light: on
Power Saving: off
        13 02 00 00 00 00 00 00 00 00 00 00

24 hours:
        13 03 00 00 00 00 00 00 00 00 00 00

Byte 2 as binary:
24 hours:       00000001
button tone     00000010
light off:      00000100
pwr. saving off:00010000
 */
    private fun decodeToJson(casioArray: String): JSONObject {
        return createJsonSettings(casioArray)
    }

    private fun createJsonSettings(settingString: String): JSONObject {
        val settings = Settings()

        val settingArray = Utils.toIntArray(settingString)

        if (settingArray[1] and MASK_24_HOURS != 0) {
            settings.timeFormat = "24h"
        } else {
            settings.timeFormat = "12h"
        }
        settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        settings.autoLight = settingArray[1] and MASK_AUTO_LIGHT_OFF == 0
        settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0
        settings.DnD = settingArray[1] and DO_NOT_DISTURB_OFF == 0

        if (settingArray[4] == 1) {
            settings.dateFormat = "DD:MM"
        } else {
            settings.dateFormat = "MM:DD"
        }
        if (settingArray[5] == 0) {
            settings.language = "English"
        }
        if (settingArray[5] == 1) {
            settings.language = "Spanish"
        }
        if (settingArray[5] == 2) {
            settings.language = "French"
        }
        if (settingArray[5] == 3) {
            settings.language = "German"
        }
        if (settingArray[5] == 4) {
            settings.language = "Italian"
        }
        if (settingArray[5] == 5) {
            settings.language = "Russian"
        }

        if (settingArray[2] == 1) {
            settings.lightDuration = "4s"
        } else {
            settings.lightDuration = "2s"
        }

        return JSONObject(Gson().toJson(settings))
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        IO.writeCmd(
            GET_SET_MODE.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val settings = JSONObject(message).get("value") as JSONObject
        IO.writeCmd(GET_SET_MODE.SET, SettingsEncoder.encode(settings))
    }

    object SettingsEncoder {
        fun encode(settings: JSONObject): ByteArray {
            val arr = ByteArray(12)
            arr[0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()
            if (settings.get("timeFormat") == "24h") {
                arr[1] = (arr[1] or MASK_24_HOURS.toByte())
            }
            if (settings.get("buttonTone") == false) {
                arr[1] = (arr[1] or MASK_BUTTON_TONE_OFF.toByte())
            }
            if (settings.get("autoLight") == false) {
                arr[1] = (arr[1] or MASK_AUTO_LIGHT_OFF.toByte())
            }
            if (settings.get("powerSavingMode") == false) {
                arr[1] = (arr[1] or POWER_SAVING_MODE.toByte())
            }

            if (settings.get("DnD") == false) {
                arr[1] = (arr[1] or DO_NOT_DISTURB_OFF.toByte())
            }

            if (settings.get("lightDuration") == "4s") {
                arr[2] = 1
            }
            if (settings.get("dateFormat") == "DD:MM") arr[4] = 1

            when (settings.get("language")) {
                "English" -> arr[5] = 0
                "Spanish" -> arr[5] = 1
                "French" -> arr[5] = 2
                "German" -> arr[5] = 3
                "Italian" -> arr[5] = 4
                "Russian" -> arr[5] = 5
            }

            return arr
        }

        private fun setDnDFlag(flag: Byte, value: Boolean): Byte {
            return if (value) flag or DO_NOT_DISTURB_OFF.toByte() else flag and DO_NOT_DISTURB_OFF.toByte().inv()
        }
    }
}