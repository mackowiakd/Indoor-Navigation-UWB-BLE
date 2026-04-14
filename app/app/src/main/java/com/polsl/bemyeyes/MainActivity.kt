package com.polsl.bemyeyes

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.polsl.bemyeyes.navigation.*
import com.polsl.bemyeyes.ui.theme.BeMyEyesTheme
import android.content.Context

class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var bleManager: BleConnectionManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicjalizacja komponentów nawigacyjnych
        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService)

        // Inicjalizacja MQTT (usunięto context, bo MqttAsyncClient go nie potrzebuje)
       bleManager = BleConnectionManager(routingEngine)

        setContent {
            BeMyEyesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartNavigation = { targetId ->
                            routingEngine.setNavigationTarget(targetId)
                            startBleServices()
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleServices() {
        Toast.makeText(this, "Łączenie z XIAO UWB...", Toast.LENGTH_SHORT).show()

        // 1. Pobieramy główny sterownik Bluetooth z systemu telefonu
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // 2. Sprawdzamy, czy telefon w ogóle ma Bluetooth i czy jest włączony
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {

            // 3. Wskazujemy konkretny adres MAC Twojego ESP32 (Zmień, jeśli Twój MAC jest inny!)
            val device = bluetoothAdapter.getRemoteDevice("98:3D:AE:AC:4D:B2")

            // 4. Odpalamy naszą metodę z managera
            bleManager.establishConnection(device, this)

        } else {
            Toast.makeText(this, "BŁĄD: Włącz Bluetooth w telefonie!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.terminateService()
        bleManager.disconnect()
    }
}

@Composable
fun NavigationScreen(modifier: Modifier = Modifier, onStartNavigation: (String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Asystent Nawigacji MQTT",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onStartNavigation("A1") }, // Sala 420 (kotwica A1)
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj do Sali 420 (A1)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onStartNavigation("UWB_001") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj do Sali 214")
        }
    }
}
