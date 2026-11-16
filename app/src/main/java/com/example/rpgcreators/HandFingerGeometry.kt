package com.example.rpgcreators

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Coordinates of the editable hand illustration (260 x 250 logical units).
 * No game/equipment state is stored here: moving a finger is purely cosmetic.
 */
internal data class HandPoint(val x: Float, val y: Float) {
    operator fun plus(other: HandPoint) = HandPoint(x + other.x, y + other.y)
    operator fun minus(other: HandPoint) = HandPoint(x - other.x, y - other.y)
    operator fun times(multiplier: Float) = HandPoint(x * multiplier, y * multiplier)
    fun distanceTo(other: HandPoint): Float = hypot(x - other.x, y - other.y)
}

internal object HandFingerGeometry {
    const val WIDTH = 260f
    const val HEIGHT = 250f

    // 0: thumb, 1: index, 2: middle, 3: ring, 4: little finger.
    val roots: List<HandPoint> = listOf(
        HandPoint(91f, 181f), HandPoint(94f, 137f), HandPoint(123f, 137f),
        HandPoint(152f, 137f), HandPoint(181f, 137f)
    )
    val restingTips: List<HandPoint> = listOf(
        HandPoint(55f, 149f), HandPoint(94f, 51f), HandPoint(123f, 31f),
        HandPoint(152f, 55f), HandPoint(181f, 82f)
    )

    fun marker(index: Int, tip: HandPoint): HandPoint =
        roots[index] + (tip - roots[index]) * (if (index == 0) 0.59f else 0.56f)

    /** Keep motion physically recognizable: the knuckle remains fixed, the
     * fingertip can swing and slightly curl, not teleport across the canvas.
     */
    fun restrictTip(index: Int, proposed: HandPoint): HandPoint {
        val root = roots[index]
        val rest = restingTips[index]
        val baseAngle = atan2(rest.y - root.y, rest.x - root.x)
        val proposedAngle = atan2(proposed.y - root.y, proposed.x - root.x)
        val angleDelta = atan2(sin(proposedAngle - baseAngle), cos(proposedAngle - baseAngle))
            .coerceIn(if (index == 0) -0.70f else -0.54f, if (index == 0) 0.70f else 0.54f)
        val restLength = root.distanceTo(rest)
        val radius = root.distanceTo(proposed).coerceIn(restLength * 0.64f, restLength * 1.03f)
        val angle = baseAngle + angleDelta
        return HandPoint(root.x + cos(angle) * radius, root.y + sin(angle) * radius)
    }

    /** Select the finger nearest to a touch along its actual moving segment. */
    fun hitFinger(point: HandPoint, tips: List<HandPoint>): Int? {
        if (tips.size != 5) return null
        val choice = (0 until 5).minByOrNull { index ->
            distanceToSegment(point, roots[index], tips[index])
        } ?: return null
        return choice.takeIf { distanceToSegment(point, roots[it], tips[it]) <= 19f }
    }

    private fun distanceToSegment(p: HandPoint, a: HandPoint, b: HandPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else
            (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return p.distanceTo(HandPoint(a.x + t * dx, a.y + t * dy))
    }
}
