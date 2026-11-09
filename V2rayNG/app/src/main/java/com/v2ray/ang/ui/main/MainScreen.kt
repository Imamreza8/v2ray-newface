package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.v2ray.ang.ui.compose.SpatialBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    // The current connection result is independent of bulk-test progress and selected profile delay.
    val bottomText = mainViewModel.formatStatus(uiState.connectionStatus)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap
    val topState by mainViewModel.topState.collectAsStateWithLifecycle()
    var showTopSheet by remember { mutableStateOf(false) }
    var showFullList by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { onAction(MainAction.RefreshTop) }

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val searchQuery = uiState.searchQuery
    var showGroupChooser by rememberSaveable { mutableStateOf(false) }
    var showSubscriptionDetails by rememberSaveable { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(uiState.selectedGroupId) { showSubscriptionDetails = false; showGroupChooser = false }
    val listNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                controlsExpanded = controlsExpandedAfterScroll(controlsExpanded, available.x, available.y)
                return Offset.Zero
            }
        }
    }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    uiState.exportProposal?.let { proposal ->
        ExportScopeDialog(
            proposal = proposal,
            onConfirm = { onAction(MainAction.ConfirmExport(it)) },
            onDismiss = { onAction(MainAction.DismissExport) },
        )
    }

    uiState.saveOrderProposal?.let { proposal ->
        SaveOrderDialog(
            proposal = proposal,
            onConfirm = { onAction(MainAction.ConfirmSaveOrder) },
            onDismiss = { onAction(MainAction.DismissSaveOrder) },
        )
    }

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }
    if (showTopSheet) {
        TopConfigsSheet(
            topState = topState,
            selectedGuid = selectedGuid,
            isTesting = uiState.isTesting,
            onAction = onAction,
            onDismiss = { showTopSheet = false },
        )
    }
    uiState.expandProposal?.let { proposal ->
        ExpandDialog(proposal = proposal, onAction = onAction)
    }
    uiState.autoSelectProposal?.let { proposal ->
        AutoSelectDialog(
            proposal = proposal,
            isRunning = isRunning,
            onAction = onAction,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        if (!showFullList) {
            SpatialHome(
                uiState = uiState,
                statusText = displayText,
                onAction = onAction,
                onOpenMenu = { scope.launch { drawerState.open() } },
                onOpenList = { showFullList = true },
                onOpenTop = {
                    onAction(MainAction.RefreshTop)
                    showTopSheet = true
                },
            )
        } else {
        BackHandler { showFullList = false }
        BackHandler(enabled = showSearch) {
            onAction(MainAction.Search(""))
            showSearch = false
        }
        GroupSelector(
            groups = groups,
            selectedGroupId = uiState.selectedGroupId,
            mainViewModel = mainViewModel,
            onSelectGroup = { groupId ->
                scope.launch {
                    val target = latestGroups.indexOfFirst { it.id == groupId }
                    if (target >= 0) {
                        onAction(MainAction.SelectGroup(groupId))
                        pagerState.scrollToPage(target)
                    }
                }
            },
            onManageGroups = { onNavigate(MainDestination.Subscriptions) },
            showGroups = showGroupChooser,
            onShowGroups = { showGroupChooser = it },
            showDetails = showSubscriptionDetails,
            onShowDetails = { showSubscriptionDetails = it },
            onAction = onAction,
        )
        val connectionBarHaze = rememberHazeState()
        var footerHeightPx by remember { mutableIntStateOf(0) }
        var requestedConnectionBar by remember { mutableStateOf(false) }
        val navigationInsetPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
        Box(modifier = Modifier.fillMaxSize()) {
        // Use the actual active pager page, not a row index, to observe only its list/grid.
        val activeGroupId = groups.getOrNull(pagerState.currentPage)?.id
        val serverListScrolling = if (doubleColumnDisplay) {
            activeGroupId?.let { lazyGridStates[it]?.isScrollInProgress } == true
        } else {
            activeGroupId?.let { lazyListStates[it]?.isScrollInProgress } == true
        }
        LaunchedEffect(activeGroupId) { requestedConnectionBar = false }
        LaunchedEffect(serverListScrolling, pagerState.isScrollInProgress) {
            if (serverListScrolling || pagerState.isScrollInProgress) requestedConnectionBar = false
        }
        val barOverlapPx = (footerHeightPx - navigationInsetPx).coerceAtLeast(0)
        val lastRowCovered by remember(activeGroupId, doubleColumnDisplay, barOverlapPx) {
            derivedStateOf {
                if (doubleColumnDisplay) {
                    activeGroupId?.let { lazyGridStates[it] }?.layoutInfo?.let { info ->
                        val last = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 1 }
                        val bottom = last?.let { cell ->
                            info.visibleItemsInfo.filter { it.row == cell.row }
                                .maxOfOrNull { it.offset.y + it.size.height }
                        }
                        lastRowCoveredByBar(bottom, info.viewportEndOffset, barOverlapPx)
                    } ?: false
                } else {
                    activeGroupId?.let { lazyListStates[it] }?.layoutInfo?.let { info ->
                        val last = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 1 }
                        lastRowCoveredByBar(last?.let { it.offset + it.size }, info.viewportEndOffset, barOverlapPx)
                    } ?: false
                }
            }
        }
        // Capture both backdrop and server list, but not the floating bar itself.
        Box(modifier = Modifier.fillMaxSize().hazeSource(connectionBarHaze)) {
        // Same space backdrop as the home screen, so the list screen no longer looks like another app
        SpatialBackground()
        val selectedGroupState = if (groups.isNotEmpty()) key(uiState.selectedGroupId) {
            val selectedGroupFlow = remember(uiState.selectedGroupId) {
                mainViewModel.serverGroupState(uiState.selectedGroupId)
            }
            val state by selectedGroupFlow.collectAsStateWithLifecycle()
            state
        } else null
        val visibleTestCount = selectedGroupState?.let {
            visibleScopedTestCount(it, searchQuery, uiState.statusFilter, uiState.displaySort)
        }
        Scaffold(
            containerColor = Color.Transparent,
            // Transparent Scaffold cannot infer a contrasting foreground from its container.
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    displaySort = uiState.displaySort,
                    showSubscriptionDetails = hasSubscriptionDetails(groups.firstOrNull { it.id == uiState.selectedGroupId }),
                    isTesting = uiState.isTesting,
                    isRunning = isRunning,
                    testEnabled = groups.isNotEmpty(),
                    visibleTestCount = visibleTestCount,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onShowConnectionBar = { requestedConnectionBar = true },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    groupSelector = {
                        GroupSelectorTrigger(groups, uiState.selectedGroupId, mainViewModel) { showGroupChooser = true }
                    },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.PrepareSaveOrder)
                            MainMoreMenuAction.GroupDetails -> { showSubscriptionDetails = true }
                            MainMoreMenuAction.TestAll -> onAction(MainAction.PrepareScopedTest(ScopedTestKind.TCP))
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.PrepareScopedTest(ScopedTestKind.REAL))
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },
            floatingActionButton = {},
        ) { innerPadding ->
            if (groups.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(listNestedScroll)
                ) {
                    if (controlsExpanded) GroupSubscriptionSummary(groups, uiState.selectedGroupId) {
                        showSubscriptionDetails = true
                    }
                    ServerListChrome(
                        uiState = uiState,
                        visibleCount = visibleTestCount,
                        totalCount = selectedGroupState?.totalCount,
                        controlsExpanded = controlsExpanded,
                        onToggleControls = { controlsExpanded = !controlsExpanded },
                        onAction = onAction,
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                    ) { page ->
                        val group = groups.getOrNull(page) ?: return@HorizontalPager

                        GroupPagerPage(
                            groupId = group.id,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            locateTarget = uiState.locateTarget,
                            doubleColumnDisplay = doubleColumnDisplay,
                            manualReorderAllowed = !isLoading && !uiState.isTesting &&
                                uiState.displaySort == ServerDisplaySort.DEFAULT &&
                                !hasActiveServerFilter(searchQuery, uiState.statusFilter),
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                            onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = removeServer,
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = 0.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                }
            }
        }
        }
        AnimatedVisibility(
            visible = connectionBarVisible(
                pagerState.isScrollInProgress, serverListScrolling, lastRowCovered, requestedConnectionBar,
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            MainBottomBar(
                displayText = bottomText,
                connectionStatus = uiState.connectionStatus,
                isRunning = isRunning,
                isTesting = uiState.isTesting,
                isDarkTheme = isDarkTheme,
                hazeState = connectionBarHaze,
                onAction = onAction,
                modifier = Modifier.onSizeChanged { if (it.height > 0) footerHeightPx = it.height },
            )
        }
        }
        }
    }
}
