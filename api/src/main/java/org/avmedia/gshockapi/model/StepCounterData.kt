package org.avmedia.gshockapi.model

import java.time.LocalDateTime

/**
 * ABL-100WE life-log record.
 *
 * `hourlySteps` contains the 144 two-byte history slots (six 24-hour blocks),
 * while `dailyHistory` contains the 14 four-byte day slots. `null` represents
 * the watch's unavailable sentinel rather than a genuine zero-step period.
 */
data class StepCounterData(
    val timestamp: LocalDateTime? = null,
    val dayOfWeek: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null,
    val hourlySteps: List<Int?>,
    val dailyHistory: List<Int?>,
    val dailyDistances: List<Int?> = emptyList(),
    val currentDaySteps: Int?,
    val distanceMeters: Int? = null,
    val pendingDistanceMeters: Int? = null,
    val totalDistanceMeters: Int? = null,
    val bcdTotalSteps: Int? = null,
    val raw: ByteArray? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun unavailable() = StepCounterData(
            timestamp = null,
            dayOfWeek = 0,
            month = 0,
            dayOfMonth = 0,
            hourlySteps = emptyList(),
            dailyHistory = emptyList(),
            dailyDistances = emptyList(),
            currentDaySteps = null,
            distanceMeters = null,
            pendingDistanceMeters = null,
            totalDistanceMeters = null,
            bcdTotalSteps = null,
            raw = null,
            warnings = listOf("step counter unavailable")
        )
    }
}
