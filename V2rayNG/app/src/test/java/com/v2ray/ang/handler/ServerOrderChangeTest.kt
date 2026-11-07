package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerOrderChangeTest {
    @Test
    fun acceptsOnlyAnExactPermutationOfAnUnchangedNonemptySnapshot() {
        val expected = listOf("a", "b", "c")
        assertTrue(validServerOrderChange(expected, expected, listOf("c", "b", "a")))
        assertTrue(validServerOrderChange(expected, expected, expected))
        assertFalse(validServerOrderChange(emptyList(), emptyList(), emptyList()))
        assertFalse(validServerOrderChange(listOf("a", "c", "b"), expected, expected))
        assertFalse(validServerOrderChange(expected, expected, listOf("a", "b")))
        assertFalse(validServerOrderChange(expected, expected, listOf("a", "b", "d")))
        assertFalse(validServerOrderChange(expected, expected, listOf("a", "a", "c")))
        assertFalse(validServerOrderChange(listOf("a", "a"), listOf("a", "a"), listOf("a", "a")))
    }
    @Test
    fun automaticSortIsStableAndPublishesOnlyAgainstTheSnapshot() {
        val before = listOf("u", "slow", "a", "b", "failed")
        val delays = mapOf("u" to 0L, "slow" to 400L, "a" to 100L, "b" to 100L, "failed" to -1L)
        var expectedRead: List<String>? = null
        var sorted: List<String>? = null
        val outcome = saveDelaySortedOrder(before, delays::get) { expected, proposed ->
            expectedRead = expected
            sorted = proposed
            true
        }
        assertEquals(SortedOrderOutcome.SAVED, outcome)
        assertEquals(before, expectedRead)
        assertEquals(listOf("a", "b", "slow", "u", "failed"), sorted)
        assertEquals(listOf("u", "slow", "a", "b", "failed"), before)
        assertEquals(SortedOrderOutcome.STALE, saveDelaySortedOrder(before, delays::get) { _, _ -> false })
    }

    @Test
    fun automaticSortSkipsEmptyAndPropagatesReadAndWriteFailures() {
        var called = false
        assertEquals(SortedOrderOutcome.EMPTY, saveDelaySortedOrder(emptyList(), { called = true; 0L }) { _, _ -> called = true; true })
        assertFalse(called)
        assertThrows(IllegalStateException::class.java) {
            saveDelaySortedOrder(listOf("a"), { throw IllegalStateException() }) { _, _ -> true }
        }
        assertThrows(IllegalStateException::class.java) {
            saveDelaySortedOrder(listOf("a"), { null }) { _, _ -> throw IllegalStateException() }
        }
    }
}
