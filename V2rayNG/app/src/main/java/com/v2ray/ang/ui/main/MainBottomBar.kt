package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun MainBottomBar(
    displayText: String,
    connectionStatus: MainStatus,
    isRunning: Boolean,
    isTesting: Boolean,
    isDarkTheme: Boolean,
    hazeState: HazeState,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
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
                TextButton(onClick = { showConnectionDetails = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) rotationAnim.snapTo(0f)
    }
    Column(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        val shape = RoundedCornerShape(32.dp)
        val surface = MaterialTheme.colorScheme.surface
        val blurStyle = remember(surface) {
            HazeBlurStyle {
                blurRadius(22.dp)
                // Keep light text legible even over bright content behind the panel.
                colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = CONNECTION_GLASS_TINT_ALPHA))))
                // Android 11 and below use Haze's scrim fallback, not backdrop blur.
                fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = 0.72f)))
                noiseFactor(0f)
            }
        }
        val glassHighlight = Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (isDarkTheme) CONNECTION_GLASS_HIGHLIGHT_ALPHA_DARK else 0.24f),
            Color.White.copy(alpha = 0.03f),
        ))
        Row(
            modifier = Modifier.fillMaxWidth().shadow(10.dp, shape, clip = false)
                .clip(shape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable),
                    style = blurStyle,
                )
                .background(glassHighlight, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), shape)
                .heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(surface.copy(alpha = CONNECTION_TEXT_SCRIM_ALPHA), RoundedCornerShape(24.dp))
                    .clickable(
                        enabled = canStartCurrentConnectionTest(isRunning, isTesting),
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.scoped_test_current),
                        onClick = { onAction(MainAction.TestCurrentServer) },
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    Icon(painterResource(R.drawable.ic_flash_on_24dp), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = compactText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { showConnectionDetails = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_about_24dp),
                    contentDescription = stringResource(R.string.connection_open_details),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.background(surface.copy(alpha = CONNECTION_TEXT_SCRIM_ALPHA), CircleShape)
                        .padding(3.dp).size(20.dp),
                )
            }
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
internal fun connectionBarVisible(
    pageScrolling: Boolean,
    serverListScrolling: Boolean,
    lastRowCovered: Boolean = false,
    requestedByUser: Boolean = false,
): Boolean = !pageScrolling && !serverListScrolling && (!lastRowCovered || requestedByUser)

/** Last row is otherwise untappable once the floating panel reappears at the end of the list. */
internal fun lastRowCoveredByBar(lastRowBottom: Int?, viewportEnd: Int, barOverlap: Int): Boolean =
    lastRowBottom != null && barOverlap > 0 && lastRowBottom > viewportEnd - barOverlap

// Shared with the contrast regression test for a white backdrop beneath the blur.
internal const val CONNECTION_GLASS_TINT_ALPHA = 0.55f
internal const val CONNECTION_TEXT_SCRIM_ALPHA = 0.35f
internal const val CONNECTION_GLASS_HIGHLIGHT_ALPHA_DARK = 0.12f
