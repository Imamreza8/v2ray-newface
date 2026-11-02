package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileHistoryCarryOverTest {

    private fun profile(remarks: String, server: String, port: String = "443", password: String = "pw") =
        ProfileItem.create(EConfigType.VMESS).apply {
            this.remarks = remarks
            this.server = server
            this.serverPort = port
            this.password = password
        }

    @Test
    fun renamedConfigKeepsHistory() {
        val result = ProfileReplacement.findHistoryCarryOver(
            oldProfiles = linkedMapOf("old1" to profile("Old name", "a.example")),
            newProfiles = linkedMapOf("new1" to profile("New name", "a.example")),
        )
        assertEquals(mapOf("new1" to "old1"), result)
    }

    @Test
    fun connectionChangeDropsHistory() {
        val result = ProfileReplacement.findHistoryCarryOver(
            oldProfiles = linkedMapOf(
                "port" to profile("a", "a.example", port = "443"),
                "pass" to profile("b", "b.example", password = "one"),
            ),
            newProfiles = linkedMapOf(
                "newPort" to profile("a", "a.example", port = "8443"),
                "newPass" to profile("b", "b.example", password = "two"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun eachOldProfileIsUsedOnce() {
        val result = ProfileReplacement.findHistoryCarryOver(
            oldProfiles = linkedMapOf("old" to profile("x", "dup.example")),
            newProfiles = linkedMapOf(
                "n1" to profile("x1", "dup.example"),
                "n2" to profile("x2", "dup.example"),
            ),
        )
        assertEquals(mapOf("n1" to "old"), result)
    }

    @Test
    fun sameGuidIsLeftAlone() {
        val result = ProfileReplacement.findHistoryCarryOver(
            oldProfiles = linkedMapOf("same" to profile("x", "a.example")),
            newProfiles = linkedMapOf("same" to profile("x", "a.example")),
        )
        assertTrue(result.isEmpty())
    }
}
