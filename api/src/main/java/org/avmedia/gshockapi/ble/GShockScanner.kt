package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerMatchMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerSettings
import no.nordicsemi.android.kotlin.ble.core.scanner.FilteredServiceUuid
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.ProgressEvents

object GShockScanner {
    @SuppressLint("MissingPermission")
    val CASIO_SERVICE_UUID = "00001804-0000-1000-8000-00805f9b34fb"

    private lateinit var scannerFlow: Job

    @SuppressLint("MissingPermission")
    fun scan(
        context: Context,
        filter: (DeviceInfo) -> Boolean,
        onDeviceFound: (DeviceInfo) -> Unit
    ) {
        val gShockFilters = listOf(
            BleScanFilter(
                serviceUuid = FilteredServiceUuid(
                    ParcelUuid.fromString(CASIO_SERVICE_UUID)
                )
            )
        )

        val settings = BleScannerSettings(
            matchMode = BleScannerMatchMode.MATCH_MODE_STICKY,
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
            reportDelay = 0 // Immediate reporting to avoid "could not find callback wrapper"
        )

        // Main.immediate ensures we don't wait for the next event loop to process hits
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val seenAddresses = mutableSetOf<String>()
        var hasFoundDevice = false // Logical guard to prevent double-triggering in one session

        cancelFlow()

        scannerFlow = scope.launch {
            try {
                BleScanner(context).scan(filters = gShockFilters, settings = settings)
                    .onStart {
                        ProgressEvents.onNext("BLE Scanning Started")
                    }
                    .onEach { scanResult ->

                        val device = scanResult.device
                        val address = device.address

                        // Only process if we haven't found a device in this specific scan session
                        if (!hasFoundDevice && address !in seenAddresses) {
                            val name = device.name ?: return@onEach
                            val info = DeviceInfo(name, address)

                            if (filter(info)) {
                                hasFoundDevice = true // Block further hits immediately
                                seenAddresses += address

                                // Order matters: execute callback before cancelling the flow
                                onDeviceFound(info)
                                cancelFlow()
                            }
                        }
                    }
                    .catch { e ->
                        ProgressEvents.onNext("ApiError", "BLE Scanning Error $e")
                    }
                    .collect()
            } catch (e: Exception) {
                ProgressEvents.onNext(
                    "ApiError",
                    "Failed to start BLE Scanner: ${e.message}"
                )
            }
        }
    }

    fun cancelFlow() {
        if (::scannerFlow.isInitialized && scannerFlow.isActive) {
            scannerFlow.cancel()
        }
    }
}
