package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ServerAffiliationInfo

/**
 * "Top configs": the best servers according to the *stored* real-ping history, so the user does not
 * have to re-test a 36k-server subscription from scratch to find a good one.
 *
 * Nothing here deletes or changes servers; it only reads test results.
 */
object TopConfigManager {

    const val DEFAULT_LIMIT = 30
    const val DEFAULT_MAX_DELAY_MS = 250L

    /** Where the country of a config comes from. */
    enum class CountrySource {
        /** Flag emoji in the config name (an estimate). */
        NAME,

        /** Exit IP country learned when the user tested that config while connected to it. */
        EXIT,
    }

    /** A config shown in the top list. */
    data class Entry(
        val guid: String,
        val remarks: String,
        val protocol: String,
        val delayMillis: Long,
        val lastTestTime: Long,
        val testCount: Int,
        val successCount: Int,
        /** 0..100 combining success ratio, jitter and age; see [stabilityScore]. */
        val stability: Int,
        /** ISO-3166 alpha-2 code for the chosen [CountrySource], or null when unknown. */
        val countryCode: String?,
    ) {
        /** Too little history to call it stable yet. */
        val isCandidate: Boolean get() = testCount < MIN_HISTORY_FOR_STABLE
    }

    private const val MIN_HISTORY_FOR_STABLE = 2
    private const val UNKNOWN_HISTORY_RELIABILITY = 0.7
    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val JITTER_WEIGHT = 0.5

    fun getLimit(): Int =
        MmkvManager.decodeSettingsInt(AppConfig.PREF_TOP_LIMIT, DEFAULT_LIMIT).coerceIn(5, 200)

    fun getMaxDelayMillis(): Long =
        MmkvManager.decodeSettingsLong(AppConfig.PREF_TOP_MAX_DELAY, DEFAULT_MAX_DELAY_MS).coerceIn(50L, 5_000L)

