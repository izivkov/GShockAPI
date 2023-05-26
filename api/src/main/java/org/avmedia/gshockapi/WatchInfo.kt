package org.avmedia.gshockapi

/**
 * This class keeps track of what is the model and name of the Watch.
 * Currently supported models are: B2100, B5600 (which also includes the B5000, but they have identical functionality)
 */
object WatchInfo {

    enum class WATCH_MODEL {
        B2100, B5600, UNKNOWN
    }

    var model = WATCH_MODEL.UNKNOWN
    private var deviceName: String = ""

    /**
     * Sets the name of the watch, usually obtained from BLE scanning.
     */
    fun setDeviceName(name: String) {
        deviceName = name

        model = if (deviceName.contains("2100")) {
            WATCH_MODEL.B2100
        } else {
            WATCH_MODEL.B5600
        }
    }

    /**
     * Returns name of the watch.
     */
    fun getDeviceName(): String {
        return deviceName
    }
}