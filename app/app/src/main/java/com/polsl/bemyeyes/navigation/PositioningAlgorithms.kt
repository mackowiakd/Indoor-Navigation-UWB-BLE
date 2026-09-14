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
//logika 1D: