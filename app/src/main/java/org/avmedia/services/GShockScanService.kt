package org.avmedia.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.GShockScanner

class GShockScanService : Service() {

    private lateinit var api: GShockAPI

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        api = GShockAPI(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("GShockScanService: started")

        GShockScanner.startScan(
            context = applicationContext,
            isBluetoothOn = { bluetoothAdapter?.isEnabled == true },
            filter = { info ->
                api.getAssociations(this).contains(info.address)
            },
            onDeviceFound = { info ->
                println("GShockScanService: onDeviceFound address=${info.address} name=${info.name}")
                ProgressEvents.onNext("DeviceAppeared", info.address)
            }
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        println("GShockScanService: destroyed")
        GShockScanner.stopScan()
        super.onDestroy()
    }
}
