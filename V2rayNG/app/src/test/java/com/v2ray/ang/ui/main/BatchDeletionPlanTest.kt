package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatchDeletionPlanTest {
    private fun row(guid: String, group: String = "g", host: String = "host", delay: Long = 0L,
                    type: EConfigType = EConfigType.VLESS) =
        ServersCache(guid, ProfileItem(configType = type, subscriptionId = group, server = host, remarks = guid), delay)

    @Test
    fun freezesExactVisibleTargetsForEachKind() {
        val rows = listOf(row("first", delay = 1), row("duplicate", delay = -1),
            row("other", host = "other", delay = -3), row("untested"), row("custom", type = EConfigType.CUSTOM))
        assertEquals(rows.map { it.guid }, BatchDeletionPlan.fromVisible("g", BatchDeletionKind.VISIBLE, null, rows)!!.targetGuids)
        assertEquals(listOf("duplicate", "untested"), BatchDeletionPlan.fromVisible("g", BatchDeletionKind.DUPLICATES, null, rows)!!.targetGuids)
        assertEquals(listOf("duplicate", "other"), BatchDeletionPlan.fromVisible("g", BatchDeletionKind.INVALID, null, rows)!!.targetGuids)
        assertTrue(BatchDeletionPlan.fromVisible("g", BatchDeletionKind.VISIBLE, null, emptyList())!!.targetGuids.isEmpty())
    }

    @Test
    fun rejectsAmbiguousIdsAndWrongGroupsButAcceptsDefaultAndAllGroups() {
        assertNull(BatchDeletionPlan.fromVisible("g", BatchDeletionKind.VISIBLE, null, listOf(row(""))))
        assertNull(BatchDeletionPlan.fromVisible("g", BatchDeletionKind.VISIBLE, null, listOf(row("a"), row("a"))))
        assertNull(BatchDeletionPlan.fromVisible("g", BatchDeletionKind.VISIBLE, null, listOf(row("a", "other"))))
        assertNotNull(BatchDeletionPlan.fromVisible(AppConfig.DEFAULT_SUBSCRIPTION_ID, BatchDeletionKind.VISIBLE, null, listOf(row("a", ""))))
        assertNotNull(BatchDeletionPlan.fromVisible("", BatchDeletionKind.VISIBLE, null, listOf(row("a", "g"), row("b", "other"))))
    }

    @Test
    fun detectsChangesToTargetsDuplicateKeepersSelectionAndGroup() {
        val rows = listOf(row("a"), row("b"))
        val plan = BatchDeletionPlan.fromVisible("g", BatchDeletionKind.DUPLICATES, "a", rows)!!
        val profiles = rows.associate { it.guid to it.profile.copy() }.toMutableMap()
        val members = profiles.keys.toSet()
        fun valid(groupExists: Boolean = true, selected: String? = "a", present: Set<String> = members) =
            plan.isStillValid(groupExists, selected, present, profiles::get) { 0 }
        assertTrue(valid())
        assertFalse(valid(groupExists = false))
        assertFalse(valid(selected = "b"))
        assertFalse(valid(present = setOf("b"))) // The keeper disappeared.
        assertFalse(valid(present = setOf("a"))) // The target disappeared.
        profiles["a"] = profiles.getValue("a").copy(server = "different")
        assertFalse(valid())
    }

    @Test
    fun copiesMutableProfilesAndRejectsNewResultsForInvalidDeletion() {
        val row = row("a", delay = -1)
        val plan = BatchDeletionPlan.fromVisible("g", BatchDeletionKind.INVALID, null, listOf(row))!!
        val original = row.profile.copy()
        fun valid(profile: ProfileItem?, delay: Long?) = plan.isStillValid(true, null, setOf("a"), { profile }, { delay })
        assertTrue(valid(original, -1L))
        assertFalse(valid(original, 1L))
        assertFalse(valid(original, null))
        assertFalse(valid(null, -1L))
        row.profile.remarks = "edited"
        assertFalse(valid(row.profile, -1L))
        assertTrue(valid(original, -1L))
        val empty = BatchDeletionPlan.fromVisible("g", BatchDeletionKind.INVALID, null, emptyList())!!
        assertFalse(empty.isStillValid(true, null, emptySet(), { null }, { null }))
    }
}
