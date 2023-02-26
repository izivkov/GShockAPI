/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 10:57 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 10:57 a.m.
 */

package org.avmedia.gshockapi.casio

import com.google.gson.Gson
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

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

object SettingsDecoder {

    fun toJson(casioArray: String): JSONObject {
        return createJsonSettings(casioArray)
    }

    private fun createJsonSettings(settingString: String): JSONObject {
        val MASK_24_HOURS = 0b00000001
        val MASK_BUTTON_TONE_OFF = 0b00000010
        val MASK_LIGHT_OFF = 0b00000100
        val POWER_SAVING_MODE = 0b00010000

        val settings = Settings()

        val settingArray = Utils.toIntArray(settingString)

        if (settingArray[1] and MASK_24_HOURS != 0) {
            settings.timeFormat = "24h"
        } else {
            settings.timeFormat = "12h"
        }
        settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        settings.autoLight = settingArray[1] and MASK_LIGHT_OFF == 0
        settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0

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

    fun getTimeAdjustment(data: String, settings: Settings) {
        // syncing off: 110f0f0f0600500004000100->80<-37d2
        // syncing on:  110f0f0f0600500004000100->00<-37d2

        CasioIsAutoTimeOriginalValue.value = data // save original data for future use
        settings.timeAdjustment = Utils.toIntArray(data)[12] == 0
    }

    fun toJsonTimeAdjustment(settings: Settings): JSONObject {
        return JSONObject("{\"timeAdjustment\": ${settings.timeAdjustment} }")
    }

    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }
}
