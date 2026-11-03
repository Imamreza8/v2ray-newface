package com.v2ray.ang.handler

/**
 * A pure plan, not an MMKV operation. The input must contain every readable group index, including
 * orphaned indexes; otherwise callers must abort rather than risk removing a shared payload.
 */
internal class ProfileIndexRemoval(
    val changedIndexes: Map<String, List<String>>,
    val unreferencedGuids: Set<String>,
    val clearSelection: Boolean,
)

/**
 * Remove only requested references. Payloads are eligible for removal only if no resulting index
 * references them. Duplicate/blank IDs and missing targets invalidate the entire plan.
 */
internal fun planProfileIndexRemoval(
    allIndexes: Map<String, List<String>>,
    targetsByGroup: Map<String, Set<String>>,
    selectedGuid: String?,
): ProfileIndexRemoval? {
    if (targetsByGroup.isEmpty() || targetsByGroup.any { (group, targets) -> group.isBlank() || targets.isEmpty() }) return null
    if (allIndexes.any { (group, ids) ->
            group.isBlank() || ids.any { it.isBlank() } || ids.toSet().size != ids.size
        }
    ) return null
    val allTargets = targetsByGroup.values.flatten().toSet()
    if (allTargets.any { it.isBlank() }) return null
    if (targetsByGroup.any { (group, targets) -> allIndexes[group]?.containsAll(targets) != true }) return null

    val changed = linkedMapOf<String, List<String>>()
    val remainingReferences = HashSet<String>()
    allIndexes.forEach { (group, ids) ->
        val targets = targetsByGroup[group]
        val result = if (targets == null) ids else ids.filterNot { it in targets }
        if (targets != null) changed[group] = result
        remainingReferences.addAll(result)
    }
    val removed = allTargets.filterTo(linkedSetOf()) { it !in remainingReferences }
    return ProfileIndexRemoval(changed, removed, selectedGuid != null && selectedGuid in removed)
}
