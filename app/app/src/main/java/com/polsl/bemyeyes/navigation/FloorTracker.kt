package com.polsl.bemyeyes.navigation

import kotlin.math.abs

// =========================================================
// DETEKCJA PIĘTRA / SCHODÓW na podstawie współrzędnej Z
// Poziomy (piętra, podesty) pochodzą z konfiguracji klatki - bez zmian w bazie danych.
// =========================================================

data class FloorLevel(val name: String, val z: Double)

enum class StairDirection { UP, DOWN, UNKNOWN }

sealed class FloorState {
    object Unknown : FloorState()

    /** Użytkownik stoi na poziomie [index] (posortowane rosnąco po Z). */
    data class AtLevel(val index: Int, val name: String) : FloorState()

    /** Użytkownik jest na biegu schodów między poziomami [lowerIndex] i [upperIndex]. */
    data class OnStairs(val lowerIndex: Int, val upperIndex: Int, val direction: StairDirection) : FloorState()
}

/**
 * Maszyna stanów z histerezą i potwierdzaniem: zmiana stanu następuje dopiero po [confirmSamples]
 * kolejnych próbkach wskazujących na ten sam nowy stan, żeby szum Z nie powodował "migania" pięter.
 *
 * Wejście: współrzędna Z z solwera 3D (najlepiej już po filtracji).
 */
class FloorTracker(
    levels: List<FloorLevel>,
    private val levelTolerance: Double = 0.25, // |z - poziom| <= tolerance => jesteśmy na poziomie [m]
    private val hysteresis: Double = 0.15,     // dodatkowy zapas przy opuszczaniu poziomu [m]
    private val confirmSamples: Int = 3,
    private val trendWindow: Int = 6,
    private val trendDeadband: Double = 0.05   // minimalna zmiana Z w oknie uznawana za ruch [m]
) {
    private val levels: List<FloorLevel> = levels.sortedBy { it.z }

    var state: FloorState = FloorState.Unknown
        private set

    /** Ostatni potwierdzony poziom (None = jeszcze żadnego). */
    var lastLevelIndex: Int? = null
        private set

    /** Wywoływane po potwierdzeniu wejścia na nowy poziom: (poprzedni indeks lub null, nowy indeks). */
    var onLevelChanged: ((Int?, Int) -> Unit)? = null

    private val history = ArrayDeque<Double>()
    private var trend = StairDirection.UNKNOWN
    private var candidateKey: Triple<Int, Int, Int>? = null
    private var candidateCount = 0

    init {
        require(levels.size >= 2) { "Wymagane co najmniej 2 poziomy" }
    }

    fun update(z: Double): FloorState {
        if (!z.isFinite()) return state

        history.addLast(z)
        if (history.size > trendWindow) history.removeFirst()
        trend = if (history.size >= 2) {
            val diff = history.last() - history.first()
            when {
                diff > trendDeadband -> StairDirection.UP
                diff < -trendDeadband -> StairDirection.DOWN
                else -> StairDirection.UNKNOWN
            }
        } else StairDirection.UNKNOWN

        val raw = classify(z)
        val rawKey = keyOf(raw)

        if (rawKey == keyOf(state)) {
            state = raw // ten sam stan - odświeżamy np. kierunek
            candidateKey = null
            candidateCount = 0
            return state
        }

        if (rawKey == candidateKey) candidateCount++ else {
            candidateKey = rawKey
            candidateCount = 1
        }
        if (candidateCount >= confirmSamples) {
            state = raw
            candidateKey = null
            candidateCount = 0
            if (raw is FloorState.AtLevel) {
                val previous = lastLevelIndex
                lastLevelIndex = raw.index
                if (previous != raw.index) onLevelChanged?.invoke(previous, raw.index)
            }
        }
        return state
    }

    fun reset() {
        state = FloorState.Unknown
        lastLevelIndex = null
        history.clear()
        trend = StairDirection.UNKNOWN
        candidateKey = null
        candidateCount = 0
    }

    private fun classify(z: Double): FloorState {
        val current = state
        if (current is FloorState.AtLevel &&
            abs(z - levels[current.index].z) <= levelTolerance + hysteresis
        ) return current

        val nearest = levels.indices
            .filter { abs(z - levels[it].z) <= levelTolerance }
            .minByOrNull { abs(z - levels[it].z) }
        if (nearest != null) return FloorState.AtLevel(nearest, levels[nearest].name)

        if (z < levels.first().z - levelTolerance || z > levels.last().z + levelTolerance) return FloorState.Unknown

        val upper = levels.indexOfFirst { it.z > z }
        if (upper <= 0) return FloorState.Unknown
        return FloorState.OnStairs(upper - 1, upper, trend)
    }

    // OnStairs porównujemy bez kierunku - zmiana kierunku nie jest zmianą stanu wymagającą potwierdzenia
    private fun keyOf(s: FloorState): Triple<Int, Int, Int> = when (s) {
        is FloorState.Unknown -> Triple(0, 0, 0)
        is FloorState.AtLevel -> Triple(1, s.index, 0)
        is FloorState.OnStairs -> Triple(2, s.lowerIndex, s.upperIndex)
    }
}
