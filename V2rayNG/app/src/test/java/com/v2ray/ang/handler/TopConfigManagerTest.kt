package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.google.gson.Gson

class TopConfigManagerTest {

    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun info(real: Long, tests: Int, ok: Int, ageHours: Long = 0) = ServerAffiliationInfo(
        testDelayMillis = real,
        realDelayMillis = real,
        testCount = tests,
        successCount = ok,
        lastTestTime = now - ageHours * hour,
        lastSuccessTime = now - ageHours * hour,
    )

    @Test
    fun rank_dropsFailedUntestedAndOverThreshold() {
        val ranked = TopConfigManager.rank(
            mapOf(
                "ok" to info(120, 3, 3),
                "failed" to info(-1, 3, 0),
                "never" to ServerAffiliationInfo(),
                "slow" to info(900, 3, 3),
            ),
            maxDelayMillis = 250,
            now = now,
        )
        assertEquals(listOf("ok"), ranked)
    }

    @Test
    fun rank_tcpOnlyResultsDoNotQualify() {
        // testDelayMillis was set by a TCP ping, realDelayMillis was never set.
        val tcpOnly = ServerAffiliationInfo(testDelayMillis = 40)
        assertTrue(TopConfigManager.rank(mapOf("tcp" to tcpOnly), 250, now).isEmpty())
    }

    @Test
    fun rank_stableServerBeatsSlightlyFasterFlakyOne() {
        val ranked = TopConfigManager.rank(
            mapOf(
                "flaky" to info(100, 10, 4),   // 40 % success
                "stable" to info(130, 10, 10), // 100 % success
            ),
            250, now,
        )
        assertEquals(listOf("stable", "flaky"), ranked)
    }

    @Test
    fun rank_freshResultBeatsOldResultOfSameDelay() {
        val ranked = TopConfigManager.rank(
            mapOf(
                "old" to info(100, 5, 5, ageHours = 40),
                "fresh" to info(100, 5, 5, ageHours = 0),
            ),
            250, now,
        )
        assertEquals(listOf("fresh", "old"), ranked)
    }

    @Test
    fun candidate_flagFollowsHistoryLength() {
        fun entry(tests: Int) = TopConfigManager.Entry("g", "n", "VLESS", 100, now, tests, tests, 80, null)
        assertTrue(entry(1).isCandidate)
        assertFalse(entry(2).isCandidate)
    }

    @Test
    fun countryFromName_readsFlagEmoji() {
        assertEquals("DE", TopConfigManager.countryFromName("\uD83C\uDDE9\uD83C\uDDEA Frankfurt 01"))
        assertEquals("US", TopConfigManager.countryFromName("fast \uD83C\uDDFA\uD83C\uDDF8 node"))
        assertNull(TopConfigManager.countryFromName("Plain name"))
        assertNull(TopConfigManager.countryFromName("\uD83C\uDDE9"))
    }

    @Test
    fun flagEmoji_roundTrips() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", TopConfigManager.flagEmoji("DE"))
        assertEquals("", TopConfigManager.flagEmoji(null))
    }

    @Test
    fun pickBestFresh_ignoresStaleFailedAndMissing() {
        val since = now
        val infos = mapOf(
            "stale" to ServerAffiliationInfo(realDelayMillis = 20, lastTestTime = since - 1),
            "failed" to ServerAffiliationInfo(realDelayMillis = -1, lastTestTime = since + 5),
            "slow" to ServerAffiliationInfo(realDelayMillis = 200, lastTestTime = since + 5),
            "fast" to ServerAffiliationInfo(realDelayMillis = 90, lastTestTime = since + 7),
        )
        val best = TopConfigManager.pickBestFresh(infos, listOf("stale", "failed", "slow", "fast", "missing"), since)
        assertEquals("fast" to 90L, best)
    }

    @Test
    fun pickBestFresh_emptyWhenNothingAnswered() {
        assertNull(TopConfigManager.pickBestFresh(emptyMap(), listOf("a"), now))
        assertNull(TopConfigManager.pickBestFresh(mapOf("a" to info(-1, 1, 0)), listOf("a"), now))
        assertNull(TopConfigManager.pickBestFresh(mapOf("a" to info(80, 1, 1)), emptyList(), now))
    }

    @Test
    fun affiliationInfo_readsFormatWrittenByOlderVersions() {
        val old = Gson().fromJson("{\"testDelayMillis\":120}", ServerAffiliationInfo::class.java)
        assertEquals(120L, old.testDelayMillis)
        assertEquals(0L, old.realDelayMillis)
        assertEquals(0, old.testCount)
        // An old entry has no real-ping history, so it never qualifies as a top config.
        assertTrue(TopConfigManager.rank(mapOf("old" to old), 250, now).isEmpty())
    }

    @Test
    fun normalizeCountryCode_acceptsOnlyTwoLetterCodes() {
        assertEquals("DE", TopConfigManager.normalizeCountryCode(" de "))
        assertEquals("US", TopConfigManager.normalizeCountryCode("US"))
        assertNull(TopConfigManager.normalizeCountryCode("Germany"))
        assertNull(TopConfigManager.normalizeCountryCode("D1"))
        assertNull(TopConfigManager.normalizeCountryCode(""))
        assertNull(TopConfigManager.normalizeCountryCode(null))
    }

    @Test
    fun rank_lowJitterBeatsHighJitterWithSameMean() {
        fun withSpread(m2: Double) = ServerAffiliationInfo(
            realDelayMillis = 120, testCount = 10, successCount = 10,
            lastTestTime = now, delaySamples = 10, delayMean = 120.0, delayM2 = m2,
        )
        val ranked = TopConfigManager.rank(
            mapOf("jittery" to withSpread(9 * 90.0 * 90.0), "steady" to withSpread(9 * 3.0 * 3.0)),
            250, now,
        )
        assertEquals(listOf("steady", "jittery"), ranked)
    }

    @Test
    fun stabilityScore_isBoundedAndOrdered() {
        val good = ServerAffiliationInfo(realDelayMillis = 100, testCount = 10, successCount = 10, lastTestTime = now, delaySamples = 10, delayMean = 100.0, delayM2 = 9 * 4.0)
        val flaky = good.copy(successCount = 4)
        val old = good.copy(lastTestTime = now - 60 * hour)
        assertTrue(TopConfigManager.stabilityScore(good, now) in 0..100)
        assertTrue(TopConfigManager.stabilityScore(good, now) > TopConfigManager.stabilityScore(flaky, now))
        assertTrue(TopConfigManager.stabilityScore(good, now) > TopConfigManager.stabilityScore(old, now))
    }

    @Test
    fun parseFilterInputs_rejectOutOfRangeAndGarbage() {
        assertEquals(300L, TopConfigManager.parseMaxDelay(" 300 "))
        assertNull(TopConfigManager.parseMaxDelay("10"))
        assertNull(TopConfigManager.parseMaxDelay("abc"))
        assertEquals(40, TopConfigManager.parseLimit("40"))
        assertNull(TopConfigManager.parseLimit("500"))
        assertNull(TopConfigManager.parseLimit(""))
    }
}
