package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import java.util.Collections

internal enum class BatchDeletionKind { VISIBLE, DUPLICATES, INVALID }

/**
 * A memory-only intent, not a storage transaction or permission to delete. The complete visible
 * snapshot is kept so a changed duplicate keeper, edited target, or changed failed result can
 * invalidate the entire confirmation. Profile contents must never be logged or serialized here.
 */
internal class BatchDeletionPlan private constructor(
    val groupId: String,
    val kind: BatchDeletionKind,
    val targetGuids: List<String>,
    private val selectedGuid: String?,
    private val visible: List<Snapshot>,
) {
    private class Snapshot(val guid: String, val profile: ProfileItem, val delayMillis: Long)

    /** Storage must also verify shared references and provide an atomic commit or rollback. */
    fun isStillValid(
        groupExists: Boolean,
        selectedNow: String?,
        groupMembers: Set<String>,
        readProfile: (String) -> ProfileItem?,
        readDelay: (String) -> Long?,
    ): Boolean {
        if (!groupExists || targetGuids.isEmpty() || selectedGuid != selectedNow) return false
        return visible.all { snapshot ->
            snapshot.guid in groupMembers && readProfile(snapshot.guid) == snapshot.profile &&
                (kind != BatchDeletionKind.INVALID || readDelay(snapshot.guid) == snapshot.delayMillis)
        }
    }

    companion object {
        fun fromVisible(
            groupId: String,
            kind: BatchDeletionKind,
            selectedGuid: String?,
            servers: List<ServersCache>,
        ): BatchDeletionPlan? {
            val ids = servers.map { it.guid }
            if (ids.any { it.isBlank() } || ids.toSet().size != ids.size) return null
            if (groupId.isNotEmpty() && servers.any {
                    it.profile.subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID } != groupId
                }
            ) return null
            val seen = HashSet<ProfileItem>()
            val targets = ArrayList<String>()
            val snapshots = servers.map { server ->
                // ProfileItem is mutable; keep independent copies throughout confirmation.
                val profile = server.profile.copy()
                val target = when (kind) {
                    BatchDeletionKind.VISIBLE -> true
                    BatchDeletionKind.INVALID -> server.testDelayMillis < 0L
                    BatchDeletionKind.DUPLICATES -> !profile.configType.isComplexType() &&
                        !seen.add(profile.duplicateIdentity())
                }
                if (target) targets += server.guid
                Snapshot(server.guid, profile, server.testDelayMillis)
            }
            return BatchDeletionPlan(groupId, kind, Collections.unmodifiableList(ArrayList(targets)), selectedGuid, snapshots)
        }
    }
}
