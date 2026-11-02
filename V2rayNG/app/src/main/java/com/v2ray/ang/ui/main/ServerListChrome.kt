package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    onAction: (MainAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(MainAction.ResumeTesting) }) {
                    Text(stringResource(R.string.spatial_resume, uiState.resumableCount))
                }
            }
        }
        ServerFilterBar(
            selected = uiState.statusFilter,
            counts = uiState.filterCounts,
            onSelect = { onAction(MainAction.SetStatusFilter(it)) },
        )
    }
}

@Composable
private fun TestProgressStrip(stats: TestRunStats, onCancel: () -> Unit) {
    val fraction = if (stats.total > 0) (stats.done.toFloat() / stats.total).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.spatial_test_stats, stats.done, stats.total, stats.ok, stats.failed),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
        // A plain two-box progress bar (no dependency on a particular Material 3 progress API)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ServerFilterBar(
    selected: ServerStatusFilter,
    counts: FilterCounts,
    onSelect: (ServerStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
