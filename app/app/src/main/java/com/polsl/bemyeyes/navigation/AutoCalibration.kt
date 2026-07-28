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
}