package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * Conservative preflight for name-based proxy-chain and policy-group fallback dependencies.
 * The caller must supply every indexed profile and names referenced by subscriptions and routing.
 * Null for either input, an unreadable profile, or a missing target means the view is incomplete;
 * deletion must fail closed. The result contains only GUIDs, not profile contents or names. Other
 * dynamic or opaque configuration dependencies still require validation before enabling deletion.
 */
internal fun namedDeletionDependencies(
    targetGuids: Set<String>,
    indexedProfiles: Map<String, ProfileItem?>?,
    externalReferenceNames: Collection<String>?,
): Set<String>? {
    if (indexedProfiles == null || externalReferenceNames == null) return null
    if (targetGuids.any { it.isBlank() } || indexedProfiles.keys.any { it.isBlank() } ||
        indexedProfiles.values.any { it == null } || targetGuids.any { it !in indexedProfiles }
    ) return null
    if (targetGuids.isEmpty()) return emptySet()

    val namedTargets = targetGuids.mapNotNull { guid ->
        indexedProfiles.getValue(guid)?.remarks?.takeIf { it.isNotBlank() }?.let { guid to it }
    }
    if (namedTargets.isEmpty()) return emptySet()
    val protectedNames = HashSet<String>()
    externalReferenceNames.filter { it.isNotBlank() }.forEach { name ->
        protectedNames.add(name)
        protectedNames.add(name.trim())
    }
    indexedProfiles.forEach { (guid, profile) ->
        if (guid !in targetGuids && profile != null) {
            when (profile.configType) {
                EConfigType.PROXYCHAIN -> profile.proxyChainProfiles.orEmpty().split(',').forEach { name ->
                    // The core resolves raw names; include a trimmed spelling too as a conservative guard.
                    if (name.isNotBlank()) { protectedNames.add(name); protectedNames.add(name.trim()) }
                }
                EConfigType.POLICYGROUP -> profile.policyGroupFallbackTag
                    ?.takeIf { it.isNotBlank() && it !in AppConfig.BUILTIN_OUTBOUND_TAGS }
                    ?.let { protectedNames.add(it) }
                else -> Unit
            }
        }
    }
    return namedTargets.filterTo(linkedSetOf()) { (_, name) -> name in protectedNames || name.trim() in protectedNames }
        .mapTo(linkedSetOf()) { (guid, _) -> guid }
}
