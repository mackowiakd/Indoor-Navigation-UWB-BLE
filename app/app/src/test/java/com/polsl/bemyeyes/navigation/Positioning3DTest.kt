package com.polsl.bemyeyes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.hypot

class Positioning3DTest {

    // Klatka w kształcie "C" otwartego w stronę +X, wysokość 0..4 m
    private val cVolume = StairwellVolume(
        listOf(
            Point2D(0.0, 0.0), Point2D(6.0, 0.0), Point2D(6.0, 2.0), Point2D(2.0, 2.0),
            Point2D(2.0, 4.0), Point2D(6.0, 4.0), Point2D(6.0, 6.0), Point2D(0.0, 6.0)
        ),
        zMin = 0.0, zMax = 4.0
    )

    private val strategy = Trilateration3DStrategy()

    private fun ranged(anchors: List<Triple<Double, Double, Double>>, tx: Double, ty: Double, tz: Double,
                       noise: Double = 0.0, rnd: Random? = null): List<RangedPoint3D> =
        anchors.map { (ax, ay, az) ->
            val d = Math.sqrt((ax - tx) * (ax - tx) + (ay - ty) * (ay - ty) + (az - tz) * (az - tz))
            RangedPoint3D(ax, ay, az, d + (rnd?.nextGaussian() ?: 0.0) * noise, sigma = 0.03)
        }

    private fun error(p: Position3D, tx: Double, ty: Double, tz: Double) =
        hypot(hypot(p.x - tx, p.y - ty), p.z - tz)

    private val fourAnchors = listOf(
        Triple(0.3, 0.3, 0.3), Triple(5.7, 1.0, 3.5), Triple(0.5, 5.5, 3.0), Triple(5.5, 5.5, 0.5)
    )

    @Test
    fun fourAnchors_noiseless_recoversExactPosition() {
        val p = strategy.calculatePosition(ranged(fourAnchors, 1.0, 3.0, 2.0), cVolume)
        assertNotNull(p)
        assertTrue("błąd ${error(p!!, 1.0, 3.0, 2.0)}", error(p, 1.0, 3.0, 2.0) < 1e-3)
        assertTrue(p.insideVolume)
    }

    @Test
    fun fourAnchors_noise5cm_meanErrorBelow20cm() {
        val rnd = Random(42)
        var sum = 0.0
        val trials = 200
        repeat(trials) {
            val p = strategy.calculatePosition(ranged(fourAnchors, 1.0, 3.0, 2.0, 0.05, rnd), cVolume)
            assertNotNull(p)
            sum += error(p!!, 1.0, 3.0, 2.0)
        }
        assertTrue("średni błąd ${sum / trials}", sum / trials < 0.20)
    }

    @Test
    fun threeCoplanarAnchors_mirrorOutsideVolume_picksInsideSolution() {
        // Kotwice w płaszczyźnie z=3; punkt (1,1,1) ma lustro (1,1,5) powyżej zMax=4
        val anchors = listOf(Triple(0.5, 0.5, 3.0), Triple(5.5, 1.0, 3.0), Triple(1.0, 5.5, 3.0))
        val p = strategy.calculatePosition(ranged(anchors, 1.0, 1.0, 1.0), cVolume)
        assertNotNull(p)
        assertTrue("błąd ${error(p!!, 1.0, 1.0, 1.0)}", error(p, 1.0, 1.0, 1.0) < 1e-2)
        assertTrue(p.insideVolume)
        assertFalse(p.ambiguous)
    }

    @Test
    fun threeCoplanarAnchors_bothSolutionsInside_isAmbiguousUnlessHintGiven() {
        // Kotwice w płaszczyźnie z=2; punkt (1,1,1) ma lustro (1,1,3) - oba w bryle
        val anchors = listOf(Triple(0.5, 0.5, 2.0), Triple(5.5, 1.0, 2.0), Triple(1.0, 5.5, 2.0))
        val measurements = ranged(anchors, 1.0, 1.0, 1.0)

        val noHint = strategy.calculatePosition(measurements, cVolume)
        assertNotNull(noHint)
        assertTrue(noHint!!.ambiguous)

        val hint = Position3D(1.0, 1.0, 1.1, 0.0, 0.0, 3, true, false)
        val withHint = strategy.calculatePosition(measurements, cVolume, hint)
        assertNotNull(withHint)
        assertFalse(withHint!!.ambiguous)
        assertTrue("błąd ${error(withHint, 1.0, 1.0, 1.0)}", error(withHint, 1.0, 1.0, 1.0) < 1e-2)
    }

    @Test
    fun noSolutionInsideVolume_isFlagged() {
        val farVolume = StairwellVolume(
            listOf(Point2D(100.0, 100.0), Point2D(101.0, 100.0), Point2D(101.0, 101.0), Point2D(100.0, 101.0)),
            0.0, 4.0
        )
        val p = strategy.calculatePosition(ranged(fourAnchors, 1.0, 3.0, 2.0), farVolume)
        assertNotNull(p)
        assertFalse(p!!.insideVolume)
        assertTrue(error(p, 1.0, 3.0, 2.0) < 1e-2)
    }

    @Test
    fun tooFewOrInvalidPoints_returnsNull() {
        assertNull(strategy.calculatePosition(ranged(fourAnchors.take(2), 1.0, 3.0, 2.0), cVolume))
        val bad = ranged(fourAnchors, 1.0, 3.0, 2.0).toMutableList()
        bad[0] = bad[0].copy(distance = Double.NaN)
        bad[1] = bad[1].copy(distance = -1.0)
        assertNull(strategy.calculatePosition(bad, cVolume))
    }

    @Test
    fun volume_containsHandlesConcaveShapeAndMargin() {
        assertTrue(cVolume.contains(1.0, 3.0, 2.0))   // lewy pas "C"
        assertFalse(cVolume.contains(4.0, 3.0, 2.0))  // wnętrze "C" (szyb) - poza bryłą
        assertTrue(cVolume.contains(4.0, 3.0, 2.0, margin = 1.1)) // margines obejmuje krawędź szybu
        assertFalse(cVolume.contains(1.0, 3.0, 5.0))  // ponad zMax
        assertTrue(cVolume.contains(1.0, 3.0, 4.2, margin = 0.3))
    }
}
