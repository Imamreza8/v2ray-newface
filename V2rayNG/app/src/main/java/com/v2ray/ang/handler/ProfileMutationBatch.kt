package com.v2ray.ang.handler

/**
 * A transient before/after batch. Values may contain secrets: never persist or log this object.
 * The caller must hold the same cross-process lock used by *all* relevant readers and writers.
 * This helper handles reported in-process failures only; it is not a crash-recovery journal.
 */
internal class ProfileRecordMutation(
    val store: ProfileRecordStore,
    val key: String,
    val before: String?,
    val after: String?,
)

internal enum class ProfileRecordStore { INDEX, PROFILE, RAW, AFFILIATION, SELECTION }

internal interface ProfileRecordAccess {
    fun read(store: ProfileRecordStore, key: String): String?
    /** Null deletes a record; implementations must throw if the write cannot be verified. */
    fun write(store: ProfileRecordStore, key: String, value: String?)
}

internal enum class ProfileBatchOutcome { APPLIED, STALE, EMPTY }

/** [rollbackComplete] is false if recovery itself failed; callers must not claim success. */
internal class ProfileBatchWriteException(
    val rollbackComplete: Boolean,
    cause: Throwable,
) : IllegalStateException("Profile batch failed", cause)

/**
 * Checks every precondition before the first write; on any reported write failure, attempts to
 * restore *all attempted records*, including the failed one, in reverse order. This does not make
 * MMKV stores atomic across process death, nor protect against writers which ignore the lock.
 */
internal fun applyProfileMutationBatch(
    access: ProfileRecordAccess,
    changes: List<ProfileRecordMutation>,
): ProfileBatchOutcome {
    if (changes.isEmpty()) return ProfileBatchOutcome.EMPTY
    require(changes.none { it.key.isBlank() })
    require(changes.map { it.store to it.key }.toSet().size == changes.size)
    if (changes.any { access.read(it.store, it.key) != it.before }) return ProfileBatchOutcome.STALE

    val attempted = ArrayList<ProfileRecordMutation>(changes.size)
    try {
        for (change in changes) {
            if (change.before == change.after) continue
            attempted.add(change) // A failing write may already have changed the record.
            access.write(change.store, change.key, change.after)
        }
    } catch (failure: Throwable) {
        val rollbackErrors = ArrayList<Throwable>()
        for (change in attempted.asReversed()) {
            try {
                access.write(change.store, change.key, change.before)
            } catch (error: Throwable) {
                rollbackErrors.add(error)
            }
        }
        val result = ProfileBatchWriteException(rollbackErrors.isEmpty(), failure)
        rollbackErrors.forEach(result::addSuppressed)
        throw result
    }
    return ProfileBatchOutcome.APPLIED
}
