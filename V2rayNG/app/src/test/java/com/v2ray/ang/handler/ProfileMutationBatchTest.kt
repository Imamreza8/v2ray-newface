package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileMutationBatchTest {

    private class FakeAccess(initial: Map<Pair<ProfileRecordStore, String>, String>) : ProfileRecordAccess {
        val values = initial.toMutableMap()
        var writes = 0
        var reads = 0
        var failBefore = emptySet<Int>()
        var failAfter = emptySet<Int>()
        var failRead = false

        override fun read(store: ProfileRecordStore, key: String): String? {
            reads++
            if (failRead) throw IllegalStateException("read failure")
            return values[store to key]
        }

        override fun write(store: ProfileRecordStore, key: String, value: String?) {
            writes++
            if (writes in failBefore) throw IllegalStateException("write failure")
            if (value == null) values.remove(store to key) else values[store to key] = value
            if (writes in failAfter) throw IllegalStateException("write failure after mutation")
        }
    }

    private val initial = mapOf(
        (ProfileRecordStore.INDEX to "group") to "index",
        (ProfileRecordStore.PROFILE to "a") to "profile",
        (ProfileRecordStore.RAW to "a") to "raw",
        (ProfileRecordStore.AFFILIATION to "a") to "affiliation",
    )
    private fun changes() = listOf(
        ProfileRecordMutation(ProfileRecordStore.INDEX, "group", "index", "new-index"),
        ProfileRecordMutation(ProfileRecordStore.PROFILE, "a", "profile", null),
        ProfileRecordMutation(ProfileRecordStore.RAW, "a", "raw", null),
        ProfileRecordMutation(ProfileRecordStore.AFFILIATION, "a", "affiliation", null),
    )

    @Test
    fun commitsAllAndTreatsEmptyAndNoOpBatchesExplicitly() {
        val access = FakeAccess(initial)
        assertEquals(ProfileBatchOutcome.EMPTY, applyProfileMutationBatch(access, emptyList()))
        assertEquals(0, access.reads)
        assertEquals(ProfileBatchOutcome.APPLIED, applyProfileMutationBatch(access, changes()))
        assertEquals(mapOf((ProfileRecordStore.INDEX to "group") to "new-index"), access.values)
        val noOp = listOf(ProfileRecordMutation(ProfileRecordStore.INDEX, "group", "new-index", "new-index"))
        assertEquals(ProfileBatchOutcome.APPLIED, applyProfileMutationBatch(access, noOp))
        assertEquals(4, access.writes)
    }

    @Test
    fun rejectsStaleOrInvalidInputsBeforeWriting() {
        val access = FakeAccess(initial)
        access.values[ProfileRecordStore.RAW to "a"] = "changed"
        assertEquals(ProfileBatchOutcome.STALE, applyProfileMutationBatch(access, changes()))
        assertEquals(0, access.writes)
        assertThrows(IllegalArgumentException::class.java) {
            applyProfileMutationBatch(access, listOf(changes()[0], changes()[0]))
        }
        assertThrows(IllegalArgumentException::class.java) {
            applyProfileMutationBatch(access, listOf(ProfileRecordMutation(ProfileRecordStore.RAW, " ", null, "x")))
        }
        access.failRead = true
        assertThrows(IllegalStateException::class.java) { applyProfileMutationBatch(access, changes()) }
        assertEquals(0, access.writes)
    }

    @Test
    fun restoresEveryAttemptedRecordWhetherFailureHappensBeforeOrAfterMutation() {
        for (failedWrite in 1..changes().size) {
            for (failAfterMutation in listOf(false, true)) {
                val access = FakeAccess(initial)
                if (failAfterMutation) access.failAfter = setOf(failedWrite) else access.failBefore = setOf(failedWrite)
                val failure = assertThrows(ProfileBatchWriteException::class.java) {
                    applyProfileMutationBatch(access, changes())
                }
                assertTrue(failure.rollbackComplete)
                assertEquals(initial, access.values, "write $failedWrite, after=$failAfterMutation")
            }
        }
    }

    @Test
    fun reportsIncompleteRollbackAndStillAttemptsOtherRestores() {
        val access = FakeAccess(initial)
        access.failAfter = setOf(2)
        access.failBefore = setOf(3) // first rollback fails, but the index must still be restored
        val failure = assertThrows(ProfileBatchWriteException::class.java) {
            applyProfileMutationBatch(access, changes())
        }
        assertFalse(failure.rollbackComplete)
        assertEquals(1, failure.suppressed.size)
        assertEquals("index", access.values[ProfileRecordStore.INDEX to "group"])
        assertFalse(access.values.containsKey(ProfileRecordStore.PROFILE to "a"))
        assertEquals(4, access.writes)
    }
}
