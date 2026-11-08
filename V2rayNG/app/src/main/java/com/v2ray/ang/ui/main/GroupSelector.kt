package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.util.Utils

@Composable
internal fun GroupSelector(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    mainViewModel: MainViewModel,
    onSelectGroup: (String) -> Unit,
    onManageGroups: () -> Unit,
    showGroups: Boolean,
    onShowGroups: (Boolean) -> Unit,
    showDetails: Boolean,
    onShowDetails: (Boolean) -> Unit,
    onAction: (MainAction) -> Unit,
) {
    val selected = groups.firstOrNull { it.id == selectedGroupId } ?: return
    val usage = selected.usageInfo

    if (showGroups) {
        var query by rememberSaveable { mutableStateOf("") }
        val visibleGroups = remember(groups, query) {
            groups.filter { it.remarks.contains(query.trim(), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { onShowGroups(false) },
            title = { Text(stringResource(R.string.spatial_choose_group)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.menu_item_search)) },
                            singleLine = true,
                        )
                    }
                    if (visibleGroups.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.spatial_no_matching_groups),
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(visibleGroups, key = { "group:${it.id}" }) { group ->
                        val groupFlow = remember(group.id, mainViewModel) { mainViewModel.serverGroupState(group.id) }
                        val groupState by groupFlow.collectAsStateWithLifecycle()
                        GroupChoice(
                            label = group.remarks,
                            count = groupState.totalCount,
                            selected = group.id == selectedGroupId,
                            onSelect = { onShowGroups(false); onSelectGroup(group.id) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowGroups(false); onManageGroups() }) {
                    Text(stringResource(R.string.title_sub_setting))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowGroups(false) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (showDetails && hasSubscriptionDetails(selected)) {
        AlertDialog(
            onDismissRequest = { onShowDetails(false) },
            title = { Text(selected.remarks) },
            text = {
                if (usage != null) {
                    SubscriptionInfoCard(
                        info = usage,
                        fallbackTitle = selected.remarks,
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                        onRefresh = { onAction(MainAction.UpdateGroupSubscription(selected.id)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowDetails(false) }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
internal fun GroupSubscriptionSummary(groups: List<GroupMapItem>, selectedGroupId: String, onOpen: () -> Unit) {
    val selected = groups.firstOrNull { it.id == selectedGroupId } ?: return
    if (!hasSubscriptionDetails(selected)) return
    val usage = selected.usageInfo ?: return
    val summary = remember(usage) { buildSubscriptionInfoUiModel(usage, System.currentTimeMillis()) }
    val traffic = if (summary.hasTrafficData) {
        val total = if (summary.totalBytes > 0L) summary.totalBytes.toTrafficString()
            else stringResource(R.string.subscription_total_unknown)
        stringResource(R.string.sub_info_traffic, summary.usedBytes.toTrafficString(), total)
    } else null
    val expiry = summary.expireMillis?.let { Utils.formatTimestamp(it, EXPIRY_DATE_PATTERN) }
    val line = when {
        traffic != null && expiry != null -> stringResource(R.string.subscription_summary_line, traffic, expiry)
        traffic != null -> traffic
        expiry != null -> stringResource(R.string.subscription_expiry_only, expiry)
        else -> stringResource(R.string.spatial_subscription_details)
    }
    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun GroupSelectorTrigger(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    mainViewModel: MainViewModel,
    onOpen: () -> Unit,
) {
    val selected = groups.firstOrNull { it.id == selectedGroupId } ?: return
    androidx.compose.runtime.key(selected.id) {
        val selectedGroupFlow = remember(selected.id, mainViewModel) { mainViewModel.serverGroupState(selected.id) }
        val selectedState by selectedGroupFlow.collectAsStateWithLifecycle()
        val description = selectedState.totalCount?.let { count ->
            stringResource(R.string.spatial_choose_group_current_with_count, selected.remarks, count)
        } ?: stringResource(R.string.spatial_choose_group_current, selected.remarks)
        val shape = RoundedCornerShape(24.dp)
        TextButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .semantics(mergeDescendants = true) { contentDescription = description },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Text(selected.remarks, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            selectedState.totalCount?.let { count ->
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.spatial_config_count_short, count), maxLines = 1)
            }
            Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
        }
    }
}

@Composable
private fun GroupChoice(
    label: String,
    count: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.spatial_group_count, it), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
