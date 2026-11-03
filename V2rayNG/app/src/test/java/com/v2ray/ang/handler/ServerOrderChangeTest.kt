package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
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
}
