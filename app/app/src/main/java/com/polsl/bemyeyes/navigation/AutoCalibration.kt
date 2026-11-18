package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.PositioningStrategy

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
// 3. Implementacja dla 3 lub więcej kotwic (Wykorzystuje Least Squares / Gaussa-Newtona)
class MultiAnchorLeastSquaresCalibrationStrategy : CalibrationStrategy {
    override fun calibrate(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice> {
        val n = anchors.size
        if (n < 3) throw IllegalArgumentException("Wymagane co najmniej 3 kotwice!")

        val updatedAnchors = mutableListOf<IoTDevice>()

        // 1. Kotwica 0 w punkcie (0, 0)
        updatedAnchors.add(anchors[0].copy(globalX = 0.0, globalY = 0.0))

        // 2. Kotwica 1 na osi X w odległości d01
        val d01 = distanceMatrix[0][1]
        if (d01 < 0.1) throw IllegalStateException("Zbyt mała odległość między kotwicą 0 i 1 ($d01 m)")
        updatedAnchors.add(anchors[1].copy(globalX = d01, globalY = 0.0))

        // 3. Kotwica 2 wyznaczana trygonometrycznie z trójkąta (0, 1, 2)
        val d02 = distanceMatrix[0][2]
        val d12 = distanceMatrix[1][2]
        val x2 = (d01 * d01 + d02 * d02 - d12 * d12) / (2 * d01)
        val y2 = kotlin.math.sqrt(kotlin.math.max(0.0, d02 * d02 - x2 * x2))
        updatedAnchors.add(anchors[2].copy(globalX = x2, globalY = y2))

        // 4. Dla każdej kolejnej kotwicy (i >= 3) używamy Least Squares względem wszystkich poprzednich
        val lsStrategy = LeastSquaresStrategy(iterations = 10)

        for (i in 3 until n) {
            val rangedPoints = mutableListOf<RangedPoint>()
            for (j in 0 until i) {
                val prevAnchor = updatedAnchors[j]
                val measuredDist = distanceMatrix[i][j]
                if (prevAnchor.globalX != null && prevAnchor.globalY != null && measuredDist > 0.0) {
                    rangedPoints.add(RangedPoint(prevAnchor.globalX, prevAnchor.globalY, measuredDist))
                }
            }

            // Odpalamy algorytm LS z PositionAlgorithms.kt dla nowej kotwicy
            val calculatedPos = lsStrategy.calculatePosition(rangedPoints)

            if (calculatedPos != null) {
                updatedAnchors.add(anchors[i].copy(globalX = calculatedPos.first, globalY = calculatedPos.second))
            } else {
                // Awaryjny fallback, gdyby macierz była osobliwa
                updatedAnchors.add(anchors[i].copy(globalX = 0.0, globalY = 0.0))
            }
        }

        return updatedAnchors
    }
}

// 4. GŁÓWNY SILNIK (Zarządca)
class AutoCalibrationEngine {
    // Pamięć podręczna na kotwice, które aktualnie każemy kalibrować ESP32
    private var currentCalibrationAnchors: List<IoTDevice> = emptyList()
    private var currentCalibrationTag: IoTDevice? = null
    var onTagCalibrated: ((String, Map<String, Double>) -> Unit)? = null // callback??
    var isCalibratingTag = false

    private val distanceBuffer = mutableMapOf<String, MutableList<Double>>()

