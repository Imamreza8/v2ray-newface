package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopedTestRequestsTest {
    private fun server(id: String, delay: Long) =
        ServersCache(id, ProfileItem(configType = EConfigType.VMESS, remarks = id), delay)

    @Test
    fun testChoicesNeverExpandBeyondTheVisibleSnapshot() {
        val visible = listOf(server("a", 0), server("b", 250), server("c", 251), server("d", -1))
        assertEquals(listOf("a", "b", "c", "d"), scopedTestGuids(ScopedTestKind.REAL, visible, 250))
        assertEquals(listOf("a", "b", "c", "d"), scopedTestGuids(ScopedTestKind.TCP, visible, 250))
        assertEquals(listOf("b"), scopedTestGuids(ScopedTestKind.TOP, visible, 250))
        assertEquals(listOf("d"), scopedTestGuids(ScopedTestKind.FAILED, visible, 250))
        assertEquals(emptyList<String>(), scopedTestGuids(ScopedTestKind.CURRENT, visible, 250))
        assertEquals(emptyList<String>(), scopedTestGuids(ScopedTestKind.TOP, visible, 0))
        assertEquals(emptyList<String>(), scopedTestGuids(ScopedTestKind.FAILED, emptyList(), 250))
    }

    @Test
    fun snapshotsHaveUniqueNonBlankIdsInVisibleOrder() {
        val visible = listOf(server("b", -1), server("", 5), server("a", -1), server("b", -1))
        assertEquals(listOf("b", "a"), scopedTestGuids(ScopedTestKind.FAILED, visible, 250))
    }

    @Test
    fun staleOrUnloadedPresentationCannotBeUsedForConfirmation() {
        val ready = ServerGroupUiState(totalCount = 3, keyword = "foo", appliedFilter = ServerStatusFilter.ALIVE)
        assertTrue(scopedListReady(ready, "foo", ServerStatusFilter.ALIVE))
        assertFalse(scopedListReady(ready, "bar", ServerStatusFilter.ALIVE))
        assertFalse(scopedListReady(ready, "foo", ServerStatusFilter.FAILED))
        assertFalse(scopedListReady(ready.copy(totalCount = null), "foo", ServerStatusFilter.ALIVE))
    }

    @Test
    fun confirmationRejectsEmptyMissingAndDeletedGroupsWithoutChangingTheSnapshot() {
        val snapshot = mutableListOf("a", "b")
        val selected = scopedTestGuids(ScopedTestKind.REAL, snapshot.map { server(it, 0) }, 250)
        snapshot.clear()
        assertEquals(listOf("a", "b"), selected)
        assertTrue(scopedSnapshotStillExists(selected, setOf("a", "b", "c"), true))
        assertFalse(scopedSnapshotStillExists(selected, setOf("a"), true))
        assertFalse(scopedSnapshotStillExists(selected, setOf("a", "b"), false))
        assertFalse(scopedSnapshotStillExists(emptyList(), setOf("a"), true))
    }
}
