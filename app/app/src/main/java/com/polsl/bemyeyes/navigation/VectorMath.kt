package com.polsl.bemyeyes.navigation

data class RelativePosition(
    val forwardDistance: Double,
    val lateralDistance: Double,
    val side: String,
    val isDeadZone: Boolean
)

fun calculateRelativePosition(
    moveDx: Double, moveDy: Double,
    poiDx: Double, poiDy: Double
): RelativePosition {
    val vLength = Math.hypot(moveDx, moveDy)
    // 1. ILOCZYN WEKTOROWY (Cross Product)
    val crossProduct = (moveDx * poiDy) - (moveDy * poiDx)
    // 2. ILOCZYN SKALARNY (Dot Product)
    val dotProduct = (moveDx * poiDx) + (moveDy * poiDy)

    return RelativePosition(
        // 3. FIZYCZNE ODLEGŁOŚCI WZGLĘDEM KIERUNKU TWOJEGO RUCHU
        forwardDistance = dotProduct / vLength,
        lateralDistance = Math.abs(crossProduct) / vLength,
        side = if (crossProduct > 0) "lewej" else "prawej",
        isDeadZone = Math.abs(crossProduct) < 0.05
    )
}
