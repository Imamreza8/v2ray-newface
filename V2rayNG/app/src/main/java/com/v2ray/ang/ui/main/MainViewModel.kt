package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.handler.TopConfigManager
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis, testTime = System.currentTimeMillis())
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis, testTime = System.currentTimeMillis())
    }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
            spatialExpanded = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPATIAL_EXPANDED, true),
            // Explicit choice wins; otherwise follow the system "remove animations" setting.
            reduceMotion = MmkvManager.decodeSettingsString(AppConfig.PREF_REDUCE_MOTION)?.let { it == "1" }
                ?: animationsDisabledBySystem(application),
            simpleMode = MmkvManager.decodeSettingsBool(AppConfig.PREF_SIMPLE_MODE, false),
            speedEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED, false),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Top configs / auto-select ----------
    private val _topState = MutableStateFlow(
        TopUiState(
            limit = TopConfigManager.getLimit(),
            maxDelayMillis = TopConfigManager.getMaxDelayMillis(),
        )
    )
    internal val topState: StateFlow<TopUiState> = _topState.asStateFlow()
    private var topLoadJob: Job? = null

    private class AutoSelectRun(val guids: List<String>, val startedAt: Long)

    @Volatile
    private var pendingAutoSelect: AutoSelectRun? = null

    // Bulk-test run tracking (main thread only): which configs were asked, which already answered.
    private var runGuids: List<String> = emptyList()
    private var runOnlyTcp = false
    private val runDone = HashSet<String>()
    private var runOk = 0
    private var runFailed = 0
    private var resumeGuids: List<String> = emptyList()
    private var speedTestJob: Job? = null

    // Auto-select bookkeeping (main thread only)
    private val autoSelectTried = HashSet<String>()
    private var expansionBatch: List<String> = emptyList()
    private var smartConnectRequested = false

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private val statusFilter: ServerStatusFilter get() = uiState.value.statusFilter
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var selectedInfoJob: Job? = null
    private var testResultFlushJob: Job? = null
    private val pendingTestResults = linkedMapOf<String, Long>()

    private val testRequests = MainTestRequests()
    private var bulkTestJob: Job? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        setupGroupTab()
        refreshSelectedInfo()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunningState(true)
            }

            is MainServiceEvent.StateStartFailure -> {
                if (!event.message.isNullOrBlank()) {
                    toastError(event.message)
                } else {
                    toastError(R.string.toast_services_failure)
                }
                updateRunningState(false)
            }

            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning || !testRequests.completeCurrent(event.requestId)) return
                _uiState.update { it.copy(isTesting = testRequests.isTesting, status = MainStatus.ConnectionTest(event.result)) }
                learnExitCountry(event.result)
            }

            is MainServiceEvent.Traffic -> {
                if (!uiState.value.isRunning) return
                _uiState.update {
                    it.copy(
                        trafficDownBps = event.speed.downlinkBytesPerSec,
                        trafficUpBps = event.speed.uplinkBytesPerSec,
                        trafficHistory = (it.trafficHistory + event.speed.downlinkBytesPerSec).takeLast(TRAFFIC_HISTORY_SIZE),
                    )
                }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    pendingAutoSelect = null
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        if (runDone.add(result.guid)) {
            if (result.delayMillis > 0L) runOk++ else runFailed++
        }
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val updates = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(cached, drained)
            }
            drained
        }
        publishRunStats()
        if (updates.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(current.servers, updates),
                rows = applyTestDelayResultsToRows(current.rows, updates),
            )
        }
        refreshFilterCountsFor(request.groupId)
        if (uiState.value.selectedGuid?.let { it in updates } == true) refreshSelectedInfo()
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )

        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelByUser()
            MainAction.ResumeTesting -> startGuidTest(resumeGuids, runOnlyTcp)
            // The theme itself is observed from ThemeManager by AppTheme, so only persist the choice here.
            is MainAction.SetSpatialTheme ->
                viewModelScope.launch(ioDispatcher) { ThemeManager.setSpatialThemeEnabled(action.enabled) }
            MainAction.SpeedTestCurrent -> speedTestCurrent()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub(uiState.value.selectedGroupId)
            is MainAction.UpdateGroupSubscription -> importConfigViaSub(action.groupId)
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            is MainAction.SetSpatialExpanded -> {
                _uiState.update { it.copy(spatialExpanded = action.expanded) }
                viewModelScope.launch(ioDispatcher) {
                    MmkvManager.encodeSettings(AppConfig.PREF_SPATIAL_EXPANDED, action.expanded)
                }
            }
            is MainAction.SetReduceMotion -> {
                _uiState.update { it.copy(reduceMotion = action.reduce) }
                viewModelScope.launch(ioDispatcher) {
                    MmkvManager.encodeSettings(AppConfig.PREF_REDUCE_MOTION, if (action.reduce) "1" else "0")
                }
            }
            is MainAction.SetSimpleMode -> {
                _uiState.update { it.copy(simpleMode = action.enabled) }
                viewModelScope.launch(ioDispatcher) {
                    MmkvManager.encodeSettings(AppConfig.PREF_SIMPLE_MODE, action.enabled)
                }
            }
            MainAction.SmartConnect -> {
                smartConnectRequested = true
                autoSelect()
            }
            MainAction.ConfirmExpand -> confirmExpand()
            MainAction.DismissExpand -> {
                expansionBatch = emptyList()
                _uiState.update { it.copy(expandProposal = null) }
            }
            is MainAction.SetStatusFilter -> setStatusFilter(action.filter)
            MainAction.RefreshTop -> loadTop()
            is MainAction.SetTopFilter -> setTopFilter(
                action.limit, action.maxDelayMillis, action.countryCode, action.countrySource
            )
            MainAction.TestTopConfigs -> testTopConfigs()
            MainAction.AutoSelect -> {
                smartConnectRequested = false
                autoSelect()
            }
            MainAction.DismissAutoSelect -> _uiState.update { it.copy(autoSelectProposal = null) }
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent,
            is MainAction.OpenSubscriptionLink,
            is MainAction.ApplyAutoSelect -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
                speedEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED, false),
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L,
                testTime = affiliation?.testTime ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val keywordFiltered = applyKeywordFilter(servers)
        val filteredServers = applyStatusFilter(keywordFiltered)
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = filteredServers,
            rows = buildServerRows(groupId, filteredServers)
        )
        publishFilterCounts(groupId, keywordFiltered)
    }

    private fun applyStatusFilter(servers: List<ServersCache>): List<ServersCache> {
        val filter = statusFilter
        if (filter == ServerStatusFilter.ALL) return servers
        val topMax = TopConfigManager.getMaxDelayMillis()
        return servers.filter { matchesServerStatus(it.testDelayMillis, filter, topMax) }
    }

    private fun publishFilterCounts(groupId: String, servers: List<ServersCache>) {
        val topMax = TopConfigManager.getMaxDelayMillis()
        var top = 0
        var alive = 0
        var untested = 0
        var failed = 0
        for (server in servers) {
            val delay = server.testDelayMillis
            when {
                delay > 0L -> {
                    alive++
                    if (delay <= topMax) top++
                }
                delay == 0L -> untested++
                else -> failed++
            }
        }
        val counts = FilterCounts(servers.size, top, alive, untested, failed)
        _uiState.update {
            if (it.selectedGroupId != groupId || it.filterCounts == counts) it else it.copy(filterCounts = counts)
        }
    }

    /** Recomputes the chip counts for [groupId] from the cache (used when the selected group changes). */
    private fun refreshFilterCountsFor(groupId: String) {
        viewModelScope.launch(defaultDispatcher) {
            val servers = cacheMutex.withLock { groupDataCache[groupId] } ?: return@launch
            publishFilterCounts(groupId, applyKeywordFilter(servers))
        }
    }

    private fun setStatusFilter(filter: ServerStatusFilter) {
        if (filter == statusFilter) return
        _uiState.update { it.copy(statusFilter = filter) }
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty()
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(
                        id = it.guid,
                        remarks = it.subscription.remarks,
                        usageInfo = it.subscription.usageInfo?.let { info ->
                            SubscriptionInfoParser.withWebPageFallback(info, it.subscription.url)
                        },
                    )
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer(),
                    )
                }
                refreshSelectedInfo()
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub(subId: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && !hasActiveServerFilter(keywordFilter, statusFilter)) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && !hasActiveServerFilter(keywordFilter, statusFilter)) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || hasActiveServerFilter(keywordFilter, statusFilter)
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
            refreshFilterCountsFor(id)
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
        refreshSelectedInfo()
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
        refreshSelectedInfo()
    }

    /** Loads name + last delay of the selected server (by GUID) off the main thread. */
    private fun refreshSelectedInfo() {
        selectedInfoJob?.cancel()
        selectedInfoJob = viewModelScope.launch(ioDispatcher) {
            val guid = uiState.value.selectedGuid
            val info = if (guid == null) {
                null
            } else {
                dataSource.decodeServerConfig(guid)?.let { profile ->
                    val affiliation = dataSource.decodeAffiliationInfo(guid)
                    SelectedServerInfo(
                        remarks = profile.remarks,
                        delayMillis = affiliation?.testDelayMillis ?: 0L,
                        exitCountry = TopConfigManager.normalizeCountryCode(affiliation?.exitCountry),
                    )
                }
            }
            _uiState.update { state ->
                if (state.selectedGuid == guid) state.copy(selectedInfo = info) else state
            }
        }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        pendingAutoSelect = null
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val servers = currentServers()
        if (servers.isEmpty()) {
            return
        }
        val serverGuids = servers.map { it.guid }
        beginRun(serverGuids, onlyTcp)
        // Previous results are kept (not reset to "untested"): each one is replaced as soon as its
        // new result arrives, and a cancelled run does not throw the old results away.
        val request = testRequests.beginBulk(groupId)
        val filtered = hasActiveServerFilter(keywordFilter, statusFilter)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            // A partial test must not trigger whole-subscription auto-removal or sorting.
            subscriptionId = if (filtered) "" else groupId,
            serverGuids = if (filtered) serverGuids else emptyList(),
            onlyTcp = onlyTcp
        )
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    /** Real-pings exactly [guids] (any group). Group auto-remove/auto-sort do not apply (no subscriptionId). */
    private fun startGuidTest(guids: List<String>, onlyTcp: Boolean = false) {
        cancelAllPing()
        if (guids.isEmpty()) return
        beginRun(guids, onlyTcp)
        val request = testRequests.beginBulk(uiState.value.selectedGroupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = "",
            serverGuids = guids,
            onlyTcp = onlyTcp
        )
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        bulkTestJob = viewModelScope.launch {
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    // ---------- Top configs ----------
    private fun loadTop() = loadTop(_topState.value)

    private fun loadTop(target: TopUiState) {
        topLoadJob?.cancel()
        _topState.update {
            it.copy(
                limit = target.limit,
                maxDelayMillis = target.maxDelayMillis,
                countryCode = target.countryCode,
                countrySource = target.countrySource,
                isLoading = true,
            )
        }
        topLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                val entries = TopConfigManager.loadTop(
                    limit = target.limit,
                    maxDelayMillis = target.maxDelayMillis,
                    countryCode = target.countryCode,
                    countrySource = target.countrySource,
                )
                _topState.update { it.copy(entries = entries, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load top configs", e)
                _topState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun setTopFilter(
        limit: Int,
        maxDelayMillis: Long,
        countryCode: String?,
        countrySource: TopConfigManager.CountrySource,
    ) {
        viewModelScope.launch(ioDispatcher) { TopConfigManager.saveSettings(limit, maxDelayMillis) }
        loadTop(
            TopUiState(
                limit = limit,
                maxDelayMillis = maxDelayMillis,
                countryCode = countryCode,
                countrySource = countrySource,
            )
        )
    }

    /** Re-tests only the current top list (fast path before a full test of every config). */
    private fun testTopConfigs() {
        val filter = _topState.value
        viewModelScope.launch {
            val top = withContext(ioDispatcher) {
                TopConfigManager.loadTop(filter.limit, filter.maxDelayMillis, filter.countryCode, filter.countrySource)
            }
            if (top.isEmpty()) {
                toast(R.string.top_none)
                return@launch
            }
            startGuidTest(top.map { it.guid })
        }
    }

    /**
     * Re-tests the top list, then *proposes* the fastest config that answered in this run.
     * Nothing is selected or restarted until the user confirms the proposal.
     */
    private fun autoSelect() {
        val filter = _topState.value
        viewModelScope.launch {
            val top = withContext(ioDispatcher) {
                TopConfigManager.loadTop(filter.limit, filter.maxDelayMillis, filter.countryCode, filter.countrySource)
            }
            if (top.isEmpty()) {
                // No stored result yet (or none under the limit): offer to test a batch instead of stopping.
                autoSelectTried.clear()
                proposeExpansion()
                return@launch
            }
            val guids = top.map { it.guid }
            autoSelectTried.clear()
            autoSelectTried.addAll(guids)
            val startedAt = System.currentTimeMillis()
            startGuidTest(guids)
            pendingAutoSelect = AutoSelectRun(guids, startedAt)
        }
    }

    /**
     * Nothing in the tried set answered. Offer the next batch from the current group: never-tested configs
     * first, then ones that answered before (fastest first), failed ones last. Nothing is tested until the
     * user confirms.
     */
    private fun proposeExpansion() {
        val batch = currentServers()
            .filter { it.guid !in autoSelectTried }
            .sortedWith(
                compareBy<ServersCache>(
                    { server ->
                        when {
                            server.testDelayMillis == 0L -> 0
                            server.testDelayMillis > 0L -> 1
                            else -> 2
                        }
                    },
                    { server -> if (server.testDelayMillis > 0L) server.testDelayMillis else 0L },
                    { server -> server.testTime },
                )
            )
            .take(EXPANSION_BATCH_SIZE)
            .map { it.guid }
        if (batch.isEmpty()) {
            smartConnectRequested = false
            toast(R.string.top_auto_select_none)
            return
        }
        expansionBatch = batch
        _uiState.update { it.copy(expandProposal = ExpandProposal(batch.size)) }
    }

    private fun confirmExpand() {
        val batch = expansionBatch
        expansionBatch = emptyList()
        _uiState.update { it.copy(expandProposal = null) }
        if (batch.isEmpty()) return
        autoSelectTried.addAll(batch)
        val startedAt = System.currentTimeMillis()
        startGuidTest(batch)
        pendingAutoSelect = AutoSelectRun(batch, startedAt)
    }

    private fun finishAutoSelect(run: AutoSelectRun) {
        viewModelScope.launch {
            val best = withContext(ioDispatcher) {
                val infos = run.guids.mapNotNull { guid ->
                    dataSource.decodeAffiliationInfo(guid)?.let { guid to it }
                }.toMap()
                TopConfigManager.pickBestFresh(infos, run.guids, run.startedAt)
            }
            if (best == null) {
                proposeExpansion()
                return@launch
            }
            val remarks = withContext(ioDispatcher) {
                dataSource.decodeServerConfig(best.first)?.remarks.orEmpty()
            }
            _uiState.update {
                it.copy(
                    autoSelectProposal = AutoSelectProposal(best.first, remarks, best.second, connect = smartConnectRequested),
                    expandProposal = null,
                )
            }
        }
    }

    private fun beginRun(guids: List<String>, onlyTcp: Boolean) {
        runGuids = guids
        runOnlyTcp = onlyTcp
        runDone.clear()
        runOk = 0
        runFailed = 0
        resumeGuids = emptyList()
        _uiState.update { it.copy(runStats = TestRunStats(total = guids.size), resumableCount = 0) }
    }

    private fun publishRunStats() {
        val stats = TestRunStats(total = runGuids.size, done = runDone.size, ok = runOk, failed = runFailed)
        _uiState.update { if (it.runStats == stats) it else it.copy(runStats = stats) }
    }

    /** User pressed cancel: remember which configs of this run never answered, so the run can be resumed. */
    private fun cancelByUser() {
        val remaining = if (testRequests.bulk != null) runGuids.filter { it !in runDone } else emptyList()
        cancelAllPing()
        resumeGuids = remaining
        publishRunStats()
        _uiState.update { it.copy(resumableCount = remaining.size) }
    }

    /** Keeps the exit-IP country (two-letter code only) of the connected config for the country filter. */
    private fun learnExitCountry(result: ConnectionTestResult) {
        if (result.delayMillis < 0L) return
        val guid = uiState.value.selectedGuid ?: return
        val code = TopConfigManager.normalizeCountryCode(result.country) ?: return
        viewModelScope.launch(ioDispatcher) { MmkvManager.encodeServerExitCountry(guid, code) }
    }

    /**
     * Downloads about 5 MB through the connected proxy and reports the speed. Runs only on request,
     * only while connected, and contacts the host in [AppConfig.SPEED_TEST_URL].
     */
    private fun speedTestCurrent() {
        if (!uiState.value.isRunning || uiState.value.speedTest == SpeedTestState.Running) return
        speedTestJob?.cancel()
        _uiState.update { it.copy(speedTest = SpeedTestState.Running) }
        speedTestJob = viewModelScope.launch(ioDispatcher) {
            val measured = try {
                val httpPort = SettingsManager.getHttpPort()
                if (httpPort == 0) {
                    null
                } else {
                    HttpUtil.measureDownload(
                        UrlContentRequest(
                            url = AppConfig.SPEED_TEST_URL,
                            timeout = 10_000,
                            httpPort = httpPort,
                            proxyUsername = SettingsManager.getSocksUsername(),
                            proxyPassword = SettingsManager.getSocksPassword(),
                        ),
                        AppConfig.SPEED_TEST_MAX_BYTES,
                        SPEED_TEST_MAX_MILLIS,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Speed test failed", e)
                null
            }
            val state = if (measured == null || measured.first <= 0L) {
                SpeedTestState.Failed
            } else {
                SpeedTestState.Result(megabitsPerSecond(measured.first, measured.second), measured.first)
            }
            _uiState.update { it.copy(speedTest = state) }
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing(requestId)
    }

    private fun onTestsFinished(requestId: String) {
        if (testRequests.completeBulk(requestId) == null) return
        resetTestStatus()
        val autoSelectRun = pendingAutoSelect
        pendingAutoSelect = null
        resumeGuids = emptyList()
        publishRunStats()
        _uiState.update { it.copy(resumableCount = 0) }
        if (autoSelectRun != null) finishAutoSelect(autoSelectRun)
        loadTop()
        refreshSelectedInfo()
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.clear() }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Running state ----------
    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                trafficDownBps = if (running) state.trafficDownBps else 0L,
                trafficUpBps = if (running) state.trafficUpBps else 0L,
                trafficHistory = if (running) state.trafficHistory else emptyList(),
                speedTest = if (running) state.speedTest else SpeedTestState.Idle,
            )
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        speedTestJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
        const val TRAFFIC_HISTORY_SIZE = 24
        const val SPEED_TEST_MAX_MILLIS = 12_000L
        const val EXPANSION_BATCH_SIZE = 200
    }
}

/** True when the user turned animations off in the system settings (animator duration scale = 0). */
private fun animationsDisabledBySystem(application: Application): Boolean = try {
    android.provider.Settings.Global.getFloat(
        application.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (e: Exception) {
    false
}
