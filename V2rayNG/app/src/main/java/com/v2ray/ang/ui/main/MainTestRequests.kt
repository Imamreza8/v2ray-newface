package com.v2ray.ang.ui.main

import java.util.UUID

/** Main-thread request ownership; a late reply must not complete a newer test. */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private var current: String? = null
    var bulk: Bulk? = null
        private set
    val currentPending: Boolean get() = current != null
    val isTesting: Boolean get() = currentPending || bulk != null

    fun beginCurrent(): String = UUID.randomUUID().toString().also { current = it }

    fun completeCurrent(id: String): Boolean {
        if (id != current) return false
        current = null
        return true
    }

    fun invalidateCurrent() {
        current = null
    }

    fun beginBulk(groupId: String): Bulk = Bulk(UUID.randomUUID().toString(), groupId).also { bulk = it }

    fun completeBulk(id: String): Bulk? {
        val request = bulk?.takeIf { it.id == id } ?: return null
        bulk = null
        return request
    }

    fun cancelBulk() {
        bulk = null
    }
}

/** Pure selection from the already visible group/filter snapshot; it never expands the scope. */
internal fun scopedTestGuids(
    kind: ScopedTestKind,
    visible: List<com.v2ray.ang.dto.entities.ServersCache>,
    topMaxMillis: Long,
): List<String> = visible.asSequence().filter { server ->
    server.guid.isNotBlank() && when (kind) {
        ScopedTestKind.REAL, ScopedTestKind.TCP -> true
        ScopedTestKind.TOP -> matchesServerStatus(server.testDelayMillis, ServerStatusFilter.TOP, topMaxMillis)
        ScopedTestKind.FAILED -> matchesServerStatus(server.testDelayMillis, ServerStatusFilter.FAILED, topMaxMillis)
        ScopedTestKind.CURRENT -> false
    }
}.map { it.guid }.distinct().toList()

internal fun scopedListReady(
    state: ServerGroupUiState,
    keyword: String,
    filter: ServerStatusFilter,
): Boolean = state.totalCount != null && state.keyword == keyword && state.appliedFilter == filter

/** Reject the entire confirmed snapshot rather than silently changing its count. */
internal fun scopedSnapshotStillExists(guids: List<String>, existing: Set<String>, groupExists: Boolean): Boolean =
    groupExists && guids.isNotEmpty() && guids.all { it in existing }

/** Only expose a count for the currently rendered, matching group/filter/search snapshot. */
internal fun visibleScopedTestCount(
    state: ServerGroupUiState,
    keyword: String,
    filter: ServerStatusFilter,
    sort: ServerDisplaySort,
): Int? = if (scopedListReady(state, keyword, filter) && state.appliedSort == sort) state.servers.size else null
