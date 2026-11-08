package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingConnectionBarTest {
    @Test
    fun visibleOnlyWhenPagerAndActiveServerListAreIdle() {
        assertTrue(connectionBarVisible(pageScrolling = false, serverListScrolling = false))
        assertFalse(connectionBarVisible(pageScrolling = true, serverListScrolling = false))
        assertFalse(connectionBarVisible(pageScrolling = false, serverListScrolling = true))
        assertFalse(connectionBarVisible(pageScrolling = true, serverListScrolling = true))
        // The same idle state applies again once a drag, fling or page change settles.
        assertTrue(connectionBarVisible(pageScrolling = false, serverListScrolling = false))
    }
    @Test
    fun lastRowIsNeverPermanentlyBlockedByTheFloatingBar() {
        assertFalse(lastRowCoveredByBar(null, viewportEnd = 800, barOverlap = 80))
        assertFalse(lastRowCoveredByBar(800, viewportEnd = 800, barOverlap = 0))
        assertFalse(lastRowCoveredByBar(720, viewportEnd = 800, barOverlap = 80))
        assertTrue(lastRowCoveredByBar(721, viewportEnd = 800, barOverlap = 80))
        assertFalse(connectionBarVisible(false, false, lastRowCovered = true))
        assertTrue(connectionBarVisible(false, false, lastRowCovered = true, requestedByUser = true))
        assertFalse(connectionBarVisible(false, true, lastRowCovered = true, requestedByUser = true))
        assertFalse(connectionBarVisible(true, false, lastRowCovered = true, requestedByUser = true))
    }
}
