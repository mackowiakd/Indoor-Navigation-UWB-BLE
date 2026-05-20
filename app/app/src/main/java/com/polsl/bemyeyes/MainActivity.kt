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

    // --- KONSOLA 2: Wysyłka z Apki (APP -> ESP) ---
    private val appToEspLogs = mutableStateListOf<String>() //  Pamięta ostatnio wysłany filtr
    private val currentTargetNameState = mutableStateOf("Brak celu (Wybierz coś z listy)")

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
                if (nowaWiadomosc.contains("Wysłano")) {
                    appToEspLogs.add(0, nowaWiadomosc)
                    if (appToEspLogs.size > 20) appToEspLogs.removeAt(appToEspLogs.size - 1)
                } else {
                    // W przeciwnym razie - to odczyt z ESP32, idzie do głównej konsoli
                debugLogs.add(0, nowaWiadomosc) // Najnowsze na samej górze
                if (debugLogs.size > 50) debugLogs.removeAt(debugLogs.size - 1) // Pamiętamy tylko 50 ostatnich
                }
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
                val appLogs = appToEspLogs.first()// <--- Odczytujemy stan
                // Pamięta nazwę celu, do którego aktualnie idziemy
                // MAGIA COMPOSE: Teraz kiedy dbVer się zmieni, Kotlin obliczy listy na nowo!
                val macroList = remember(dbVer) { topologyDatabase.getMacroTargets() }
                val microList = remember(currentLocation, dbVer) { topologyDatabase.getMicroTargets(currentLocation) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(

                        modifier = Modifier.padding(innerPadding),
                        logs = debugLogs,
                        currentLocationId = currentLocation, // PRZEKAZUJEMY STAN
                        topologyDb = topologyDatabase,
                        appLogs = appToEspLogs,
                        currentTargetName = currentTargetNameState.value,


                        onStartNavigation = { target ->

                            if (target.associatedMac != null) {
                                // 1. KLUCZOWE UZUPEŁNIENIE: Budzimy silnik nawigacji i przekazujemy cel!
                                routingEngine.setNavigationTarget(target)
                                currentTargetNameState.value = target.name
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
                            //loging/printing current location list mikro & makro
                            // --- LOGOWANIE ZAWARTOŚCI BAZY ---
                            val devices = topologyDatabase.cachedDevices
                            val macro = topologyDatabase.getMacroTargets()
                            val micro = topologyDatabase.getMicroTargets(currentLocationIdState.value)

                            //z apki do espa
                            appToEspLogs.add(0, "📊 --- STATUS BAZY DANYCH ---")
                            appToEspLogs.add(0, "📱 Urządzenia (${devices.size}): " + devices.joinToString(", ") { it.macAddress })
                            appToEspLogs.add(0, "📍 Cele Makro (${macro.size}): " + macro.joinToString(", ") { it.name })
                            appToEspLogs.add(0, "🎯 Cele Mikro dla Loc=${currentLocationIdState.value} (${micro.size})")
                            appToEspLogs.add(0, "📊 ---------------------------")


                        }
                    )
                }
            }
        }
    }
    // Wyciągnięte do osobnej funkcji, żeby kod był czystszy
    private suspend fun fetchDatabase() {
        try {
            appToEspLogs.add(0, "📡 Łączę z PostgreSQL...")
            val allDevices = RetrofitClient.apiService.getAllDevices()
            val allTargets = RetrofitClient.apiService.getNavigationTargets()

            topologyDatabase.cachedDevices = allDevices
            topologyDatabase.cachedTargets = allTargets

            // ⚠️ Zmuszamy Jetpack Compose do odświeżenia UI ⚠️
            dbSyncVersion.value += 1

            appToEspLogs.add(0, "📊 --- STATUS BAZY DANYCH ---")
            appToEspLogs.add(
                0,
                "📱 Urządzenia (${allDevices.size}): " + allDevices.joinToString(", ") { it.macAddress })
            appToEspLogs.add(
                0,
                "📍 Cele Makro (${allTargets.count { it.isMacroTarget }}): " + allTargets.filter { it.isMacroTarget }
                    .joinToString(", ") { it.name })
            appToEspLogs.add(0, "📊 ---------------------------")

        } catch (e: Exception) {
            appToEspLogs.add(0, "❌ BŁĄD API: ${e.message}")

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
    appToEspLogs: List<String>, // NOWA KONSOLA WYCHODZĄCA
    currentTargetName: String,

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
        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================================
        // 3. PANEL: AKTYWNY CEL NAWIGACJI
        // ==========================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)) // Ciemnoniebieski
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏁 ", fontSize = 24.sp)
                Column {
                    Text("Aktywny cel podróży:", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    Text(currentTargetName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))


        Button(
            onClick = onTestApiClick, // <--- PRZYCISK TYLKO KRZYCZY "KLIKNIĘTO MNIE!"
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
        ) {
            Text("Testuj API ")
        }

        Spacer(modifier = Modifier.height(16.dp))
        //obie nie dzialaja nic sie nie zmienia nie da sie kliknac- zwykly tekst
        // ==========================================================
        // 5. LISTA MAKRO (Przekazanie zmiennej 'macroTargets' do UI)
        // ==========================================================
        Text("📍 MAKRONAWIGACJA (Stałe)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(macroTargets) { target -> // <--- TUTAJ UŻYWAMY LISTY MAKRO
                Button(
                    onClick = { onStartNavigation(target) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text(target.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================================
        // 6. LISTA MIKRO (Przekazanie zmiennej 'microTargets' do UI)
        // ==========================================================
        Text("🎯 MIKRONAWIGACJA (W zasięgu)", style = MaterialTheme.typography.titleMedium)
        if (microTargets.isEmpty()) {
            Text("Brak precyzyjnych celów. Zbliż się do pokoju...", color = Color.Gray)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(microTargets) { target -> // <--- TUTAJ UŻYWAMY LISTY MIKRO
                Button(
                    onClick = { onStartNavigation(target) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(target.name)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))


        // 🔥 [3] NOWA KONSOLA: APP -> ESP / DATABASE 🔥
        Card(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("APP -> ESP / DATABASE:", color = Color.Yellow, style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(appToEspLogs) { msg ->
                        Text(msg, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
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