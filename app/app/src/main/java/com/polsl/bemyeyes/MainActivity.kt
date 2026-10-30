package com.polsl.bemyeyes

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.polsl.bemyeyes.navigation.*
import com.polsl.bemyeyes.navigation.dataBase.BuildingTopologyDatabase
import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget
import com.polsl.bemyeyes.navigation.dataBase.RetrofitClient
import com.polsl.bemyeyes.ui.theme.BeMyEyesTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle


class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var telemetryLogger: TelemetryLogger
    private lateinit var bleManager: BleConnectionManager

    protected val debugLogs = mutableStateListOf<String>()  // Stan trzymający logi, obserwowany przez UI (Jetpack Compose)
    private val currentLocationIdState = mutableStateOf<Int?>(null)  // Stan Compose, który pamięta gdzie jesteśmy (np. Location_ID = 2)
    private val userPositionState = mutableStateOf<Pair<Double, Double>?>(null)
    private val appToEspLogs = mutableStateListOf<String>() // --- KONSOLA 2: Wysyłka z Apki (APP -> ESP) ---
    private val currentTargetNameState = mutableStateOf("Brak celu (Wybierz coś z listy)")
    private val dbSyncVersion = mutableStateOf(0) //oberwowany przez Compose - informuje o zmiane w DB
    private val autoCalibrationEngine = AutoCalibrationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        //@TODO evoke fun to downloead data from DB into local cache (like singleton RAM file?)
        topologyDatabase = BuildingTopologyDatabase()
        speechService = AccessibilitySpeechService(this)
        telemetryLogger = TelemetryLogger(this)
        // 1. INICJALIZACJA SILNIKA I JEGO CALLBACK (Zamknięty klamrą!)
        routingEngine = NavigationRoutingEngine(topologyDatabase, speechService, telemetryLogger ) { locationId ->
            runOnUiThread {
                currentLocationIdState.value = locationId
            }
        }
        routingEngine.onPositionUpdated = { x, y ->
            runOnUiThread {
                userPositionState.value = Pair(x, y)
            }
        }

        bleManager = BleConnectionManager(routingEngine, autoCalibrationEngine) { nowaWiadomosc ->
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
        // Wewnątrz onCreate():
        bleManager.onCalibrationResultReceived = { rawData ->
            runOnUiThread {
                appToEspLogs.add(0, "🛠 Otrzymano pomiary. Przeliczam kalibrację...")

                lifecycleScope.launch {
                    try {
                        // Silnik przetwarza surowy tekst i zwraca przeliczone kotwice na bazie zapamiętanych wcześniej kotwic
                        val calibratedAnchors = autoCalibrationEngine.processCalibrationData(rawData)

                        appToEspLogs.add(0, "✅ Kalibracja Zakończona! Wysyłam do bazy...")

                        calibratedAnchors.forEach { anchor ->
                            val updatePayload = mapOf(
                                "global_x" to (anchor.globalX ?: 0.0),
                                "global_y" to (anchor.globalY ?: 0.0)
                            )

                            val response = RetrofitClient.apiService.updateDeviceCoordinates(
                                "eq.${anchor.macAddress}",
                                updatePayload
                            )

                            if (response.isSuccessful) {
                                appToEspLogs.add(0, "☁️ Zaktualizowano DB dla: ${anchor.macAddress} (X=${anchor.globalX}, Y=${anchor.globalY})")
                            } else {
                                appToEspLogs.add(0, "❌ Odrzucono zapis dla ${anchor.macAddress}. Kod: ${response.code()}")
                            }
                        }
                    } catch (e: Exception) {
                        appToEspLogs.add(0, "❌ Błąd kalibracji / zapisu DB: ${e.message}")
                    }
                }
            }
        }

        setContent {
            BeMyEyesTheme {
                val scope = rememberCoroutineScope()
                // Odczytujemy stan lokalizacji. Gdy się zmieni, UI się przebuduje!
                val currentLocation = currentLocationIdState.value

                val dbVer = dbSyncVersion.value // <--- obserwujemy wersje bazy
                val macroList = remember(dbVer) { topologyDatabase.getMacroTargets() }
                val microList = remember(currentLocation, dbVer) { topologyDatabase.getMicroTargets(currentLocation) }
                // Pobieramy WSZYSTKIE urządzenia z obecnego pokoju na potrzeby mapy

                val userPos = userPositionState.value
                val zoneDevicesList = remember(currentLocation, dbVer) {
                    currentLocation?.let { topologyDatabase.getDevicesForLocation(it) } ?: emptyList()
                }

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
                        zoneDevices = zoneDevicesList,
                        userPosition = userPos,

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
                        onDisconnect = {

                        bleManager.disconnect() // 1. Zatrzymanie sprzętowe
                        routingEngine.currentLocationId = null
                        routingEngine.clearCurrentTarget()  // 2. Czyszczenie silnika nawigacji (Zatrzymanie Watchdoga i wyzerowanie celu) // routingEngine.currentLocationId = null
                        // 3. Resetowanie interfejsu
                        currentTargetNameState.value = "Brak celu (Wybierz coś z listy)"
                        currentLocationIdState.value = null
                        userPositionState.value = null
                        // Opcjonalne wyczyszczenie filtru
                        bleManager.sendFilterToEsp(emptyList())
                        debugLogs.add(0, "🛑 Wymuszono rozłączenie (Manual)")
                         },
                        onTestApiClick = {
                            scope.launch { fetchDatabase() }
                        },

                        // --- NOWE: Wpinamy logikę wywołania kalibracji ---
                        onCalibrateZoneClick = { locationId ->
                            if (locationId != null) {
                                appToEspLogs.add(
                                    0,
                                    "🛠 [ADMIN] Uruchamiam Auto-Kalibrację dla strefy $locationId"
                                )

                                val zoneAnchors = topologyDatabase.getDevicesForLocation(locationId)
                                    .filter { it.deviceType == "UWB_ANCHOR" }

                                if (zoneAnchors.size >= 2) {
                                     // 1. Przekazujemy kotwice do silnika, żeby pamiętał, co kalibrujemy
                                    autoCalibrationEngine.prepareCalibration(zoneAnchors)

                                    // 2. Wysyłamy komendę do ESP32 i czekamy na asynchroniczny odzew w callbacku
                                    bleManager.sendFilterToEsp(zoneAnchors, "CALIB")
                                    appToEspLogs.add(0, "⏳ Wysłano żądanie do ESP32. Oczekiwanie na pomiary UWB...")
                                    //i tu robimy drugie zadanie pomiaru (dla 3 > kotwic w naszej lokalizacji => sprawdzamuy czy jest tyle dostepnych
                                    //jesi nie to zwracamy -1 jako not possible,
                                    //w sliniku AuCalib dodajemy funkcje ktora z przetwrza te 3 pomiary z kotwicy i nadaje tagowi
                                    scope.launch { fetchDatabase() } //odswiezamy DB

                                } else {
                                    appToEspLogs.add(0, "❌ Za mało kotwic w strefie do kalibracji (wymagane min. 2)")
                                }
                            } else {
                                appToEspLogs.add(0, "❌ Najpierw musisz wejść do jakiejś strefy (Cold Start)!")
                            }

                        },
                        onCalibrateTagClick = { tag ->
                            appToEspLogs.add(0, "🛠 Rozpoczęto nasłuch UWB dla taga: $tag.. Trzymaj urządzenie w bezruchu...")

                        val zoneAnchors = topologyDatabase.getDevicesForLocation(
                            routingEngine.currentLocationId!!)
                        autoCalibrationEngine.startTagCalibration(tag, zoneAnchors)

                        },
                        onStartContinuousLog = {
                            telemetryLogger.startLogging(false )
                            appToEspLogs.add(0, "🔴 Rozpoczęto logowanie ciągłe (bez X, Y)")
                        },

                        onStopLogging = {
                            val fileName = telemetryLogger.currentSessionFile?.name
                            telemetryLogger.stopLogging()
                            // Wizualne potwierdzenie natychmiast na ekranie (Toast)
                            Toast.makeText(this, "📁 Zapisano plik: ${fileName ?: "brak pliku"}", Toast.LENGTH_LONG).show()
                            appToEspLogs.add(0, "⏹ Zatrzymano logowanie, plik zapisany")
                        },
                        //onStopLogging -brak

                        onLogStaticSample = { refX, refY ->
                            scope.launch {
                                // 1. Otwieramy plik dedykowany dla statyki (uwb_static_...)
                                telemetryLogger.startLogging(isStatic = true)
                                appToEspLogs.add(0, "⏳ Zbieram serię 10 pomiarów dla X:$refX, Y:$refY...")

                                var successfulSamples = 0

                                // 2. Pętla zbierająca próbki przez chwilę (np. 10 iteracji co 200ms = 2 sekundy pomiaru)
                                for (i in 1..10) {
                                    //distance will be deliv by clallback => we log only if recieve new
                                    val snapshotDistance = routingEngine.getCurrentDistancesSnapshot()
                                    val snapshotPoint = routingEngine.getCurrentPositionSnapshot()

                                    if (snapshotDistance.isNotEmpty()) {
                                        snapshotDistance.forEach { (macAddress, distance) ->
                                            telemetryLogger.logDataRow(
                                                refX = refX,
                                                refY = refY,
                                                measuredDist = distance,
                                                anchorId = macAddress,
                                                calculatedPose = snapshotPoint
                                            )
                                        }
                                        successfulSamples++
                                    }

                                    // Czekamy chwilę, aż układ UWB wypluje kolejną, świeżą paczkę danych
                                    kotlinx.coroutines.delay(200)
                                }

                                // 3. Zamykamy plik po zebraniu całej serii
                                telemetryLogger.stopLogging()

                                if (successfulSamples > 0) {
                                    appToEspLogs.add(0, "✅ Zapisano serię ($successfulSamples paczek) dla X:$refX, Y:$refY")
                                    Toast.makeText(this@MainActivity, "📁 Zapisano serię pomiarów statycznych!", Toast.LENGTH_SHORT).show()
                                } else {
                                    appToEspLogs.add(0, "⚠️ Brak sygnału UWB podczas próby dla X:$refX, Y:$refY")
                                    Toast.makeText(this@MainActivity, "⚠️ Brak danych z kotwic!", Toast.LENGTH_SHORT).show()
                                }
                            }
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
    onTestApiClick: () -> Unit, // <---  PARAMETR (Callback)
    appToEspLogs: List<String>, //  KONSOLA WYCHODZĄCA
    macroTargets: List<NavigationTarget>,
    microTargets: List<NavigationTarget>,
    currentTargetName: String,
    currentLocation: Int? = null,
    onClearNavigation: () -> Unit,
    onCalibrateZoneClick: (Int?) -> Unit,
    onCalibrateTagClick: (IoTDevice) -> Unit, // TODO UI dla wyboru tagaBLE do kalib (dla  konkretnego locationID)
    zoneDevices: List<IoTDevice>,
    userPosition: Pair<Double, Double>?,
    onStartContinuousLog: () -> Unit,
    onStopLogging: () -> Unit,
    onLogStaticSample: (Double, Double) -> Unit
) {

    val scrollState = rememberScrollState()
    // for telemetryTest
    var isDevPanelExpanded by remember { mutableStateOf(false) }
    var refXInput by remember { mutableStateOf("") }
    var refYInput by remember { mutableStateOf("") }
    var isContinuousLogging by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(8.dp))
        // ==========================================================
        // PANEL ADMINA: Przycisk Auto-Kalibracji - zamiast tego  PRZYCISK ROZWIJANIA PANELU ---
        // ==========================================================
      //TODO callbacki do zapisu onLogStaticSample(x, y) i logDataRow(..)
        Button(
            onClick = { isDevPanelExpanded = !isDevPanelExpanded },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text(if (isDevPanelExpanded) "Ukryj Panel Dewelopera" else "⚙️ Pokaż Panel Dewelopera")
        }
//        Button(
//            onClick = { onCalibrateZoneClick(currentLocation) },
//            modifier = Modifier.fillMaxWidth().height(40.dp),
//            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
//        ) {
//            Text("🔧 ADMIN: Autokalibruj obecną strefę")
//        }
        // --- ZAWARTOŚĆ PANELU ---
        if (isDevPanelExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Narzędzia Testowe", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. ZARZĄDZANIE KALIBRACJĄ
                    Button(
                        onClick = { onCalibrateZoneClick(currentLocation) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Autokalibruj obecną strefę (Infrastruktura)")
                    }


                    Divider(color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. LOGOWANIE CIĄGŁE (Test w ruchu - bez referencji)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isContinuousLogging,
                            onCheckedChange = { isChecked ->
                                isContinuousLogging = isChecked
                                if (isChecked) onStartContinuousLog() else onStopLogging() //main ma wywolac callback do logDataRow( X, Y distance, anchor). x,y not null we need them
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logowanie ciągłe (bez współrzędnych)", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. LOGOWANIE PUNKTOWE (Test statyczny z podaniem X, Y)
                    Text("Referencyjny Punkt testowy:", color = Color.LightGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = refXInput,
                            onValueChange = { refXInput = it },
                            label = { Text("X [m]") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = refYInput,
                            onValueChange = { refYInput = it },
                            label = { Text("Y [m]") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val x = refXInput.toDoubleOrNull()
                            val y = refYInput.toDoubleOrNull()
                            if (x != null && y != null) {
                                onLogStaticSample(x, y)
                                // ROZWIĄZANIE 1: ZEROWANIE PÓL
                                // Po poprawnym pobraniu i wysłaniu danych, czyścimy UI
                                refXInput = ""
                                refYInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                    ) {
                        Text("Zapisz Próbkę Statyczną")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // ROZWIĄZANIE 2: GŁÓWNY PRZYCISK ZAKOŃCZENIA LOGOWANIA
                    Button(
                        onClick = {
                            onStopLogging()
                            isContinuousLogging = false // Odklikujemy też switcha, żeby UI było spójne
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("⏹ Zakończ logowanie i ZAPISZ plik CSV")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // ==========================================================
        // NOWY PANEL: MAPA 2D
        // ==========================================================
        Text("📍 Podgląd Przestrzenny (Live)", style = MaterialTheme.typography.titleMedium)
        RoomMapCanvas(
            devices = zoneDevices,
            userPosition = userPosition,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
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

        Spacer(modifier = Modifier.height(8.dp))

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

@Composable
fun RoomMapCanvas(
    devices: List<IoTDevice>,
    userPosition: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = Color.White, fontSize = 8.sp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFF222222)) // Ciemne tło wykresu
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 1. Zbieramy wszystkie współrzędne (urządzenia + użytkownik)
            val allXs = devices.mapNotNull { it.globalX } + listOfNotNull(userPosition?.first)
            val allYs = devices.mapNotNull { it.globalY } + listOfNotNull(userPosition?.second)

            // Jeśli nie ma żadnych urządzeń, przerywamy rysowanie
            if (allXs.isEmpty() || allYs.isEmpty()) return@Canvas

            // 2. Szukamy skrajnych wartości (rozmiar strefy)
            val minX = allXs.minOrNull() ?: 0.0
            val maxX = allXs.maxOrNull() ?: 0.0
            val minY = allYs.minOrNull() ?: 0.0
            val maxY = allYs.maxOrNull() ?: 0.0

            // 3. Obliczamy szerokość i wysokość w fizycznych metrach
            // Dodajemy 1.5 metra "marginesu" z każdej strony, żeby obiekty nie dotykały krawędzi ekranu
            val paddingMeters = 1.5
            val physicalWidth = (maxX - minX + 2 * paddingMeters).toFloat()
            val physicalHeight = (maxY - minY + 2 * paddingMeters).toFloat()

            // 4. DYNAMICZNA SKALA  (piksele na metr)
            // Bierzemy mniejszą wartość, aby cała strefa na pewno zmieściła się na ekranie
            val scaleX = size.width / physicalWidth
            val scaleY = size.height / physicalHeight
            val scale = minOf(scaleX, scaleY)

            // 5. Wyliczamy środek i nowy początek układu współrzędnych (0,0)
            val centerX = ((minX + maxX) / 2).toFloat()
            val centerY = ((minY + maxY) / 2).toFloat()

            // Przesuwamy punkt startowy, aby środek naszej strefy trafił idealnie w środek Canvasu
            val originX = (size.width / 2f) - (centerX * scale)
            val originY = (size.height / 2f) + (centerY * scale) // Plus, bo oś Y rośnie do góry

            // 6. Rysowanie dynamicznej siatki (kratki co 1 metr)
            val startGridX = Math.floor(minX - paddingMeters).toInt()
            val endGridX = Math.ceil(maxX + paddingMeters).toInt()
            for (i in startGridX..endGridX) {
                val gridX = originX + (i * scale)
                drawLine(Color.DarkGray, Offset(gridX, 0f), Offset(gridX, size.height), 1f)
            }

            val startGridY = Math.floor(minY - paddingMeters).toInt()
            val endGridY = Math.ceil(maxY + paddingMeters).toInt()
            for (i in startGridY..endGridY) {
                val gridY = originY - (i * scale)
                drawLine(Color.DarkGray, Offset(0f, gridY), Offset(size.width, gridY), 1f)
            }

            // 7. Rysowanie Urządzeń z Bazy (Kotwice i Tagi)
            devices.forEach { device ->
                val x = device.globalX?.toFloat() ?: return@forEach
                val y = device.globalY?.toFloat() ?: return@forEach
                // Przygotowujemy skróconą etykietę (np. "A_5d" z adresu "0x005d" albo semanticRole)
                val shortName = if (device.deviceType == "UWB_ANCHOR") {
                    "A_" + device.macAddress.takeLast(2).uppercase() // Z "0x0001" zrobi "A_01"
                } else {
                    device.semanticRole ?: "POI" // dla BLE np "Coffee" z bazy
                }
                // 2. NOWE: Formatowanie współrzędnych (np. "A_01\n(4.50m, 0.00m)")
                val coordsText = String.format(java.util.Locale.US, "\n(%.2fm, %.2fm)", x, y)
                val fullLabel = shortName + coordsText
                // Tłumaczymy metry na piksele (zwróć uwagę na minus przy osi Y)
                val canvasX = originX + (x * scale)
                val canvasY = originY - (y * scale)

                if (device.deviceType == "UWB_ANCHOR") {
                    // Kotwica UWB = Niebieski Kwadrat
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(canvasX - 15f, canvasY - 15f),
                        size = androidx.compose.ui.geometry.Size(30f, 30f)
                    )
                    // Podpisujemy kotwicę
                    drawText(
                        textMeasurer = textMeasurer,
                        text = fullLabel,
                        topLeft = Offset(canvasX - 15f, canvasY + 20f), // Pod kwadratem
                        style = textStyle
                    )
                } else {
                    // Cel BLE = Szare Koło
                    drawCircle(
                        color = Color.LightGray,
                        radius = 12f,
                        center = Offset(canvasX, canvasY)
                    )
                    // Podpisujemy BEACON
                    drawText(
                        textMeasurer = textMeasurer,
                        text = fullLabel,
                        topLeft = Offset(canvasX - 15f, canvasY + 15f), // Pod kołem
                        style = textStyle
                    )
                }
            }

            // 3. Rysowanie Użytkownika (Wynik algorytmu)
            userPosition?.let { (x, y) ->
                val canvasX = originX + (x.toFloat() * scale)
                val canvasY = originY - (y.toFloat() * scale)

                // Użytkownik = Czerwona Kropka
                drawCircle(
                    color = Color.Red,
                    radius = 18f,
                    center = Offset(canvasX, canvasY)
                )
            }
        }
    }
}