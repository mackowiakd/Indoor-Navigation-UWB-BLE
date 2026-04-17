package com.polsl.bemyeyes

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polsl.bemyeyes.navigation.*
import com.polsl.bemyeyes.ui.theme.BeMyEyesTheme

class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var bleManager: BleConnectionManager

    // Stan trzymający logi, obserwowany przez UI (Jetpack Compose)
    private val debugLogs = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService)

        // Inicjalizacja MQTT (usunięto context, bo MqttAsyncClient go nie potrzebuje)
        bleManager = BleConnectionManager(routingEngine) { nowaWiadomosc ->
            // Upewniamy się, że modyfikujemy interfejs w głównym wątku
            runOnUiThread {
                debugLogs.add(0, nowaWiadomosc) // Najnowsze na samej górze
                if (debugLogs.size > 50) debugLogs.removeAt(debugLogs.size - 1) // Pamiętamy tylko 50 ostatnich
            }
        }

        setContent {
            BeMyEyesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(
                        modifier = Modifier.padding(innerPadding),
                        logs = debugLogs,
                        onStartNavigation = { targetId ->
                            routingEngine.setNavigationTarget(targetId)
                            startBleServices()
                        },
                        onDisconnect = { // <--- NOWA AKCJA
                        bleManager.disconnect()
                        debugLogs.add(0, "🛑 Wymuszono rozłączenie (Manual)")
                         }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleServices() {
        Toast.makeText(this, "Rozpoczynam procedurę BLE...", Toast.LENGTH_SHORT).show()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            val device = bluetoothAdapter.getRemoteDevice("98:3D:AE:AC:4D:B2") // ZMIEŃ NA SWÓJ MAC ESP32!
            bleManager.establishConnection(device, this)
        } else {
            debugLogs.add(0, "BŁĄD: Włącz Bluetooth w ustawieniach!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.terminateService() // Może zgłaszać błąd jeśli masz inną nazwę w klasie, w razie czego zwiń to w try-catch lub zakomentuj na ten test
        bleManager.disconnect()
    }
}

@Composable
fun NavigationScreen(
    modifier: Modifier = Modifier,
    logs: List<String>,
    onStartNavigation: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tytuł i przycisk w jednym rzędzie
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Asystent UWB/BLE", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Rozłącz")
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onStartNavigation("A1") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj do Sali 420 (Kotwica A1)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onStartNavigation("UWB_001") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj do Sali 214")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onStartNavigation("A1") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj: Drzwi wyjściowe (UWB)")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onStartNavigation("TAG_DESK") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj: Biurko (BLE)")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onStartNavigation("TAG_COFFEE") },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("Nawiguj: Ekspres do kawy (BLE)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // KONSOLA DEBUG (odpowiednik nRF Connect)
        Text("Konsola Debugowania BLE:", style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Zajmuje resztę ekranu
                .background(Color.Black)
                .padding(8.dp)
        ) {
            LazyColumn {
                items(logs) { logMsg ->
                    Text(
                        text = logMsg,
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}