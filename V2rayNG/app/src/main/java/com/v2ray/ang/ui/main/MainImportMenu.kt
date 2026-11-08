package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** Testing has a dedicated control; locating is available in the tools sheet. */
internal fun visibleToolActions(category: ToolCategory): List<MainMoreMenuAction> =
    MainMoreMenuAction.entries.filter { action ->
        toolCategory(action) == category && action !in setOf(
            MainMoreMenuAction.TestAll, MainMoreMenuAction.TestAllRealPing,
        )
    }

internal fun primaryToolActions(): List<MainMoreMenuAction> = listOf(
    MainMoreMenuAction.UpdateSubscriptions, MainMoreMenuAction.ExportAll, MainMoreMenuAction.RestartService,
)

internal fun secondaryToolActions(): List<MainMoreMenuAction> = listOf(
    MainMoreMenuAction.GroupDetails, MainMoreMenuAction.LocateSelected,
)

// ModalBottomSheet is experimental in the pinned Material 3 version; reevaluate when its stable
// equivalent is available. MainImportMenuTest covers tool routing and category membership.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuDialog(
    displaySort: ServerDisplaySort,
    onSort: (ServerDisplaySort) -> Unit,
    onSearch: () -> Unit,
    onSelected: (MainMoreMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.acc_more), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                primaryToolActions().forEach { action ->
                    val (icon, accent) = when (action) {
                        MainMoreMenuAction.UpdateSubscriptions -> R.drawable.ic_cloud_download_24dp to MaterialTheme.colorScheme.tertiary
                        MainMoreMenuAction.ExportAll -> R.drawable.ic_share_24dp to MaterialTheme.colorScheme.primary
                        else -> R.drawable.ic_power_spatial_24dp to androidx.compose.ui.graphics.Color(0xFFFFC569)
                    }
                    OutlinedButton(
                        onClick = { onSelected(action) },
                        modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = accent.copy(alpha = 0.10f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(action.labelRes), textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            val orderShape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, orderShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, orderShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.save_order_title), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.display_sort), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    DisplaySortMenu(selected = displaySort, onSelect = onSort)
                    TextButton(onClick = { onSelected(MainMoreMenuAction.SortByTestResults) }) {
                        Text(stringResource(R.string.routing_settings_save))
                    }
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_item_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = null) },
                onClick = onSearch,
            )
            secondaryToolActions().forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.labelRes)) },
                    onClick = { onSelected(action) },
                )
            }
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f), shape)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), shape),
            ) {
                Text(
                    stringResource(ToolCategory.DELETE.labelRes),
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                visibleToolActions(ToolCategory.DELETE).forEach { action ->
                    key(action) {
                        DropdownMenuItem(
                            text = { Text(stringResource(action.labelRes), color = MaterialTheme.colorScheme.error) },
                            onClick = { onSelected(action) },
                        )
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
