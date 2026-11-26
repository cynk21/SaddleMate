package com.example.saddlemate.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.saddlemate.domain.Gait
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BleHrState(
    val status: String = "Kein Gurt verbunden",
    val deviceName: String? = null,
    val bpm: Int? = null,
    val connected: Boolean = false,
    val simulating: Boolean = false,
)

/**
 * Plug-and-Play BLE bridge for standard Heart Rate Service (0x180D / 0x2A37, e.g. Polar Equine)
 * with a built-in simulator mode for testing indoors without a physical chest strap.
 */
@Singleton
class SensorBridgeBLE @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BleHrState())
    val state: StateFlow<BleHrState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var simJob: Job? = null
    private var scanJob: Job? = null
    private var scanning = false

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        stopSimulation()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BleHrState(status = "Bluetooth aus – bitte einschalten oder Test-Gurt nutzen")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.value = BleHrState(status = "BLE-Scanner nicht verfügbar")
            return
        }
        if (scanning) return
        scanning = true
        _state.value = BleHrState(status = "Suche Herzfrequenz-Gurt (0x180D)…")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                scanning = false
                runCatching { scanner.stopScan(this) }
                connectDevice(device)
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                _state.value = BleHrState(status = "BLE-Scan fehlgeschlagen ($errorCode)")
            }
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onFailure {
                scanning = false
                _state.value = BleHrState(status = "Bluetooth-Berechtigung fehlt oder Scan abgelehnt")
                return
            }

        scanJob?.cancel()
        scanJob = scope.launch {
            delay(10_000L)
            if (scanning) {
                scanning = false
                runCatching { scanner.stopScan(callback) }
                if (!_state.value.connected) {
                    _state.value = BleHrState(status = "Kein BLE-Gurt gefunden (Polar Equine einschalten)")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        _state.value = BleHrState(status = "Verbinde mit ${device.name ?: device.address}…")
        gatt?.close()
        gatt = device.connectGatt(
            context,
            false,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        _state.value = BleHrState(
                            status = "Verbunden: ${device.name ?: "HR-Gurt"}",
                            deviceName = device.name ?: device.address,
                            connected = true,
                        )
                        g.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        _state.value = BleHrState(status = "Gurt getrennt")
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    val hrChar = g.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_CHAR_UUID) ?: return
                    g.setCharacteristicNotification(hrChar, true)
                    val cccd = hrChar.getDescriptor(CCCD_UUID) ?: return
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (characteristic.uuid == HR_CHAR_UUID) {
                        @Suppress("DEPRECATION")
                        val bpm = parseHeartRateBpm(characteristic.value)
                        if (bpm != null) {
                            _state.value = _state.value.copy(bpm = bpm)
                        }
                    }
                }
            },
        )
    }

    /** Starts a realistic simulated Polar Equine HR stream for indoor testing without a strap. */
    fun startDummyStream(gaitProvider: () -> Gait) {
        disconnect()
        _state.value = BleHrState(
            status = "Test-Gurt aktiv (Dummy)",
            deviceName = "Polar Equine (Demo)",
            bpm = 42,
            connected = true,
            simulating = true,
        )
        simJob = scope.launch {
            var current = 42.0
            while (_state.value.simulating) {
                val target = when (gaitProvider()) {
                    Gait.STANDSTILL -> 38.0
                    Gait.WALK -> 62.0
                    Gait.TROT -> 112.0
                    Gait.CANTER -> 148.0
                    Gait.GALLOP -> 178.0
                }
                current += (target - current) * 0.25
                _state.value = _state.value.copy(bpm = current.toInt())
                delay(1_000L)
            }
        }
    }

    fun stopSimulation() {
        simJob?.cancel()
        simJob = null
        if (_state.value.simulating) {
            _state.value = BleHrState()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopSimulation()
        scanJob?.cancel()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        _state.value = BleHrState()
    }

    companion object {
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Parses BLE 0x2A37 Heart Rate Measurement payload (8-bit or 16-bit BPM). */
        fun parseHeartRateBpm(bytes: ByteArray?): Int? {
            if (bytes == null || bytes.size < 2) return null
            val flags = bytes[0].toInt() and 0xFF
            val isUint16 = (flags and 0x01) != 0
            return if (isUint16) {
                if (bytes.size < 3) null
                else (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
            } else {
                bytes[1].toInt() and 0xFF
            }
        }
    }
}
