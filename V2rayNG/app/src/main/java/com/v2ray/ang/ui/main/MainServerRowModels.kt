package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.TopConfigManager
import java.util.Locale

internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    /** When [testDelayMillis] was measured (0 = unknown); the list marks old results. */
    val testTime: Long,
    val subscriptionBadge: String,
    /** Last recorded exit country, if present and a known ISO region. */
    val exitCountry: String? = null,
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
    /** Null while this group has not been loaded. Counts are before search/status filtering. */
    val totalCount: Int? = null,
    /** Keyword-matched source rows, including those hidden by the status filter. */
    val keywordServers: List<ServersCache> = emptyList(),
    val keywordRows: List<ServerRowUiModel> = emptyList(),
    val keyword: String = "",
    val appliedFilter: ServerStatusFilter = ServerStatusFilter.ALL,
    val appliedSort: ServerDisplaySort = ServerDisplaySort.DEFAULT,
    val topMaxMillis: Long = 0L,
    val counts: FilterCounts = FilterCounts(),
)

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
): ServerRowUiModel {
    val profile = server.profile
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        testTime = server.testTime,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        exitCountry = validRecordedExitCountry(server.exitCountry),
    )
}

private val isoRegions: Set<String> by lazy { Locale.getISOCountries().toSet() }

/** A saved exit code is historical metadata, never inferred from a name or hostname. */
internal fun validRecordedExitCountry(raw: String?): String? =
    TopConfigManager.normalizeCountryCode(raw)?.takeIf { it in isoRegions }

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}

internal enum class ResultAgeUnit { UNKNOWN, MINUTES, HOURS, DAYS }
internal data class ResultAge(val unit: ResultAgeUnit, val value: Long = 0L)

/** Keep the accessible age while avoiding a visually noisy zero-minute badge. */
internal fun shouldShowResultAge(age: ResultAge?): Boolean =
    age != null && !(age.unit == ResultAgeUnit.MINUTES && age.value == 0L)

/** No age for untested entries; timestamps from old versions or a future clock are unknown. */
internal fun serverResultAge(delayMillis: Long, testedAt: Long, now: Long): ResultAge? {
    if (delayMillis == 0L) return null
    if (testedAt <= 0L || now <= 0L || testedAt > now) return ResultAge(ResultAgeUnit.UNKNOWN)
    val minutes = (now - testedAt) / 60_000L
    return when {
        minutes < 60L -> ResultAge(ResultAgeUnit.MINUTES, minutes)
        minutes < 24L * 60L -> ResultAge(ResultAgeUnit.HOURS, minutes / 60L)
        else -> ResultAge(ResultAgeUnit.DAYS, minutes / (24L * 60L))
    }
}
