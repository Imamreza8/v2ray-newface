package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.SelectListDialog

internal enum class ImportMenuAction(@StringRes val labelRes: Int, val action: MainAction) {
    QRCode(R.string.menu_item_import_config_qrcode, MainAction.ImportQRcode),
    Clipboard(R.string.menu_item_import_config_clipboard, MainAction.ImportClipboard),
    LocalFile(R.string.menu_item_import_config_local, MainAction.ImportConfigLocal),
    PolicyGroup(R.string.menu_item_import_config_policy_group, MainAction.ImportManually(EConfigType.POLICYGROUP.value)),
    ProxyChain(R.string.menu_item_import_config_proxy_chain, MainAction.ImportManually(EConfigType.PROXYCHAIN.value)),
    Vmess(R.string.menu_item_import_config_manually_vmess, MainAction.ImportManually(EConfigType.VMESS.value)),
    Vless(R.string.menu_item_import_config_manually_vless, MainAction.ImportManually(EConfigType.VLESS.value)),
    Shadowsocks(R.string.menu_item_import_config_manually_ss, MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)),
    Socks(R.string.menu_item_import_config_manually_socks, MainAction.ImportManually(EConfigType.SOCKS.value)),
    Http(R.string.menu_item_import_config_manually_http, MainAction.ImportManually(EConfigType.HTTP.value)),
    Trojan(R.string.menu_item_import_config_manually_trojan, MainAction.ImportManually(EConfigType.TROJAN.value)),
    WireGuard(R.string.menu_item_import_config_manually_wireguard, MainAction.ImportManually(EConfigType.WIREGUARD.value)),
    Hysteria2(R.string.menu_item_import_config_manually_hysteria2, MainAction.ImportManually(EConfigType.HYSTERIA2.value))
}

enum class MainMoreMenuAction(@StringRes val labelRes: Int) {
    RestartService(R.string.title_service_restart),
    DeleteAll(R.string.title_del_all_config),
    DeleteDuplicate(R.string.title_del_duplicate_config),
    DeleteInvalid(R.string.title_del_invalid_config),
    ExportAll(R.string.title_export_all),
    LocateSelected(R.string.title_locate_selected_config),
    SortByTestResults(R.string.save_order_title),
    GroupDetails(R.string.spatial_subscription_details),
    TestAll(R.string.title_ping_all_server),
    TestAllRealPing(R.string.title_real_ping_all_server),
    UpdateSubscriptions(R.string.title_sub_update)
}

internal enum class ServerMenuAction(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQRCode(R.string.share_method_qrcode, isShareAction = true, supportsComplexProfiles = false),
    ShareClipboard(R.string.share_method_clipboard, isShareAction = true, supportsComplexProfiles = false),
    ShareFullContent(R.string.share_method_full_content, isShareAction = true, supportsComplexProfiles = true),
    Edit(R.string.action_edit, isShareAction = false, supportsComplexProfiles = true),
    Delete(R.string.action_delete, isShareAction = false, supportsComplexProfiles = true),
}

internal fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
): List<ServerMenuAction> = ServerMenuAction.entries.filter { action ->
    (includeManagementActions || action.isShareAction) && (!isComplexProfile || action.supportsComplexProfiles)
}

internal fun quickImportActions(): List<ImportMenuAction> =
    ImportMenuAction.entries.filter { it.action !is MainAction.ImportManually }

internal fun manualImportActions(): List<ImportMenuAction> =
    ImportMenuAction.entries.filter { it.action is MainAction.ImportManually }

@Composable
fun ImportMenuContent(onAction: (MainAction) -> Unit, onManual: () -> Unit) {
    AppDropdownMenuItems(
        items = quickImportActions(),
        labelRes = { it.labelRes },
        onSelected = { onAction(it.action) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.spatial_manual_create)) },
        onClick = onManual,
    )
}

@Composable
fun ManualImportDialog(onAction: (MainAction) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spatial_manual_create)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                manualImportActions().forEach { action ->
                    key(action) {
                        DropdownMenuItem(
                            text = { Text(stringResource(action.labelRes)) },
                            onClick = { onDismiss(); onAction(action.action) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

internal enum class ToolCategory(@StringRes val labelRes: Int) {
    SUBSCRIPTIONS(R.string.spatial_tools_subscriptions),
    SERVICE(R.string.spatial_tools_service),
    EXPORT(R.string.spatial_tools_export),
    ORDER(R.string.spatial_tools_order),
    DELETE(R.string.spatial_tools_delete),
}

internal fun toolCategory(action: MainMoreMenuAction): ToolCategory = when (action) {
    MainMoreMenuAction.TestAll, MainMoreMenuAction.TestAllRealPing,
    MainMoreMenuAction.SortByTestResults, MainMoreMenuAction.LocateSelected -> ToolCategory.ORDER
    MainMoreMenuAction.UpdateSubscriptions, MainMoreMenuAction.GroupDetails -> ToolCategory.SUBSCRIPTIONS
    MainMoreMenuAction.RestartService -> ToolCategory.SERVICE
    MainMoreMenuAction.ExportAll -> ToolCategory.EXPORT
    MainMoreMenuAction.DeleteAll, MainMoreMenuAction.DeleteDuplicate,
    MainMoreMenuAction.DeleteInvalid -> ToolCategory.DELETE
}

/** Testing and locating have dedicated controls; do not duplicate them in tools. */
internal fun visibleToolActions(category: ToolCategory): List<MainMoreMenuAction> =
    MainMoreMenuAction.entries.filter { action ->
        toolCategory(action) == category && action !in setOf(
            MainMoreMenuAction.TestAll, MainMoreMenuAction.TestAllRealPing, MainMoreMenuAction.LocateSelected,
        )
    }

// ModalBottomSheet is experimental in the pinned Material 3 version; reevaluate when its stable
// equivalent is available. MainImportMenuTest covers tool routing and category membership.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuDialog(onSelected: (MainMoreMenuAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.acc_more), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            ToolCategory.entries.forEach { category ->
                val actions = visibleToolActions(category)
                if (actions.isNotEmpty()) key(category) {
                    Text(
                        stringResource(category.labelRes),
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = if (category == ToolCategory.DELETE) androidx.compose.material3.MaterialTheme.colorScheme.error
                        else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                    actions.forEach { action ->
                        key(action) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(action.labelRes),
                                        color = if (category == ToolCategory.DELETE) androidx.compose.material3.MaterialTheme.colorScheme.error
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = { onSelected(action) },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
    )
    SelectListDialog(
        options = menuActions,
        optionText = { stringResource(it.labelRes) },
        onSelected = { action ->
            onDismiss()
            when (action) {
                ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                ServerMenuAction.Delete -> onRemove(guid, profile.remarks)
            }
        },
        onDismiss = onDismiss
    )
}
