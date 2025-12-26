package org.avmedia.services

import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.ProgressEvents

@RequiresApi(Build.VERSION_CODES.S)
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        println("Device appeared (Legacy API 31-32): $address")
        ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        println("Device disappeared (Legacy API 31-32): $address")
        ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        println("Device appeared (API 33+): $address")
        ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        println("Device disappeared (API 33+): $address")
        ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
    }

    private fun sanitizeAddress(address: String): String {
        return address.uppercase().replace("-", ":")
    }
}
