package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileIndexRemovalTest {
    @Test
    fun removesOnlyConfirmedReferencesAndKeepsSharedPayloadsAndSelection() {
        val indexes = mapOf("g1" to listOf("a", "shared", "b"), "g2" to listOf("shared", "c"))
        val result = planProfileIndexRemoval(indexes, mapOf("g1" to setOf("a", "shared")), "shared")!!
        assertEquals(mapOf("g1" to listOf("b")), result.changedIndexes)
        assertEquals(setOf("a"), result.unreferencedGuids)
        assertFalse(result.clearSelection)
        assertEquals(listOf("a", "shared", "b"), indexes.getValue("g1"))
    }

    @Test
    fun removesUnreferencedPayloadAndClearsSelectedTargetAcrossGroups() {
        val indexes = mapOf("g1" to listOf("a", "b"), "g2" to listOf("c", "d"))
        val result = planProfileIndexRemoval(indexes, mapOf("g1" to setOf("a"), "g2" to setOf("c")), "c")!!
        assertEquals(mapOf("g1" to listOf("b"), "g2" to listOf("d")), result.changedIndexes)
        assertEquals(setOf("a", "c"), result.unreferencedGuids)
        assertTrue(result.clearSelection)
        assertFalse(planProfileIndexRemoval(indexes, mapOf("g1" to setOf("a")), null)!!.clearSelection)
    }

    @Test
    fun failsClosedWhenIndexesOrTargetsAreAmbiguousOrMissing() {
        val indexes = mapOf("g" to listOf("a", "b"), "other" to listOf("c"))
        assertNull(planProfileIndexRemoval(indexes, emptyMap(), null))
        assertNull(planProfileIndexRemoval(indexes, mapOf("g" to emptySet()), null))
        assertNull(planProfileIndexRemoval(indexes, mapOf("" to setOf("a")), null))
        assertNull(planProfileIndexRemoval(indexes, mapOf("missing" to setOf("a")), null))
        assertNull(planProfileIndexRemoval(indexes, mapOf("g" to setOf("z")), null))
        assertNull(planProfileIndexRemoval(mapOf("g" to listOf("a", "a")), mapOf("g" to setOf("a")), null))
        assertNull(planProfileIndexRemoval(mapOf("g" to listOf("a"), "bad" to listOf("")), mapOf("g" to setOf("a")), null))
        val allReferences = planProfileIndexRemoval(mapOf("g" to listOf("a"), "other" to listOf("a")),
            mapOf("g" to setOf("a"), "other" to setOf("a")), "a")!!
        assertEquals(setOf("a"), allReferences.unreferencedGuids)
        assertTrue(allReferences.clearSelection)
    }
}
