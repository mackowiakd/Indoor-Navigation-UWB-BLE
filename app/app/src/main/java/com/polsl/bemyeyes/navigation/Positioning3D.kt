package com.polsl.bemyeyes.navigation

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// =========================================================
// POZYCJONOWANIE 3D (klatka schodowa)
// Czysty Kotlin, bez zależności od Androida - ten sam kod działa na telefonie i w testach JVM.
// =========================================================

data class Point2D(val x: Double, val y: Double)

/** Pomiar dystansu do kotwicy o znanych współrzędnych. sigma = niepewność położenia kotwicy [m]. */
data class RangedPoint3D(
    val x: Double,
    val y: Double,
    val z: Double,
    val distance: Double,
    val sigma: Double = 0.05
)

data class Position3D(
    val x: Double,
    val y: Double,
    val z: Double,
    val rmsResidual: Double,   // sqrt(średnia z kwadratów reszt) [m] - bez wag
    val chi2: Double,          // suma ważonych kwadratów reszt
    val anchorCount: Int,
    val insideVolume: Boolean, // false = żadne rozwiązanie nie mieści się w bryle klatki
    val ambiguous: Boolean     // true = dwa (lub więcej) równie dobre rozwiązania wewnątrz bryły
)

interface PositioningStrategy3D {
    /**
     * @param volume bryła klatki - służy do odrzucenia rozwiązania lustrzanego (null = bez ograniczeń)
     * @param hint ostatnia znana pozycja - rozstrzyga niejednoznaczność przez ciągłość ruchu
     */
    fun calculatePosition(
        points: List<RangedPoint3D>,
        volume: StairwellVolume? = null,
        hint: Position3D? = null
    ): Position3D?
}

/**
 * Bryła klatki: wielokąt (może być nieprzypuklły, np. kształt "C") w rzucie z góry + zakres Z.
 */
class StairwellVolume(val polygon: List<Point2D>, val zMin: Double, val zMax: Double) {

    init {
        require(polygon.size >= 3) { "Wielokąt klatki wymaga co najmniej 3 wierzchołków" }
        require(zMin < zMax) { "zMin musi być mniejsze od zMax" }
    }

    val centroid: Point2D
        get() = Point2D(polygon.sumOf { it.x } / polygon.size, polygon.sumOf { it.y } / polygon.size)

    /** margin [m] tolerancja na błędy pomiarów - punkt tuż za ścianą nadal jest uznany za wewnątrz. */
    fun contains(x: Double, y: Double, z: Double, margin: Double = 0.0): Boolean {
        if (z < zMin - margin || z > zMax + margin) return false
        return insidePolygon(x, y) || distanceToBoundary(x, y) <= margin
    }

    private fun insidePolygon(x: Double, y: Double): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun distanceToBoundary(px: Double, py: Double): Double {
        var best = Double.MAX_VALUE
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - a.x) * dx + (py - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            best = min(best, hypot(px - (a.x + t * dx), py - (a.y + t * dy)))
        }
        return best
    }
}

/**
 * Trilateracja 3D metodą ważonych najmniejszych kwadratów (Levenberg-Marquardt) z wielu punktów startowych.
 *
 * - 3 kotwice (zawsze współpłaszczyznowe) dają dwa rozwiązania lustrzane względem płaszczyzny kotwic;
 *   wybieramy to, które leży w bryle klatki.
 * - 4+ kotwice niewspółpłaszczyznowe dają jedno rozwiązanie, a chi2 / rmsResidual mówią o jego jakości.
 */
