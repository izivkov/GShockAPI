/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-09, 10:05 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-09, 10:05 a.m.
 */

package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.appcompat.app.AppCompatActivity
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.casio.CasioConstants
import timber.log.Timber

data class BleScannerLocal(val context: Context) {
    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val foundDevices = mutableSetOf<String>()

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder().setScanMode(
        // ScanSettings.SCAN_MODE_LOW_POWER
        ScanSettings.SCAN_MODE_LOW_LATENCY
    ).build()

    private var isScanning = false

    @SuppressLint("MissingPermission")
    fun startConnection(deviceId: String?, deviceName: String?) {
        foundDevices.clear()
        if (!deviceName.isNullOrEmpty()) {
            WatchInfo.setNameAndModel(deviceName)
        }
        if (!deviceId.isNullOrEmpty()) {
            val device = bluetoothAdapter.getRemoteDevice(deviceId)
            WatchInfo.setAddress(deviceId.toString())
            if (device.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) Connection.connect(
                device, context
            )
        }
        if (isScanning) return
        if (!bluetoothAdapter.isEnabled || bleScanner == null) return
        scanSettings.describeContents()
        bleScanner.startScan(createFilters(), scanSettings, scanCallback)
        isScanning = true
    }

    /*
    I/BleExtensionsKt: Service 00001801-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--
    I/BleExtensionsKt: Service 00001800-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a00-0000-1000-8000-00805f9b34fb: READABLE
    |--00002a01-0000-1000-8000-00805f9b34fb: READABLE
    I/BleExtensionsKt: Service 00001804-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a07-0000-1000-8000-00805f9b34fb: READABLE
    I/BleExtensionsKt: Service 26eb000d-b012-49a8-b1f8-394fb2032b0f
    Characteristics:
    |--26eb002c-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE
    |--26eb002d-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
    |--26eb0023-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
    |--26eb0024-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
    */

    private fun createFilters(): ArrayList<ScanFilter> {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(CasioConstants.CASIO_SERVICE.toString())).build()

        val filters = ArrayList<ScanFilter>()
        filters.add(filter)
        return filters
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (foundDevices.contains(result.device.toString())) {
                return
            }
            foundDevices.add(result.device.toString())

            val name = result.scanRecord?.deviceName
            if (name != null) {
                WatchInfo.setNameAndModel(name.trimEnd('\u0000'))
            }
            WatchInfo.setAddress(result.device.toString())

            stopBleScan()
            Connection.connect(result.device, context)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }
}