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
}
