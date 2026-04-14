package com.polsl.bemyeyes.navigation;

import android.util.Log;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Zoptymalizowany menedżer MQTT korzystający z MqttAsyncClient (kompatybilny z Android 14+).
 */
public class MqttConnectionManager {

    private static final String TAG = "MQTT_COMM";
    private static final String SERVER_URI = "tcp://192.168.31.71:1883";
    private static final String TOPIC = "esp32/distance";
    private static final String CLIENT_ID = "AndroidUwbClient_" + System.currentTimeMillis();

    private MqttAsyncClient mqttClient;
    private final NavigationRoutingEngine routingEngine;

    public MqttConnectionManager(NavigationRoutingEngine engine) {
        this.routingEngine = engine;
        try {
            // Używamy MemoryPersistence zamiast zapisu na dysku dla stabilności
            this.mqttClient = new MqttAsyncClient(SERVER_URI, CLIENT_ID, new MemoryPersistence());
            this.mqttClient.setCallback(mqttCallback);
        } catch (MqttException e) {
            Log.e(TAG, "Błąd inicjalizacji klienta MQTT: " + e.getMessage());
        }
    }

    private final MqttCallback mqttCallback = new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
            Log.w(TAG, "Połączenie MQTT przerwane: " + (cause != null ? cause.getMessage() : "nieznany powód"));
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload());
            Log.d(TAG, "Odebrano: " + payload);
            extractProximityData(payload);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    };

    public void connect() {
        if (mqttClient == null) return;
        
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Połączono pomyślnie z brokerem");
                        subscribeToTopic();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Błąd połączenia: " + exception.getMessage());
                    }
                });
            }
        } catch (MqttException e) {
            Log.e(TAG, "Wyjątek podczas łączenia: " + e.getMessage());
        }
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe(TOPIC, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subskrypcja aktywna: " + TOPIC);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Błąd subskrypcji: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Wyjątek podczas subskrypcji: " + e.getMessage());
        }
    }

    private void extractProximityData(String payload) {
        try {
            String[] distanceRecords = payload.split(";");
            String closestAnchorId = null;
            double minimumDistance = Double.MAX_VALUE;

            for (String record : distanceRecords) {
                String trimmed = record.trim();
                if (trimmed.isEmpty()) continue;
                
                String[] components = trimmed.split(":");
                if (components.length == 2) {
                    String anchorId = components[0].trim();
                    double distanceValue = Double.parseDouble(components[1].trim());
                    
                    // Filtracja brakujących odczytów i wybór najbliższej kotwicy
                    if (distanceValue > 0.001 && distanceValue < minimumDistance) {
                        minimumDistance = distanceValue;
                        closestAnchorId = anchorId;
                    }
                }
            }

            if (closestAnchorId != null) {
                routingEngine.processNewTelemetryData(closestAnchorId, minimumDistance);
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd parsowania danych: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Błąd rozłączania: " + e.getMessage());
        }
    }
}
