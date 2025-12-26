package org.avmedia.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GShockPairingManager
import org.avmedia.gshockapi.ble.GShockScanner

class GShockScanService : Service() {

    private lateinit var api: GShockAPI
    override fun onCreate() {
        super.onCreate()
        api = GShockAPI(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // This is called when you call context.startService(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startLoop()

        return START_STICKY // Keep service running
    }

    private fun startLoop() {
        val scope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Logic: Scan only if NOT connected
                if (!Connection.isConnected()) {
                    GShockScanner.scan(
                        context = this@GShockScanService,
                        filter = { true },
                        onDeviceFound = { deviceInfo ->
                            val savedAddresses = GShockPairingManager.getAssociations(this@GShockScanService)
                            if (savedAddresses.contains(deviceInfo.address)) {
                                ProgressEvents.onNext("DeviceAppeared", deviceInfo.address)
                            }
                        }
                    )
                }
                delay(6000) // Don't spam scans, wait 6s between checks
            }
        }
    }
}
