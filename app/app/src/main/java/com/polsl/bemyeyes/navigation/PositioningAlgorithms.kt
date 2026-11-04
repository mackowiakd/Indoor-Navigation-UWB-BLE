package com.polsl.bemyeyes.navigation

import kotlin.math.abs
import kotlin.math.hypot


// =========================================================
// CZĘŚĆ 1: BUDOWANIE MAPY (Relatywna kalibracja kotwic)
// =========================================================

// DTO izolujące matematykę od bazy danych
data class RangedPoint(val x: Double, val y: Double, val distance: Double)

interface PositioningStrategy {
    fun calculatePosition(points: List<RangedPoint>): Pair<Double, Double>?
}

/**
 * Uniwersalna, nieliniowa Metoda Najmniejszych Kwadratów (Gauss-Newton).
 * Obsługuje 3, 4, 5 lub dowolną liczbę N kotwic bez modyfikacji kodu!
 */
class LeastSquaresStrategy(private val iterations: Int = 5) : PositioningStrategy {
    override fun calculatePosition(points: List<RangedPoint>): Pair<Double, Double>? {
        if (points.size < 3) return null

        // Punkt startowy: środek ciężkości (centroid) kotwic lub średnia
        var x = points.map { it.x }.average()
        var y = points.map { it.y }.average()

        repeat(iterations) {
            var a = 0.0
            var b = 0.0
            var c = 0.0
            var g0 = 0.0
            var g1 = 0.0

            for (p in points) {
                val dx = x - p.x
                val dy = y - p.y
                val rho = hypot(dx, dy).coerceAtLeast(1e-6) // zabezpieczenie przed div-by-zero

                // Elementy macierzi Jacobiana
                val jx = dx / rho
                val jy = dy / rho
                val res = rho - p.distance // residuum (różnica między obliczonym a zmierzonym dystansem)

                a += jx * jx
                b += jx * jy
                c += jy * jy
                g0 += jx * res
                g1 += jy * res
            }

            val det = a * c - b * b
            if (abs(det) < 1e-6) return null // Układ osobliwy

            // Poprawka Gaussa-Newtona
            x -= (c * g0 - b * g1) / det
            y -= (-b * g0 + a * g1) / det
        }

        return Pair(x, y)
    }
}

//ad for n==3 as Least squares for 3 anchors work way worse

class TrilaterationStrategy : PositioningStrategy {
    override fun calculatePosition(points: List<RangedPoint>): Pair<Double, Double>? {
        if (points.size < 3) return null

        val p1 = points[0]
        val p2 = points[1]
        val p3 = points[2]

        val a = 2 * (p2.x - p1.x)
        val b = 2 * (p2.y - p1.y)
        val c = (p1.distance * p1.distance) - (p2.distance * p2.distance) -
                (p1.x * p1.x) + (p2.x * p2.x) - (p1.y * p1.y) + (p2.y * p2.y)

        val d = 2 * (p3.x - p2.x)
        val e = 2 * (p3.y - p2.y)
        val f = (p2.distance * p2.distance) - (p3.distance * p3.distance) -
                (p2.x * p2.x) + (p3.x * p3.x) - (p2.y * p2.y) + (p3.y * p3.y)

        val w = (a * e) - (b * d)

        // Zabezpieczenie przed dzieleniem przez zero (punkty współliniowe)
        if (abs(w) < 0.0001) return null

        val wx = (c * e) - (b * f)
        val wy = (a * f) - (c * d)

        return Pair(wx / w, wy / w)
    }
}
class TwoAnchorCorridorPositioningStrategy : PositioningStrategy {
    override fun calculatePosition(points:  List<RangedPoint>): Pair<Double, Double>? {
        if (points.size < 2) return null


        val p1 = points[0]
        val p2 = points[1]

        // 1. Obliczamy fizyczny dystans między dwiema kotwicami
        val d = Math.hypot(p2.x - p1.x, p2.y - p1.y)
        if (d < 0.1) return null // Zabezpieczenie przed dzieleniem przez zero

        // 2. Rzutujemy pozycję taga na oś łączącą kotwice
        val distFromP1 = (p1.distance * p1.distance - p2.distance * p2.distance + d * d) / (2 * d)

        // 3. Obliczamy proporcję położenia
        val t = distFromP1 / d

        // 4. Interpolacja liniowa dla współrzędnych taga
        val tagX = p1.x + t * (p2.x - p1.x)
        val tagY = p1.y + t * (p2.y - p1.y)

        return Pair(tagX, tagY)
    }
}
//logika 1D:
