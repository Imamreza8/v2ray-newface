package com.v2ray.ang.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.TopConfigManager
import com.v2ray.ang.ui.compose.LocalSpatialTheme
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Top configs sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopConfigsSheet(
    topState: TopUiState,
    selectedGuid: String?,
    isTesting: Boolean,
    onAction: (MainAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.spatial_top_configs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.top_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            // Max ping threshold
            ChipRow(label = stringResource(R.string.top_threshold)) {
                listOf(150L, 250L, 400L).forEach { ms ->
                    FilterChip(
                        selected = topState.maxDelayMillis == ms,
                        onClick = { onAction(MainAction.SetTopFilter(topState.limit, ms, topState.countryCode, topState.countrySource)) },
                        label = { Text(stringResource(R.string.server_test_delay_value, ms)) },
                    )
                }
            }
            // List size
            ChipRow(label = stringResource(R.string.top_limit)) {
                listOf(30, 50).forEach { n ->
                    FilterChip(
                        selected = topState.limit == n,
                        onClick = { onAction(MainAction.SetTopFilter(n, topState.maxDelayMillis, topState.countryCode, topState.countrySource)) },
                        label = { Text(n.toString()) },
                    )
                }
            }
            // Where the country comes from: flag in the name (estimate) or exit IP learned from your own tests
            ChipRow(label = stringResource(R.string.top_country_source)) {
                TopConfigManager.CountrySource.values().forEach { source ->
                    FilterChip(
                        selected = topState.countrySource == source,
                        onClick = {
                            onAction(MainAction.SetTopFilter(topState.limit, topState.maxDelayMillis, null, source))
                        },
                        label = {
                            Text(
                                stringResource(
                                    if (source == TopConfigManager.CountrySource.NAME) R.string.top_source_name
                                    else R.string.top_source_exit
                                )
                            )
                        },
                    )
                }
            }
            if (topState.countrySource == TopConfigManager.CountrySource.EXIT) {
                Text(
                    text = stringResource(R.string.top_source_exit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            val countries = remember(topState.entries, topState.countryCode) {
                (topState.entries.mapNotNull { it.countryCode } + listOfNotNull(topState.countryCode))
                    .distinct().sorted()
            }
            if (countries.isNotEmpty()) {
                ChipRow(label = stringResource(R.string.top_country)) {
                    FilterChip(
                        selected = topState.countryCode == null,
                        onClick = { onAction(MainAction.SetTopFilter(topState.limit, topState.maxDelayMillis, null, topState.countrySource)) },
                        label = { Text(stringResource(R.string.top_country_all)) },
                    )
                    countries.forEach { code ->
                        FilterChip(
                            selected = topState.countryCode == code,
                            onClick = {
                                onAction(MainAction.SetTopFilter(topState.limit, topState.maxDelayMillis, code, topState.countrySource))
                            },
                            label = { Text(TopConfigManager.flagEmoji(code) + " " + code) },
                        )
                    }
                }
            }

            // Any value, not only the presets above
            var delayText by remember(topState.maxDelayMillis) { mutableStateOf(topState.maxDelayMillis.toString()) }
            var limitText by remember(topState.limit) { mutableStateOf(topState.limit.toString()) }
            val delayValue = TopConfigManager.parseMaxDelay(delayText)
            val limitValue = TopConfigManager.parseLimit(limitText)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = delayText,
                    onValueChange = { delayText = it },
                    label = { Text(stringResource(R.string.top_threshold)) },
                    isError = delayValue == null,
                    supportingText = { if (delayValue == null) Text(stringResource(R.string.top_invalid_delay)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text(stringResource(R.string.top_limit)) },
                    isError = limitValue == null,
                    supportingText = { if (limitValue == null) Text(stringResource(R.string.top_invalid_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = delayValue != null && limitValue != null,
                    onClick = {
                        if (delayValue != null && limitValue != null) {
                            onAction(MainAction.SetTopFilter(limitValue, delayValue, topState.countryCode, topState.countrySource))
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.top_apply)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(enabled = !isTesting, onClick = { onAction(MainAction.TestTopConfigs) }) {
                    Text(stringResource(R.string.spatial_test_top))
                }
                TextButton(enabled = !isTesting, onClick = {
                    onAction(MainAction.AutoSelect)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.spatial_auto_select))
                }
            }

            if (topState.entries.isEmpty()) {
                Text(
                    text = if (topState.isLoading) stringResource(R.string.top_loading) else stringResource(R.string.top_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    items(topState.entries, key = { it.guid }) { entry ->
                        TopEntryRow(
                            entry = entry,
                            selected = entry.guid == selectedGuid,
                            onClick = {
                                onAction(MainAction.SelectServer(entry.guid))
                                onDismiss()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        chips()
    }
}

@Composable
private fun TopEntryRow(entry: TopConfigManager.Entry, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.remarks,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val candidate = if (entry.isCandidate) " · " + stringResource(R.string.top_candidate) else ""
            Text(
                text = entry.protocol + " · " + formatAge(entry.lastTestTime) + " · " +
                        stringResource(R.string.top_stability, entry.stability) + candidate,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.server_test_delay_value, entry.delayMillis),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** "5 min ago" style age of the stored result, so an old result is never mistaken for a fresh one. */
@Composable
private fun formatAge(lastTestTime: Long): String {
    if (lastTestTime <= 0L) return stringResource(R.string.top_age_unknown)
    val minutes = ((System.currentTimeMillis() - lastTestTime) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 60L -> stringResource(R.string.top_age_minutes, minutes)
        minutes < 60L * 24L -> stringResource(R.string.top_age_hours, minutes / 60L)
        else -> stringResource(R.string.top_age_days, minutes / (60L * 24L))
    }
}

// ---------------------------------------------------------------------------------------------
// Auto-select confirmation
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AutoSelectDialog(
    proposal: AutoSelectProposal,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(MainAction.DismissAutoSelect) },
        title = { Text(stringResource(R.string.auto_select_title)) },
        text = {
            Text(
                stringResource(
                    R.string.auto_select_message,
                    proposal.remarks.ifBlank { proposal.guid },
                    proposal.delayMillis,
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(MainAction.ApplyAutoSelect(proposal.guid, reconnect = isRunning || proposal.connect)) }
            ) {
                Text(
                    stringResource(
                        when {
                            proposal.connect && !isRunning -> R.string.auto_select_apply_connect
                            isRunning -> R.string.auto_select_apply_reconnect
                            else -> R.string.auto_select_apply
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MainAction.DismissAutoSelect) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Asks before testing more configs when nothing in the top list answered. */
@Composable
internal fun ExpandDialog(
    proposal: ExpandProposal,
    onAction: (MainAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(MainAction.DismissExpand) },
        title = { Text(stringResource(R.string.auto_select_expand_title)) },
        text = { Text(stringResource(R.string.auto_select_expand_message, proposal.count)) },
        confirmButton = {
            TextButton(onClick = { onAction(MainAction.ConfirmExpand) }) {
                Text(stringResource(R.string.auto_select_expand_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MainAction.DismissExpand) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
