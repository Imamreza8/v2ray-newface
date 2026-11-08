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
}
