package com.polsl.bemyeyes

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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



class MainActivity : ComponentActivity() {

    private lateinit var topologyDatabase: BuildingTopologyDatabase
    private lateinit var speechService: AccessibilitySpeechService
    private lateinit var routingEngine: NavigationRoutingEngine
    private lateinit var bleManager: BleConnectionManager

    protected val debugLogs = mutableStateListOf<String>()  // Stan trzymający logi, obserwowany przez UI (Jetpack Compose)
    private val currentLocationIdState = mutableStateOf<Int?>(null)  // Stan Compose, który pamięta gdzie jesteśmy (np. Location_ID = 2)
    private val userPositionState = mutableStateOf<Pair<Double, Double>?>(null)
    private val appToEspLogs = mutableStateListOf<String>() // --- KONSOLA 2: Wysyłka z Apki (APP -> ESP) ---
    private val currentTargetNameState = mutableStateOf("Brak celu (Wybierz coś z listy)")
    private val dbSyncVersion = mutableStateOf(0) //oberwowany przez Compose - informuje o zmiane w DB
    private val autoCalibrationEngine = AutoCalibrationEngine()

    // --- Klatka schodowa z pliku JSON (tryb 3D) ---
    private lateinit var configStore: StairwellConfigStore
    private var stairwellSession: StairwellSession? = null
    private var localConfigActive = false // true = kotwice z pliku JSON zamiast z bazy danych
    private val stairwellStatus = mutableStateOf("Brak wczytanej klatki (kotwice z bazy danych)")
    private val stairwellWarnings = mutableStateListOf<String>()
    private val stairwellSnapshot = mutableStateOf<StairwellSnapshot?>(null)
    private val tagMacState = mutableStateOf(DEFAULT_TAG_MAC)

