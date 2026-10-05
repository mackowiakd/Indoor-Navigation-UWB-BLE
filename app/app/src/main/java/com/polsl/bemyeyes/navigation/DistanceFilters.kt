package com.polsl.bemyeyes.navigation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// =========================================================
// FILTRY DYSTANSU UWB (jedna instancja na kotwicę)
// Wspólny interfejs pozwala porównać filtry na tych samych danych (rozdział o filtracji w pracy).
// Klasy nie są wątkowo-bezpieczne - wołaj je z jednego wątku (np. callback BLE).
// =========================================================

interface DistanceFilter {
    /** @param dtSeconds czas od poprzedniego pomiaru (używa go tylko filtr Kalmana) */
    fun update(measurement: Double, dtSeconds: Double = 0.1): Double
    fun reset()
}

/** Średnia krocząca z okna o długości [window] próbek. */
class MovingAverageFilter(private val window: Int = 5) : DistanceFilter {
    private val samples = ArrayDeque<Double>()
    private var sum = 0.0

    init {
        require(window >= 1) { "window musi być >= 1" }
    }

    override fun update(measurement: Double, dtSeconds: Double): Double {
        samples.addLast(measurement)
        sum += measurement
        if (samples.size > window) sum -= samples.removeFirst()
        return sum / samples.size
    }

    override fun reset() {
        samples.clear()
        sum = 0.0
    }
}

/** Mediana z okna o długości [window] próbek - odporna na pojedyncze skoki (NLOS, multipath). */
class MedianFilter(private val window: Int = 5) : DistanceFilter {
    private val samples = ArrayDeque<Double>()

    init {
        require(window >= 1) { "window musi być >= 1" }
    }

    override fun update(measurement: Double, dtSeconds: Double): Double {
        samples.addLast(measurement)
        if (samples.size > window) samples.removeFirst()
        val sorted = samples.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    override fun reset() = samples.clear()
}

/**
 * Filtr Kalmana 1D ze stałą prędkością: stan [dystans, prędkość zmiany dystansu].
 *
 * @param accelerationVariance q - wariancja przyspieszenia [m²/s⁴] (im większa, tym filtr szybciej "wierzy" pomiarom)
 * @param measurementVariance r - wariancja szumu pomiaru [m²], np. 0.1² dla UWB
 * @param gateSigma bramka odrzucająca skoki: |innowacja| > gateSigma * sqrt(S) pomija pomiar (0 = wyłączona)
 * @param maxConsecutiveOutliers po tylu odrzuconych pomiarach z rzędu filtr resetuje się do nowego pomiaru
 */
class KalmanDistanceFilter(
    private val accelerationVariance: Double = 0.5,
    private val measurementVariance: Double = 0.01,
    private val gateSigma: Double = 0.0,
    private val maxConsecutiveOutliers: Int = 5
) : DistanceFilter {

    private var d = 0.0
    private var v = 0.0
    private var p00 = 0.0
    private var p01 = 0.0
    private var p10 = 0.0
    private var p11 = 0.0
    private var initialized = false
    private var outliers = 0

    override fun update(measurement: Double, dtSeconds: Double): Double {
        if (!initialized) return initialize(measurement)

        val dt = max(dtSeconds, 1e-3)

        // Predykcja: x = F x, P = F P Fᵀ + Q (model stałej prędkości, szum przyspieszenia)
        val dPred = d + v * dt
        val a00 = p00 + dt * (p10 + p01) + dt * dt * p11
        val a01 = p01 + dt * p11
        val a10 = p10 + dt * p11
        val a11 = p11
        val dt2 = dt * dt
        val q = accelerationVariance
        val pp00 = a00 + q * dt2 * dt2 / 4.0
        val pp01 = a01 + q * dt2 * dt / 2.0
        val pp10 = a10 + q * dt2 * dt / 2.0
        val pp11 = a11 + q * dt2

        val s = pp00 + measurementVariance
        val innovation = measurement - dPred

        if (gateSigma > 0.0 && abs(innovation) > gateSigma * sqrt(s)) {
            outliers++
            if (outliers >= maxConsecutiveOutliers) return initialize(measurement)
            // Pomiar odrzucony - przyjmujemy samą predykcję
            d = dPred
            p00 = pp00; p01 = pp01; p10 = pp10; p11 = pp11
            return d
        }
        outliers = 0

        val k0 = pp00 / s
        val k1 = pp10 / s
        d = dPred + k0 * innovation
        v += k1 * innovation
        p00 = (1 - k0) * pp00
        p01 = (1 - k0) * pp01
        p10 = pp10 - k1 * pp00
        p11 = pp11 - k1 * pp01
        return d
    }

    private fun initialize(measurement: Double): Double {
        d = measurement
        v = 0.0
        p00 = measurementVariance
        p01 = 0.0
        p10 = 0.0
        p11 = 1.0 // początkowa niepewność prędkości [m²/s²] - człowiek idący po schodach
        outliers = 0
        initialized = true
        return d
    }

    override fun reset() {
        initialized = false
        outliers = 0
    }
}

/** Zestaw filtrów: osobna instancja na każdą kotwicę (klucz = znormalizowane ID, np. "0x0001"). */
class DistanceFilterBank(private val factory: () -> DistanceFilter) {
    private val filters = mutableMapOf<String, DistanceFilter>()

    fun update(anchorId: String, measurement: Double, dtSeconds: Double = 0.1): Double =
        filters.getOrPut(anchorId, factory).update(measurement, dtSeconds)

    /** Zeruje filtr jednej kotwicy (np. po przerwie w sygnale). */
    fun reset(anchorId: String) {
        filters.remove(anchorId)
    }

    fun reset() = filters.clear()
}
