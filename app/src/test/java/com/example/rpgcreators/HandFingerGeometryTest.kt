package com.example.rpgcreators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandFingerGeometryTest {
    @Test fun everyFingerCanBeSelectedAtItsTip() {
        val tips = HandFingerGeometry.restingTips
        for (index in tips.indices) {
            assertNotNull(HandFingerGeometry.hitFinger(tips[index], tips))
        }
        assertNull(HandFingerGeometry.hitFinger(HandPoint(-100f, -100f), tips))
    }

    @Test fun draggedTipsRemainOnDrawing() {
        val tips = HandFingerGeometry.restingTips
        for (index in tips.indices) {
            for (dx in -200..200 step 17) for (dy in -190..190 step 19) {
                val rest = tips[index]
                val moved = HandFingerGeometry.restrictTip(index, HandPoint(rest.x + dx, rest.y + dy))
                assertTrue(moved.x in 0f..260f)
                assertTrue(moved.y in 0f..250f)
            }
        }
    }

    @Test fun ringMarkerAlwaysFollowsMovedFinger() {
        val index = 2
        val rest = HandFingerGeometry.restingTips[index]
        val before = HandFingerGeometry.marker(index, rest)
        val afterTip = HandFingerGeometry.restrictTip(index, HandPoint(rest.x + 20f, rest.y + 15f))
        val after = HandFingerGeometry.marker(index, afterTip)
        assertTrue(before.distanceTo(after) > 1f)
        val root = HandFingerGeometry.roots[index]
        assertTrue(after.distanceTo(root) < afterTip.distanceTo(root))
    }
}
