package org.avmedia.gshockapi

import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import java.time.Duration

/**
 * This class keeps information about the characteristics of the currently connected watch.
 * Based on that, the application can display different information.
 */
object WatchInfo {
    enum class WATCH_MODEL {
        GA, GW, DW, UNKNOWN
    }

    var name: String = ""
    var shortName = ""
    private var address: String = ""

    var model = WATCH_MODEL.UNKNOWN

    var worldCitiesCount = 2
    var dstCount = 3
    var alarmCount = 5
    var hasAutoLight = false
    var hasReminders = false

    /**
     * Info about the model.
     */
    data class ModelInfo(
        var model: WATCH_MODEL,
        var worldCitiesCount: Int,
        var dstCount: Int,
        var alarmCount: Int,
        var hasAutoLight: Boolean,
        var hasReminders: Boolean,
        var shortLightDuration: Float,
        val longLightDuration: Float,
    )

    private val models  = listOf(
        ModelInfo(WATCH_MODEL.GW, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = 2f, longLightDuration = 4f),
        ModelInfo(WATCH_MODEL.GA, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = 1.5f, longLightDuration = 3f),
        ModelInfo(WATCH_MODEL.DW, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = 1.5f, longLightDuration = 3f),
        ModelInfo(WATCH_MODEL.UNKNOWN, 2, 1, 5, hasAutoLight = false, hasReminders = false, shortLightDuration = 1.5f, longLightDuration = 3f)
    )

    private val modelMap = models.associateBy { it.model }

    /**
     * When we obtain the name of the watch from the BLE connection, we need to call this method.
     * From here, we can determine and set all the model's characteristics.
     */
    fun setNameAndModel(name: String) {
        this.name = name

        // name is like: CASIO GW-B5600
        val parts = this.name.split(" ")
        if (parts.size > 1) {
            shortName = parts[1]
        }

        model = when {
            shortName.startsWith("GA") -> WATCH_MODEL.GA
            shortName.startsWith("GW") -> WATCH_MODEL.GW
            shortName.startsWith("DW") -> WATCH_MODEL.DW
            else -> WATCH_MODEL.UNKNOWN
        }

        this.hasReminders = modelMap[model]!!.hasReminders
        this.hasAutoLight = modelMap[model]!!.hasAutoLight
        this.alarmCount = modelMap[model]!!.alarmCount
        this.worldCitiesCount = modelMap[model]!!.worldCitiesCount
        this.dstCount = modelMap[model]!!.dstCount

        ProgressEvents.onNext("DeviceName", this.name)
    }

    fun setAddress(address: String) {
        this.address = address
        ProgressEvents.onNext("DeviceAddress", address)
    }

    fun getAddress(): String {
        return address
    }

    fun reset() {
        this.address = ""
        this.name = ""
        this.shortName = ""
        this.model = WATCH_MODEL.UNKNOWN
    }
}