package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileRemovalDependenciesTest {
    private fun profile(type: EConfigType, name: String = "") = ProfileItem(configType = type, remarks = name)

    @Test
    fun protectsAllSameNameTargetsReferencedBySurvivingChainOrFallback() {
        val chain = profile(EConfigType.PROXYCHAIN).apply { proxyChainProfiles = "other, node ,missing" }
        val group = profile(EConfigType.POLICYGROUP).apply { policyGroupFallbackTag = "backup" }
        val profiles = mapOf("a" to profile(EConfigType.VLESS, "node"),
            "b" to profile(EConfigType.VMESS, "node"), "c" to profile(EConfigType.SOCKS, "backup"),
            "d" to profile(EConfigType.TROJAN, "unrelated"), "chain" to chain, "group" to group)
        assertEquals(setOf("a", "b", "c"), namedDeletionDependencies(setOf("a", "b", "c", "d"), profiles, emptyList()))
        assertEquals(emptySet<String>(), namedDeletionDependencies(setOf("a", "b", "chain"), profiles, emptyList()))
    }

    @Test
    fun rejectsIncompleteInputsAndIgnoresBuiltinsAndEmptyNames() {
        val builtin = AppConfig.BUILTIN_OUTBOUND_TAGS.first()
        val profiles = mapOf("a" to profile(EConfigType.VLESS, builtin), "blank" to profile(EConfigType.SOCKS),
            "group" to profile(EConfigType.POLICYGROUP).apply { policyGroupFallbackTag = builtin })
        assertEquals(emptySet<String>(), namedDeletionDependencies(setOf("a", "blank"), profiles, emptyList()))
        assertEquals(emptySet<String>(), namedDeletionDependencies(emptySet(), profiles, emptyList()))
        assertNull(namedDeletionDependencies(setOf("missing"), profiles, emptyList()))
        assertNull(namedDeletionDependencies(setOf(""), profiles, emptyList()))
        assertNull(namedDeletionDependencies(setOf("a"), profiles + ("unreadable" to null), emptyList()))
        assertNull(namedDeletionDependencies(setOf("a"), profiles + ("" to profile(EConfigType.VLESS)), emptyList()))
        assertNull(namedDeletionDependencies(setOf("a"), null, emptyList()))
        assertNull(namedDeletionDependencies(setOf("a"), profiles, null))
        assertEquals(setOf("a"), namedDeletionDependencies(setOf("a"), profiles, listOf(builtin)))
    }
}
