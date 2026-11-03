package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ServersCache

/** Full count is used when loading a group or changing the search; batches use only transitions. */
internal fun countServerStatuses(servers: List<ServersCache>, topMaxMillis: Long): FilterCounts {
    var top = 0
    var alive = 0
    var untested = 0
    var failed = 0
    for (server in servers) {
        val delay = server.testDelayMillis
        when {
            delay > 0 -> { alive++; if (delay <= topMaxMillis) top++ }
            delay == 0L -> untested++
            else -> failed++
        }
    }
    return FilterCounts(all = servers.size, top = top, alive = alive, untested = untested, failed = failed)
}

/** Update counters from one known before/after result without recounting unaffected entries. */
internal fun updateStatusCounts(counts: FilterCounts, before: Long?, after: Long?, topMaxMillis: Long): FilterCounts {
    fun member(value: Long?, filter: ServerStatusFilter): Int =
        if (value != null && matchesServerStatus(value, filter, topMaxMillis)) 1 else 0
    fun delta(filter: ServerStatusFilter) = member(after, filter) - member(before, filter)
    return counts.copy(
        top = counts.top + delta(ServerStatusFilter.TOP),
        alive = counts.alive + delta(ServerStatusFilter.ALIVE),
        untested = counts.untested + delta(ServerStatusFilter.UNTESTED),
        failed = counts.failed + delta(ServerStatusFilter.FAILED),
    )
}

/** Apply one batch to keyword-matched items. Only changed IDs recalculate status membership and counters. */
internal fun applyLiveServerResults(
    state: ServerGroupUiState,
    updates: Map<String, Long>,
    nowMillis: Long,
): ServerGroupUiState {
    if (updates.isEmpty() || state.keywordServers.isEmpty()) return state
    require(state.keywordServers.size == state.keywordRows.size)
    val previouslyVisible = state.servers.mapTo(HashSet()) { it.guid }
    val keywordServers = ArrayList<ServersCache>(state.keywordServers.size)
    val keywordRows = ArrayList<ServerRowUiModel>(state.keywordRows.size)
    val visibleServers = ArrayList<ServersCache>(state.servers.size)
    val visibleRows = ArrayList<ServerRowUiModel>(state.rows.size)
    var counts = state.counts
    state.keywordServers.forEachIndexed { index, old ->
        val oldRow = state.keywordRows[index]
        require(old.guid == oldRow.guid)
        val delay = updates[old.guid]
        val server = if (delay == null) old else old.copy(testDelayMillis = delay, testTime = nowMillis)
        val row = if (delay == null) oldRow else oldRow.copy(testDelayMillis = delay, testTime = nowMillis)
        if (delay != null) counts = updateStatusCounts(counts, old.testDelayMillis, delay, state.topMaxMillis)
        keywordServers.add(server)
        keywordRows.add(row)
        val visible = if (delay == null) old.guid in previouslyVisible
        else matchesServerStatus(delay, state.appliedFilter, state.topMaxMillis)
        if (visible) { visibleServers.add(server); visibleRows.add(row) }
    }
    return state.copy(
        servers = visibleServers,
        rows = visibleRows,
        keywordServers = keywordServers,
        keywordRows = keywordRows,
        counts = counts,
    )
}
