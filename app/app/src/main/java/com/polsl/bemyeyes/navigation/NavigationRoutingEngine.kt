package com.polsl.bemyeyes.navigation

import kotlin.math.roundToInt

/**
 * Silnik analizujący stany przejść grafu budynku oraz dystrybuujący komendy głosowe.
 */
class NavigationRoutingEngine(
    private val topologyDatabase: BuildingTopologyDatabase,
    private val speechService: AccessibilitySpeechService // Upewnij się, że ta klasa istnieje
) {

    // Znaki zapytania oznaczają, że na początku (przed wybraniem celu) te wartości są nullami
    private var currentEstimatedNode: AnchorNode? = null
    private var userDestinationNode: AnchorNode? = null

    private var lastAnnouncedDistanceInt: Int = -1
    private var reachAnnounced15: Boolean = false
    private var reachAnnounced10: Boolean = false
    // NOWE ZMIENNE: Zapobiegają zalaniu kolejki TTS (Spam protection)
    private var lastPassAnnouncementTimeMs: Long = 0
    private val ANNOUNCEMENT_COOLDOWN_MS = 8000L // 8 sekund całkowitej ciszy dla "mijania"

    companion object {
        private const val PASSING_THRESHOLD_METERS = 1.5
    }

    fun setNavigationTarget(targetAnchorId: String) {
        currentEstimatedNode = null
        userDestinationNode = topologyDatabase.getNodeById(targetAnchorId)
        lastAnnouncedDistanceInt = -1
        reachAnnounced15 = false
        reachAnnounced10 = false

        // Magia Kotlina: Jeśli userDestinationNode NIE jest nullem, wykonaj to w klamrach
        userDestinationNode?.let { dest ->
            speechService.announceImportant("Rozpoczynam nawigację do ${dest.humanReadableName}")
        }
    }

    fun processNewTelemetryData(anchorId: String, distanceInMeters: Double) {
        val detectedNode = topologyDatabase.getNodeById(anchorId) ?: return // Zabezpieczenie przed nieznanymi tagami

        // Obsługa dystansu do CELU (niezależnie od innych kotwic)
        if (userDestinationNode != null && anchorId == userDestinationNode!!.macAddress) {
            announceDistanceProgress(distanceInMeters)
        }



        // Logika "mijania" innych punktów (tylko jeśli są blisko)
        if (distanceInMeters <= PASSING_THRESHOLD_METERS) {

            // Ignoruj cel główny w logice mijania (żeby nie było nakładania komunikatów)
            if (userDestinationNode != null && detectedNode.macAddress == userDestinationNode!!.macAddress) {
                return
            }

            val currentTime = System.currentTimeMillis()

            // Uruchom mijanie tylko, jeśli od poprzedniego minęło 8 sekund (Cooldown)
            if (currentTime - lastPassAnnouncementTimeMs > ANNOUNCEMENT_COOLDOWN_MS) {
                if (currentEstimatedNode == null || currentEstimatedNode!!.macAddress != detectedNode.macAddress) {
                    currentEstimatedNode = detectedNode
                    lastPassAnnouncementTimeMs = currentTime // Resetujemy zegar
                    evaluatePathAndAnnounce()
                }
            }
        }
    }

    private fun announceDistanceProgress(distance: Double) {
        val distanceInt = distance.roundToInt()
        val dest = userDestinationNode ?: return

        // Scenariusz: Finisz (1.0m)
        if (distance <= 1.0 && !reachAnnounced10) {
            speechService.announceImportant("Jesteś przed ${dest.humanReadableName}")
            reachAnnounced10 = true
            return
        }

        // Scenariusz: Finisz (1.5m) - to jest moment "dotarcia"
        if (distance <= 1.5 && !reachAnnounced15) {
            speechService.announceImportant("Dotarłeś do celu. Jesteś przed ${dest.humanReadableName}")
            reachAnnounced15 = true
            return
        }

        // Scenariusz: Zbliżanie się (powyżej 1.5m)
        if (distance > 1.5) {
            if (distanceInt <= 10) {
                // Poniżej 10m: czytamy co 1 metr
                if (distanceInt != lastAnnouncedDistanceInt) {
                    speechService.announceBackground("$distanceInt ${getMeterSpelling(distanceInt)}")
                    lastAnnouncedDistanceInt = distanceInt
                }
            } else {
                // Powyżej 10m: czytamy co 5 metrów
                val roundedTo5 = (distanceInt / 5) * 5
                if (roundedTo5 != lastAnnouncedDistanceInt && distanceInt % 5 == 0) {
                    speechService.announceBackground("$distanceInt ${getMeterSpelling(distanceInt)}")
                    lastAnnouncedDistanceInt = roundedTo5
                }
            }
        }
    }

    /**
     * Zwraca poprawną formę słowa "metr" w języku polskim.
     */
    private fun getMeterSpelling(count: Int): String {
        if (count == 1) return "metr"
        val lastDigit = count % 10
        val lastTwoDigits = count % 100

        // Pięknie uproszczony warunek dzięki operatorowi 'in'
        return if (lastDigit in 2..4 && lastTwoDigits !in 12..14) {
            "metry"
        } else {
            "metrów"
        }
    }

    private fun evaluatePathAndAnnounce() {
        // Pobieramy obecny punkt z tła (jeśli to null, przerywamy)
        val current = currentEstimatedNode ?: return

        // Pamiętaj: Cel główny tu NIE wejdzie, więc nie musimy go tu czyścić.
        // Trafiają tu tylko tagi "mijane", więc po prostu je czytamy.

        // String Interpolation: zamiast + zmienna + "tekst", wrzucamy zmienną prosto do cudzysłowu z dolarem!
        speechService.announceBackground("Mijasz ${current.humanReadableName}")
    }
}