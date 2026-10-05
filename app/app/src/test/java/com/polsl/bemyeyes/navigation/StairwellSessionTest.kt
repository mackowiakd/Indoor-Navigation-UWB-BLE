package com.polsl.bemyeyes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.hypot

class StairwellSessionTest {

    // Wartości testowe - NIE są pomiarami rzeczywistej klatki
    private val json = """
        {
          "name": "test",
          "volume": {
            "polygon": [
              {"x":0,"y":0},{"x":6,"y":0},{"x":6,"y":2},{"x":2,"y":2},
              {"x":2,"y":4},{"x":6,"y":4},{"x":6,"y":6},{"x":0,"y":6}
            ],
            "zMin": 0, "zMax": 4
          },
          "levels": [{"name":"Piętro 7","z":0},{"name":"Podest","z":1.9},{"name":"Piętro 8","z":3.8}],
          "anchors": [
            {"id":"0x0001","x":0.3,"y":0.3,"z":0.3,"sigma":0.03},
            {"id":"0x0002","x":5.7,"y":1.0,"z":3.5,"sigma":0.03},
            {"id":"0x0003","x":0.5,"y":5.5,"z":3.0,"sigma":0.03},
            {"id":"0x0004","x":5.5,"y":5.5,"z":0.5,"sigma":0.03}
          ]
        }
    """.trimIndent()

    private val config = StairwellConfig.fromJson(json)
    private val ids = listOf("0x0001", "0x0002", "0x0003", "0x0004")

    private fun trueDistance(id: String, tx: Double, ty: Double, tz: Double): Double {
        val a = config.anchorById(id)!!
        return Math.sqrt((a.x - tx) * (a.x - tx) + (a.y - ty) * (a.y - ty) + (a.z - tz) * (a.z - tz))
    }

    // Filtr o oknie 1 = brak wygładzania, żeby testować samą logikę sesji
    private fun session(clock: () -> Long = { 0L }) =
        StairwellSession(config, filterFactory = { MovingAverageFilter(1) }, clock = clock)

    @Test
    fun fourAnchors_noiseless_returnsExactPosition() {
        val s = session()
        var last: StairwellSnapshot? = null
        for (id in ids) last = s.onMeasurement(id, trueDistance(id, 1.0, 3.0, 2.0))
        assertNotNull(last)
        val p = last!!.position
        assertTrue("błąd", hypot(hypot(p.x - 1.0, p.y - 3.0), p.z - 2.0) < 1e-2)
        assertEquals(4, last.usedAnchors)
    }

    @Test
    fun lessThanThreeAnchors_returnsNull() {
        val s = session()
        assertNull(s.onMeasurement("0x0001", trueDistance("0x0001", 1.0, 3.0, 2.0)))
        assertNull(s.onMeasurement("0x0002", trueDistance("0x0002", 1.0, 3.0, 2.0)))
    }

    @Test
    fun unknownAnchorAndInvalidDistance_areIgnored() {
        val s = session()
        assertNull(s.onMeasurement("0x00ff", 3.0))
        assertNull(s.onMeasurement("0x0001", Double.NaN))
        assertNull(s.onMeasurement("0x0001", -1.0))
    }

    @Test
    fun idsAreMatchedRegardlessOfCase() {
        val s = session()
        assertNull(s.onMeasurement("0X0001", 2.0)) // znana kotwica, za mało kotwic -> null, bez wyjątku
        assertEquals(2.0, s.filteredDistance("0x0001")!!, 1e-9)
    }

    @Test
    fun duplicateSnapshotValue_doesNotFeedFilter() {
        val s = StairwellSession(config, filterFactory = { MovingAverageFilter(3) })
        s.onMeasurement("0x0001", 2.0)
        s.onMeasurement("0x0001", 2.0) // duplikat migawki - pomijany
        s.onMeasurement("0x0001", 4.0)
        // z dedupem okno = [2, 4] -> 3.0 (bez dedupu byłoby [2, 2, 4] -> 2.667)
        assertEquals(3.0, s.filteredDistance("0x0001")!!, 1e-9)
    }

    @Test
    fun staleAnchors_areDroppedFromSolution() {
        var now = 0L
        val s = session { now }
        for (id in ids) s.onMeasurement(id, trueDistance(id, 1.0, 3.0, 2.0))
        now = 3000L // > 2.5 s: wszystkie poprzednie kotwice są nieświeże
        // nowy pomiar tylko jednej kotwicy (inna wartość niż poprzednio, więc nie jest duplikatem)
        assertNull(s.onMeasurement("0x0001", trueDistance("0x0001", 1.0, 3.0, 2.0) + 0.5))
    }

    @Test
    fun floorChange_isReportedAfterConfirmation() {
        val s = session()
        val changes = mutableListOf<Pair<Int?, Int>>()
        s.floorTracker.onLevelChanged = { from, to -> changes += from to to }
        val rnd = Random(1)
        var last: StairwellSnapshot? = null
        repeat(8) { // kolejne rundy z drobnym szumem (żeby nie były duplikatami)
            for (id in ids) last = s.onMeasurement(id, trueDistance(id, 1.0, 3.0, 3.8) + rnd.nextGaussian() * 0.01)
        }
        assertNotNull(last)
        assertEquals(FloorState.AtLevel(2, "Piętro 8"), last!!.floorState)
        assertEquals(listOf<Pair<Int?, Int>>(null to 2), changes)
    }

    @Test
    fun reset_forgetsState() {
        val s = session()
        for (id in ids) s.onMeasurement(id, trueDistance(id, 1.0, 3.0, 2.0))
        s.reset()
        assertNull(s.filteredDistance("0x0001"))
        assertEquals(FloorState.Unknown, s.floorTracker.state)
    }
}
