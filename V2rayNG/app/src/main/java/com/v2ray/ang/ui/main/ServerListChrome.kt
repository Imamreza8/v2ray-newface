package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * Strip above the server list: live test progress (checked / OK / failed with a progress bar and
 * Cancel, or Continue after a cancelled run) and quick filters by test result with counts.
 */
@Composable
internal fun ServerListChrome(
    uiState: MainUiState,
    controlsExpanded: Boolean,
    onToggleControls: () -> Unit,
    onAction: (MainAction) -> Unit,
) {
    uiState.scopedTestProposal?.let { proposal ->
        ScopedTestConfirmDialog(
            proposal = proposal,
            onConfirm = { onAction(MainAction.ConfirmScopedTest) },
            onDismiss = { onAction(MainAction.DismissScopedTest) },
        )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DisplaySortMenu(
                selected = uiState.displaySort,
                onSelect = { onAction(MainAction.SetDisplaySort(it)) },
            )
            if (controlsExpanded) {
                ServerFilterBar(
                    modifier = Modifier.weight(1f),
                    selected = uiState.statusFilter,
                    counts = uiState.filterCounts,
                    onSelect = { onAction(MainAction.SetStatusFilter(it)) },
                )
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onToggleControls) {
                Icon(
                    painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = stringResource(if (controlsExpanded) R.string.controls_hide else R.string.controls_show),
                    modifier = Modifier.graphicsLayer { rotationZ = if (controlsExpanded) 180f else 0f },
                )
            }
        }
        val stats = uiState.runStats
        if (uiState.isTesting && stats.total > stats.done) {
            TestProgressStrip(stats = stats, onCancel = { onAction(MainAction.CancelTesting) })
        } else if (uiState.resumableCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.spatial_test_stats, stats.done, stats.total, stats.ok, stats.failed),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(MainAction.ResumeTesting) }) {
                    Text(stringResource(R.string.spatial_resume, uiState.resumableCount))
                }
            }
        }
    }
}

@Composable
private fun TestProgressStrip(stats: TestRunStats, onCancel: () -> Unit) {
    val fraction = if (stats.total > 0) (stats.done.toFloat() / stats.total).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.weight(0.35f).height(3.dp).clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
        Text(
            text = stringResource(R.string.spatial_test_stats, stats.done, stats.total, stats.ok, stats.failed),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f),
        )
        TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ServerFilterBar(
    modifier: Modifier = Modifier,
    selected: ServerStatusFilter,
    counts: FilterCounts,
    onSelect: (ServerStatusFilter) -> Unit,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipItem(selected == ServerStatusFilter.ALL, stringResource(R.string.filter_all, counts.all)) { onSelect(ServerStatusFilter.ALL) }
        FilterChipItem(selected == ServerStatusFilter.TOP, stringResource(R.string.filter_top, counts.top)) { onSelect(ServerStatusFilter.TOP) }
        FilterChipItem(selected == ServerStatusFilter.ALIVE, stringResource(R.string.filter_alive, counts.alive)) { onSelect(ServerStatusFilter.ALIVE) }
        FilterChipItem(selected == ServerStatusFilter.UNTESTED, stringResource(R.string.filter_untested, counts.untested)) { onSelect(ServerStatusFilter.UNTESTED) }
        FilterChipItem(selected == ServerStatusFilter.FAILED, stringResource(R.string.filter_failed, counts.failed)) { onSelect(ServerStatusFilter.FAILED) }
    }
}

@Composable
private fun FilterChipItem(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

// ModalBottomSheet is experimental in the pinned Material 3 version; reevaluate when its stable
// equivalent is available. ScopedTestRequestsTest covers the selection/confirmation contract.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TestOptionsSheet(
    enabled: Boolean,
    visibleTestCount: Int?,
    onChoose: (ScopedTestKind) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.scoped_test_options), style = MaterialTheme.typography.titleMedium)
            ScopedTestKind.entries.forEach { kind ->
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    onClick = { onChoose(kind) },
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(scopedTestLabel(kind)))
                        if (kind == ScopedTestKind.REAL && visibleTestCount != null) {
                            Text(stringResource(R.string.scoped_test_count, visibleTestCount),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopedTestConfirmDialog(proposal: ScopedTestProposal, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val isCurrent = proposal.kind == ScopedTestKind.CURRENT
    val filterLabel = if (!isCurrent) {
        val resource = when (proposal.filter) {
            ServerStatusFilter.ALL -> R.string.filter_all
            ServerStatusFilter.TOP -> R.string.filter_top
            ServerStatusFilter.ALIVE -> R.string.filter_alive
            ServerStatusFilter.UNTESTED -> R.string.filter_untested
            ServerStatusFilter.FAILED -> R.string.filter_failed
        }
        stringResource(resource, proposal.visibleCount)
    } else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(scopedTestLabel(proposal.kind))) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (isCurrent) stringResource(R.string.scoped_test_current_scope)
                    else stringResource(R.string.scoped_test_scope, proposal.guids.size, proposal.groupName, filterLabel)
                )
                if (proposal.kind == ScopedTestKind.TOP) Text(stringResource(R.string.scoped_test_top_note))
                if (!isCurrent && proposal.searchActive) Text(stringResource(R.string.scoped_test_search))
                if (!isCurrent && proposal.guids.isEmpty()) Text(stringResource(R.string.scoped_test_empty))
                if (!isCurrent) Text(stringResource(R.string.scoped_test_no_auto_actions))
            }
        },
        confirmButton = {
            TextButton(enabled = isCurrent || proposal.guids.isNotEmpty(), onClick = onConfirm) {
                Text(stringResource(R.string.spatial_speed_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun scopedTestLabel(kind: ScopedTestKind): Int = when (kind) {
    ScopedTestKind.REAL -> R.string.scoped_test_real
    ScopedTestKind.TCP -> R.string.scoped_test_tcp
    ScopedTestKind.TOP -> R.string.scoped_test_top
    ScopedTestKind.FAILED -> R.string.scoped_test_failed
    ScopedTestKind.CURRENT -> R.string.scoped_test_current
}

@Composable
internal fun DisplaySortMenu(selected: ServerDisplaySort, onSelect: (ServerDisplaySort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_sort_display_24dp), stringResource(R.string.display_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ServerDisplaySort.entries.forEach { sort ->
                val label = when (sort) {
                    ServerDisplaySort.DEFAULT -> R.string.display_sort_default
                    ServerDisplaySort.PING -> R.string.display_sort_ping
                    ServerDisplaySort.NAME -> R.string.display_sort_name
                }
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    modifier = Modifier.semantics { this.selected = sort == selected },
                    onClick = { expanded = false; onSelect(sort) },
                )
            }
        }
    }
}
