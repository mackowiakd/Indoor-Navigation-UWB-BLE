package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget
import kotlin.math.roundToInt
import kotlinx.coroutines.*

/**
 * Silnik analizujący stany przejść grafu budynku oraz dystrybuujący komendy głosowe.
 */
class NavigationRoutingEngine(
    private val buildingTopologyDB: BuildingTopologyDatabase,
    private val speechService: AccessibilitySpeechService, // Upewnij się, że ta klasa istnieje
    private val onLocationChanged: ((Int) -> Unit)? = null //callback o zmienie locationID
) {

    // 1. STANY SILNIKA NAWIGACJI

    private var currentTarget: NavigationTarget? = null // Dokąd idę?

    private var lastPassAnnouncementTimeMs: Long = 0
    private val PASSING_THRESHOLD_METERS = 2.0
    private val ANNOUNCEMENT_COOLDOWN_MS = 8000
    private var lastArrivalAnnouncementTimeMs = 0L   // Kiedy ostatnio powiedziano "Cel osiągnięty / Jesteś przed..."
    private var lastProximityAnnouncementTimeMs = 0L // Kiedy ostatnio powiedziano "Zbliżasz się..."

    // Stała konfiguracja: 6 sekund przerwy między powtórzeniami tego samego alertu
    private val TARGET_ZONE_COOLDOWN_MS = 6000L
    private var lastAnnouncedDistanceInt = -1

    //--zmienne stanu (przsylanie listy urzadzen do espa) --
    var currentLocationId: Int? = null // Gdzie aktualnie jestem? (do Cold Startu)
    private var lastTransitionTime: Long = 0  // Kiedy ostatnio zmieniliśmy strefę?

    // Ustawienia systemu
    private val COOLDOWN_MS = 4000L // 4 sekundy blokady po zmianie strefy (żeby wyjść z progu)
    private val TRIGGER_THRESHOLD = 2 // Zmiana strefy następuje dopiero, gdy podejdziemy bliżej niż na 2 metry
    // --- ZMIENNE WATCHDOGA ---
    private var watchdogJob: Job? = null
    private var lastTargetSignalTime: Long = 0L
    // =========================================================================
    // AKCJE UŻYTKOWNIKA (Wywoływane z MainActivity po kliknięciu przycisku)
    // =========================================================================

    fun setNavigationTarget(target: NavigationTarget) {
        // RESETUJEMY FLAGI PRZY WYBORZE NOWEGO CELU!
        //brak COLD start- > target null usatwiamy makro w
        lastArrivalAnnouncementTimeMs = 0
        lastProximityAnnouncementTimeMs = 0

        lastAnnouncedDistanceInt = -1
        currentTarget = target
        lastTargetSignalTime = System.currentTimeMillis() // Resetujemy czas!

        if (target.isMacroTarget) {
            speechService.announceImportant("Rozpoczynam nawigację do strefy: ${target.name}.")
        } else {
            speechService.announceImportant("Szukam precyzyjnie: ${target.name}.")
        }
        startSignalWatchdog()
    }

    private fun startSignalWatchdog() {
        watchdogJob?.cancel() // Ubijamy starego watchdoga (jeśli użytkownik szybko kliknął inny cel)

        watchdogJob = CoroutineScope(Dispatchers.Main).launch {
            // Pętla kręci się tak długo, jak mamy cel i do niego nie dotarliśmy
            while (currentTarget != null ) {
                delay(8000) // Usypiamy w tle na 8 sekund

                val timeSinceLastSignal = System.currentTimeMillis() - lastTargetSignalTime

                // Jeśli minęło więcej niż 8 sekund bez żadnego piku z BLE/UWB
                if (timeSinceLastSignal >= 8000) {
                    speechService.announceBackground("Sygnał słaby lub poza zasięgiem. Zrób kilka kroków.")
                }
            }
        }
    }
    // =========================================================================
    // AKCJE Z BLUETOOTHA (Wywoływane przez BleConnectionManager setki razy)
    // =========================================================================
    fun processScannedDevice(macAddress: String, distance: Double): List<IoTDevice>? {

        // 1. Szukamy, czy to urządzenie z Cache'u? ( w senie czy nie zlapalismy silnego sygnalu (przy cold start) z np kogos sluchawek BT -> zwraca null
        val scannedDev = buildingTopologyDB.cachedDevices.find { it.macAddress == macAddress } ?: return null


        // UWAGA: Sprawdzamy, czy urządzenie, które usłyszeliśmy, wymusza zmianę lokalizacji
        // Dzieje się tak podczas ZIMNEGO STARTU (currentLocationId == null)
        // LUB gdy złapaliśmy Trigger w windzie
        if (currentLocationId != scannedDev.locationId) {
            // 3. HISTEREZA: Czy przekroczyliśmy fizyczny próg nowej strefy?
            val currentTime = System.currentTimeMillis()


            if(currentLocationId == null){
                // Zwracamy listę nowych urządzeń OD RAZU (pierwsze uruchomienie)
                currentLocationId = scannedDev.locationId
                println("nowa LOKALIZACJI : $currentLocationId")
                //zapis czasu na kolejne pomiary
                lastTransitionTime = currentTime
                // ========================================================
                //  BRAKUJĄCY CALLBACK DLA COLD STARTU!
                // ========================================================
                onLocationChanged?.invoke(currentLocationId!!)
                return buildingTopologyDB.getDevicesForLocation(currentLocationId!!)
            }

            if (distance > TRIGGER_THRESHOLD) {
                return null // Widzimy nową strefę z daleka, ale jeszcze do niej nie weszliśmy.
            }

            // COOLDOWN: Ochrona przed szamotaniem (Ping-Pongiem) w drzwiach

            if (currentTime - lastTransitionTime < COOLDOWN_MS) {
                return null // Jesteśmy w trakcie "zamrożenia" po poprzedniej zmianie.
            }
            //ZMIENIAMY STREFĘ NA NOWĄ

            lastTransitionTime = currentTime
            currentLocationId = scannedDev.locationId
            // 🔥 KRZYCZYMY DO MAIN ACTIVITY:
            onLocationChanged?.invoke(currentLocationId!!)
            return buildingTopologyDB.getDevicesForLocation(currentLocationId!!)

        }

        return null
    }

    // =========================================================================
    // AKCJE Z BLUETOOTHA (Logika mijania i dotarcia)

    //- obsluga ID z kotwicy (rzutowanie na opwiedni format z DB?)
    // =========================================================================
    fun processNewTelemetryData(macAddress : String,distanceOrRssi: Double) {

        // 1. Zidentyfikuj, co usłyszało ESP32
        val detectedDevice = buildingTopologyDB.getDeviceByMac(macAddress) ?: return

        // 2. LOGIKA DOTARCIA DO CELU (Czy jestem blisko tego, co zaklikałem w UI?)
        if (currentTarget != null) {

            if (currentTarget!!.isMacroTarget) {
                announceDistanceProgress(detectedDevice,distanceOrRssi)
                // TRYB MAKRO: Osiągamy cel, jeśli zlapaliśmy sygnał z JAKIEGOKOLWIEK
                // urządzenia, które leży w docelowym Location_ID

                speechService.announceImportant("Jesteś w strefie: ${currentTarget!!.name}. Wybierz teraz dokładny cel z listy.")
                currentTarget = null // Osiągnięto cel, resetujemy!

                // UWAGA: UI samo się tu zaktualizuje, bo zmieni się currentLocationId

            } else {
                // TRYB MIKRO: Mierzymy odległość tylko do JEDNEGO konkretnego adresu MAC
                if (macAddress == currentTarget!!.associatedMac) {
                    announceDistanceProgress(detectedDevice,distanceOrRssi)
                }
            }
        }

        // 3. LOGIKA MIJANIA INNYCH OBIEKTÓW PO DRODZE (Eksploracja tła)
        if (distanceOrRssi <= PASSING_THRESHOLD_METERS) {

            // Upewniamy się, że nie mówimy "Mijasz biurko", jeśli biurko jest naszym głównym celem
            val isMyMainTarget = (currentTarget != null && !currentTarget!!.isMacroTarget && macAddress == currentTarget!!.associatedMac)

            if (!isMyMainTarget) {
                // Wywołujemy Twoją odświeżoną funkcję!
                evaluatePathAndAnnounce(detectedDevice)
            }
        }
    }

    // =========================================================================
    // FUNKCJE POMOCNICZE AUDIO
    // =========================================================================

    private fun evaluatePathAndAnnounce(detectedDevice: IoTDevice) {
        val currentTime = System.currentTimeMillis()

        // Sprawdzamy Cooldown (żeby nie spamować użytkownika)
        if (currentTime - lastPassAnnouncementTimeMs > ANNOUNCEMENT_COOLDOWN_MS) {
            lastPassAnnouncementTimeMs = currentTime

            // String Interpolation: wrzucamy zmienną prosto do cudzysłowu!
            // Używamy semanticRole, bo to tam w bazie trzymamy opis np. "Ekspres do kawy"
            speechService.announceBackground("Mijasz: ${detectedDevice.semanticRole}")
        }
    }
    private fun announceDistanceProgress(device: IoTDevice,distance: Double) {
        val distanceInt = distance.roundToInt()
        val dest = currentTarget ?: return // Używamy nowej zmiennej currentTarget!
        val currentTime = System.currentTimeMillis()

        lastTargetSignalTime = currentTime // tzn ze dostalismy syganl z targetu

        if (device.deviceType == "UWB_ANCHOR") {
            // =======================================================
            // ŚCIEŻKA UWB: Dokładne odliczanie ("Autostrada")
            // Scenariusz: Finisz (1.5m) -> Tutaj kończymy podróż!
            if (distance <= 1.5) {
                if (currentTime - lastArrivalAnnouncementTimeMs > TARGET_ZONE_COOLDOWN_MS) {
                    lastArrivalAnnouncementTimeMs = currentTime


                    // Gdyby w przyszłości pojawił się cel mikro na UWB
                    speechService.announceImportant("Dotarłeś do celu. Jesteś przed ${dest.name}")


                    currentTarget =
                        null // 🔥 Resetujemy cel DOPIERO na prawdziwym finiszu odległościowym!
                    return
                }
            }

            // Scenariusz: Zbliżanie się// czyli to zostawiamy bez podzialu na device type?
            if (distance > 1.5) {
                if (distanceInt <= 10) {
                    if (distanceInt != lastAnnouncedDistanceInt) {
                        speechService.announceBackground("$distanceInt ${getMeterSpelling(distanceInt)}")
                        lastAnnouncedDistanceInt = distanceInt
                    }
                } else {
                    if (currentTime - lastProximityAnnouncementTimeMs > TARGET_ZONE_COOLDOWN_MS) {
                        lastProximityAnnouncementTimeMs = currentTime

                        speechService.announceImportant("Jesteś w strefie: ${dest.name}. Wybierz teraz dokładny cel z listy.")

                        val roundedTo5 = (distanceInt / 5) * 5
                        if (roundedTo5 != lastAnnouncedDistanceInt && distanceInt % 5 == 0) {
                            speechService.announceBackground(
                                "$roundedTo5 ${
                                    getMeterSpelling(
                                        roundedTo5
                                    )
                                }"
                            )
                            lastAnnouncedDistanceInt = roundedTo5

                        }
                    }
                }
            }
        }
        else if (device.deviceType == "BLE_BEACON") {
            // =======================================================
            // ŚCIEŻKA BLE: Logika rozmyta (Brak odliczania metrów!)
            // =======================================================
            // Zamiast czytać przeskakujące metry, odpalamy komunikaty
            // tylko po przekroczeniu "magicznych barier" bliskości.

            // Bariera 1: Cel osiągnięty (Bardzo mocny sygnał BLE)
            if (distance <= 1.5 ) {
                if (currentTime - lastArrivalAnnouncementTimeMs > TARGET_ZONE_COOLDOWN_MS) {
                    lastArrivalAnnouncementTimeMs = currentTime
                speechService.announceImportant("Cel odnaleziony. Jesteś przy: ${dest.name}")

                currentTarget = null // Czyścimy cel mikro po znalezieniu
                return
                }
            }

            // Bariera 2: Strefa pobliża (Zaczynamy łapać sygnał BLE)
            // Wymaga dodania małej flagi 'nearAnnounced' (lub użycia reachAnnounced10 w nowym celu)
            if (distance > 1.5 && distance <= 4.0 ) {
                if (currentTime - lastProximityAnnouncementTimeMs > TARGET_ZONE_COOLDOWN_MS) {
                    lastProximityAnnouncementTimeMs = currentTime
                    speechService.announceImportant("Zbliżasz się do: ${dest.name}")
                }
            }
        }
    }

    private fun getMeterSpelling(count: Int): String {
        if (count == 1) return "metr"
        val lastDigit = count % 10
        val lastTwoDigits = count % 100

        return if (lastDigit in 2..4 && lastTwoDigits !in 12..14) {
            "metry"
        } else {
            "metrów"
        }
    }
}