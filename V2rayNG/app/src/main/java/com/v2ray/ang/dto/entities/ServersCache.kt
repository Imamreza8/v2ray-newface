package com.v2ray.ang.dto.entities

data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    val testDelayMillis: Long = 0L,
    /** When [testDelayMillis] was measured; 0 = unknown (result stored by an older version). */
    val testTime: Long = 0L,
    /** Last recorded exit country; not a claim about the currently active connection. */
    val exitCountry: String? = null,
)