    fun saveSettings(limit: Int, maxDelayMillis: Long) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOP_LIMIT, limit)
        MmkvManager.encodeSettings(AppConfig.PREF_TOP_MAX_DELAY, maxDelayMillis)
    }

    /**
     * Pure ranking: returns guids ordered best-first.
     *
     * A server qualifies only if its latest real-ping result is a success under [maxDelayMillis].
     * Score (lower is better) = delay / reliability + age penalty, where reliability is the recent
     * success ratio (0.7 while there is too little history) and the age penalty grows with the
     * hours since the last test, up to 300 ms. Old results therefore lose to fresh ones.
     */
    fun rank(
        infos: Map<String, ServerAffiliationInfo>,
        maxDelayMillis: Long,
        now: Long,
    ): List<String> {
        return infos.asSequence()
            .filter { (_, info) -> info.realDelayMillis in 1L..maxDelayMillis }
            .map { (guid, info) -> guid to score(info, now) }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
    }

    internal fun score(info: ServerAffiliationInfo, now: Long): Double {
        val reliability = if (info.testCount >= MIN_HISTORY_FOR_STABLE) {
            (info.successCount.toDouble() / info.testCount).coerceIn(0.05, 1.0)
        } else {
            UNKNOWN_HISTORY_RELIABILITY
        }
        val agePenalty = if (info.lastTestTime <= 0L) {
            300.0
        } else {
            val hours = ((now - info.lastTestTime).coerceAtLeast(0L)).toDouble() / MILLIS_PER_HOUR
            (hours * 5.0).coerceAtMost(300.0)
        }
        return info.realDelayMillis / reliability + agePenalty + jitterMillis(info) * JITTER_WEIGHT
    }

    /** Standard deviation of the successful real-ping delays; 0 while there are fewer than two samples. */
    fun jitterMillis(info: ServerAffiliationInfo): Double =
        if (info.delaySamples >= 2) kotlin.math.sqrt((info.delayM2 / (info.delaySamples - 1)).coerceAtLeast(0.0)) else 0.0

    /**
     * 0..100. Success ratio (0.7 while history is short) x jitter factor (up to -50 % when the spread
     * equals the mean delay) x age factor (up to -30 % after 48 h).
     */
    fun stabilityScore(info: ServerAffiliationInfo, now: Long): Int {
        val reliability = if (info.testCount >= MIN_HISTORY_FOR_STABLE) {
            (info.successCount.toDouble() / info.testCount).coerceIn(0.0, 1.0)
        } else {
            UNKNOWN_HISTORY_RELIABILITY
        }
        val mean = info.delayMean.coerceAtLeast(50.0)
        val jitterFactor = 1.0 - (jitterMillis(info) / mean).coerceIn(0.0, 1.0) * 0.5
        val ageHours = if (info.lastTestTime <= 0L) 48.0 else ((now - info.lastTestTime).coerceAtLeast(0L)).toDouble() / MILLIS_PER_HOUR
        val ageFactor = 1.0 - (ageHours / 48.0).coerceIn(0.0, 1.0) * 0.3
        return Math.round(100.0 * reliability * jitterFactor * ageFactor).toInt().coerceIn(0, 100)
    }

    /** Parses the "max ping" field; null when it is not a whole number in 50..5000 ms. */
    fun parseMaxDelay(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 50L..5_000L }

    /** Parses the "count" field; null when it is not a whole number in 5..200. */
    fun parseLimit(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 5..200 }

    /**
     * Loads the top entries. If [countryCode] is set, only servers whose name carries that flag
     * emoji are kept (an estimate from the name, not a geo lookup).
     * Blocking (MMKV reads): call from a background dispatcher.
     */
    fun loadTop(
        limit: Int = getLimit(),
        maxDelayMillis: Long = getMaxDelayMillis(),
        countryCode: String? = null,
        countrySource: CountrySource = CountrySource.NAME,
        now: Long = System.currentTimeMillis(),
    ): List<Entry> {
        val infos = MmkvManager.decodeAllServerAffiliations()
        val ranked = rank(infos, maxDelayMillis, now)
        val result = ArrayList<Entry>(limit)
        for (guid in ranked) {
            if (result.size >= limit) break
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue // server was deleted
            val info = infos[guid] ?: continue
            val flagCountry = when (countrySource) {
                CountrySource.NAME -> countryFromName(profile.remarks)
                CountrySource.EXIT -> normalizeCountryCode(info.exitCountry)
            }
            if (countryCode != null && flagCountry != countryCode) continue
            result += Entry(
                guid = guid,
                remarks = profile.remarks,
                protocol = profile.configType.name,
                delayMillis = info.realDelayMillis,
                lastTestTime = info.lastTestTime,
                testCount = info.testCount,
                successCount = info.successCount,
                stability = stabilityScore(info, now),
                countryCode = flagCountry,
            )
        }
        return result
    }

    /**
     * Pure: among [guids], the one with the lowest real-ping delay whose result was stored at or
     * after [since] (so a result left over from an older run is never picked). Null if none answered.
     */
    fun pickBestFresh(
        infos: Map<String, ServerAffiliationInfo>,
        guids: List<String>,
        since: Long,
    ): Pair<String, Long>? {
        var best: Pair<String, Long>? = null
        for (guid in guids) {
            val info = infos[guid] ?: continue
            if (info.lastTestTime < since || info.realDelayMillis <= 0L) continue
            if (best == null || info.realDelayMillis < best.second) best = guid to info.realDelayMillis
        }
        return best
    }

    /**
     * Accepts only a two-letter country code from the IP-info service (e.g. "de" or "DE") and returns it
     * in upper case. A full country name or anything else returns null, so it is never guessed.
     */
    fun normalizeCountryCode(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.length != 2 || !value.all { it.isLetter() && it.code < 128 }) return null
        return value.uppercase()
    }

    /**
     * Finds a regional-indicator flag emoji (two code points U+1F1E6..U+1F1FF) in [name] and turns it
     * into an ISO alpha-2 code. Returns null when the name has no flag.
     */
    fun countryFromName(name: String): String? {
        var i = 0
        while (i < name.length) {
            val first = name.codePointAt(i)
            val firstLen = Character.charCount(first)
            if (first in REGIONAL_A..REGIONAL_Z && i + firstLen < name.length) {
                val second = name.codePointAt(i + firstLen)
                if (second in REGIONAL_A..REGIONAL_Z) {
                    val a = 'A' + (first - REGIONAL_A)
                    val b = 'A' + (second - REGIONAL_A)
                    return "$a$b"
                }
            }
            i += firstLen
        }
        return null
    }

    /** Turns an ISO alpha-2 code back into its flag emoji, for display. */
    fun flagEmoji(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return ""
        val a = REGIONAL_A + (countryCode[0].uppercaseChar() - 'A')
        val b = REGIONAL_A + (countryCode[1].uppercaseChar() - 'A')
        return String(Character.toChars(a)) + String(Character.toChars(b))
    }

    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF
}
