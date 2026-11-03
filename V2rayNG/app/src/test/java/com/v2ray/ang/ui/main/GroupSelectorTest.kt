package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroupSelectorTest {
    @Test
    fun groupNamesAreDisambiguatedWithoutUsingPositionAsIdentity() {
        val groups = listOf(
            GroupMapItem("", "All"),
            GroupMapItem("a", "import sub"),
            GroupMapItem("b", "import sub"),
            GroupMapItem("c", "Other"),
        )
        assertNull(duplicateGroupOrdinal(groups, ""))
        assertEquals(1, duplicateGroupOrdinal(groups, "a"))
        assertEquals(2, duplicateGroupOrdinal(groups, "b"))
        assertNull(duplicateGroupOrdinal(groups, "c"))
        assertNull(duplicateGroupOrdinal(groups, "missing"))
        assertNull(duplicateGroupOrdinal(emptyList(), "a"))
    }

    @Test
    fun unloadedGroupDoesNotPretendToHaveZeroConfigs() {
        assertNull(ServerGroupUiState().totalCount)
        assertEquals(0, ServerGroupUiState(totalCount = 0).totalCount)
        assertEquals(7959, ServerGroupUiState(totalCount = 7959).totalCount)
    }
}
