package com.polsl.bemyeyes.navigation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import kotlin.math.abs

// =========================================================
// KONFIGURACJA KLATKI SCHODOWEJ (lokalny plik JSON, bez bazy danych)
// Plik tworzy edytor tools/stairwell-editor/index.html; nieznane pola (np. editorState) są ignorowane.
// Wszystkie wymiary w metrach, względem punktu bazowego klatki (Z w górę).
// =========================================================

data class AnchorConfig(
    val id: String,        // np. "0x0001" - ten sam format co w ramce z ESP32 (U_0x0001=...)
    val x: Double,
    val y: Double,
    val z: Double,
    val sigma: Double,     // niepewność położenia kotwicy [m]
    val note: String?
)

data class ReferencePoint(val name: String, val x: Double, val y: Double, val z: Double, val note: String?)

data class VolumeConfig(val polygon: List<Point2D>, val zMin: Double, val zMax: Double)

data class StairwellConfig(
    val name: String,
    val origin: String?,
    val volume: VolumeConfig,
    val levels: List<FloorLevel>,
    val anchors: List<AnchorConfig>,
    val referencePoints: List<ReferencePoint>
) {
    fun toVolume(): StairwellVolume = StairwellVolume(volume.polygon, volume.zMin, volume.zMax)

    fun createFloorTracker(): FloorTracker = FloorTracker(levels)

    fun anchorById(rawId: String): AnchorConfig? {
        val id = normalizeAnchorId(rawId)
        return anchors.find { normalizeAnchorId(it.id) == id }
    }

    /** Zamienia mapę {ID kotwicy -> zmierzony dystans} na punkty dla solwera; nieznane kotwice pomija. */
    fun rangedPoints(distances: Map<String, Double>): List<RangedPoint3D> =
        distances.mapNotNull { (rawId, dist) ->
            val a = anchorById(rawId) ?: return@mapNotNull null
            RangedPoint3D(a.x, a.y, a.z, dist, a.sigma)
        }

    /**
     * Kotwice jako urządzenia topologii (zamiast pobierania z bazy). macAddress ma format ramki z ESP32
     * ("0x%04X", wielkie litery), bo silnik porównuje ID dokładnie tak, jak je przysyła tag.
     */
    fun toDevices(locationId: Int = LOCAL_LOCATION_ID): List<IoTDevice> = anchors.map { a ->
        IoTDevice(
            macAddress = espAnchorId(a.id),
            deviceType = "UWB_ANCHOR",
            locationId = locationId,
            semanticRole = a.note?.takeIf { it.isNotBlank() } ?: "Kotwica ${a.id}",
            txPowerConfig = null,
            globalX = a.x,
            globalY = a.y,
            globalZ = a.z
        )
    }

    /** Zwraca listę ostrzeżeń (pusta = konfiguracja wygląda poprawnie). */
    fun validate(margin: Double = 0.30): List<String> {
        val issues = mutableListOf<String>()
        val poly = volume.polygon

        if (poly.size < 3) issues += "Wielokąt klatki ma mniej niż 3 wierzchołki"
        if (volume.zMin >= volume.zMax) issues += "zMin musi być mniejsze od zMax"
        if (poly.size >= 4 && polygonSelfIntersects(poly)) issues += "Wielokąt klatki przecina się (zła kolejność wierzchołków?)"

        if (levels.size < 2) issues += "Wymagane co najmniej 2 poziomy Z"
        if (levels.map { it.z }.distinct().size != levels.size) issues += "Poziomy Z się powtarzają"
        levels.filter { it.z < volume.zMin - margin || it.z > volume.zMax + margin }
            .forEach { issues += "Poziom '${it.name}' (z=${it.z}) leży poza zakresem Z klatki" }

        if (anchors.size < 3) issues += "Potrzeba co najmniej 3 kotwic (jest ${anchors.size})"
        if (anchors.map { normalizeAnchorId(it.id) }.distinct().size != anchors.size) issues += "ID kotwic się powtarzają"

        if (poly.size >= 3 && volume.zMin < volume.zMax) {
            val vol = toVolume()
            anchors.filter { !vol.contains(it.x, it.y, it.z, margin) }
                .forEach { issues += "Kotwica ${it.id} leży poza bryłą klatki" }
        }

        if (anchors.size >= 3) {
            val (a, b, c) = anchors
            val cross = doubleArrayOf(
                (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y),
                (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z),
                (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            )
            val triangleArea = Math.sqrt(cross.sumOf { it * it }) / 2.0 // pole trójkąta kotwic [m²]
            if (triangleArea < 0.05) issues += "Pierwsze 3 kotwice są (prawie) współliniowe - trilateracja niemożliwa"
        }
        if (anchors.size >= 4) {
            val (a, b, c, d) = anchors
            val v = tripleProduct(b, a, c, d)
            if (abs(v) < 0.05) issues += "Pierwsze 4 kotwice są (prawie) współpłaszczyznowe - rozwiązanie 3D jest niejednoznaczne, polegamy na bryle klatki"
        }
        return issues
    }

    companion object {
        const val DEFAULT_ANCHOR_SIGMA = 0.05

        /** location_id nadawany kotwicom z pliku (poza zakresem lokalizacji z bazy, gdzie są 1 i 2). */
        const val LOCAL_LOCATION_ID = 100

        /** @throws IllegalArgumentException gdy brakuje wymaganych pól lub JSON jest niepoprawny. */
        fun fromJson(json: String): StairwellConfig {
            val root = try {
                JsonParser().parse(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Niepoprawny JSON: ${e.message}")
            }
            require(root.isJsonObject) { "Konfiguracja musi być obiektem JSON" }
            val o = root.asJsonObject

            val vol = o.requireObject("volume")
            val polygon = vol.requireArray("polygon").map {
                val p = it.asJsonObject
                Point2D(p.requireDouble("x", "wierzchołek wielokąta"), p.requireDouble("y", "wierzchołek wielokąta"))
            }
            val volume = VolumeConfig(polygon, vol.requireDouble("zMin", "volume"), vol.requireDouble("zMax", "volume"))

            val levels = o.requireArray("levels").map {
                val l = it.asJsonObject
                FloorLevel(l.optString("name") ?: "", l.requireDouble("z", "poziom"))
            }
            val anchors = o.requireArray("anchors").map {
                val a = it.asJsonObject
                val id = a.optString("id") ?: throw IllegalArgumentException("Kotwica bez pola 'id'")
                val sigma = a.optDouble("sigma") ?: 0.0
                AnchorConfig(
                    id = id,
                    x = a.requireDouble("x", "kotwica $id"),
                    y = a.requireDouble("y", "kotwica $id"),
                    z = a.requireDouble("z", "kotwica $id"),
                    sigma = if (sigma > 0.0) sigma else DEFAULT_ANCHOR_SIGMA,
                    note = a.optString("note")
                )
            }
            val refs = (o.optArray("referencePoints") ?: JsonArray()).map {
                val r = it.asJsonObject
                val n = r.optString("name") ?: ""
                ReferencePoint(
                    n,
                    r.requireDouble("x", "punkt $n"),
                    r.requireDouble("y", "punkt $n"),
                    r.requireDouble("z", "punkt $n"),
                    r.optString("note")
                )
            }
            return StairwellConfig(o.optString("name") ?: "", o.optString("origin"), volume, levels, anchors, refs)
        }
    }
}

/** Zamienia "1", "0x1" lub "0x0001" na jednolity format "0x0001" (jak formatAnchorId w AutoCalibration). */
fun normalizeAnchorId(rawId: String): String {
    val clean = rawId.trim().removePrefix("0x").removePrefix("0X")
    val numeric = clean.toIntOrNull(16) ?: return rawId.trim()
    return String.format("0x%04x", numeric)
}

/** Jak normalizeAnchorId, ale wielkimi literami - tak ESP32 formatuje ID w ramce (snprintf "0x%04X"). */
fun espAnchorId(rawId: String): String {
    val clean = rawId.trim().removePrefix("0x").removePrefix("0X")
    val numeric = clean.toIntOrNull(16) ?: return rawId.trim()
    return String.format("0x%04X", numeric)
}

private fun tripleProduct(b: AnchorConfig, a: AnchorConfig, c: AnchorConfig, d: AnchorConfig): Double {
    // objętość równoległościanu rozpiętego przez wektory a->b, a->c, a->d [m³]
    val u = doubleArrayOf(b.x - a.x, b.y - a.y, b.z - a.z)
    val v = doubleArrayOf(c.x - a.x, c.y - a.y, c.z - a.z)
    val w = doubleArrayOf(d.x - a.x, d.y - a.y, d.z - a.z)
    return u[0] * (v[1] * w[2] - v[2] * w[1]) -
        u[1] * (v[0] * w[2] - v[2] * w[0]) +
        u[2] * (v[0] * w[1] - v[1] * w[0])
}

private fun orient(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double =
    (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

private fun polygonSelfIntersects(poly: List<Point2D>): Boolean {
    val n = poly.size
    for (i in 0 until n) {
        val a = poly[i]
        val b = poly[(i + 1) % n]
        for (j in i + 1 until n) {
            // krawędzie sąsiednie dzielą wierzchołek - pomijamy
            if (j == i || (j + 1) % n == i || (i + 1) % n == j) continue
            val c = poly[j]
            val d = poly[(j + 1) % n]
            val d1 = orient(a.x, a.y, b.x, b.y, c.x, c.y)
            val d2 = orient(a.x, a.y, b.x, b.y, d.x, d.y)
            val d3 = orient(c.x, c.y, d.x, d.y, a.x, a.y)
            val d4 = orient(c.x, c.y, d.x, d.y, b.x, b.y)
            if (d1 * d2 < 0 && d3 * d4 < 0) return true
        }
    }
    return false
}

private fun JsonObject.requireObject(key: String): JsonObject =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        ?: throw IllegalArgumentException("Brak obiektu '$key' w konfiguracji")

private fun JsonObject.requireArray(key: String): JsonArray =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalArgumentException("Brak tablicy '$key' w konfiguracji")

private fun JsonObject.optArray(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.requireDouble(key: String, context: String): Double {
    val e = get(key)
    if (e == null || !e.isJsonPrimitive) throw IllegalArgumentException("Brak liczby '$key' ($context)")
    return e.asDouble
}

private fun JsonObject.optDouble(key: String): Double? = get(key)?.takeIf { it.isJsonPrimitive }?.asDouble

private fun JsonObject.optString(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
