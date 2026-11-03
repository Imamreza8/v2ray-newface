package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ManualServerOrderTest {
    @Test
    fun resolvesEndpointsAgainstCurrentIds() {
        val ids = listOf("a", "b", "c")
        assertEquals(0 to 2, serverMovePositions(ids, ids, "a", "c"))
        assertEquals(2 to 0, serverMovePositions(ids, ids, "c", "a"))
        val reordered = listOf("c", "a", "b")
        assertEquals(1 to 0, serverMovePositions(reordered, reordered, "a", "c"))
    }

    @Test
    fun rejectsMissingDuplicateBlankSameAndMismatchedRows() {
        val ids = listOf("a", "b")
        assertNull(serverMovePositions(emptyList(), emptyList(), "a", "b"))
        assertNull(serverMovePositions(ids, ids, "a", "missing"))
        assertNull(serverMovePositions(ids, ids, "missing", "a"))
        assertNull(serverMovePositions(ids, ids, "a", "a"))
        assertNull(serverMovePositions(ids, ids, "", "a"))
        assertNull(serverMovePositions(ids, listOf("b", "a"), "a", "b"))
        assertNull(serverMovePositions(listOf("a", "a", "b"), listOf("a", "a", "b"), "a", "b"))
        assertNull(serverMovePositions(listOf("", "a", "b"), listOf("", "a", "b"), "a", "b"))
    }
}
