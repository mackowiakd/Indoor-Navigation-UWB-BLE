package com.polsl.bemyeyes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StairwellConfigTest {

    // Wartości testowe - NIE są pomiarami rzeczywistej klatki
    private val json = """
        {
          "name": "test",
          "origin": "narożnik (0,0,0)",
          "volume": {
            "polygon": [
              {"x":0,"y":0},{"x":6,"y":0},{"x":6,"y":2},{"x":2,"y":2},
              {"x":2,"y":4},{"x":6,"y":4},{"x":6,"y":6},{"x":0,"y":6}
            ],
            "zMin": 0, "zMax": 4
          },
          "levels": [{"name":"Piętro 7","z":0},{"name":"Podest","z":1.9},{"name":"Piętro 8","z":3.8}],
          "anchors": [
            {"id":"1","x":0.3,"y":0.3,"z":0.3,"sigma":0.03},
            {"id":"0x0002","x":5.7,"y":1.0,"z":3.5},
            {"id":"0x0003","x":0.5,"y":5.5,"z":3.0,"note":"przy oknie"},
            {"id":"0x0004","x":5.5,"y":5.5,"z":0.5}
          ],
          "editorState": {"unit":"cm"}
        }
    """.trimIndent()

    @Test
    fun fromJson_parsesAndAppliesDefaultSigma() {
        val c = StairwellConfig.fromJson(json)
        assertEquals(8, c.volume.polygon.size)
        assertEquals(3, c.levels.size)
        assertEquals(4, c.anchors.size)
        assertEquals(0.03, c.anchors[0].sigma, 1e-9)
        assertEquals(StairwellConfig.DEFAULT_ANCHOR_SIGMA, c.anchors[1].sigma, 1e-9)
        assertEquals("przy oknie", c.anchors[2].note)
        assertTrue(c.referencePoints.isEmpty())
    }

    @Test
    fun anchorLookup_normalizesIds() {
        val c = StairwellConfig.fromJson(json)
        assertNotNull(c.anchorById("0x0001"))
        assertNotNull(c.anchorById("0x1"))
        assertNull(c.anchorById("0x0009"))
        assertEquals("0x0001", normalizeAnchorId("1"))
    }

    @Test
    fun toDevices_usesEspIdFormatAndLocalLocation() {
        val devices = StairwellConfig.fromJson(json).toDevices()
        assertEquals(listOf("0x0001", "0x0002", "0x0003", "0x0004"), devices.map { it.macAddress })
        assertTrue(devices.all { it.deviceType == "UWB_ANCHOR" && it.locationId == StairwellConfig.LOCAL_LOCATION_ID })
        assertEquals(0.3, devices[0].globalX!!, 1e-9)
        assertEquals(0.3, devices[0].globalZ!!, 1e-9)
        assertEquals("0x000A", espAnchorId("0xa"))
        assertEquals("0x000A", espAnchorId("A"))
    }

    @Test
    fun rangedPoints_skipsUnknownAnchors() {
        val c = StairwellConfig.fromJson(json)
        val pts = c.rangedPoints(mapOf("0x0001" to 2.0, "0x0002" to 3.0, "0x00ff" to 4.0))
        assertEquals(2, pts.size)
    }

    @Test
    fun validate_validConfigHasNoIssues() {
        assertEquals(emptyList<String>(), StairwellConfig.fromJson(json).validate())
    }

    @Test
    fun validate_detectsAnchorOutsideAndSelfIntersection() {
        val outside = json.replace("\"x\":5.5,\"y\":5.5,\"z\":0.5", "\"x\":4.0,\"y\":3.0,\"z\":0.5") // w szybie "C"
        assertTrue(StairwellConfig.fromJson(outside).validate().any { it.contains("poza bryłą") })

        val bowtie = json.replace("{\"x\":6,\"y\":0},{\"x\":6,\"y\":2}", "{\"x\":6,\"y\":2},{\"x\":6,\"y\":0}")
        assertTrue(StairwellConfig.fromJson(bowtie).validate().any { it.contains("przecina") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromJson_missingVolume_throws() {
        StairwellConfig.fromJson("""{"levels":[],"anchors":[]}""")
    }

    @Test
    fun createFloorTracker_usesConfigLevels() {
        val t = StairwellConfig.fromJson(json).createFloorTracker()
        repeat(3) { t.update(3.8) }
        assertEquals(FloorState.AtLevel(2, "Piętro 8"), t.state)
    }
}
