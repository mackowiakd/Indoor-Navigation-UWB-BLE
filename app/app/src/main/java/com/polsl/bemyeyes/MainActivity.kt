package com.polsl.bemyeyes

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

class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var mqttManager: MqttConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicjalizacja komponentów nawigacyjnych
        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService)
        
        // Inicjalizacja MQTT (usunięto context, bo MqttAsyncClient go nie potrzebuje)
        mqttManager = MqttConnectionManager(routingEngine)

        setContent {
            BeMyEyesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartNavigation = { targetId ->
                            routingEngine.setNavigationTarget(targetId)
                            startMqttServices()
                        }
                    )
                }
            }
        }
    }

    private fun startMqttServices() {
        Toast.makeText(this, "Łączenie z brokerem MQTT...", Toast.LENGTH_SHORT).show()
        mqttManager.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.terminateService()
        mqttManager.disconnect()
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