    // Systemowy wybieracz plików (bez żadnych uprawnień): użytkownik wskazuje stairwell_config.json
    private val pickConfigLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadConfigFromUri(uri)
    }

    private companion object {
        const val DEFAULT_TAG_MAC = "A4:E5:7C:DE:BC:F2" // dotychczasowa wartość z kodu; zmienialna w UI
        const val PREFS = "bemyeyes"
        const val PREF_TAG_MAC = "tag_mac"
    }

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

        // Klatka z pliku: zapisaną kopię wczytujemy PRZED fetchDatabase(), żeby baza jej nie nadpisała
        configStore = StairwellConfigStore(this)
        tagMacState.value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TAG_MAC, DEFAULT_TAG_MAC)
            ?: DEFAULT_TAG_MAC
        bleManager.onUwbMeasurement = { id, dist ->
            val snapshot = stairwellSession?.onMeasurement(id, dist)
            if (snapshot != null) {
                runOnUiThread {
                    stairwellSnapshot.value = snapshot
                    userPositionState.value = Pair(snapshot.position.x, snapshot.position.y)
                }
            }
        }
        configStore.loadSaved()?.let { applyStairwellConfig(it, persist = false) }

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

                        // --- Klatka schodowa z pliku JSON ---
                        onLoadStairwellClick = { pickConfigLauncher.launch(arrayOf("*/*")) },
                        onSendAnchorsClick = {
                            val anchors = topologyDatabase.cachedDevices.filter { it.deviceType == "UWB_ANCHOR" }
                            if (anchors.isEmpty()) {
                                appToEspLogs.add(0, "❌ Brak kotwic - wczytaj klatkę z pliku albo dane z bazy.")
                            } else {
                                // tag zaczyna mierzyć dopiero po otrzymaniu listy kotwic
                                bleManager.sendFilterToEsp(anchors)
                            }
                        },
                        stairwellStatus = stairwellStatus.value,
                        stairwellWarnings = stairwellWarnings,
                        stairwellInfo = describeSnapshot(stairwellSnapshot.value),
                        tagMac = tagMacState.value,
                        onTagMacChange = { newMac ->
                            tagMacState.value = newMac
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_TAG_MAC, newMac).apply()
                        },

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

                        }


                    )
                }
            }
        }
    }
    /**
     * Wczytuje konfigurację klatki z JSON: kotwice z pliku zastępują dane z bazy, trilateracja 2D jest
     * wyłączana, a pozycję liczy sesja 3D (filtry + solver + detekcja piętra).
     */
    private fun applyStairwellConfig(json: String, persist: Boolean): Boolean {
        return try {
            val config = StairwellConfig.fromJson(json)
            val session = StairwellSession(config)
            val levelNames = config.levels.sortedBy { it.z }.map { it.name }
            session.floorTracker.onLevelChanged = { from, to ->
                runOnUiThread {
                    val fromName = from?.let { levelNames.getOrNull(it) } ?: "?"
                    appToEspLogs.add(0, "🏢 Poziom: $fromName → ${levelNames.getOrNull(to) ?: to}")
                }
            }

            topologyDatabase.cachedDevices = config.toDevices()
            topologyDatabase.cachedTargets = emptyList()
            routingEngine.use2dPositioning = false
            routingEngine.currentLocationId = null // zimny start dla nowego zestawu kotwic
            currentLocationIdState.value = null
            userPositionState.value = null
            stairwellSession = session
            localConfigActive = true

            stairwellWarnings.clear()
            stairwellWarnings.addAll(config.validate())
            stairwellStatus.value =
                "✅ ${config.name.ifBlank { "(bez nazwy)" }}: ${config.anchors.size} kotwic, ${config.levels.size} poziomów (z pliku)"
            stairwellSnapshot.value = null
            dbSyncVersion.value += 1

            if (persist) {
                try {
                    configStore.save(json)
                } catch (e: Exception) {
                    appToEspLogs.add(0, "⚠️ Nie udało się zapisać kopii konfiguracji: ${e.message}")
                }
            }
            appToEspLogs.add(0, "📂 Wczytano klatkę z pliku. Kotwice: " + config.anchors.joinToString(", ") { espAnchorId(it.id) })
            true
        } catch (e: Exception) {
            appToEspLogs.add(0, "❌ Błąd konfiguracji klatki: ${e.message}")
            false
        }
    }

    private fun loadConfigFromUri(uri: Uri) {
        try {
            applyStairwellConfig(configStore.readUri(uri), persist = true)
        } catch (e: Exception) {
            appToEspLogs.add(0, "❌ Nie można odczytać pliku: ${e.message}")
        }
    }

    private fun describeSnapshot(s: StairwellSnapshot?): String {
        if (s == null) return ""
        val p = s.position
        val floor = when (val f = s.floorState) {
            is FloorState.AtLevel -> "na poziomie: ${f.name}"
            is FloorState.OnStairs -> "na schodach, kierunek: ${f.direction}"
            is FloorState.Unknown -> "poziom nieznany"
        }
        val flags = listOfNotNull(
            if (p.ambiguous) "NIEJEDNOZNACZNE (odbicie lustrzane)" else null,
            if (!p.insideVolume) "POZA BRYŁĄ KLATKI" else null
        )
        return String.format(
            java.util.Locale.US,
            "x=%.2f  y=%.2f  z=%.2f m\nRMS reszt: %.3f m, kotwic: %d\n%s",
            p.x, p.y, p.z, p.rmsResidual, s.usedAnchors, floor
        ) + flags.joinToString("") { "\n⚠ $it" }
    }

    // Wyciągnięte do osobnej funkcji, żeby kod był czystszy
    private suspend fun fetchDatabase() {
        try {
            if (localConfigActive) {
                appToEspLogs.add(0, "ℹ️ Aktywna klatka z pliku JSON - pomijam pobieranie z bazy.")
                return
            }
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
            val tagMac = tagMacState.value.trim().uppercase()
            if (!BluetoothAdapter.checkBluetoothAddress(tagMac)) {
                debugLogs.add(0, "BŁĄD: niepoprawny adres MAC tagu: '$tagMac'")
                return
            }
            val device = bluetoothAdapter.getRemoteDevice(tagMac)
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
    onClearNavigation: () -> Unit ,
    onCalibrateZoneClick: (Int?) -> Unit,
    onCalibrateTagClick: (IoTDevice) -> Unit, // brak UI dla wyboru taga do kalib (dla  konkretnego locationID)
    zoneDevices: List<IoTDevice>,
    onLoadStairwellClick: () -> Unit,
    onSendAnchorsClick: () -> Unit,
    stairwellStatus: String,
    stairwellWarnings: List<String>,
    stairwellInfo: String,
    tagMac: String,
    onTagMacChange: (String) -> Unit,
    userPosition: Pair<Double, Double>?
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

        Spacer(modifier = Modifier.height(8.dp))
        // ==========================================================
        // PANEL ADMINA: Przycisk Auto-Kalibracji
        // ==========================================================
        Button(
            onClick = { onCalibrateZoneClick(currentLocation) },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
        ) {
            Text("🔧 ADMIN: Autokalibruj obecną strefę")
        }
        Spacer(modifier = Modifier.height(4.dp))

        // ==========================================================
        // NOWY PANEL: MAPA 2D
        // ==========================================================
        // ==========================================================
        // PANEL: KLATKA SCHODOWA (plik JSON, pozycja 3D)
        // ==========================================================
        Text("🏢 Klatka schodowa (plik JSON)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = tagMac,
            onValueChange = onTagMacChange,
            label = { Text("Adres MAC tagu (ESP32)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLoadStairwellClick, modifier = Modifier.weight(1f)) {
                Text("Wczytaj klatkę (JSON)")
            }
            Button(onClick = onSendAnchorsClick, modifier = Modifier.weight(1f)) {
                Text("Wyślij kotwice do ESP")
            }
        }
        Text(stairwellStatus, fontSize = 12.sp)
        stairwellWarnings.forEach { warning ->
            Text("⚠ $warning", color = Color(0xFFFF9800), fontSize = 12.sp)
        }
        if (stairwellInfo.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text(
                    stairwellInfo,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

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
                } else {
                    // Cel BLE = Szare Koło
                    drawCircle(
                        color = Color.LightGray,
                        radius = 12f,
                        center = Offset(canvasX, canvasY)
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