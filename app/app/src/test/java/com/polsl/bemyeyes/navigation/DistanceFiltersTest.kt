package com.polsl.bemyeyes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

class DistanceFiltersTest {

    @Test
    fun movingAverage_averagesOnlyLastWindow() {
        val f = MovingAverageFilter(3)
        assertEquals(1.0, f.update(1.0), 1e-9)
        assertEquals(1.5, f.update(2.0), 1e-9)
        assertEquals(2.0, f.update(3.0), 1e-9)
        assertEquals(3.0, f.update(4.0), 1e-9) // okno: 2,3,4 -> 3
    }

    @Test
    fun median_rejectsSingleSpike() {
        val f = MedianFilter(5)
        listOf(5.0, 5.1, 4.9, 5.0).forEach { f.update(it) }
        val out = f.update(12.0) // skok NLOS
        assertEquals(5.0, out, 1e-9)
    }

    @Test
    fun median_evenWindowAveragesMiddleValues() {
        val f = MedianFilter(4)
        f.update(1.0); f.update(2.0); f.update(3.0)
        assertEquals(2.5, f.update(4.0), 1e-9)
    }

    @Test
    fun reset_forgetsHistory() {
        val f = MovingAverageFilter(3)
        f.update(10.0); f.update(10.0)
        f.reset()
        assertEquals(2.0, f.update(2.0), 1e-9)
    }

    @Test
    fun kalman_reducesNoiseOnConstantDistance() {
        val rnd = Random(7)
        val f = KalmanDistanceFilter(accelerationVariance = 0.01, measurementVariance = 0.01)
        var rawSq = 0.0
        var filtSq = 0.0
        val n = 300
        for (i in 0 until n) {
            val m = 5.0 + rnd.nextGaussian() * 0.1
            val out = f.update(m, 0.1)
            if (i >= n - 100) {
                rawSq += (m - 5.0) * (m - 5.0)
                filtSq += (out - 5.0) * (out - 5.0)
            }
        }
        assertTrue("surowe ${sqrt(rawSq / 100)} vs Kalman ${sqrt(filtSq / 100)}", filtSq < rawSq)
    }

    @Test
    fun kalman_tracksLinearMotion() {
        val f = KalmanDistanceFilter(accelerationVariance = 0.5, measurementVariance = 0.01)
        var last = 0.0
        for (i in 0 until 100) last = f.update(2.0 + 0.5 * i * 0.1, 0.1) // 0.5 m/s, bez szumu
        assertEquals(2.0 + 0.5 * 99 * 0.1, last, 0.05)
    }

    @Test
    fun kalman_gateIgnoresSpikeButFollowsLevelShift() {
        val f = KalmanDistanceFilter(0.01, 0.01, gateSigma = 4.0, maxConsecutiveOutliers = 3)
        repeat(30) { f.update(5.0, 0.1) }
        val afterSpike = f.update(15.0, 0.1)
        assertTrue("po skoku ${afterSpike}", abs(afterSpike - 5.0) < 0.5) // pojedynczy skok odrzucony
        var out = 0.0
        repeat(5) { out = f.update(15.0, 0.1) }  // trwała zmiana - filtr musi się przestawić
        assertTrue("po zmianie poziomu $out", abs(out - 15.0) < 1.0)
    }

    @Test
    fun bank_keepsSeparateStatePerAnchor() {
        val bank = DistanceFilterBank { MovingAverageFilter(2) }
        bank.update("0x0001", 2.0)
        bank.update("0x0002", 10.0)
        assertEquals(3.0, bank.update("0x0001", 4.0), 1e-9)
        assertEquals(11.0, bank.update("0x0002", 12.0), 1e-9)
    }
}
