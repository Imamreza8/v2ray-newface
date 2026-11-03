package com.v2ray.ang.handler

/** A reorder may change positions only, never membership or duplicate a GUID. */
internal fun validServerOrderChange(current: List<String>, expected: List<String>, proposed: List<String>): Boolean {
    if (current.isEmpty() || current != expected || proposed.size != current.size) return false
    val currentIds = current.toSet()
    return currentIds.size == current.size && proposed.toSet() == currentIds && proposed.distinct().size == proposed.size
}