    fun startTagCalibration(tag: IoTDevice, knownAnchors: List<IoTDevice>) {
        currentCalibrationTag = tag
        currentCalibrationAnchors = knownAnchors
        distanceBuffer.clear()
        isCalibratingTag = true
    }
    // Konsumuje surowe dane z BleConnectionManager.
    // Zwraca zaktualizowany obiekt IoTDevice (Tag), gdy zbierze dość danych. W przeciwnym razie zwraca null.
    fun processTagMeasurement(anchorMac: String, distance: Double): IoTDevice? {
        if (!isCalibratingTag || currentCalibrationTag == null)
            return null

        // 1. Dodajemy pomiar do bufora
        val formattedMac = formatAnchorId(anchorMac) // trzeba spr czy to anchor a nie tag
        distanceBuffer.getOrPut(formattedMac) { mutableListOf() }.add(distance)

        // 2. Filtrujemy tylko te kotwice, z których mamy już stabilną próbkę (np. 10 pomiarów)
        val readyAnchors = distanceBuffer.filter { it.value.size >= 10 }

        // 3. Jeśli mamy co najmniej 3 stabilne kotwice -> odpalamy matematykę
        if (readyAnchors.size >= 2) {

            val points = mutableListOf<RangedPoint>()

            // Mapujemy uśrednione dystanse na fizyczne współrzędne z bazy - czemu?? przeciez to powinny byc walsnie odleglsoci kotwic wgledem nas an nie ich punkty z kalibracji
            for ((mac, distances) in readyAnchors) {
                val anchor = currentCalibrationAnchors.find { it.macAddress == mac }
                if (anchor?.globalX != null && anchor.globalY != null && anchor.deviceType=="UWB_ANCHOR") {
                    points.add(RangedPoint(anchor.globalX, anchor.globalY, distances.average()))
                }
            }

            if (points.size >= 2) {
                isCalibratingTag = false // Mamy sukces, wyłączamy nasłuch

                //  DYNAMICZNY WYBÓR ALGORYTMU
                val strategy: PositioningStrategy = if (points.size == 2) {
                    TwoAnchorCorridorPositioningStrategy() // Uruchomi się podczas Twoich testów 1D
                } else  {
                    LeastSquaresStrategy() //n>=3
                }

                val position = strategy.calculatePosition(points)
                distanceBuffer.clear()

                if (position != null) {
                    // Zwracamy kopię taga z wpisanymi współrzędnymi X i Y
                    return currentCalibrationTag!!.copy(globalX = position.first, globalY = position.second)
                }
            }
        }

        return null // Wciąż zbieramy dane...
    }
    // ANCHORS
    fun prepareCalibration(anchors: List<IoTDevice>) {
        currentCalibrationAnchors = anchors

    }

    fun performAnchorCalibration(distanceMatrix: Array<DoubleArray>, anchors: List<IoTDevice>): List<IoTDevice> {
        val anchorCount = anchors.size

        // System SAM decyduje, jakiej matematyki użyć
        val strategy: CalibrationStrategy = when {
            anchorCount == 2 -> TwoAnchorCorridorStrategy()
            anchorCount == 3 -> ThreeAnchorTrigonometryStrategy()
            anchorCount > 3 -> MultiAnchorLeastSquaresCalibrationStrategy()
            else -> throw IllegalArgumentException("Nieobsługiwana liczba kotwic: $anchorCount")
        }

        return strategy.calibrate(distanceMatrix, anchors)
    }


    // Funkcja pomocnicza zamieniająca "1", "0x1" lub "0x0001" na jednolity format "0x0001"
    private fun formatAnchorId(rawId: String): String {


        // 2. Próbujemy zamienić tekst na liczbę (w systemie szesnastkowym - 16)
        val numericId = rawId.toIntOrNull(10)

        // 3. Twój bezpieczny blok if-else!
        return if (numericId != null) {
            String.format("0x%04x", numericId)
        } else {
            rawId.trim() // Jeśli ESP32 przysłało jakieś literki nie do rozszyfrowania, oddajemy oryginał
        }
    }

    // NOWA FUNKCJA: Tłumaczenie tekstu na macierz i wysyłka do API
    fun processCalibrationData(rawData: String): List<IoTDevice> {
        val size = currentCalibrationAnchors.size
        if (size < 2) throw IllegalStateException("Nie wystarczająca liczba kotwic do kalibracji")

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
        // Zwracamy wynik natychmiastowego przeliczenia algorytmu
        return performAnchorCalibration(distanceMatrix, currentCalibrationAnchors)
    }



}