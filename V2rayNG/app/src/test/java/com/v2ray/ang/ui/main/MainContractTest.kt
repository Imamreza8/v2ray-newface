package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainContractTest {

    @Test
    fun megabitsPerSecond_convertsBytesAndMillis() {
        // 5,000,000 bytes in 4 s = 10 Mbit/s
        assertEquals(10.0, megabitsPerSecond(5_000_000L, 4_000L), 1e-9)
    }

    @Test
    fun megabitsPerSecond_neverDividesByZero() {
        assertEquals(8_000.0, megabitsPerSecond(1_000_000L, 0L), 1e-9)
    }

    @Test
    fun initialUiState_hasNoRunOrSpeedTest() {
        val state = MainUiState()
        assertEquals(TestRunStats(), state.runStats)
        assertEquals(0, state.resumableCount)
        assertEquals(SpeedTestState.Idle, state.speedTest)
    }
    @Test
    fun statusFiltersMatchTheDisplayedPing() {
        assertTrue(matchesServerStatus(-1, ServerStatusFilter.ALL, 250))
        assertTrue(matchesServerStatus(0, ServerStatusFilter.UNTESTED, 250))
        assertFalse(matchesServerStatus(0, ServerStatusFilter.ALIVE, 250))
        assertTrue(matchesServerStatus(-1, ServerStatusFilter.FAILED, 250))
        assertFalse(matchesServerStatus(1, ServerStatusFilter.FAILED, 250))
        assertTrue(matchesServerStatus(250, ServerStatusFilter.TOP, 250))
        assertFalse(matchesServerStatus(251, ServerStatusFilter.TOP, 250))
        assertTrue(matchesServerStatus(251, ServerStatusFilter.ALIVE, 250))
    }

    @Test
    fun filterScopeDistinguishesAllServersFromFilteredResults() {
        assertFalse(hasActiveServerFilter("", ServerStatusFilter.ALL))
        assertFalse(hasActiveServerFilter("  ", ServerStatusFilter.ALL))
        assertTrue(hasActiveServerFilter("server", ServerStatusFilter.ALL))
        assertTrue(hasActiveServerFilter("", ServerStatusFilter.FAILED))
    }

}
