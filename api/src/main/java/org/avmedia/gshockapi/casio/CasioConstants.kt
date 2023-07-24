/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-27, 9:52 a.m.
 */
package org.avmedia.gshockapi.casio

import java.util.*

object CasioConstants {
    val CASIO_SERVICE = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
    val CASIO_VIRTUAL_SERVER_SERVICE = UUID.fromString("26eb0007-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_VIRTUAL_SERVER_FEATURES = UUID.fromString("26eb0008-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_A_NOT_W_REQ_NOT = UUID.fromString("26eb0009-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_A_NOT_COM_SET_NOT = UUID.fromString("26eb000a-b012-49a8-b1f8-394fb2032b0f")
    val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Immediate Alert
    val IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")

    // Alert
    val ALERT_SERVICE_UUID = UUID.fromString("26eb0000-b012-49a8-b1f8-394fb2032b0f")
    val ALERT_CHARACTERISTIC_UUID = UUID.fromString("00002a46-0000-1000-8000-00805f9b34fb")
    val ALERT_NOTIFICATION_CONTROL_POINT = UUID.fromString("00002a44-0000-1000-8000-00805f9b34fb")

    // More Alert
    val MORE_ALERT_SERVICE_UUID = UUID.fromString("26eb001a-b012-49a8-b1f8-394fb2032b0f")
    val MORE_ALERT_UUID = UUID.fromString("26eb001b-b012-49a8-b1f8-394fb2032b0f")
    val MORE_ALERT_FOR_LONG_UUID = UUID.fromString("26eb001c-b012-49a8-b1f8-394fb2032b0f")

    // Phone Alert
    val CASIO_PHONE_ALERT_STATUS_SERVICE = UUID.fromString("26eb0001-b012-49a8-b1f8-394fb2032b0f")
    val RINGER_CONTROL_POINT = UUID.fromString("00002a40-0000-1000-8000-00805f9b34fb")

    // Phone Finder
    val CASIO_IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("26eb0005-b012-49a8-b1f8-394fb2032b0f")
    val ALERT_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

    // Current Time
    val CURRENT_TIME_SERVICE_UUID = UUID.fromString("26eb0002-b012-49a8-b1f8-394fb2032b0f")
    val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
    val LOCAL_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb")

    // Control Mode
    val WATCH_FEATURES_SERVICE_UUID = UUID.fromString("26eb000d-b012-49a8-b1f8-394fb2032b0f")
    val WATCH_CTRL_SERVICE_UUID = UUID.fromString("26eb0018-b012-49a8-b1f8-394fb2032b0f")
    val KEY_CONTAINER_CHARACTERISTIC_UUID = UUID.fromString("26eb0019-b012-49a8-b1f8-394fb2032b0f")
    val NAME_OF_APP_CHARACTERISTIC_UUID = UUID.fromString("26eb001d-b012-49a8-b1f8-394fb2032b0f")
    val FUNCTION_SWITCH_CHARACTERISTIC = UUID.fromString("26eb001e-b012-49a8-b1f8-394fb2032b0f")
    const val MUSIC_MESSAGE = "Music"

    // Modern Watches - All Features
    val CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID =
        UUID.fromString("26eb002c-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_ALL_FEATURES_CHARACTERISTIC_UUID =
        UUID.fromString("26eb002d-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID =
        UUID.fromString("26eb0023-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_CONVOY_CHARACTERISTIC_UUID = UUID.fromString("26eb0024-b012-49a8-b1f8-394fb2032b0f")

    val CASIO_NOTIFICATION_CHARACTERISTIC_UUID =
        UUID.fromString("26eb0030-b012-49a8-b1f8-394fb2032b0f")

    // Link Loss
    val LINK_LOSS_SERVICE = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb")

    // TxPower
    val TX_POWER_SERVICE_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
    val TX_POWER_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")

    // Settings
    val CASIO_SETTING_FOR_BLE_CHARACTERISTIC_UUID =
        UUID.fromString("26eb000f-b012-49a8-b1f8-394fb2032b0f")
    val CASIO_SETTING_FOR_ALM_CHARACTERISTIC_UUID =
        UUID.fromString("26eb0013-b012-49a8-b1f8-394fb2032b0f")

    // Notification Types - GB6900
    const val CALL_NOTIFICATION_ID: Byte = 3
    const val MAIL_NOTIFICATION_ID: Byte = 1
    const val CALENDAR_NOTIFICATION_ID: Byte = 7
    const val SNS_NOTIFICATION_ID: Byte = 13
    const val SMS_NOTIFICATION_ID: Byte = 5

    // Notification Types - GBX100
    const val CATEGORY_ADVERTISEMENT: Byte = 13
    const val CATEGORY_BUSINESS: Byte = 9
    const val CATEGORY_CONDITION: Byte = 12
    const val CATEGORY_EMAIL: Byte = 6
    const val CATEGORY_ENTERTAINMENT: Byte = 11
    const val CATEGORY_HEALTH_AND_FITNESS: Byte = 8
    const val CATEGORY_INCOMING_CALL: Byte = 1
    const val CATEGORY_LOCATION: Byte = 10
    const val CATEGORY_MISSED_CALL: Byte = 2
    const val CATEGORY_NEWS: Byte = 7
    const val CATEGORY_OTHER: Byte = 0
    const val CATEGORY_SCHEDULE_AND_ALARM: Byte = 5
    const val CATEGORY_SNS: Byte = 4
    const val CATEGORY_VOICEMAIL: Byte = 3
    const val CASIO_CONVOY_DATATYPE_STEPS = 0x04
    const val CASIO_CONVOY_DATATYPE_CALORIES = 0x05
    const val CASIO_FAKE_RING_SLEEP_DURATION = 3000
    const val CASIO_FAKE_RING_RETRIES = 10
    const val CASIO_AUTOREMOVE_MESSAGE_DELAY = 10000

    // experimentally found:
    val CASIO_GET_DEVICE_NAME: UUID =
        UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb") // returns 0x43 41 53 49 4F 20 47 57 2D 42 35 36 30 30 00 00 (CASIO GW-B5600)

    val CASIO_TX_POWER_LEVEL: UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")
    val CASIO_APPEARANCE: UUID = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")

    // possibly not supported...
    val SERIAL_NUMBER_STRING: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")

    enum class CHARACTERISTICS(val code: Int) {
        CASIO_WATCH_NAME(0x23),
        CASIO_APP_INFORMATION(0x22),
        CASIO_BLE_FEATURES(0x10),
        CASIO_SETTING_FOR_BLE(0x11),
        CASIO_ADVERTISE_PARAMETER_MANAGER(0x3b),
        CASIO_CONNECTION_PARAMETER_MANAGER(0x3a),
        CASIO_MODULE_ID(0x26),
        CASIO_WATCH_CONDITION(0x28), // battery %
        CASIO_VERSION_INFORMATION(0x20),
        CASIO_DST_WATCH_STATE(0x1d),
        CASIO_DST_SETTING(0x1e),
        CASIO_SERVICE_DISCOVERY_MANAGER(0x47),
        CASIO_CURRENT_TIME(0x09),
        CASIO_SETTING_FOR_USER_PROFILE(0x45),
        CASIO_SETTING_FOR_TARGET_VALUE(0x43),
        ALERT_LEVEL(0x0a),
        CASIO_SETTING_FOR_ALM(0x15),
        CASIO_SETTING_FOR_ALM2(0x16),
        CASIO_SETTING_FOR_BASIC(0x13),
        CASIO_CURRENT_TIME_MANAGER(0x39),
        CASIO_WORLD_CITIES(0x1f),
        CASIO_REMINDER_TITLE(0x30),
        CASIO_REMINDER_TIME(0x31),
        CASIO_TIMER(0x18),
        ERROR(0xFF),
        UNKNOWN(0x0A)
    }

    enum class Model {
        MODEL_CASIO_GENERIC, MODEL_CASIO_6900B, MODEL_CASIO_5600B, MODEL_CASIO_GBX100, MODEL_CASIO_STB1000
    }

    enum class ConfigurationOption {
        OPTION_GENDER, OPTION_WEIGHT, OPTION_HEIGHT, OPTION_WRIST, OPTION_BIRTHDAY, OPTION_STEP_GOAL, OPTION_DISTANCE_GOAL, OPTION_ACTIVITY_GOAL, OPTION_AUTOLIGHT, OPTION_TIMEFORMAT, OPTION_KEY_VIBRATION, OPTION_OPERATING_SOUNDS, OPTION_ALL
    }
}