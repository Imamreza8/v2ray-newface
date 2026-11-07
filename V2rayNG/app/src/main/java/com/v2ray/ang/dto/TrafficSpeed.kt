package com.v2ray.ang.dto

import java.io.Serializable

/** Proxy-only speed (bytes per second) measured by the daemon from the core's outbound stats. */
data class TrafficSpeed(
    val uplinkBytesPerSec: Long,
    val downlinkBytesPerSec: Long,
) : Serializable
