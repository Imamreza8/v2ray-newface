package com.v2ray.ang.ui.main

/** Resolve both drag endpoints against the same current GUID snapshot, never an old list index. */
internal fun serverMovePositions(
    serverGuids: List<String>,
    rowGuids: List<String>,
    fromGuid: String,
    toGuid: String,
): Pair<Int, Int>? {
    if (serverGuids != rowGuids || fromGuid.isBlank() || toGuid.isBlank() || fromGuid == toGuid ||
        serverGuids.any { it.isBlank() } || serverGuids.toSet().size != serverGuids.size
    ) return null
    val from = serverGuids.indexOf(fromGuid)
    val to = serverGuids.indexOf(toGuid)
    return if (from >= 0 && to >= 0) Pair(from, to) else null
}
