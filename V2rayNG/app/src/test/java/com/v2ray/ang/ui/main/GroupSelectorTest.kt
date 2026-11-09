package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
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
    @Test
    fun locateResolvesUngroupedAndFallsBackToAllOnlyWhenAvailable() {
        val defaultId = AppConfig.DEFAULT_SUBSCRIPTION_ID
        assertEquals(defaultId, locateGroupId("", listOf("", defaultId)))
        assertEquals("group", locateGroupId("group", listOf("", "group")))
        assertEquals("", locateGroupId("missing", listOf("", "group")))
        assertEquals("", locateGroupId("", listOf("")))
        assertNull(locateGroupId("missing", listOf("group")))
        assertNull(locateGroupId("", emptyList()))
    }
}
