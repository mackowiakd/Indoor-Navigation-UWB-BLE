package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget
import kotlin.math.roundToInt

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

    private var reachAnnounced10 = false
    private var reachAnnounced15 = false
    private var lastAnnouncedDistanceInt = -1

    //--zmienne stanu (przsylanie listy urzadzen do espa) --
    var currentLocationId: Int? = null // Gdzie aktualnie jestem? (do Cold Startu)
    private var lastTransitionTime: Long = 0  // Kiedy ostatnio zmieniliśmy strefę?

    // Ustawienia systemu
    private val COOLDOWN_MS = 4000L // 4 sekundy blokady po zmianie strefy (żeby wyjść z progu)
    private val TRIGGER_THRESHOLD = 2 // Zmiana strefy następuje dopiero, gdy podejdziemy bliżej niż na 2 metry
    // =========================================================================
    // AKCJE UŻYTKOWNIKA (Wywoływane z MainActivity po kliknięciu przycisku)
    // =========================================================================

    fun setNavigationTarget(target: NavigationTarget) {
        // RESETUJEMY FLAGI PRZY WYBORZE NOWEGO CELU!
        //brak COLD start- > target null usatwiamy makro w
        reachAnnounced10 = false
        reachAnnounced15 = false
        lastAnnouncedDistanceInt = -1
        currentTarget = target

        if (target.isMacroTarget) {
            speechService.announceImportant("Rozpoczynam nawigację do strefy: ${target.name}.")
        } else {
            speechService.announceImportant("Szukam precyzyjnie: ${target.name}.")
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
                println("🔥 ZMIANA LOKALIZACJI NA: $currentLocationId")
                //zapis czasu na kolejne pomiary
                lastTransitionTime = currentTime
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
                // TRYB MAKRO: Osiągamy cel, jeśli zlapaliśmy sygnał z JAKIEGOKOLWIEK
                // urządzenia, które leży w docelowym Location_ID
                if (detectedDevice.locationId == currentTarget!!.locationId) {
                    speechService.announceImportant("Jesteś w strefie: ${currentTarget!!.name}. Wybierz teraz dokładny cel z listy.")
                    currentTarget = null // Osiągnięto cel, resetujemy!

                    // UWAGA: UI samo się tu zaktualizuje, bo zmieni się currentLocationId
                }
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
    // FUNKCJE POMOCNICZE AUDIO (Twoje autorskie, zaktualizowane modele)
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

        if (device.deviceType == "UWB_ANCHOR") {
            // =======================================================
            // ŚCIEŻKA UWB: Dokładne odliczanie ("Autostrada")
            // =====
            // Scenariusz: Finisz (1.0m)
            if (distance <= 1.0 && !reachAnnounced10) {
                speechService.announceImportant("Jesteś przed ${dest.name}")
                reachAnnounced10 = true
                return
            }

            // Scenariusz: Finisz (1.5m)
            if (distance <= 1.5 && !reachAnnounced15) {
                speechService.announceImportant("Dotarłeś do celu. Jesteś przed ${dest.name}")
                reachAnnounced15 = true
                return
            }

            // Scenariusz: Zbliżanie się// czyli to zostawiamy bez podzialu na device type?
            if (distance > 1.5) {
                if (distanceInt <= 10) {
                    if (distanceInt != lastAnnouncedDistanceInt) {
                        speechService.announceBackground(
                            "$distanceInt ${
                                getMeterSpelling(
                                    distanceInt
                                )
                            }"
                        )
                        lastAnnouncedDistanceInt = distanceInt
                    }
                } else {
                    val roundedTo5 = (distanceInt / 5) * 5
                    if (roundedTo5 != lastAnnouncedDistanceInt && distanceInt % 5 == 0) {
                        speechService.announceBackground(
                            "$distanceInt ${
                                getMeterSpelling(
                                    distanceInt
                                )
                            }"
                        )
                        lastAnnouncedDistanceInt = roundedTo5
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
            if (distance <= 1.5 && !reachAnnounced15) {
                speechService.announceImportant("Cel odnaleziony. Jesteś przy: ${dest.name}")
                reachAnnounced15 = true
                reachAnnounced10 = true // Blokujemy dalsze komunikaty
                return
            }

            // Bariera 2: Strefa pobliża (Zaczynamy łapać sygnał BLE)
            // Wymaga dodania małej flagi 'nearAnnounced' (lub użycia reachAnnounced10 w nowym celu)
            if (distance > 1.5 && distance <= 4.0 && !reachAnnounced10) {
                speechService.announceImportant("Zbliżasz się do: ${dest.name}")
                reachAnnounced10 = true // Flaga chroni przed powtarzaniem w kółko
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