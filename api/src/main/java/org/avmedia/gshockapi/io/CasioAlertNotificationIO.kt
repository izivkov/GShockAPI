package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.io.IO.writeCmd

object CasioAlertNotificationIO {
    private const val MAX_ALERT_TEXT_BYTES = 18
    private const val ALERT_COUNT = 1

    enum class AlertCategory(val code: Int) {
        EMAIL(1),
        INCOMING_CALL(3),
        SMS_MMS(5),
        SCHEDULE(7),
        SNS(13)
    }

    /**
     * Sends the legacy Casio Alert Notification payload used by watches that
     * expose the all-features characteristic but not the newer notification
     * characteristic. The watch receives:
     *
     *   0x07, category, count, text...
     *
     * where 0x07 is the Casio NEW_ALERT class and text is limited to 18 UTF-8
     * bytes, matching the alert payload length observed in the Casio apps.
     */
    fun send(notification: AppNotification) {
        writeCmd(GetSetMode.SET, encode(notification))
    }

    fun encode(notification: AppNotification): ByteArray {
        val category = categoryFor(notification.type)
        val text = notification.alertText()

        return byteArrayOf(
            CasioConstants.CHARACTERISTICS.CASIO_NEW_ALERT.code.toByte(),
            category.code.toByte(),
            ALERT_COUNT.toByte()
        ) + truncateUtf8(text, MAX_ALERT_TEXT_BYTES)
    }

    private fun categoryFor(type: NotificationType): AlertCategory =
        when (type) {
            NotificationType.PHONE_CALL,
            NotificationType.PHONE_CALL_URGENT -> AlertCategory.INCOMING_CALL

            NotificationType.EMAIL -> AlertCategory.EMAIL
            NotificationType.MESSAGE,
            NotificationType.EMAIL_SMS -> AlertCategory.SMS_MMS

            NotificationType.CALENDAR -> AlertCategory.SCHEDULE
            NotificationType.GENERIC -> AlertCategory.SNS
        }

    private fun AppNotification.alertText(): String =
        shortText.ifBlank {
            title.ifBlank {
                text.ifBlank {
                    app
                }
            }
        }

    private fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        val result = ArrayList<Byte>(maxBytes)

        for (char in text) {
            val bytes = char.toString().toByteArray(Charsets.UTF_8)
            if (result.size + bytes.size > maxBytes) {
                break
            }
            result.addAll(bytes.toList())
        }

        return result.toByteArray()
    }
}
