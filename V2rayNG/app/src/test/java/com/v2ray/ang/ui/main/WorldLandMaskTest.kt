package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldLandMaskTest {

    @Test
    fun knownPlaces_areClassifiedAsExpected() {
        assertTrue(WorldLandMask.isLand(2.3f, 48.9f))      // Paris
        assertTrue(WorldLandMask.isLand(-100f, 40f))       // USA
        assertTrue(WorldLandMask.isLand(135f, -25f))       // Australia
        assertTrue(WorldLandMask.isLand(139.7f, 35.7f))    // Tokyo
        assertTrue(WorldLandMask.isLand(78f, 22f))         // India
        assertTrue(WorldLandMask.isLand(-55f, -10f))       // Brazil
        assertFalse(WorldLandMask.isLand(-150f, 0f))       // Pacific
        assertFalse(WorldLandMask.isLand(-30f, 0f))        // Atlantic
        assertFalse(WorldLandMask.isLand(80f, -30f))       // Indian Ocean
    }

    @Test
    fun globePoints_haveUnitLengthAndPlausibleLandShare() {
        val globe = GlobePoints.build(900)
        assertEquals(900, globe.size)
        for (i in 0 until globe.size) {
            val length = globe.x[i] * globe.x[i] + globe.y[i] * globe.y[i] + globe.z[i] * globe.z[i]
            assertEquals(1f, length, 1e-3f)
        }
        val share = globe.land.count { it }.toFloat() / globe.size
        assertTrue(share in 0.15f..0.40f, "land share was $share")
    }
}
