package com.polsl.bemyeyes.navigation;

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

/**
 * Menedżer protokołu GATT odpowiedzialny za ciągłe nasłuchiwanie odczytów 
 * odległości transmitowanych z urządzenia ESP32 WROOM.
 */
public class BleConnectionManager {

    private BluetoothGatt connectedGattClient;
    private final NavigationRoutingEngine routingEngine;
    
    // Standardowe identyfikatory UUID dla transmisji strumieniowej (np. profil UART)
    private static final UUID SERVICE_UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHAR_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    public BleConnectionManager(NavigationRoutingEngine engine) {
        this.routingEngine = engine;
    }

    public void establishConnection(BluetoothDevice targetDevice, Context applicationContext) {
        connectedGattClient = targetDevice.connectGatt(applicationContext, false, gattEventHandler);
    }

    private final BluetoothGattCallback gattEventHandler = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE_COMM", "Ustanowiono połączenie z wbudowanym modułem ESP32");
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattCharacteristic rxCharacteristic = gatt.getService(SERVICE_UART_UUID).getCharacteristic(CHAR_TX_UUID);
                if (rxCharacteristic != null) {
                    gatt.setCharacteristicNotification(rxCharacteristic, true);
                    
                    // Zapisanie modyfikacji na deskryptorze serwera celem cyklicznych powiadomień
                    BluetoothGattDescriptor descriptor = rxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Zakładamy odbiór ramki tekstowej postaci: UWB_001:1.2;UWB_002:1.8;
            byte[] value = characteristic.getValue();
            if (value != null) {
                String rawPayload = new String(value, StandardCharsets.UTF_8);
                extractProximityData(rawPayload);
            }
        }
    };

    /**
     * Realizuje uproszczony model proksymacji pozbawiony analizy trilateracyjnej.
     * Wyodrębnia z ramki węzeł charakteryzujący się najbliższą odległością fizyczną.
     */
    private void extractProximityData(String payload) {
        try {
            String[] distanceRecords = payload.split(";");
            String closestAnchorId = null;
            double minimumDistance = Double.MAX_VALUE;

            for (String record : distanceRecords) {
                if (record.trim().isEmpty()) continue;
                String[] components = record.split(":");
                if (components.length == 2) {
                    String anchorId = components[0].trim();
                    double distanceValue = Double.parseDouble(components[1].trim());
                    
                    if (distanceValue < minimumDistance) {
                        minimumDistance = distanceValue;
                        closestAnchorId = anchorId;
                    }
                }
            }

            if (closestAnchorId != null) {
                routingEngine.processNewTelemetryData(closestAnchorId, minimumDistance);
            }
        } catch (NumberFormatException e) {
            Log.e("BLE_COMM", "Błąd formatowania danych numerycznych w ramce UWB");
        }
    }
}
