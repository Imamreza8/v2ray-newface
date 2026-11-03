package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DisplaySortTest {
    private fun server(id: String, name: String, delay: Long) =
        ServersCache(id, ProfileItem(configType = EConfigType.VMESS, remarks = name), delay)

    private fun row(server: ServersCache) = ServerRowUiModel(
        guid = server.guid, profile = server.profile, remarks = server.profile.remarks,
        statistics = "", typeDescription = "", testDelayMillis = server.testDelayMillis,
        testTime = 0L, subscriptionBadge = "",
    )

    @Test
    fun defaultKeepsOriginalListsAndNeverMutatesThem() {
        val servers = listOf(server("1", "Zulu", 0), server("2", "alpha", 100))
        val rows = servers.map(::row)
        val result = sortVisibleServerRows(servers, rows, ServerDisplaySort.DEFAULT)
        assertSame(servers, result.first)
        assertSame(rows, result.second)
        assertEquals(listOf("1", "2"), servers.map { it.guid })
        assertEquals(emptyList<ServersCache>(), sortVisibleServerRows(emptyList(), emptyList(), ServerDisplaySort.PING).first)
    }

    @Test
    fun pingSortShowsPositiveDelaysFirstAndKeepsTiesStable() {
        val servers = listOf(server("u", "u", 0), server("x", "x", -1), server("slow", "s", 200),
            server("a", "a", 100), server("b", "b", 100))
        val rows = servers.map(::row)
        val (sorted, sortedRows) = sortVisibleServerRows(servers, rows, ServerDisplaySort.PING)
        assertEquals(listOf("a", "b", "slow", "u", "x"), sorted.map { it.guid })
        assertEquals(sorted.map { it.guid }, sortedRows.map { it.guid })
        assertEquals(listOf("u", "x", "slow", "a", "b"), servers.map { it.guid })
    }

    @Test
    fun nameSortIsCaseInsensitiveAndKeepsTiesInStoredOrder() {
        val servers = listOf(server("1", "Zulu", 0), server("2", "alpha", -1),
            server("3", "Alpha", 9))
        assertEquals(listOf("2", "3", "1"), sortVisibleServerRows(servers, servers.map(::row), ServerDisplaySort.NAME).first.map { it.guid })
    }

    @Test
    fun mismatchedPairsAreRejected() {
        val a = server("a", "A", 100)
        val b = server("b", "B", 100)
        assertThrows(IllegalArgumentException::class.java) { sortVisibleServerRows(listOf(a), emptyList(), ServerDisplaySort.DEFAULT) }
        assertThrows(IllegalArgumentException::class.java) { sortVisibleServerRows(listOf(a), listOf(row(b)), ServerDisplaySort.PING) }
    }

    @Test
    fun liveResultsReorderVisiblePingWithoutChangingKeywordOrder() {
        val servers = listOf(server("a", "A", 100), server("b", "B", 0))
        val state = ServerGroupUiState(
            servers = servers, rows = servers.map(::row), keywordServers = servers,
            keywordRows = servers.map(::row), totalCount = 2, appliedSort = ServerDisplaySort.PING,
            counts = countServerStatuses(servers, 250), topMaxMillis = 250,
        )
        val updated = applyLiveServerResults(state, mapOf("b" to 50), 1000)
        assertEquals(listOf("b", "a"), updated.rows.map { it.guid })
        assertEquals(listOf("a", "b"), updated.keywordRows.map { it.guid })
        assertEquals(FilterCounts(all = 2, top = 2, alive = 2), updated.counts)
    }
}
