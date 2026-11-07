package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroupSelectorTest {
    @Test
    fun unloadedGroupDoesNotPretendToHaveZeroConfigs() {
        assertNull(ServerGroupUiState().totalCount)
        assertEquals(0, ServerGroupUiState(totalCount = 0).totalCount)
        assertEquals(7959, ServerGroupUiState(totalCount = 7959).totalCount)
    }
}
