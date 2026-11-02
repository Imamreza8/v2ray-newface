package com.v2ray.ang.ui.main

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
}
