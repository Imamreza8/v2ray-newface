package com.v2ray.ang.handler

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ShareLinkTest {
    @Test
    fun unsupportedTypesNeverSerializeOrProduceAPseudoLink() {
        listOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN,
            EConfigType.HTTP, EConfigType.HYSTERIA).forEach { type ->
            var serialized = false
            assertEquals("", formatShareLink(type) { serialized = true; "data" })
            assertFalse(serialized)
        }
    }

    @Test
    fun supportedTypeRequiresAnEncodedBody() {
        assertEquals("", formatShareLink(EConfigType.VLESS) { "" })
        assertEquals("", formatShareLink(EConfigType.VLESS) { " " })
        listOf(EConfigType.VMESS, EConfigType.SHADOWSOCKS, EConfigType.SOCKS,
            EConfigType.VLESS, EConfigType.TROJAN, EConfigType.WIREGUARD,
            EConfigType.HYSTERIA2).forEach { type ->
            assertEquals(type.protocolScheme + "body", formatShareLink(type) { "body" })
        }
    }
    @Test
    fun copyCountsActualLinksAndFailsClosedWhenClipboardFails() {
        var copied = ""
        val getLink: (String) -> String = { if (it == "skip") "" else "vless://$it" }
        assertEquals(2, copyShareLinks(listOf("a", "skip", "b"), getLink) { copied = it; true })
        assertEquals("vless://a\nvless://b\n", copied)
        assertEquals(-1, copyShareLinks(listOf("a"), getLink) { false })
        var called = false
        assertEquals(0, copyShareLinks(listOf("skip"), getLink) { called = true; true })
        assertFalse(called)
    }
}
