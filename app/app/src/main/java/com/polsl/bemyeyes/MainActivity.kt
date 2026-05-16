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
import androidx.lifecycle.lifecycleScope
import com.polsl.bemyeyes.navigation.*
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget
import com.polsl.bemyeyes.navigation.dataBase.RetrofitClient
import com.polsl.bemyeyes.ui.theme.BeMyEyesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var bleManager: BleConnectionManager

    // Stan trzymający logi, obserwowany przez UI (Jetpack Compose)
    protected val debugLogs = mutableStateListOf<String>()
    // Stan Compose, który pamięta gdzie jesteśmy (np. Location_ID = 2)
    private val currentLocationIdState = mutableStateOf<Int?>(null)

    private val currentEspFilterState = mutableStateOf("Brak (Oczekuję na skan / Cold Start...)") //  Pamięta ostatnio wysłany filtr

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //@TODO evoke fun to downloead data from DB into local cache (like singleton RAM file?)
        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService)

        // Inicjalizacja MQTT (usunięto context, bo MqttAsyncClient go nie potrzebuje)
        bleManager = BleConnectionManager(routingEngine) { nowaWiadomosc ->
            // Upewniamy się, że modyfikujemy interfejs w głównym wątku
            runOnUiThread {
                // WYCHWYTYWANIE FILTRU: Jeśli log zawiera te słowa, wyciągamy samą listę (payload)
                if (nowaWiadomosc.contains("Wysłano listę")) {
                    currentEspFilterState.value = nowaWiadomosc.substringAfter("ESP32: ").trim()
                }
                debugLogs.add(0, nowaWiadomosc) // Najnowsze na samej górze
                if (debugLogs.size > 50) debugLogs.removeAt(debugLogs.size - 1) // Pamiętamy tylko 50 ostatnich
            }
        }
        // W MainActivity.kt
        lifecycleScope.launch {
            fetchDatabase()
        }

        setContent {
            BeMyEyesTheme {
                val scope = rememberCoroutineScope()
                // Odczytujemy stan lokalizacji. Gdy się zmieni, UI się przebuduje!
                val currentLocation = currentLocationIdState.value
                val currentFilter = currentEspFilterState.value // <--- Odczytujemy stan
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(

                        modifier = Modifier.padding(innerPadding),
                        logs = debugLogs,
                        currentLocationId = currentLocation, // PRZEKAZUJEMY STAN
                        topologyDb = topologyDatabase,
                        currentEspFilter = currentFilter, // <--- PRZEKAZUJEMY STAN

                        onStartNavigation = { target ->
                            if (target.associatedMac != null) {
                                // TRYB MIKRO: Celujemy w konkretny przedmiot
                                debugLogs.add(0, "🎯 Tryb Precyzyjny: Szukam ${target.name}")
                                val singleDevice = listOfNotNull(topologyDatabase.getDeviceByMac(target.associatedMac))
                                bleManager.sendFilterToEsp(singleDevice)
                            } else {
                                // TRYB MAKRO: Szukamy wejścia do strefy
                                debugLogs.add(0, "📍 Tryb Eksploracji: Kieruj do ${target.name}")
                                // Pobieramy wszystkie kotwice/tagi dla tej lokalizacji
                                val areaDevices = topologyDatabase.getDevicesForLocation(target.locationId)
                                bleManager.sendFilterToEsp(areaDevices)
                            }
                        },
                        onConnect = { // <--- TUTAJ PODPINAMY BLE!
                            startBleServices()
                            debugLogs.add(0, "🔄 Inicjalizacja połączenia z ESP32...")
                        },
                        onDisconnect = { // <--- NOWA AKCJA
                        bleManager.disconnect()
                        debugLogs.add(0, "🛑 Wymuszono rozłączenie (Manual)")
                         },
                        onTestApiClick = {
                            // Co robi przycisk Test API? Służy jako "Ręczne Odświeżenie"
                            scope.launch { fetchDatabase() }


                            // DO TESTÓW: Możesz tu na sztywno zmienić lokalizację,
                            // żeby zobaczyć jak pojawiają się przyciski mikro!
                            // currentLocationIdState.value = 2
                        }
                    )
                }
            }
        }
    }
    // Wyciągnięte do osobnej funkcji, żeby kod był czystszy
    private suspend fun fetchDatabase() {
        try {
            debugLogs.add(0, "📡 Pobieram mapę budynku...")
            val allDevices = RetrofitClient.apiService.getAllDevices()
            topologyDatabase.cachedDevices = allDevices

            val allTargets = RetrofitClient.apiService.getNavigationTargets()
            topologyDatabase.cachedTargets = allTargets

            debugLogs.add(0, "✅ Cache gotowy: ${allDevices.size} dev, ${allTargets.size} celów.")
        } catch (e: Exception) {
            debugLogs.add(0, "❌ BŁĄD API: ${e.message}")
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleServices() {
        Toast.makeText(this, "Rozpoczynam procedurę BLE...", Toast.LENGTH_SHORT).show()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            val device = bluetoothAdapter.getRemoteDevice("A4:E5:7C:DE:BC:F2") // ZMIEŃ NA SWÓJ MAC ESP32!
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
    onStartNavigation: (NavigationTarget) -> Unit, // zamiast string?? bo potrzebujemy tez mac adressu
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTestApiClick: () -> Unit, // <--- NOWY PARAMETR (Callback)
    currentLocationId: Int?, // Musisz przekazać to z MainActivity/RoutingEngine
    topologyDb: BuildingTopologyDatabase,
    currentEspFilter: String, // <--- dane wysylane APP->ESP

) {
    // 1. TWORZYMY LISTY NA PODSTAWIE STANU LOKALIZACJI bo zapomnialam "wyciągnąć" te listy z bazy wewnątrz funkcji @Composable.
    val macroTargets = topologyDb.getMacroTargets()
    val microTargets = topologyDb.getMicroTargets(currentLocationId)

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- GÓRNY PASEK STEROWANIA BLE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Asystent UWB", style = MaterialTheme.typography.titleLarge)

            // Pudełko na dwa przyciski (Połącz / Rozłącz)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Zielony
                ) {
                    Text("Połącz")
                }
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Rozłącz")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        Button(
            onClick = onTestApiClick, // <--- PRZYCISK TYLKO KRZYCZY "KLIKNIĘTO MNIE!"
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
        ) {
            Text("Testuj API (Location 2)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("📍 MAKRONAWIGACJA (Stałe)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(macroTargets) { target ->
                Button(
                    onClick = { onStartNavigation(target) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text(target.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("🎯 MIKRONAWIGACJA (W zasięgu)", style = MaterialTheme.typography.titleMedium)
        if (microTargets.isEmpty()) {
            Text("Brak precyzyjnych celów. Zbliż się do pokoju...", color = Color.Gray)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(microTargets) { target ->
                Button(
                    onClick = { onStartNavigation(target) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(target.name)
                }
            }
        }
        // 🔥 NOWOŚĆ: DEDYKOWANY PANEL STANU ESP32 🔥
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)) // Ciemnoszary
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📡 Aktualny filtr załadowany do ESP32:",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Tutaj wyświetli się np. U:0x001;B:ff:ff:12...
                Text(
                    text = currentEspFilter,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
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