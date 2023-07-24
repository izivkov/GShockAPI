/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.io.*
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import java.util.*

object MessageDispatcher {

    @RequiresApi(Build.VERSION_CODES.O)
    private val watchSenders = mapOf<String, (String) -> Unit>(
        "RESET_HAND" to HandIO::sendToWatch,
        "GET_ALARMS" to AlarmsIO::sendToWatch,
        "SET_ALARMS" to AlarmsIO::sendToWatchSet,
        "SET_REMINDERS" to EventsIO::sendToWatchSet,
        "GET_SETTINGS" to SettingsIO::sendToWatch,
        "SET_SETTINGS" to SettingsIO::sendToWatchSet,
        "GET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatch,
        "SET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatchSet,
        "GET_TIMER" to TimerIO::sendToWatch,
        "SET_TIMER" to TimerIO::sendToWatchSet,
        "SET_TIME" to TimeIO::sendToWatchSet,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendToWatch(message: String) {
        val action = JSONObject(message).get("action")
        watchSenders[action]!!.invoke(message)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val toJsonConverters = mapOf<Int, (String) -> JSONObject>(
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code to AlarmsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code to AlarmsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code to DstForWorldCitiesIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code to EventsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code to EventsIO::toJsonTitle,
        CasioConstants.CHARACTERISTICS.CASIO_TIMER.code to TimerIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code to WorldCitiesIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code to DstWatchStateIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code to WatchNameIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code to WatchConditionIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_APP_INFORMATION.code to AppInfoIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code to ButtonPressedIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code to SettingsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code to TimeAdjustmentIO::toJson,

        CasioConstants.CHARACTERISTICS.ERROR.code to ErrorIO::toJson,
        CasioConstants.CHARACTERISTICS.UNKNOWN.code to UnknownIO::toJson,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun toJson(data: String): JSONObject {
        val intArray = Utils.toIntArray(data)
        val key = intArray[0]
        if (toJsonConverters[key] == null) {
            Timber.e("GShockAPI", "Unknown key: $key")
        }
        return toJsonConverters[key]!!.invoke(data)
    }
}
