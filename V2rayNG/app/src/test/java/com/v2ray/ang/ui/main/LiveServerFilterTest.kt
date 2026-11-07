package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LiveServerFilterTest {
    private fun server(id: String, delay: Long) =
        ServersCache(id, ProfileItem(configType = EConfigType.VMESS, remarks = id), delay, 1L)

    private fun row(server: ServersCache) = ServerRowUiModel(
        guid = server.guid, profile = server.profile, remarks = server.profile.remarks,
        statistics = "", typeDescription = "", testDelayMillis = server.testDelayMillis,
        testTime = server.testTime, subscriptionBadge = "",
    )

    private fun state(servers: List<ServersCache>, filter: ServerStatusFilter): ServerGroupUiState {
        val rows = servers.map(::row)
        val visibleIndices = servers.indices.filter { matchesServerStatus(servers[it].testDelayMillis, filter, 250) }
        return ServerGroupUiState(
            servers = visibleIndices.map { servers[it] }, rows = visibleIndices.map { rows[it] },
            keywordServers = servers, keywordRows = rows, totalCount = servers.size,
            appliedFilter = filter, topMaxMillis = 250, counts = countServerStatuses(servers, 250),
        )
    }

    @Test
    fun emptyAndUnknownUpdatesLeaveCountsAndRowsUnchanged() {
        val empty = state(emptyList(), ServerStatusFilter.UNTESTED)
        assertEquals(FilterCounts(), empty.counts)
        assertSame(empty, applyLiveServerResults(empty, mapOf("missing" to -1L), 10))
        val one = state(listOf(server("a", 0)), ServerStatusFilter.UNTESTED)
        assertSame(one, applyLiveServerResults(one, emptyMap(), 10))
        assertEquals(one, applyLiveServerResults(one, mapOf("missing" to 120L), 10))
    }

    @Test
    fun failedResultLeavesUntestedFilterAndUpdatesCounts() {
        val original = state(listOf(server("a", 0), server("b", 0)), ServerStatusFilter.UNTESTED)
        val updated = applyLiveServerResults(original, mapOf("a" to -1L), 100)
        assertEquals(listOf("b"), updated.rows.map { it.guid })
        assertEquals(FilterCounts(all = 2, untested = 1, failed = 1), updated.counts)
        assertEquals(100L, updated.keywordRows.first().testTime)
        assertEquals(listOf("a", "b"), updated.keywordServers.map { it.guid })
    }

    @Test
    fun previouslyHiddenServerEntersActiveFilterInOriginalOrder() {
        val original = state(listOf(server("a", 0), server("b", -1), server("c", -1)), ServerStatusFilter.ALIVE)
        val updated = applyLiveServerResults(original, mapOf("c" to 300L, "a" to 100L), 100)
        assertEquals(listOf("a", "c"), updated.rows.map { it.guid })
        assertEquals(FilterCounts(all = 3, top = 1, alive = 2, failed = 1), updated.counts)
        assertEquals(updated.counts, countServerStatuses(updated.keywordServers, 250))
    }

    @Test
    fun repeatedAndUnchangedResultsDoNotDoubleCount() {
        val original = state(listOf(server("a", 100), server("b", -1)), ServerStatusFilter.ALL)
        val once = applyLiveServerResults(original, mapOf("a" to 100L, "b" to 200L), 100)
        val twice = applyLiveServerResults(once, mapOf("b" to 200L), 200)
        assertEquals(FilterCounts(all = 2, top = 2, alive = 2), twice.counts)
        assertEquals(listOf("a", "b"), twice.rows.map { it.guid })
    }
}
