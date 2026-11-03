package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

/** Ordinals are display-only; all selection and updates still use the original group ID. */
internal fun duplicateGroupOrdinal(groups: List<GroupMapItem>, id: String): Int? {
    val group = groups.firstOrNull { it.id == id } ?: return null
    val sameName = groups.filter { it.remarks == group.remarks }
    if (sameName.size < 2) return null
    return sameName.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1)
}

@Composable
internal fun GroupSelector(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    mainViewModel: MainViewModel,
    onSelectGroup: (String) -> Unit,
    onManageGroups: () -> Unit,
    onAction: (MainAction) -> Unit,
) {
    val selected = groups.firstOrNull { it.id == selectedGroupId } ?: return
    val selectedGroupFlow = remember(selected.id, mainViewModel) { mainViewModel.serverGroupState(selected.id) }
    val selectedState by selectedGroupFlow.collectAsStateWithLifecycle()
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedGroupId) { showDetails = false }
    val selectedLabel = groupLabel(groups, selected)
    val chooserDescription = stringResource(R.string.spatial_choose_group_current, selectedLabel)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { showGroups = true },
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = chooserDescription },
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            selectedState.totalCount?.let { count ->
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.spatial_group_count, count), maxLines = 1)
            }
            Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
        }
        if (selected.id.isNotEmpty()) {
            TextButton(onClick = { showDetails = true }) {
                Text(stringResource(R.string.spatial_subscription_details))
            }
        }
    }

    if (showGroups) {
        var query by rememberSaveable { mutableStateOf("") }
        val visibleGroups = remember(groups, query) {
            groups.filter { it.remarks.contains(query.trim(), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showGroups = false },
            title = { Text(stringResource(R.string.spatial_choose_group)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.menu_item_search)) },
                        singleLine = true,
                    )
                    if (visibleGroups.isEmpty()) {
                        Text(
                            stringResource(R.string.spatial_no_matching_groups),
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    visibleGroups.forEach { group ->
                        key(group.id) {
                            GroupChoice(
                                group = group,
                                label = groupLabel(groups, group),
                                selected = group.id == selectedGroupId,
                                mainViewModel = mainViewModel,
                                onSelect = { showGroups = false; onSelectGroup(group.id) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroups = false; onManageGroups() }) {
                    Text(stringResource(R.string.title_sub_setting))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroups = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    val usage = selected.usageInfo
    if (showDetails && selected.id.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(groupLabel(groups, selected)) },
            text = {
                if (usage != null) {
                    SubscriptionInfoCard(
                        info = usage,
                        fallbackTitle = selected.remarks,
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                        onRefresh = { onAction(MainAction.UpdateGroupSubscription(selected.id)) },
                    )
                } else {
                    Column {
                        Text(stringResource(R.string.spatial_no_subscription_details))
                        TextButton(onClick = { onAction(MainAction.UpdateGroupSubscription(selected.id)) }) {
                            Text(stringResource(R.string.title_sub_update))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun groupLabel(groups: List<GroupMapItem>, group: GroupMapItem): String {
    val ordinal = remember(groups, group.id, group.remarks) { duplicateGroupOrdinal(groups, group.id) }
    return if (ordinal == null) group.remarks
    else stringResource(R.string.spatial_duplicate_group_name, group.remarks, ordinal)
}

@Composable
private fun GroupChoice(
    group: GroupMapItem,
    label: String,
    selected: Boolean,
    mainViewModel: MainViewModel,
    onSelect: () -> Unit,
) {
    val groupFlow = remember(group.id, mainViewModel) { mainViewModel.serverGroupState(group.id) }
    val state by groupFlow.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        state.totalCount?.let { count ->
            Text(stringResource(R.string.spatial_group_count, count), style = MaterialTheme.typography.bodySmall)
        }
    }
}
