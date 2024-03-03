package org.avmedia.gshockapi.ble;

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.casio.CasioConstants
import timber.log.Timber
import java.util.*

enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, DISCONNECTING
}

typealias onConnectedType = (String, String) -> Unit

interface GSHock {
    suspend fun connect(device: BluetoothDevice, onConnected: (String, String) -> Unit)
    fun release()
    fun setDataCallback(dataCallback: IDataReceived?)
    fun enableNotifications()
    suspend fun write(handle: Int, data: ByteArray)
    abstract var connectionState: ConnectionState
}

class IGShockManager(
    context: Context,
) : GSHock by GShockManagerImpl(context)

private class GShockManagerImpl(
    context: Context,
) : BleManager(context), GSHock {

    private lateinit var readCharacteristic: BluetoothGattCharacteristic
    private lateinit var writeCharacteristic: BluetoothGattCharacteristic
    var dataReceivedCallback: IDataReceived? = null
    private lateinit var device: BluetoothDevice
    override var connectionState = ConnectionState.DISCONNECTED
    private lateinit var onConnected: onConnectedType

    init {
        connectionObserver = ConnectionEventHandler()
    }

    override fun initialize() {
        super.initialize()
        setNotificationCallback(writeCharacteristic).with { _, data ->
            Timber.i("Received data from characteristic: ${data.value}")

            fun ByteArray.toHexString(): String =
                joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

            dataReceivedCallback?.dataReceived(data.value?.toHexString())
        }

        enableNotifications(writeCharacteristic).enqueue()
        ProgressEvents.onNext("BleManagerInitialized")
    }

    @SuppressLint("MissingPermission")
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect(device: BluetoothDevice, onConnected: onConnectedType) {
        this.onConnected = onConnected

        connect(device)
            .retry(3, 300)
            .useAutoConnect(false)
            .timeout(30 * 24 * 60 * 1000)
            .enqueue()
    }

    override fun release() {
        // Cancel all coroutines.
        scope.cancel()

        val wasConnected = isReady
        // If the device wasn't connected, it means that ConnectRequest was still pending.
        // Cancelling queue will initiate disconnecting automatically.
        cancelQueue()

        // If the device was connected, we have to disconnect manually.
        if (wasConnected) {
            disconnect().enqueue()
        }
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun enableNotifications() {
        enableNotifications(writeCharacteristic)
            .fail { device, status ->
                // Handle failure to enable notifications
                Timber.i("Failed to enable notifications. Status: $status")
                ProgressEvents.onNext("ApiError")
            }
            .done { device ->
                // Notifications enabled successfully
                ProgressEvents.onNext("NotificationsEnabled", device)
            }
            .enqueue()
    }

    // Connection events
    inner class ConnectionEventHandler : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceConnecting!!!!!!")
            connectionState = ConnectionState.CONNECTING
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceConnected(device: BluetoothDevice) {
            ProgressEvents.onNext("ConnectionStarted")
            connectionState = ConnectionState.CONNECTED
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("ConnectionFailed")
            connectionState = ConnectionState.DISCONNECTED
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceReady(device: BluetoothDevice) {
            Timber.i("$device DeviceReady!!!!!!")

            onConnected(device.name, device.address)

            // inform the caller that we have connected
            ProgressEvents.onNext("ConnectionSetupComplete", device)
            ProgressEvents.onNext("DeviceName", device.name)
            ProgressEvents.onNext("DeviceAddress", device.address)
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceDisconnecting!!!!!!")
            connectionState = ConnectionState.DISCONNECTING
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("Disconnect", device)
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("NewApi", "MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        gatt.getService(CasioConstants.WATCH_FEATURES_SERVICE_UUID)?.apply {
            readCharacteristic = getCharacteristic(
                CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID,
            )

            writeCharacteristic = getCharacteristic(
                CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID,
            )
            return true
        }
        return false
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

    override suspend fun write(handle: Int, data: ByteArray) {

        val characteristic = if (handle == 0xC) readCharacteristic else writeCharacteristic
        val writeType = if (handle == 0xC) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        writeCharacteristic(
            characteristic,
            data,
            writeType
        ).enqueue()
    }
}


