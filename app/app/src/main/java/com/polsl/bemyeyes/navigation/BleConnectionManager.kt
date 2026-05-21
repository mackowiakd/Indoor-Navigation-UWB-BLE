package com.polsl.bemyeyes.navigation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.RetrofitClient
import com.polsl.bemyeyes.navigation.dataBase.TopologyApiService
import java.nio.charset.StandardCharsets
import java.util.UUID



//Ignorujemy ostrzeżenia IDE o uprawnieniach, bo załatwimy je w MainActivity
@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val routingEngine: NavigationRoutingEngine,
    private val onLogUpdate: (String) -> Unit) {

    private var connectedGatt: BluetoothGatt? = null
    // Bezpieczny domyślny payload to 20 bajtów (MTU 23 - 3 bajty nagłówka)
    private var currentPayloadLimit: Int = 20

    // Odpowiednik statycznych zmiennych z Javy (w Kotlinie trzymane w companion object)
    companion object {
        private const val TAG = "BLE_COMM"

        // UUID Z ESP32
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

        // Standardowy UUID deskryptora dla powiadomień (CCCD)
        val DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        // NOWE: UUID uszu ESP32
        val FILTER_CHAR_UUID: UUID = UUID.fromString("c0de0001-feed-4688-b7f5-ea07361b26a8")

        var update_dev_list: Boolean=false;


    }
    private fun postLog(message: String) {
        Log.i(TAG, message)
        onLogUpdate(message)
    }
    fun establishConnection(device: BluetoothDevice, context: Context) {
        postLog("⚙️ Szukam: ${device.name ?: device.address}")
        // autoConnect=false jest stabilniejsze przy pierwszym parowaniu
        connectedGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Złota zasada Android BLE: Dodajemy opóźnienie (Delay) przed operacjami na GATT
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    postLog("📦 Negocjuję rozmiar MTU...")
                    gatt.requestMtu(512)
                }, 600) // 600 milisekund opóźnienia
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // KRYTYCZNA ZMIANA: Dopiero tu niszczymy i czyścimy obiekt GATT!
                gatt.close()
                connectedGatt = null
                postLog("❌ ROZŁĄCZONO.")
            }
        }

        // Ta metoda odpali się, gdy telefon i ESP32 dogadają się co do wielkości paczki
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Zapisujemy nowy limit (MTU minus 3 bajty na nagłówek BLE)
                currentPayloadLimit = mtu - 3
                postLog("📦 MTU powiększone do: $mtu bajtów!")
                // Dopiero TERAZ szukamy serwisów
                gatt.discoverServices()
            }
        }


        //truning NOTIFY updates ON
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
                        postLog("🔔 Powiadomienia (Notify) AKTYWNE!")
                    }

                }

             else {
                postLog("❌ Błąd odkrywania serwisów, status: $status")
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
                postLog("📡 RX: $payload")

                extractProximityData(payload)
            }
        }
    }

    private fun extractProximityData(payload: String) {
        try {
        //Każda Kotwica w telefonie będzie miała swoje ID (np. 1, 2) i Kotlin musi dostać jasny raport: U_1=2.45;U_2=5.10;B_ff:ff...=1.50.
           // uwb tez musi miec ID
            val records = payload.split(";")

            for (record in records) {
                var id = ""
                var dist: Double? = null

                // PARSOWANIE ZGODNE Z NOWYM FORMATEM ESP32
                if (record.startsWith("UWB:")) {
                    id = "UWB" //to co jest po "UWB"
                    dist = record.substringAfter("UWB:").toDoubleOrNull()
                } else if (record.startsWith("BLE_")) {
                    // Oczekiwany format: BLE_ff:ff:12:b1:64:d1=1.50
                    val bleData = record.substringAfter("BLE_")
                    id = bleData.substringBefore("=").trim() // Wyciągamy czysty MAC
                    dist = bleData.substringAfter("=").toDoubleOrNull()
                }

                if (dist != null) {
                    // 1. ZIMNY START: Pytamy silnik, czy ten MAC to nowe piętro?
                    // routingEngine sprawdzi to w bazie i ew. zwróci nam całą listę!
                    val newDevicesToSend = routingEngine.processScannedDevice(id, dist)

                    // 2. Czy jest coś do wysłania?
                    if (newDevicesToSend != null) {
                        sendFilterToEsp(newDevicesToSend) // Wysyłamy!
                        // Zmuszamy UI do odświeżenia (żeby pojawiły się przyciski mikro)
                        update_dev_list = !update_dev_list
                    }

                    // 3. Wywołujemy główny wątek UI, aby wyświetlić pozycję na ekranie
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            routingEngine.processNewTelemetryData(id, dist)

                    }
                }
                else {
                    postLog(" brak danych od $id: $payload")
                }



            }
        } catch (e: Exception) {
            postLog("⚠️ Błąd parsowania: $payload")
        }
    }

    fun sendFilterToEsp(devices: List<IoTDevice>) {
        // 1. Upewniamy się, że mamy połączenie i znajdujemy naszą "rurę"
        val gatt = connectedGatt ?: return
        val service = gatt.getService(SERVICE_UUID) ?: return
        val filterChar = service.getCharacteristic(FILTER_CHAR_UUID) ?: return

        // oczekiwany format listy:U:123;B:ff:ff:12:b1:64:d1,a8:03:2a:b8:ee:fa
        // 2. Filtrujemy urządzenia UWB i łączymy ich adresy MAC przecinkiem
        val uwbMacs = devices
            .filter { it.deviceType == "UWB_ANCHOR" }
            .joinToString(",") { it.macAddress }

        // 3. Filtrujemy urządzenia BLE i łączymy ich adresy MAC przecinkiem
        val bleMacs = devices
            .filter { it.deviceType == "BLE_BEACON" }
            .joinToString(",") { it.macAddress }

        // 4. Składamy wszystko w docelowy format
        val payload = "U:$uwbMacs;B:$bleMacs"

        // 3. Wkładamy do rury i wysyłamy
        filterChar.value = payload.toByteArray(Charsets.UTF_8)
        filterChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(filterChar)

        postLog("✅ Wysłano listę MAC do ESP32: $payload")
    }

    fun disconnect() {
        postLog("🔌 Próba bezpiecznego rozłączenia...")
        // Wywołujemy TYLKO disconnect. Zamykanie zostawiamy na później!
        connectedGatt?.disconnect()
    }

}