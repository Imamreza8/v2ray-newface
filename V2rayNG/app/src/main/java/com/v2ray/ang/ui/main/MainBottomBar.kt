package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalSpatialTheme
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.launch

@Composable
fun MainBottomBar(
    displayText: String,
    connectionStatus: MainStatus,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showConnectionDetails by rememberSaveable { mutableStateOf(false) }
    val result = (connectionStatus as? MainStatus.ConnectionTest)?.result
    val compactText = when (connectionSummaryKind(connectionStatus)) {
        ConnectionSummaryKind.NEEDS_TEST -> stringResource(R.string.connection_compact_needs_test)
        ConnectionSummaryKind.SUCCESS -> stringResource(
            R.string.connection_compact_delay, stringResource(R.string.server_test_delay_value, result!!.delayMillis),
        )
        ConnectionSummaryKind.SUCCESS_WITH_COUNTRY -> stringResource(
            R.string.connection_compact_country,
            stringResource(R.string.server_test_delay_value, result!!.delayMillis), result.country.orEmpty(),
        )
        ConnectionSummaryKind.OTHER -> displayText.lineSequence().firstOrNull().orEmpty()
    }
    if (showConnectionDetails) {
        AlertDialog(
            onDismissRequest = { showConnectionDetails = false },
            title = { Text(stringResource(R.string.spatial_connection)) },
            text = { Text(displayText, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(enabled = isRunning, onClick = {
                    showConnectionDetails = false
                    onAction(MainAction.TestCurrentServer)
                }) { Text(stringResource(R.string.scoped_test_current)) }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionDetails = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) rotationAnim.snapTo(0f)
    }
    Column(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        val shape = RoundedCornerShape(32.dp)
        val glass = Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ))
        Row(
            modifier = Modifier.fillMaxWidth().shadow(10.dp, shape, clip = false)
                .background(glass, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
                .heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = compactText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.connection_open_details),
                        onClick = { showConnectionDetails = true },
                    )
                    .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
            )
            FloatingActionButton(
                onClick = {
                    if (!isRunning) {
                        scope.launch { rotationAnim.animateTo(720f, animationSpec = tween(durationMillis = 3000)) }
                    }
                    onAction(MainAction.ToggleService)
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (LocalSpatialTheme.current && isRunning) MaterialTheme.colorScheme.tertiary
                else if (isRunning) colorFabActive
                else if (isDarkTheme) colorFabInactiveDark
                else colorFabInactiveLight,
            ) {
                Icon(
                    painter = painterResource(
                        if (LocalSpatialTheme.current) R.drawable.ic_power_spatial_24dp
                        else if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                    ),
                    contentDescription = stringResource(if (isRunning) R.string.acc_stop else R.string.acc_start),
                    tint = if (LocalSpatialTheme.current && isRunning) MaterialTheme.colorScheme.onTertiary else Color.White,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotationAnim.value },
                )
            }
        }
    }
}

/** Presentation only: scrolling never changes the actual VPN connection. */
internal fun connectionBarVisible(pageScrolling: Boolean, serverListScrolling: Boolean): Boolean =
    !pageScrolling && !serverListScrolling
