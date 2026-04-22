package org.avmedia.services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents

class GShockScanService : Service() {

    private lateinit var api: GShockAPI
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        api = GShockAPI(this)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanFilters = listOf(
        ScanFilter.Builder()
            .setDeviceName(null) // no name filter; we filter by association below
            .build()
    )

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceAddress = result.device.address ?: return
            val deviceName = result.device.name

            val associations = api.getAssociations(this@GShockScanService)
            if (associations.contains(deviceAddress)) {
                println("GShockScanService: matched association $deviceAddress ($deviceName)")
                onDeviceFound(deviceAddress, deviceName)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            println("GShockScanService: BLE scan failed with error code $errorCode")
            isScanning = false
        }
    }

    // This is called when you call context.startService(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("GShockScanService: started")
        startScanLoop()
        return START_STICKY
    }

    private fun startScanLoop() {
        serviceScope.launch {
            while (isActive) {
                if (bluetoothAdapter?.isEnabled == true) {
                    startScan()
                    delay(SCAN_DURATION_MS)
                    stopScan()
                    delay(SCAN_INTERVAL_MS)
                } else {
                    println("GShockScanService: Bluetooth not enabled, waiting...")
                    delay(BLUETOOTH_RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun startScan() {
        if (isScanning) return
        try {
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            println("GShockScanService: scan started")
        } catch (e: SecurityException) {
            println("GShockScanService: missing BLE scan permission")
        }
    }

    private fun stopScan() {
        if (!isScanning) return
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            println("GShockScanService: scan stopped")
        } catch (e: SecurityException) {
            println("GShockScanService: missing BLE scan permission")
        }
    }

    private fun onDeviceFound(address: String, name: String?) {
        ProgressEvents.onNext("DeviceAppeared", address)
    }

    override fun onDestroy() {
        stopScan()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SCAN_DURATION_MS = 5_000L   // scan active for 5s
        private const val SCAN_INTERVAL_MS = 3_000L   // pause between scans
        private const val BLUETOOTH_RETRY_DELAY_MS = 10_000L  // wait if BT is off
    }
}

