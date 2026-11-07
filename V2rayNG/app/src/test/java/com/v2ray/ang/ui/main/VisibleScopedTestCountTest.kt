package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VisibleScopedTestCountTest {
    private val row = ServersCache("a", ProfileItem(configType = EConfigType.VLESS))

    @Test
    fun onlyShowsCountForLoadedMatchingVisibleState() {
        val state = ServerGroupUiState(servers = listOf(row), totalCount = 4,
            keyword = "query", appliedFilter = ServerStatusFilter.ALIVE, appliedSort = ServerDisplaySort.PING)
        assertEquals(1, visibleScopedTestCount(state, "query", ServerStatusFilter.ALIVE, ServerDisplaySort.PING))
        assertEquals(0, visibleScopedTestCount(state.copy(servers = emptyList()), "query", ServerStatusFilter.ALIVE, ServerDisplaySort.PING))
        assertNull(visibleScopedTestCount(state.copy(totalCount = null), "query", ServerStatusFilter.ALIVE, ServerDisplaySort.PING))
        assertNull(visibleScopedTestCount(state, "other", ServerStatusFilter.ALIVE, ServerDisplaySort.PING))
        assertNull(visibleScopedTestCount(state, "query", ServerStatusFilter.FAILED, ServerDisplaySort.PING))
        assertNull(visibleScopedTestCount(state, "query", ServerStatusFilter.ALIVE, ServerDisplaySort.NAME))
        assertNull(visibleScopedTestCount(ServerGroupUiState(), "", ServerStatusFilter.ALL, ServerDisplaySort.DEFAULT))
    }
}
