package com.polsl.bemyeyes.navigation

import kotlin.math.abs


// =========================================================
// CZĘŚĆ 1: BUDOWANIE MAPY (Relatywna kalibracja kotwic)
// =========================================================

// DTO izolujące matematykę od bazy danych
data class RangedPoint(val x: Double, val y: Double, val distance: Double)

interface PositioningStrategy {
    fun calculatePosition(points: List<RangedPoint>): Pair<Double, Double>?
}

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