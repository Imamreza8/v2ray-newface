package com.v2ray.ang.handler

/** A reorder may change positions only, never membership or duplicate a GUID. */
internal fun validServerOrderChange(current: List<String>, expected: List<String>, proposed: List<String>): Boolean {
    if (current.isEmpty() || current != expected || proposed.size != current.size) return false
    val currentIds = current.toSet()
    return currentIds.size == current.size && proposed.toSet() == currentIds && proposed.distinct().size == proposed.size
}

internal enum class SortedOrderOutcome { EMPTY, SAVED, STALE }

/** Compute from a fixed index and ask the storage owner to compare it before publishing. */
internal fun saveDelaySortedOrder(
    expected: List<String>,
    readDelay: (String) -> Long?,
    compareAndSave: (List<String>, List<String>) -> Boolean,
): SortedOrderOutcome {
    if (expected.isEmpty()) return SortedOrderOutcome.EMPTY
    val sorted = expected.map { guid ->
        val delay = readDelay(guid) ?: 0L
        guid to if (delay <= 0L) Long.MAX_VALUE else delay
    }.sortedBy { it.second }.map { it.first }
    return if (compareAndSave(expected, sorted)) SortedOrderOutcome.SAVED else SortedOrderOutcome.STALE
}
