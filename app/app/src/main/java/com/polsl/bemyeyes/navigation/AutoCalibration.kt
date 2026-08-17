package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice

// 1. Uniwersalny interfejs dla każdego algorytmu (Zmieniono UwbAnchor na IoTDevice)
interface CalibrationStrategy {
    // Przyjmuje macierz odległości i listę kotwic (jako IoTDevice). Zwraca kotwice z nadpisanymi (X, Y)
    fun calibrate(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice>
}

// 2. Implementacja dla 2 kotwic (Korytarz 1.5D)
class TwoAnchorCorridorStrategy : CalibrationStrategy {
    override fun calibrate(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice> {
        if (anchors.size != 2) throw IllegalArgumentException("Wymagane dokładnie 2 kotwice!")

        val a1 = anchors[0]
        val a2 = anchors[1]

        // W macierzy odległości dystans między K1 a K2 jest pod indeksem [0][1]
        val distanceBetweenAnchors = distanceMatrix[0][1]

        // MAGIA KOTLINA: Klonujemy obiekty i nadpisujemy tylko X i Y
        val updatedA1 = a1.copy(globalX = 0.0, globalY = 0.0)
        val updatedA2 = a2.copy(globalX = 0.0, globalY = distanceBetweenAnchors)

        return listOf(updatedA1, updatedA2)
    }
}

// 3. Implementacja dla 3 kotwic (Trygonometria 2D - zostawiam szkielet na przyszłość)
class ThreeAnchorTrigonometryStrategy : CalibrationStrategy {
    override fun calibrate(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice> {
        if (anchors.size != 3) throw IllegalArgumentException("Wymagane 3 kotwice!")

        val d12 = distanceMatrix[0][1]
        val d13 = distanceMatrix[0][2]
        val d23 = distanceMatrix[1][2]

        val updatedA1 = anchors[0].copy(globalX = 0.0, globalY = 0.0)
        val updatedA2 = anchors[1].copy(globalX = d12, globalY = 0.0)

        val x3 = (d12 * d12 + d13 * d13 - d23 * d23) / (2 * d12)
        val y3 = Math.sqrt(Math.max(0.0, d13 * d13 - x3 * x3))

        val updatedA3 = anchors[2].copy(globalX = x3, globalY = y3)

        return listOf(updatedA1, updatedA2, updatedA3)
    }
}

// 4. GŁÓWNY SILNIK (Zarządca)
class AutoCalibrationEngine {
    // Pamięć podręczna na kotwice, które aktualnie każemy kalibrować ESP32
    private var currentCalibrationAnchors: List<IoTDevice> = emptyList()
    fun performCalibration(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice> {
        val anchorCount = anchors.size

        // System SAM decyduje, jakiej matematyki użyć
        val strategy: CalibrationStrategy = when (anchorCount) {
            2 -> TwoAnchorCorridorStrategy()
            3 -> ThreeAnchorTrigonometryStrategy()
            in 4..10 -> throw NotImplementedError("Pełny algorytm MDS pozostawiony na przyszłość")
            else -> throw IllegalArgumentException("Nieobsługiwana liczba kotwic: $anchorCount")
        }

        return strategy.calibrate(distanceMatrix, anchors)
    }

    // Funkcja pomocnicza zamieniająca "1", "0x1" lub "0x0001" na jednolity format "0x0001"
    private fun formatAnchorId(rawId: String): String {
        // 1. Usuwamy "0x" (jeśli ESP32 je przysłało) żeby móc bezpiecznie zrzutować na liczbę
        val cleanId = rawId.removePrefix("0x").trim()

        // 2. Próbujemy zamienić tekst na liczbę (w systemie szesnastkowym - 16)
        val numericId = cleanId.toIntOrNull(16)

        // 3. Twój bezpieczny blok if-else!
        return if (numericId != null) {
            String.format("0x%04x", numericId)
        } else {
            rawId.trim() // Jeśli ESP32 przysłało jakieś literki nie do rozszyfrowania, oddajemy oryginał
        }
    }

    // NOWA FUNKCJA: Tłumaczenie tekstu na macierz i wysyłka do API
    fun processCalibrationData(rawData: String) {
        val size = currentCalibrationAnchors.size
        if (size < 2) return // Zabezpieczenie

        // 1. Tworzymy pustą macierz z samymi zerami (np. 2x2)
        val distanceMatrix = Array(size) { DoubleArray(size) { 0.0 } }

        // 2. Tniemy odpowiedź (np. "1_2=11.45;") na kawałki
        val records = rawData.split(";")

        for (record in records) {
            if (record.isBlank()) continue

            val parts = record.split("=") // np. parts[0] = "1_2", parts[1] = "11.45"
            if (parts.size == 2) {
                val ids = parts[0].split("_")
                val dist = parts[1].toDoubleOrNull() ?: continue

                if (ids.size == 2) {
                    val id1 = formatAnchorId(ids[0])
                    val id2 = formatAnchorId(ids[1])
                    // Szukamy, pod którym indeksem w naszej macierzy są te konkretne kotwice
                    val index1 = currentCalibrationAnchors.indexOfFirst { it.macAddress == id1 }
                    val index2 = currentCalibrationAnchors.indexOfFirst { it.macAddress == id2 }

                    if (index1 != -1 && index2 != -1) {
                        distanceMatrix[index1][index2] = dist
                        distanceMatrix[index2][index1] = dist // Odbicie lustrzane dla macierzy
                    }
                }
            }
        }
    }
}