class Trilateration3DStrategy(
    private val rangingSigma: Double = 0.10,     // szum pojedynczego pomiaru UWB [m]
    private val volumeMargin: Double = 0.30,     // tolerancja bryły klatki [m]
    private val ambiguityChi2Gap: Double = 4.0,  // różnica chi2, poniżej której rozwiązania są "równie dobre"
    private val maxIterations: Int = 60
) : PositioningStrategy3D {

    private class Candidate(val x: Double, val y: Double, val z: Double, val chi2: Double)

    override fun calculatePosition(
        points: List<RangedPoint3D>,
        volume: StairwellVolume?,
        hint: Position3D?
    ): Position3D? {
        val valid = points.filter {
            it.distance.isFinite() && it.distance > 0.0 &&
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }
        if (valid.size < 3) return null

        val sigmas = DoubleArray(valid.size) { hypot(max(valid[it].sigma, 0.0), rangingSigma) }

        val candidates = mutableListOf<Candidate>()
        for (start in startPoints(valid, volume, hint)) {
            val c = refine(start, valid, sigmas) ?: continue
            candidates += c
        }
        if (candidates.isEmpty()) return null

        // Klasteryzacja: to samo rozwiązanie z różnych startów liczymy raz
        val distinct = mutableListOf<Candidate>()
        for (c in candidates.sortedBy { it.chi2 }) {
            if (distinct.none { hypot(hypot(it.x - c.x, it.y - c.y), it.z - c.z) < 0.05 }) distinct += c
        }

        val inside = distinct.filter { volume?.contains(it.x, it.y, it.z, volumeMargin) ?: true }
        val insideVolume = inside.isNotEmpty()
        val pool = if (insideVolume) inside else distinct

        var best = pool[0]
        var ambiguous = pool.size > 1 && pool[1].chi2 - pool[0].chi2 < ambiguityChi2Gap
        if (ambiguous && hint != null) {
            // Ciągłość ruchu: spośród równie dobrych rozwiązań bierzemy najbliższe poprzedniej pozycji
            best = pool.filter { it.chi2 - pool[0].chi2 < ambiguityChi2Gap }
                .minByOrNull { hypot(hypot(it.x - hint.x, it.y - hint.y), it.z - hint.z) } ?: pool[0]
            ambiguous = false
        }

        var sumSq = 0.0
        for (p in valid) {
            val r = dist(best.x, best.y, best.z, p) - p.distance
            sumSq += r * r
        }
        return Position3D(
            x = best.x, y = best.y, z = best.z,
            rmsResidual = sqrt(sumSq / valid.size),
            chi2 = best.chi2,
            anchorCount = valid.size,
            insideVolume = insideVolume,
            ambiguous = ambiguous
        )
    }

    private fun startPoints(
        pts: List<RangedPoint3D>,
        volume: StairwellVolume?,
        hint: Position3D?
    ): List<DoubleArray> {
        val bases = mutableListOf(
            doubleArrayOf(pts.sumOf { it.x } / pts.size, pts.sumOf { it.y } / pts.size, pts.sumOf { it.z } / pts.size)
        )
        if (volume != null) {
            val c = volume.centroid
            bases += doubleArrayOf(c.x, c.y, (volume.zMin + volume.zMax) / 2.0)
        }
        // Każdy offset ma składową Z, żeby nie utknąć w płaszczyźnie kotwic (tam jakobian jest osobliwy)
        val r = max(0.5, pts.sumOf { it.distance } / pts.size * 0.5)
        val offsets = listOf(
            doubleArrayOf(0.0, 0.0, r), doubleArrayOf(0.0, 0.0, -r),
            doubleArrayOf(r, 0.0, r), doubleArrayOf(r, 0.0, -r),
            doubleArrayOf(-r, 0.0, r), doubleArrayOf(-r, 0.0, -r),
            doubleArrayOf(0.0, r, r), doubleArrayOf(0.0, r, -r),
            doubleArrayOf(0.0, -r, r), doubleArrayOf(0.0, -r, -r)
        )
        val starts = mutableListOf<DoubleArray>()
        for (b in bases) for (o in offsets) starts += doubleArrayOf(b[0] + o[0], b[1] + o[1], b[2] + o[2])
        if (hint != null) starts += doubleArrayOf(hint.x, hint.y, hint.z)
        return starts
    }

    private fun dist(x: Double, y: Double, z: Double, p: RangedPoint3D): Double {
        val dx = x - p.x
        val dy = y - p.y
        val dz = z - p.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun chi2(p: DoubleArray, pts: List<RangedPoint3D>, sig: DoubleArray): Double {
        var s = 0.0
        for (i in pts.indices) {
            val r = (dist(p[0], p[1], p[2], pts[i]) - pts[i].distance) / sig[i]
            s += r * r
        }
        return s
    }

    private fun refine(start: DoubleArray, pts: List<RangedPoint3D>, sig: DoubleArray): Candidate? {
        var p = start.copyOf()
        var cost = chi2(p, pts, sig)
        var lambda = 1e-2

        var iter = 0
        var converged = false
        while (iter < maxIterations && !converged) {
            iter++
            val jtj = Array(3) { DoubleArray(3) }
            val g = DoubleArray(3)
            for (i in pts.indices) {
                val d = max(dist(p[0], p[1], p[2], pts[i]), 1e-9)
                val r = (d - pts[i].distance) / sig[i]
                val j = doubleArrayOf(
                    (p[0] - pts[i].x) / d / sig[i],
                    (p[1] - pts[i].y) / d / sig[i],
                    (p[2] - pts[i].z) / d / sig[i]
                )
                for (a in 0 until 3) {
                    g[a] += j[a] * r
                    for (b in 0 until 3) jtj[a][b] += j[a] * j[b]
                }
            }

            var improved = false
            var tries = 0
            while (tries < 10 && !improved) {
                tries++
                val m = Array(3) { a -> DoubleArray(3) { b -> jtj[a][b] } }
                for (k in 0 until 3) m[k][k] += lambda * (jtj[k][k] + 1e-3)
                val delta = solve3(m, doubleArrayOf(-g[0], -g[1], -g[2]))
                if (delta == null) {
                    lambda *= 4.0
                    continue
                }
                val np = doubleArrayOf(p[0] + delta[0], p[1] + delta[1], p[2] + delta[2])
                val nc = chi2(np, pts, sig)
                if (nc < cost) {
                    val step = sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2])
                    p = np
                    cost = nc
                    lambda = max(lambda / 3.0, 1e-9)
                    improved = true
                    if (step < 1e-7) converged = true
                } else {
                    lambda *= 4.0
                }
            }
            if (!improved) converged = true // brak poprawy = jesteśmy w minimum (lokalnym)
        }
        if (!cost.isFinite() || p.any { !it.isFinite() }) return null
        return Candidate(p[0], p[1], p[2], cost)
    }

    /** Rozwiązuje układ 3x3 eliminacją Gaussa z wyborem elementu głównego; null = macierz osobliwa. */
    private fun solve3(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val m = Array(3) { i -> doubleArrayOf(a[i][0], a[i][1], a[i][2], b[i]) }
        for (col in 0 until 3) {
            var pivot = col
            for (r in col + 1 until 3) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) return null
            val tmp = m[col]
            m[col] = m[pivot]
            m[pivot] = tmp
            for (r in col + 1 until 3) {
                val f = m[r][col] / m[col][col]
                for (c in col until 4) m[r][c] -= f * m[col][c]
            }
        }
        val x = DoubleArray(3)
        for (i in 2 downTo 0) {
            var s = m[i][3]
            for (c in i + 1 until 3) s -= m[i][c] * x[c]
            x[i] = s / m[i][i]
        }
        return x
    }
}
