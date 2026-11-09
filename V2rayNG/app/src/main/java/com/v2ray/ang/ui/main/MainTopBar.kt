package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
fun MainTopBar(
    isLoading: Boolean,
    displaySort: ServerDisplaySort,
    showSubscriptionDetails: Boolean,
    isTesting: Boolean,
    isRunning: Boolean,
    testEnabled: Boolean,
    visibleTestCount: Int?,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onShowConnectionBar: () -> Unit,
    onMenuClick: () -> Unit,
    groupSelector: @Composable () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showManualImport by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTestOptions by rememberSaveable { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    if (showManualImport) {
        ManualImportDialog(onAction = onAction, onDismiss = { showManualImport = false })
    }
    if (showTestOptions) {
        TestOptionsSheet(
            isTesting = isTesting,
            isRunning = isRunning,
            visibleTestCount = visibleTestCount,
            onChoose = { kind ->
                showTestOptions = false
                onAction(actionForTestChoice(kind))
            },
            onDismiss = { showTestOptions = false },
        )
    }
    if (showMenu) {
        MoreMenuDialog(
            displaySort = displaySort,
            showDetails = showSubscriptionDetails,
            onSort = { onAction(MainAction.SetDisplaySort(it)) },
            onSearch = { showMenu = false; onSearchToggle(true) },
            onShowConnectionBar = { showMenu = false; onShowConnectionBar() },
            onSelected = { action -> showMenu = false; onMoreMenuAction(action) },
            onDismiss = { showMenu = false },
        )
    }

    AppTopBar(
        containerColor = Color.Transparent,
        title = stringResource(R.string.title_server),
        titleContent = groupSelector,
        onBackClick = {},
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        navigationIcon = {
            if (showSearch) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back))
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = stringResource(R.string.acc_open_menu))
                }
            }
        },
        actions = {
            IconButton(onClick = { showTestOptions = true }, enabled = testEnabled && !isTesting) {
                Icon(
                    painterResource(R.drawable.ic_flash_on_24dp),
                    contentDescription = stringResource(R.string.scoped_test_options),
                    tint = if (testEnabled && !isTesting) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showImportMenu = true }) {
                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add))
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    scrollState = importMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(importMenuScrollState)
                ) {
                    ImportMenuContent(
                        onAction = { action ->
                            showImportMenu = false
                            onAction(action)
                        },
                        onManual = { showImportMenu = false; showManualImport = true },
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.acc_more))
                }

            }
        }
    )
}
