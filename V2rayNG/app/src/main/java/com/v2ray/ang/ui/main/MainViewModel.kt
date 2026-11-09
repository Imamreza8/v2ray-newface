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

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
    nowMillis: Long,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis, testTime = nowMillis)
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
    private var runGroupId = ""
    private var resumeGroupId = ""
    private var scopedTestPreparationJob: Job? = null
    private var saveOrderPreparationJob: Job? = null
    private var exportPreparationJob: Job? = null
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
    private val keywordFilter: String get() = uiState.value.searchQuery
    private val statusFilter: ServerStatusFilter get() = uiState.value.statusFilter
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    // Serializes filtered snapshots and live batches across background dispatchers.
    private val presentationMutex = Mutex()
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
    private var locateJob: Job? = null
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
                _uiState.update { it.copy(
                    isTesting = testRequests.isTesting,
                    status = MainStatus.ConnectionTest(event.result),
                    connectionStatus = MainStatus.ConnectionTest(event.result),
                ) }
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
                    // Let any already-drained batch finish before processing the final results.
                    scheduledFlush?.join()
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
        if (pendingTestResults.isEmpty() || testRequests.bulk?.id != request.id) return
        val updates = pendingTestResults.toMap()
        pendingTestResults.clear()
        publishRunStats()
        val now = System.currentTimeMillis()
        withContext(defaultDispatcher) {
            presentationMutex.withLock {
                val updatedGroups = cacheMutex.withLock {
                    groupDataCache.mapValues { (_, servers) -> applyTestDelayResults(servers, updates, now) }
                        .also { groupDataCache.putAll(it) }
                }
                for ((groupId, servers) in updatedGroups) {
                    currentCoroutineContext().ensureActive()
                    val stateFlow = mutableServerGroupState(groupId)
                    val current = stateFlow.value
                    if (current.totalCount == null) continue
                    if (current.keyword != keywordFilter || current.appliedFilter != statusFilter ||
                        current.appliedSort != uiState.value.displaySort || current.topMaxMillis != TopConfigManager.getMaxDelayMillis()
                    ) {
                        renderGroupUi(groupId, servers)
                    } else {
                        val updated = applyLiveServerResults(current, updates, now)
                        stateFlow.value = updated
                        publishFilterCounts(groupId, updated.counts)
                    }
                }
            }
        }
        if (testRequests.bulk?.id != request.id) return
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
            MainAction.TestAllServers -> prepareScopedTest(ScopedTestKind.TCP)
            MainAction.TestRealAllServers -> prepareScopedTest(ScopedTestKind.REAL)
            is MainAction.PrepareScopedTest -> prepareScopedTest(action.kind)
            MainAction.ConfirmScopedTest -> confirmScopedTest()
            MainAction.DismissScopedTest -> _uiState.update { it.copy(scopedTestProposal = null) }
            MainAction.CancelTesting -> cancelByUser()
            MainAction.ResumeTesting -> if (!uiState.value.isTesting) startGuidTest(resumeGuids, runOnlyTcp, resumeGroupId)
            // The theme itself is observed from ThemeManager by AppTheme, so only persist the choice here.
            is MainAction.SetSpatialTheme ->
                viewModelScope.launch(ioDispatcher) { ThemeManager.setSpatialThemeEnabled(action.enabled) }
            MainAction.SpeedTestCurrent -> speedTestCurrent()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> prepareSaveOrder()
            MainAction.PrepareSaveOrder -> prepareSaveOrder()
            MainAction.ConfirmSaveOrder -> confirmSaveOrder()
            MainAction.DismissSaveOrder -> _uiState.update { it.copy(saveOrderProposal = null) }
            MainAction.UpdateSubscriptions -> importConfigViaSub(uiState.value.selectedGroupId)
            is MainAction.UpdateGroupSubscription -> importConfigViaSub(action.groupId)
            MainAction.ExportAll -> prepareExport()
            is MainAction.ConfirmExport -> confirmExport(action.scope)
            MainAction.DismissExport -> _uiState.update { it.copy(exportProposal = null) }
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.MoveServer -> moveServer(action.groupId, action.fromGuid, action.toGuid)
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
            is MainAction.SetDisplaySort -> setDisplaySort(action.sort)
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
                testTime = affiliation?.testTime ?: 0L,
                exitCountry = affiliation?.exitCountry,
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

    private suspend fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        withContext(defaultDispatcher) {
            presentationMutex.withLock {
                val latest = cacheMutex.withLock { groupDataCache[groupId] ?: servers }
                renderGroupUi(groupId, latest)
            }
        }
    }

    /** Caller holds presentationMutex so a filter rebuild cannot overwrite a newer test batch. */
    private suspend fun renderGroupUi(groupId: String, servers: List<ServersCache>) {
        val query = keywordFilter
        val filter = statusFilter
        val sort = uiState.value.displaySort
        val topMax = TopConfigManager.getMaxDelayMillis()
        val keywordServers = filterServersByKeyword(servers, query)
        val keywordRows = buildServerRows(groupId, keywordServers)
        val visibleServers = ArrayList<ServersCache>()
        val visibleRows = ArrayList<ServerRowUiModel>()
        keywordServers.forEachIndexed { index, server ->
            if (matchesServerStatus(server.testDelayMillis, filter, topMax)) {
                visibleServers.add(server)
                visibleRows.add(keywordRows[index])
            }
        }
        val counts = countServerStatuses(keywordServers, topMax)
        val (sortedServers, sortedRows) = sortVisibleServerRows(visibleServers, visibleRows, sort)
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = sortedServers,
            rows = sortedRows,
            totalCount = servers.size,
            keywordServers = keywordServers,
            keywordRows = keywordRows,
            keyword = query,
            appliedFilter = filter,
            appliedSort = sort,
            topMaxMillis = topMax,
            counts = counts,
        )
        publishFilterCounts(groupId, counts)
    }

    private fun publishFilterCounts(groupId: String, counts: FilterCounts) {
        _uiState.update {
            if (it.selectedGroupId != groupId || it.filterCounts == counts) it else it.copy(filterCounts = counts)
        }
    }

    private fun refreshFilterCountsFor(groupId: String) {
        viewModelScope.launch(defaultDispatcher) {
            presentationMutex.withLock {
                val state = mutableServerGroupState(groupId).value
                if (state.totalCount != null && state.keyword == keywordFilter) {
                    publishFilterCounts(groupId, state.counts)
                }
            }
        }
    }

    private fun setDisplaySort(sort: ServerDisplaySort) {
        if (uiState.value.displaySort == sort) return
        _uiState.update { it.copy(displaySort = sort) }
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

    private fun setStatusFilter(filter: ServerStatusFilter) {
        if (filter == statusFilter) return
        _uiState.update { it.copy(statusFilter = filter, locateTarget = null) }
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

    private suspend fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionIds = if (groupId.isEmpty()) servers.asSequence()
            .map { it.profile.subscriptionId }.filter { it.isNotEmpty() }.distinct().toList() else emptyList()
        val subscriptionRemarks = withContext(ioDispatcher) {
            subscriptionIds.associateWith { dataSource.getSubscriptionItem(it)?.remarks.orEmpty() }
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
                        usageInfo = it.subscription.usageInfo?.takeIf(::hasMeaningfulSubscriptionInfo)?.let { info ->
                            SubscriptionInfoParser.withWebPageFallback(info, it.subscription.url)
                        },
                    )
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                val selectedBeforeRead = _uiState.value.selectedGuid
                val persistedSelected = dataSource.getSelectServer()
                _uiState.update { it.copy(groups = groups, selectedGroupId = selectedGroup) }
                withContext(Dispatchers.Main.immediate) {
                    // A selection made while the IO read was pending is newer than this snapshot.
                    if (_uiState.value.selectedGuid == selectedBeforeRead) {
                        applySelectedGuid(persistedSelected)
                    }
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

    private fun prepareExport() {
        exportPreparationJob?.cancel()
        val ui = _uiState.value
        val group = ui.groups.firstOrNull { it.id == ui.selectedGroupId } ?: return
        val state = mutableServerGroupState(group.id).value
        val keyword = keywordFilter
        if (!scopedListReady(state, keyword, ui.statusFilter) || state.appliedSort != ui.displaySort) {
            toast(R.string.scoped_test_updating)
            return
        }
        exportPreparationJob = viewModelScope.launch {
            val proposal = withContext(defaultDispatcher) {
                val source = cacheMutex.withLock { groupDataCache[group.id] } ?: return@withContext null
                ExportProposal(
                    groupId = group.id,
                    groupName = group.remarks,
                    groupGuids = source.map { it.guid }.filter { it.isNotBlank() }.distinct(),
                    visibleGuids = state.servers.map { it.guid }.filter { it.isNotBlank() }.distinct(),
                    selectedGuid = ui.selectedGuid?.takeIf { it.isNotBlank() },
                    filter = ui.statusFilter,
                    searchActive = keyword.isNotBlank(),
                )
            } ?: run { toast(R.string.scoped_test_updating); return@launch }
            val current = _uiState.value
            if (current.selectedGroupId != group.id || current.statusFilter != ui.statusFilter ||
                current.displaySort != ui.displaySort || current.selectedGuid != ui.selectedGuid || keywordFilter != keyword ||
                mutableServerGroupState(group.id).value !== state
            ) {
                toast(R.string.scoped_test_updating)
                return@launch
            }
            _uiState.update { it.copy(exportProposal = proposal) }
        }
    }

    private fun confirmExport(scope: ExportScope) {
        val proposal = _uiState.value.exportProposal ?: return
        _uiState.update { it.copy(exportProposal = null) }
        val guids = exportScopeGuids(proposal, scope)
        if (guids.isEmpty()) return
        if (scope != ExportScope.SELECTED && _uiState.value.groups.none { it.id == proposal.groupId }) {
            toast(R.string.export_scope_changed)
            return
        }
        viewModelScope.launch {
            try {
                val count = withContext(ioDispatcher) {
                    val existing = if (scope == ExportScope.SELECTED) {
                        guids.filter { dataSource.decodeServerConfig(it) != null }.toSet()
                    } else dataSource.getServerGuidList(proposal.groupId).toSet()
                    if (!scopedSnapshotStillExists(guids, existing, true)) return@withContext null
                    dataSource.shareNonCustomConfigsToClipboard(guids)
                }
                when {
                    count == null -> toast(R.string.export_scope_changed)
                    count > 0 -> toast(dataSource.getString(R.string.title_export_config_count, count))
                    else -> toastError(R.string.toast_failure)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Scoped export failed", e)
                toastError(R.string.toast_failure)
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

    private fun prepareSaveOrder() {
        val ui = _uiState.value
        val group = ui.groups.firstOrNull { it.id == ui.selectedGroupId && it.id.isNotEmpty() }
        if (ui.isTesting || isLoading.value || group == null || hasActiveServerFilter(keywordFilter, ui.statusFilter)) {
            toast(R.string.save_order_unavailable)
            return
        }
        val state = mutableServerGroupState(group.id).value
        if (!scopedListReady(state, keywordFilter, ui.statusFilter) || state.appliedSort != ui.displaySort ||
            state.totalCount != state.servers.size || state.servers.isEmpty()
        ) {
            toast(R.string.scoped_test_updating)
            return
        }
        saveOrderPreparationJob?.cancel()
        saveOrderPreparationJob = viewModelScope.launch {
            val snapshot = withContext(defaultDispatcher) {
                SaveOrderProposal(
                    groupId = group.id,
                    groupName = group.remarks,
                    sort = ui.displaySort,
                    expected = state.keywordServers.map { it.guid },
                    proposed = state.servers.map { it.guid },
                )
            }
            val current = _uiState.value
            if (current.isTesting || isLoading.value || current.selectedGroupId != group.id ||
                current.displaySort != ui.displaySort || current.statusFilter != ui.statusFilter ||
                keywordFilter.isNotBlank() || mutableServerGroupState(group.id).value !== state
            ) {
                toast(R.string.scoped_test_updating)
                return@launch
            }
            _uiState.update { it.copy(saveOrderProposal = snapshot) }
        }
    }

    private fun confirmSaveOrder() {
        val proposal = _uiState.value.saveOrderProposal ?: return
        _uiState.update { it.copy(saveOrderProposal = null) }
        if (_uiState.value.isTesting || isLoading.value || _uiState.value.groups.none { it.id == proposal.groupId }) {
            toast(R.string.save_order_unavailable)
            return
        }
        viewModelScope.launch {
            try {
                val saved = withContext(ioDispatcher) {
                    dataSource.reorderServerListIfUnchanged(proposal.groupId, proposal.expected, proposal.proposed)
                }
                if (!saved) {
                    toast(R.string.save_order_changed)
                    return@launch
                }
                withContext(ioDispatcher) { cacheMutex.withLock { groupDataCache.clear() } }
                setupGroupTab(forceRefresh = true)
                toastSuccess(R.string.toast_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to save server order", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id, locateTarget = null) }
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
        _uiState.update { it.copy(searchQuery = keyword, locateTarget = null) }
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

    /** Called on the main thread, where request ownership is maintained. */
    private fun applySelectedGuid(guid: String?) {
        if (_uiState.value.selectedGuid == guid) return
        // Results from the previous active connection must not update the new selection.
        testRequests.invalidateCurrent()
        _uiState.update { stateAfterSelectionChange(it, guid, testRequests.bulk != null) }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        applySelectedGuid(guid)
        refreshSelectedInfo()
    }

    fun refreshSelectedGuid() {
        applySelectedGuid(dataSource.getSelectServer())
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

    private fun moveServer(groupId: String, fromGuid: String, toGuid: String) {
        val ui = uiState.value
        if (ui.isTesting || isLoading.value || ui.selectedGroupId != groupId ||
            ui.displaySort != ServerDisplaySort.DEFAULT || groupId.isEmpty() ||
            hasActiveServerFilter(keywordFilter, statusFilter)
        ) return
        val groupState = mutableServerGroupState(groupId).value
        if (groupState.appliedSort != ServerDisplaySort.DEFAULT ||
            !scopedListReady(groupState, keywordFilter, statusFilter) ||
            groupState.totalCount != groupState.servers.size
        ) return
        val expected = groupState.servers.map { it.guid }
        val positions = serverMovePositions(expected, groupState.rows.map { it.guid }, fromGuid, toGuid) ?: return
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(positions.first, positions.second)) return
        val rows = groupState.rows.toMutableList()
        if (!rows.moveItem(positions.first, positions.second)) return
        val proposed = servers.map { it.guid }
        mutableServerGroupState(groupId).value = groupState.copy(servers = servers, rows = rows, keywordServers = servers, keywordRows = rows)
        // Serialize gesture updates and compare against the precise previous order. A concurrent
        // import, delete, sort, or failed prior gesture must not be overwritten by a stale list.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch {
            previousPersistenceJob?.join()
            try {
                val saved = withContext(ioDispatcher) {
                    val changed = dataSource.reorderServerListIfUnchanged(groupId, expected, proposed)
                    if (changed) cacheMutex.withLock {
                        val cached = groupDataCache[groupId]
                        if (cached != null && cached.map { it.guid } == expected) {
                            val byGuid = cached.associateBy { it.guid }
                            groupDataCache[groupId] = proposed.mapNotNull(byGuid::get)
                        } else {
                            groupDataCache.remove(groupId)
                        }
                    }
                    changed
                }
                if (!saved) {
                    toast(R.string.save_order_changed)
                    setupGroupTab(forceRefresh = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to move server in group $groupId", error)
                toastError(R.string.toast_failure)
                setupGroupTab(forceRefresh = true)
            }
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
                isBulkTesting = testRequests.bulk != null,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected,
                connectionStatus = connectionStatusAfterTestReset(
                    it.connectionStatus, it.isRunning, testRequests.currentPending
                ),
            )
        }
    }

    /** Capture the exact visible IDs before confirmation; never test an asynchronously changed filter. */
    private fun prepareScopedTest(kind: ScopedTestKind) {
        scopedTestPreparationJob?.cancel()
        val ui = _uiState.value
        if (ui.isTesting) return
        if (kind == ScopedTestKind.CURRENT) {
            if (!ui.isRunning) {
                toast(R.string.scoped_test_requires_connection)
                return
            }
            testCurrentServerRealPing()
            return
        }
        val group = ui.groups.firstOrNull { it.id == ui.selectedGroupId } ?: return
        val state = mutableServerGroupState(group.id).value
        val keyword = keywordFilter
        if (!scopedListReady(state, keyword, ui.statusFilter) || state.appliedSort != ui.displaySort) {
            toast(R.string.scoped_test_updating)
            return
        }
        scopedTestPreparationJob = viewModelScope.launch {
            val guids = withContext(defaultDispatcher) { scopedTestGuids(kind, state.servers, state.topMaxMillis) }
            val current = _uiState.value
            if (current.isTesting || current.selectedGroupId != group.id || current.statusFilter != ui.statusFilter || current.displaySort != ui.displaySort ||
                keywordFilter != keyword || mutableServerGroupState(group.id).value !== state
            ) {
                toast(R.string.scoped_test_updating)
                return@launch
            }
            _uiState.update {
                it.copy(scopedTestProposal = ScopedTestProposal(
                    kind, group.id, group.remarks, ui.statusFilter, state.servers.size, keyword.isNotBlank(), guids,
                ))
            }
        }
    }

    private fun confirmScopedTest() {
        val proposal = _uiState.value.scopedTestProposal ?: return
        _uiState.update { it.copy(scopedTestProposal = null) }
        if (_uiState.value.isTesting) return
        if (proposal.kind == ScopedTestKind.CURRENT) {
            if (_uiState.value.isRunning) testCurrentServerRealPing()
            else toast(R.string.scoped_test_requires_connection)
            return
        }
        if (proposal.guids.isEmpty()) return
        viewModelScope.launch {
            try {
                val existing = withContext(ioDispatcher) { dataSource.getServerGuidList(proposal.groupId).toSet() }
                val groupExists = _uiState.value.groups.any { it.id == proposal.groupId }
                if (!scopedSnapshotStillExists(proposal.guids, existing, groupExists)) {
                    toast(R.string.scoped_test_changed)
                    return@launch
                }
                // Another test might have started while the membership was being checked.
                if (_uiState.value.isTesting) return@launch
                startGuidTest(proposal.guids, proposal.kind == ScopedTestKind.TCP, proposal.groupId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to validate scoped test", e)
                toastError(R.string.toast_failure)
            }
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
        beginRun(serverGuids, onlyTcp, groupId)
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
                isBulkTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    /** Real-pings exactly [guids] (any group). Group auto-remove/auto-sort do not apply (no subscriptionId). */
    private fun startGuidTest(guids: List<String>, onlyTcp: Boolean = false, groupId: String = uiState.value.selectedGroupId) {
        if (guids.isEmpty()) return
        cancelAllPing()
        beginRun(guids, onlyTcp, groupId)
        val request = testRequests.beginBulk(groupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = "",
            serverGuids = guids,
            onlyTcp = onlyTcp
        )
        _uiState.update { it.copy(isTesting = true, isBulkTesting = true, status = MainStatus.Testing) }
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

    private fun beginRun(guids: List<String>, onlyTcp: Boolean, groupId: String) {
        runGuids = guids
        runOnlyTcp = onlyTcp
        runGroupId = groupId
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
        resumeGroupId = runGroupId
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
        if (!canStartCurrentConnectionTest(uiState.value.isRunning, uiState.value.isTesting)) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing, connectionStatus = MainStatus.Testing) }
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
        locateJob?.cancel()
        val selected = uiState.value.selectedGuid
        val startingGroupId = uiState.value.selectedGroupId
        if (selected.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        locateJob = viewModelScope.launch {
            try {
                val subscriptionId = withContext(ioDispatcher) {
                    dataSource.decodeServerConfig(selected)?.subscriptionId
                }
                if (subscriptionId == null || uiState.value.selectedGuid != selected ||
                    uiState.value.selectedGroupId != startingGroupId
                ) return@launch
                val groupId = locateGroupId(subscriptionId, uiState.value.groups.map { it.id })
                if (groupId == null) {
                    toastError(R.string.toast_failure)
                    return@launch
                }
                // A hidden target cannot be scrolled into view. Reset filters before rendering it.
                filterConfig("")
                setStatusFilter(ServerStatusFilter.ALL)
                subscriptionIdChanged(groupId)
                withContext(ioDispatcher) { updateGroupUi(groupId, loadGroup(groupId)) }
                val current = uiState.value
                if (current.selectedGuid == selected && current.selectedGroupId == groupId &&
                    current.searchQuery.isEmpty() && current.statusFilter == ServerStatusFilter.ALL
                ) {
                    if (mutableServerGroupState(groupId).value.rows.any { it.guid == selected }) {
                        _uiState.update { it.copy(locateTarget = LocateTarget(groupId, selected)) }
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to locate selected server", error)
                toastError(R.string.toast_failure)
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
                isBulkTesting = testRequests.bulk != null,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                connectionStatus = if (!clearTestingText && state.isRunning == running) state.connectionStatus
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
