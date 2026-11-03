package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.handler.TopConfigManager

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

internal enum class ConnectionSummaryKind { NEEDS_TEST, SUCCESS, SUCCESS_WITH_COUNTRY, OTHER }

/** Never reuse the selected profile's stored ping as a measurement of the active connection. */
internal fun connectionSummaryKind(status: MainStatus): ConnectionSummaryKind = when (status) {
    MainStatus.Connected -> ConnectionSummaryKind.NEEDS_TEST
    is MainStatus.ConnectionTest -> when {
        status.result.delayMillis < 0L -> ConnectionSummaryKind.OTHER
        status.result.country.isNullOrBlank() -> ConnectionSummaryKind.SUCCESS
        else -> ConnectionSummaryKind.SUCCESS_WITH_COUNTRY
    }
    else -> ConnectionSummaryKind.OTHER
}

/** Keep the active-connection result separate from bulk-test progress. */
internal fun connectionStatusAfterTestReset(
    status: MainStatus,
    isRunning: Boolean,
    currentPending: Boolean,
): MainStatus = when {
    !isRunning -> MainStatus.Disconnected
    status == MainStatus.Testing && !currentPending -> MainStatus.Connected
    else -> status
}

/** Result of an auto-select run, shown to the user for confirmation before anything is switched. */
data class AutoSelectProposal(
    val guid: String,
    val remarks: String,
    val delayMillis: Long,
    /** True when it came from "Smart connect": confirming also connects, even if not connected yet. */
    val connect: Boolean = false,
)

/** Nothing in the top list answered; asks before testing the next batch of configs from the current group. */
data class ExpandProposal(val count: Int)

/** Name and last test delay of the selected server, loaded off the main thread by the ViewModel. */
data class SelectedServerInfo(
    val remarks: String,
    val delayMillis: Long,
    /** Two-letter exit country learned from a connection test, or null. */
    val exitCountry: String? = null,
)

/** Quick filter of the server list by test result. */
enum class ServerStatusFilter { ALL, TOP, ALIVE, UNTESTED, FAILED }

/** Presentation only; never written to the saved subscription order. */
enum class ServerDisplaySort { DEFAULT, PING, NAME }

/** Filters are evaluated using the same ping value shown in server rows. */
internal fun matchesServerStatus(delay: Long, filter: ServerStatusFilter, topMaxMillis: Long): Boolean =
    when (filter) {
        ServerStatusFilter.ALL -> true
        ServerStatusFilter.TOP -> delay in 1L..topMaxMillis
        ServerStatusFilter.ALIVE -> delay > 0L
        ServerStatusFilter.UNTESTED -> delay == 0L
        ServerStatusFilter.FAILED -> delay < 0L
    }

internal fun hasActiveServerFilter(keyword: String, filter: ServerStatusFilter): Boolean =
    keyword.isNotBlank() || filter != ServerStatusFilter.ALL

/** How many servers of the selected group fall into each [ServerStatusFilter] (search keyword applied). */
data class FilterCounts(
    val all: Int = 0,
    val top: Int = 0,
    val alive: Int = 0,
    val untested: Int = 0,
    val failed: Int = 0,
)

/** The choices in the list test controls; TOP and FAILED are restricted to visible rows. */
enum class ScopedTestKind { REAL, TCP, TOP, FAILED, CURRENT }

/** Immutable confirmation snapshot. A group ID may be empty for the all-servers group. */
data class ScopedTestProposal(
    val kind: ScopedTestKind,
    val groupId: String,
    val groupName: String,
    val filter: ServerStatusFilter,
    val visibleCount: Int,
    val searchActive: Boolean,
    val guids: List<String>,
)

/** Counters of the running (or last) bulk test; published at most every 500 ms by the ViewModel. */
data class TestRunStats(
    val total: Int = 0,
    val done: Int = 0,
    val ok: Int = 0,
    val failed: Int = 0,
)

/** Download-speed test of the current connection. */
sealed interface SpeedTestState {
    data object Idle : SpeedTestState
    data object Running : SpeedTestState
    data class Result(val megabitsPerSecond: Double, val bytes: Long) : SpeedTestState
    data object Failed : SpeedTestState
}

/** Pure: Mbit/s for [bytes] received in [millis] (millis is clamped to at least 1). */
internal fun megabitsPerSecond(bytes: Long, millis: Long): Double =
    bytes * 8.0 / 1_000_000.0 / (millis.coerceAtLeast(1L) / 1000.0)

/** State of the "top configs" sheet. */
data class TopUiState(
    val entries: List<TopConfigManager.Entry> = emptyList(),
    val limit: Int = TopConfigManager.DEFAULT_LIMIT,
    val maxDelayMillis: Long = TopConfigManager.DEFAULT_MAX_DELAY_MS,
    /** Null = all countries; otherwise an ISO alpha-2 code estimated from the server name. */
    val countryCode: String? = null,
    val countrySource: TopConfigManager.CountrySource = TopConfigManager.CountrySource.NAME,
    val isLoading: Boolean = false,
)

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val connectionStatus: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    val autoSelectProposal: AutoSelectProposal? = null,
    val selectedInfo: SelectedServerInfo? = null,
    val spatialExpanded: Boolean = true,
    val reduceMotion: Boolean = false,
    val statusFilter: ServerStatusFilter = ServerStatusFilter.ALL,
    val displaySort: ServerDisplaySort = ServerDisplaySort.DEFAULT,
    val filterCounts: FilterCounts = FilterCounts(),
    val simpleMode: Boolean = false,
    val expandProposal: ExpandProposal? = null,
    val scopedTestProposal: ScopedTestProposal? = null,
    val runStats: TestRunStats = TestRunStats(),
    /** Number of configs left from a run the user cancelled; 0 = nothing to resume. */
    val resumableCount: Int = 0,
    /** Proxy-only speed from the daemon; only delivered while the speed notification setting is on. */
    val speedEnabled: Boolean = false,
    val trafficDownBps: Long = 0L,
    val trafficUpBps: Long = 0L,
    val trafficHistory: List<Long> = emptyList(),
    val speedTest: SpeedTestState = SpeedTestState.Idle,
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data class PrepareScopedTest(val kind: ScopedTestKind) : MainAction
    data object ConfirmScopedTest : MainAction
    data object DismissScopedTest : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data class UpdateGroupSubscription(val groupId: String) : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction
    data class OpenSubscriptionLink(val url: String) : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction

    // Top configs / auto-select
    data class SetSpatialExpanded(val expanded: Boolean) : MainAction
    data class SetReduceMotion(val reduce: Boolean) : MainAction
    data class SetStatusFilter(val filter: ServerStatusFilter) : MainAction
    data class SetDisplaySort(val sort: ServerDisplaySort) : MainAction
    data object RefreshTop : MainAction
    data class SetTopFilter(
        val limit: Int,
        val maxDelayMillis: Long,
        val countryCode: String?,
        val countrySource: TopConfigManager.CountrySource,
    ) : MainAction
    data object ResumeTesting : MainAction
    data class SetSpatialTheme(val enabled: Boolean) : MainAction
    data class SetSimpleMode(val enabled: Boolean) : MainAction
    data object SmartConnect : MainAction
    data object ConfirmExpand : MainAction
    data object DismissExpand : MainAction
    data object SpeedTestCurrent : MainAction
    data object TestTopConfigs : MainAction
    data object AutoSelect : MainAction
    data object DismissAutoSelect : MainAction
    /** Handled by the Activity (it owns the service restart); [reconnect] asks for a restart if running. */
    data class ApplyAutoSelect(val guid: String, val reconnect: Boolean) : MainAction
}
