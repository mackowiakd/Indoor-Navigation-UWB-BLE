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
    // Współrzędne historyczne użytkownika
    private var previousX: Double? = null
    private var previousY: Double? = null
    // ID urządzeń, które już minęliśmy w tej sesji (żeby nie powtarzać komunikatu co 3 sekundy)
    private val announcedPoisInStep = mutableSetOf<String>()

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

    fun clearCurrentTarget() {
        currentTarget = null
        lastArrivalAnnouncementTimeMs = 0L
        lastProximityAnnouncementTimeMs = 0L
        lastAnnouncedDistanceInt = -1
        resetMovementHistory()
    }
    // 🔥 WAŻNE: Funkcja resetująca pozycję wywoływana przy setNavigationTarget oraz clearCurrentTarget!
    fun resetMovementHistory() {
        previousX = null
        previousY = null
        announcedPoisInStep.clear()
    }
    fun setNavigationTarget(target: NavigationTarget) {
        // RESETUJEMY FLAGI PRZY WYBORZE NOWEGO CELU!
        //brak COLD start- > target null usatwiamy makro w
        lastArrivalAnnouncementTimeMs = 0
        lastProximityAnnouncementTimeMs = 0

        lastAnnouncedDistanceInt = -1
        currentTarget = target
        resetMovementHistory()
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
    /**
     * Główna funkcja wyliczenia pozycji 2D z 2 kotwic (wywoływana w strumieniu telemetrii UWB)
     */
    fun calculateUserPosition2D(anchor1Mac: String, d1: Double, anchor2Mac: String, d2: Double) {
        val a1 = buildingTopologyDB.getDeviceByMac(anchor1Mac) ?: return
        val a2 = buildingTopologyDB.getDeviceByMac(anchor2Mac) ?: return

        // Pobieramy współrzędne globalne kotwic z bazy danych
        val x1 = a1.globalX ?: return
        val y1 = a2.globalY ?: return
        val x2 = a2.globalX ?: return
        val y2 = a2.globalY ?: return

        // 1. Dystans między kotwicami obliczany z bazy danych
        val D = Math.hypot(x2 - x1, y2 - y1)
        if (D < 0.1) return // Ochrona przed dzieleniem przez zero

        // 2. Rzutowanie pozycji (względny dystans wzdłuż osi)
        val distFromA1 = (d1 * d1 - d2 * d2 + D * D) / (2 * D)

        // 3. Proporcja położenia
        val t = distFromA1 / D

        // 4. Interpolacja liniowa - Pozycja 2D użytkownika na osi korytarza
        val currentX = x1 + t * (x2 - x1)
        val currentY = y1 + t * (y2 - y1)

        // Odpalamy analizę otoczenia
        evaluatePassingObjects2D(currentX, currentY)
    }

    /**
     * Analiza iloczynu wektorowego pod kątem lewej/prawej strony
     */
    private fun evaluatePassingObjects2D(currentX: Double, currentY: Double) {
        // Jeśli nie ma historii, zapisujemy obecny punkt i czekamy na kolejną paczkę danych
        if (previousX == null || previousY == null) {
            previousX = currentX
            previousY = currentY
            return
        }

        // Wektor Twojego ruchu: skąd-dokąd (v)
        val moveDx = currentX - previousX!!
        val moveDy = currentY - previousY!!

        // Filtrowanie szumu: jeśli użytkownik stoi w miejscu, nie wyliczamy kierunku
        if (Math.hypot(moveDx, moveDy) < 0.4) return

        // Pobieramy z bazy wszystkie cele mikro (POI) dla obecnej strefy
        val activePOIs = buildingTopologyDB.getMicroTargets(currentLocationId)

        for (target in activePOIs) {

            // 🔥 KLUCZOWY KROK: Wyciągamy fizyczne urządzenie z bazy po adresie MAC,
            // aby dobrać się do jego współrzędnych globalnych X i Y
            val device = buildingTopologyDB.getDeviceByMac(target.associatedMac ?: "") ?: continue

            // Teraz bezpiecznie wyciągamy współrzędne 2D z obiektu device!
            val poiX = device.globalX ?: continue
            val poiY = device.globalY ?: continue

            // Obliczamy odległość do obiektu
            val distanceToPoi = Math.hypot(poiX - currentX, poiY - currentY)

            // Jeśli minęliśmy obiekt (jest w promieniu strefy "Mijasz", np. 1.8 metra)
            if (distanceToPoi <= 1.8) {

                // Sprawdzamy czy już o nim nie mówiliśmy przed chwilą
                if (announcedPoisInStep.contains(target.associatedMac)) continue

                // Wektor od użytkownika do obiektu (p)
                val poiDx = poiX - currentX
                val poiDy = poiY - currentY

                //  ILOCZYN WEKTOROWY 2D (Wyznacznik macierzy)
                val crossProduct = (moveDx * poiDy) - (moveDy * poiDx)

                // Strefa martwa: jeśli crossProduct jest bliski zero, obiekt leży dokładnie przed/za nami
                if (Math.abs(crossProduct) < 0.05) continue

                val side = if (crossProduct > 0) "lewej" else "prawej"

                speechService.announceBackground("Mijasz ${target.name} po Twojej $side stronie.")
                announcedPoisInStep.add(target.associatedMac ?: "")
            } else {
                // Gdy użytkownik oddali się od obiektu, usuwamy go z listy ogłoszonych,
                // żeby system mógł go ogłosić, gdy użytkownik będzie wracał korytarzem
                if (distanceToPoi > 3.0) {
                    announcedPoisInStep.remove(target.associatedMac)
                }
            }
        }

        // Aktualizacja historii
        previousX = currentX
        previousY = currentY
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

                    if (dest.isMacroTarget) {
                        // Jeśli szliśmy do pokoju:
                        speechService.announceImportant("Jesteś w strefie: ${dest.name}. Wybierz teraz dokładny cel z listy.")
                    } else {
                        // Jeśli w przyszłości podepniesz pod UWB cel mikro (np. konkretne biurko z kotwicą):
                        speechService.announceImportant("Dotarłeś do celu. Jesteś przed ${dest.name}")
                    }

                    //currentTarget = null //  logika powtarzania wtdty mija sie z zalozeniem
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
                            // Czas aktualizujemy TYLKO wtedy, gdy apka naprawdę coś powiedziała!
                            lastProximityAnnouncementTimeMs = currentTime

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