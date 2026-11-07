package com.v2ray.ang.dto.entities

/**
 * Per-server test data kept in MMKV (JSON). All new fields have defaults, so entries written by
 * older versions are still readable.
 *
 * - [testDelayMillis]: latest result of any test (real ping or TCP ping); 0 = never tested, <0 = failed.
 * - [realDelayMillis]: latest *real ping* only (same convention). Top-config ranking uses this value,
 *   because TCP-ping times are not comparable with real-ping times.
 * - [testCount]/[successCount]: recent real-ping history (halved when it grows, so old results fade).
 */
data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var realDelayMillis: Long = 0L,
    var testCount: Int = 0,
    var successCount: Int = 0,
    var lastTestTime: Long = 0L,
    var lastSuccessTime: Long = 0L,
    /** Time of the latest test of any kind (real ping or TCP ping); the list uses it to mark old results. */
    var testTime: Long = 0L,
    /** Running mean / sum of squared deviations (Welford) of successful real-ping delays, for jitter. */
    var delaySamples: Int = 0,
    var delayMean: Double = 0.0,
    var delayM2: Double = 0.0,
    /** ISO alpha-2 country of the exit IP, learned when the user ran a connection test on this config. */
    var exitCountry: String? = null,
)
