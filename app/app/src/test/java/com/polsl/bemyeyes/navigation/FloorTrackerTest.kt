package com.polsl.bemyeyes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTrackerTest {

    private val levels = listOf(FloorLevel("Piętro 7", 0.0), FloorLevel("Podest", 1.9), FloorLevel("Piętro 8", 3.8))

    private fun tracker() = FloorTracker(levels) // tolerancja 0.25, histereza 0.15, 3 próbki

    @Test
    fun staysUnknownUntilConfirmed() {
        val t = tracker()
        t.update(0.0)
        t.update(0.0)
        assertEquals(FloorState.Unknown, t.state)
        t.update(0.0)
        assertEquals(FloorState.AtLevel(0, "Piętro 7"), t.state)
    }

    @Test
    fun hysteresis_keepsLevelUntilWellOutside() {
        val t = tracker()
        repeat(5) { t.update(0.0) }
        repeat(5) { t.update(0.35) } // > tolerancji (0.25), ale w histerezie (<= 0.40)
        assertEquals(FloorState.AtLevel(0, "Piętro 7"), t.state)
        repeat(5) { t.update(0.9) }
        assertTrue(t.state is FloorState.OnStairs)
    }

    @Test
    fun singleSpike_doesNotChangeState() {
        val t = tracker()
        val changes = mutableListOf<Pair<Int?, Int>>()
        t.onLevelChanged = { from, to -> changes += from to to }
        repeat(5) { t.update(0.0) }
        t.update(1.9) // pojedynczy błędny pomiar
        repeat(5) { t.update(0.0) }
        assertEquals(FloorState.AtLevel(0, "Piętro 7"), t.state)
        assertEquals(listOf<Pair<Int?, Int>>(null to 0), changes)
    }

    @Test
    fun climbing_reportsStairsThenNewLevel() {
        val t = tracker()
        val changes = mutableListOf<Pair<Int?, Int>>()
        t.onLevelChanged = { from, to -> changes += from to to }
        repeat(5) { t.update(0.0) }

        val states = mutableListOf<FloorState>()
        for (i in 0..19) states += t.update(i * 0.1) // 0.0 .. 1.9

        assertTrue(states.any { it is FloorState.OnStairs && it.lowerIndex == 0 && it.upperIndex == 1 && it.direction == StairDirection.UP })
        assertEquals(FloorState.AtLevel(1, "Podest"), t.state)
        assertEquals(listOf<Pair<Int?, Int>>(null to 0, 0 to 1), changes)
    }

    @Test
    fun descending_reportsDirectionDown() {
        val t = tracker()
        repeat(5) { t.update(3.8) }
        val states = mutableListOf<FloorState>()
        for (i in 0..12) states += t.update(3.8 - i * 0.1)
        assertTrue(states.any { it is FloorState.OnStairs && it.lowerIndex == 1 && it.direction == StairDirection.DOWN })
    }

    @Test
    fun outsideRange_isUnknown() {
        val t = tracker()
        repeat(5) { t.update(0.0) }
        repeat(5) { t.update(-3.0) }
        assertEquals(FloorState.Unknown, t.state)
    }
}
