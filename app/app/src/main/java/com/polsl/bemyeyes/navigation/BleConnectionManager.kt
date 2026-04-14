package com.polsl.bemyeyes.navigation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

//Ignorujemy ostrzeżenia IDE o uprawnieniach, bo załatwimy je w MainActivity
@SuppressLint("MissingPermission")
class BleConnectionManager(private val routingEngine: NavigationRoutingEngine) {

    private var connectedGatt: BluetoothGatt? = null

    // Odpowiednik statycznych zmiennych z Javy (w Kotlinie trzymane w companion object)
    companion object {
        private const val TAG = "BLE_COMM"

        // UUID Z ESP32
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

        // Standardowy UUID deskryptora dla powiadomień (CCCD)
        val DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun establishConnection(device: BluetoothDevice, context: Context) {
        Log.i(TAG, "Inicjowanie połączenia z: ${device.name}")
        // autoConnect=false jest stabilniejsze przy pierwszym parowaniu
        connectedGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Połączono! Rozpoczynam szukanie serwisów...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Rozłączono z urządzeniem.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Używamy bezpiecznego wywołania (?.) typowego dla Kotlina
                val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    // AKTYWACJA NOTIFY
                    gatt.setCharacteristicNotification(characteristic, true)

                    // Zapis do fizycznego deskryptora 2902 na ESP32
                    val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.i(TAG, "Subskrypcja Notify aktywna.")
                    }
                } else {
                    Log.e(TAG, "Nie znaleziono charakterystyki UWB na urządzeniu!")
                }
            }
        }

        // Ta metoda odbiera dane. Adnotacja Deprecated wynika z faktu, że w najnowszym Androidzie 13+
        // dodano nową sygnaturę tej metody, ale ta stara nadal świetnie działa w ramach kompatybilności wstecznej.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null) {
                val payload = String(data, StandardCharsets.UTF_8)
                extractProximityData(payload)
            }
        }
    }

    private fun extractProximityData(payload: String) {
        try {
            val parts = payload.split(":")
            if (parts.size == 2) {
                val id = parts[0].trim()
                val dist = parts[1].trim().toDouble()

                // Przekazanie danych do Twojego silnika nawigacyjnego napisanego w Javie
                routingEngine.processNewTelemetryData(id, dist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd parsowania: \$payload")
        }
    }

    fun disconnect() {
        connectedGatt?.let {
            it.disconnect()
            it.close()
        }
        connectedGatt = null
    }

}