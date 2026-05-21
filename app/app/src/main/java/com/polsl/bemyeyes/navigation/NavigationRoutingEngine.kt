package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget
import kotlin.math.roundToInt

/**
 * Silnik analizujący stany przejść grafu budynku oraz dystrybuujący komendy głosowe.
 */
class NavigationRoutingEngine(
    private val buildingTopologyDB: BuildingTopologyDatabase,
    private val speechService: AccessibilitySpeechService // Upewnij się, że ta klasa istnieje
) {

    // 1. STANY SILNIKA NAWIGACJI
    var currentLocationId: Int? = null // Gdzie aktualnie jestem? (do Cold Startu)
    private var currentTarget: NavigationTarget? = null // Dokąd idę?

    private var lastPassAnnouncementTimeMs: Long = 0
    private val PASSING_THRESHOLD_METERS = 2.0
    private val ANNOUNCEMENT_COOLDOWN_MS = 8000

    private var reachAnnounced10 = false
    private var reachAnnounced15 = false
    private var lastAnnouncedDistanceInt = -1

    // =========================================================================
    // AKCJE UŻYTKOWNIKA (Wywoływane z MainActivity po kliknięciu przycisku)
    // =========================================================================

    fun setNavigationTarget(target: NavigationTarget) {
        // RESETUJEMY FLAGI PRZY WYBORZE NOWEGO CELU!
        //brak COLD start- > target null usatwiamy makro w
        reachAnnounced10 = false
        reachAnnounced15 = false
        lastAnnouncedDistanceInt = -1

        if (target.isMacroTarget) {
            speechService.announceImportant("Rozpoczynam nawigację do strefy: ${target.name}.")
        } else {
            speechService.announceImportant("Szukam precyzyjnie: ${target.name}.")
        }
    }






    // =========================================================================
    // AKCJE Z BLUETOOTHA (Wywoływane przez BleConnectionManager setki razy)
    // =========================================================================
    fun processScannedDevice(macAddress: String, rssi: Double): List<IoTDevice>? {

        // 1. Szukamy, czy to urządzenie z Cache'u? ( w senie czy nie zlapalismy silnego sygnalu (przy cold start) z np kogos sluchawek BT
        val knownDevice = buildingTopologyDB.cachedDevices.find { it.macAddress == macAddress } ?: return null

        // UWAGA: Sprawdzamy, czy urządzenie, które usłyszeliśmy, wymusza zmianę lokalizacji
        // Dzieje się tak podczas ZIMNEGO STARTU (currentLocationId == null)
        // LUB gdy złapaliśmy Trigger w windzie
        if (currentLocationId != knownDevice.locationId) {

            // Ignorujemy "zwykłe" urządzenia z innych pięter, reagujemy tylko na Triggery
            // (Chyba, że to ZIMNY START - wtedy bierzemy wszystko jak leci, żeby w ogóle zacząć)
            val isColdStart = (currentLocationId == null)
            val isTrigger = buildingTopologyDB.getBoundaryTriggers().any { it.macAddress == macAddress }


            if (isColdStart || isTrigger && rssi > -60.00) {
                currentLocationId = knownDevice.locationId
                println("🔥 ZMIANA LOKALIZACJI NA: $currentLocationId")

                // Zwracamy listę nowych urządzeń dla tej lokalizacji do wysłania!
                return buildingTopologyDB.getDevicesForLocation(currentLocationId!!)
            }
        }


        return null
    }

    // =========================================================================
    // AKCJE Z BLUETOOTHA (Logika mijania i dotarcia)
    // =========================================================================
    fun processNewTelemetryData(macAddress: String, distanceOrRssi: Double) {

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
                    announceDistanceProgress(distanceOrRssi)
                }
            }
        }

        // 3. LOGIKA MIJANIA INNYCH OBIEKTÓW PO DRODZE (Eksploracja tła)
        if (distanceOrRssi <= PASSING_THRESHOLD_METERS) {
            val currentTime = System.currentTimeMillis()

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
    private fun announceDistanceProgress(distance: Double) {
        val distanceInt = distance.roundToInt()
        val dest = currentTarget ?: return // Używamy nowej zmiennej currentTarget!

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

        // Scenariusz: Zbliżanie się
        if (distance > 1.5) {
            if (distanceInt <= 10) {
                if (distanceInt != lastAnnouncedDistanceInt) {
                    speechService.announceBackground("$distanceInt ${getMeterSpelling(distanceInt)}")
                    lastAnnouncedDistanceInt = distanceInt
                }
            } else {
                val roundedTo5 = (distanceInt / 5) * 5
                if (roundedTo5 != lastAnnouncedDistanceInt && distanceInt % 5 == 0) {
                    speechService.announceBackground("$distanceInt ${getMeterSpelling(distanceInt)}")
                    lastAnnouncedDistanceInt = roundedTo5
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