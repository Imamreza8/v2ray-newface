package com.v2ray.ang.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DeleteConfirmDialog

data class ServerDeleteTarget(
    val guid: String,
    val profileName: String,
) {
    companion object {
        val Saver = listSaver<ServerDeleteTarget?, String>(
            save = { target -> target?.let { listOf(it.guid, it.profileName) }.orEmpty() },
            restore = { ServerDeleteTarget(it[0], it[1]) },
        )
    }
}

@Composable
fun MainDialogs(
    showDelAllConfirm: Boolean,
    onDismissDelAll: () -> Unit,
    onConfirmDelAll: () -> Unit,
    showDelDuplicateConfirm: Boolean,
    onDismissDelDuplicate: () -> Unit,
    onConfirmDelDuplicate: () -> Unit,
    showDelInvalidConfirm: Boolean,
    onDismissDelInvalid: () -> Unit,
    onConfirmDelInvalid: () -> Unit,
    showRemoveConfirm: ServerDeleteTarget?,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (String) -> Unit,
) {
    if (showDelAllConfirm) {
        BulkDeleteWarningDialog(
            message = stringResource(R.string.confirm_delete_visible_profiles),
            onConfirm = onConfirmDelAll,
            onDismiss = onDismissDelAll
        )
    }
    if (showDelDuplicateConfirm) {
        BulkDeleteWarningDialog(
            message = stringResource(R.string.confirm_delete_duplicate_profiles),
            onConfirm = onConfirmDelDuplicate,
            onDismiss = onDismissDelDuplicate
        )
    }
    if (showDelInvalidConfirm) {
        BulkDeleteWarningDialog(
            message = stringResource(R.string.confirm_delete_invalid_profiles),
            onConfirm = onConfirmDelInvalid,
            onDismiss = onDismissDelInvalid
        )
    }
    showRemoveConfirm?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = target.profileName,
            onConfirm = { onConfirmRemove(target.guid) },
            onDismiss = onDismissRemove
        )
    }
}

/** Keep existing actions available while warning that legacy multi-store deletion is not atomic. */
@Composable
private fun BulkDeleteWarningDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.material3.Text(message)
                androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 12.dp))
                androidx.compose.material3.Text(stringResource(R.string.bulk_delete_legacy_warning))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) {
                androidx.compose.material3.Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun SaveOrderDialog(proposal: SaveOrderProposal, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val order = when (proposal.sort) {
        ServerDisplaySort.DEFAULT -> R.string.display_sort_default
        ServerDisplaySort.PING -> R.string.display_sort_ping
        ServerDisplaySort.NAME -> R.string.display_sort_name
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.save_order_title)) },
        text = {
            androidx.compose.material3.Text(
                stringResource(R.string.save_order_confirm, proposal.groupName, proposal.proposed.size, stringResource(order))
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(stringResource(R.string.routing_settings_save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun ExportScopeDialog(proposal: ExportProposal, onConfirm: (ExportScope) -> Unit, onDismiss: () -> Unit) {
    var scope by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(ExportScope.VISIBLE) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.export_scope_title)) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.material3.Text(stringResource(R.string.export_scope_note))
                ExportScope.entries.forEach { item ->
                    val label = when (item) {
                        ExportScope.GROUP -> stringResource(R.string.export_scope_group, proposal.groupName, proposal.groupGuids.size)
                        ExportScope.VISIBLE -> stringResource(R.string.export_scope_visible, proposal.visibleGuids.size)
                        ExportScope.SELECTED -> stringResource(R.string.export_scope_selected, if (proposal.selectedGuid == null) 0 else 1)
                    }
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = scope == item, role = Role.RadioButton, onClick = { scope = item }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = scope == item, onClick = null)
                        androidx.compose.material3.Text(label)
                    }
                }
                if (scope == ExportScope.VISIBLE) {
                    val filterLabel = when (proposal.filter) {
                        ServerStatusFilter.ALL -> R.string.filter_all
                        ServerStatusFilter.TOP -> R.string.filter_top
                        ServerStatusFilter.ALIVE -> R.string.filter_alive
                        ServerStatusFilter.UNTESTED -> R.string.filter_untested
                        ServerStatusFilter.FAILED -> R.string.filter_failed
                    }
                    androidx.compose.material3.Text(
                        stringResource(R.string.scoped_test_scope, proposal.visibleGuids.size, proposal.groupName,
                            stringResource(filterLabel, proposal.visibleGuids.size))
                    )
                    if (proposal.searchActive) androidx.compose.material3.Text(stringResource(R.string.scoped_test_search))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(scope) },
                enabled = exportScopeGuids(proposal, scope).isNotEmpty(),
            ) { androidx.compose.material3.Text(stringResource(R.string.menu_item_export_proxy_app)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(stringResource(R.string.action_cancel)) }
        },
    )
}
