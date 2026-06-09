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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    protected val debugLogs = mutableStateListOf<String>()  // Stan trzymający logi, obserwowany przez UI (Jetpack Compose)
    private val currentLocationIdState = mutableStateOf<Int?>(null)  // Stan Compose, który pamięta gdzie jesteśmy (np. Location_ID = 2)
    private val appToEspLogs = mutableStateListOf<String>() // --- KONSOLA 2: Wysyłka z Apki (APP -> ESP) ---
    private val currentTargetNameState = mutableStateOf("Brak celu (Wybierz coś z listy)")
    private val dbSyncVersion = mutableStateOf(0) //oberwowany przez Compose - informuje o zmiane w DB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //@TODO evoke fun to downloead data from DB into local cache (like singleton RAM file?)
        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        // 1. INICJALIZACJA SILNIKA I JEGO CALLBACK (Zamknięty klamrą!)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService) { locationId ->
            runOnUiThread {
                currentLocationIdState.value = locationId
            }
        }

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

                val dbVer = dbSyncVersion.value // <--- obserwujemy wersje bazy
                val macroList = remember(dbVer) { topologyDatabase.getMacroTargets() }
                val microList = remember(currentLocation, dbVer) { topologyDatabase.getMicroTargets(currentLocation) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationScreen(

                        modifier = Modifier.padding(innerPadding),
                        logs = debugLogs,
                        appToEspLogs = appToEspLogs,
                        currentTargetName = currentTargetNameState.value,
                        // PRZEKAZUJEMY GOTOWE, ŻYWE LISTY DO UI:
                        macroTargets = macroList,
                        microTargets = microList,
                        currentLocation = currentLocation,

                        // ✅ IMPLEMENTACJA REAKCJI NA RESET:
                        onClearNavigation = {
                            routingEngine.clearCurrentTarget() // Zerujemy cel w silniku
                            currentTargetNameState.value = "Brak celu (Wybierz coś z listy)" // Czyścimy UI

                            // Czyścimy filtr na ESP32 (wysyłamy pustą listę, by przestał filtrować cel)
                            bleManager.sendFilterToEsp(emptyList())

                            speechService.announceImportant("Nawigacja anulowana.")
                            debugLogs.add(0, "🛑 Manualnie zakończono nawigację.")
                        },


                        onStartNavigation = { target ->

                            routingEngine.setNavigationTarget(target)
                            currentTargetNameState.value = target.name
                            if (target.associatedMac != null) {
                                debugLogs.add(0, "🎯 Tryb Precyzyjny: Szukam ${target.name}")
                                val singleDevice = listOfNotNull(topologyDatabase.getDeviceByMac(target.associatedMac))
                                bleManager.sendFilterToEsp(singleDevice)
                            } else {
                                debugLogs.add(0, "📍 Tryb Eksploracji: Kieruj do ${target.name}")
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
                            scope.launch { fetchDatabase() }
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
            // NOWE: Logowanie Mikro
            appToEspLogs.add(0, "🎯 Cele Mikro (${allTargets.count { !it.isMacroTarget }}): " + allTargets.filter { !it.isMacroTarget }.joinToString(", ") { it.name })

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
    logs: List<String>, //esp-> app
    onStartNavigation: (NavigationTarget) -> Unit, // zamiast string?? bo potrzebujemy tez mac adressu
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTestApiClick: () -> Unit, // <--- NOWY PARAMETR (Callback)
    appToEspLogs: List<String>, // NOWA KONSOLA WYCHODZĄCA
    macroTargets: List<NavigationTarget>,
    microTargets: List<NavigationTarget>,
    currentTargetName: String,
    currentLocation: Int? = null,
    onClearNavigation: () -> Unit // ✅ NOWY PARAMETR


) {

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState), // <--- TO DAJE SCROLL CAŁEGO EKRANU
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), // Odstęp między kartą a przyciskiem
            verticalAlignment = Alignment.CenterVertically // Wyrównanie w pionie do środka
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)) // Ciemnoniebieski
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏁 ", fontSize = 24.sp)
                    Column {
                        Text(
                            "Aktywny cel podróży:",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            currentTargetName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

        // Czerwony przycisk "X" do natychmiastowego wyłączenia nawigacji
        Button(
            onClick = onClearNavigation,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.height(58.dp), // Wysokość dopasowana do karty
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text("X", color = Color.White, fontSize = 20.sp)
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
        Text("📍 MAKRONAWIGACJA (Stałe)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            macroTargets.forEach { target -> // <--- Pętla forEach zamiast items()
                Button(
                    onClick = { onStartNavigation(target) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) {
                    Text(target.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ==========================================================
        // 6. LISTA MIKRO (Przekazanie zmiennej 'microTargets' do UI)
        // ==========================================================
        Text("🎯 MIKRONAWIGACJA (Strefa: ${currentLocation ?: "Nieznana"})", style = MaterialTheme.typography.titleMedium)
        if (microTargets.isEmpty()) {
            Text("Brak precyzyjnych celów. Zbliż się do pokoju...", color = Color.Gray)

        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                microTargets.forEach { target -> // <--- Pętla forEach zamiast items()
                    Button(
                        onClick = { onStartNavigation(target) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text(target.name)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))


        //  KONSOLA: APP -> ESP / DATABASE 🔥
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
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

        Spacer(modifier = Modifier.height(8.dp))


        // KONSOLA DEBUG (odpowiednik nRF Connect)
        Text("Konsola Debugowania BLE:", style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp) // Zajmuje resztę ekranu
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