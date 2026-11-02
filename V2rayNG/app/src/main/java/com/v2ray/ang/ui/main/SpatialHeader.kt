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

// Neon "spatial" palette. Used only inside this header and its sheets; the rest of the app keeps its theme.
private val BgTop = Color(0xFF0B1124)
private val BgBottom = Color(0xFF060913)
private val GlassFill = Color(0x14FFFFFF)
private val GlassBorder = Color(0x33FFFFFF)
private val NeonCyan = Color(0xFF00F2FE)
private val NeonPurple = Color(0xFF9B5DE5)
private val NeonEmerald = Color(0xFF10B981)
private val NeonAmber = Color(0xFFF59E0B)
private val NeonRose = Color(0xFFF43F5E)
private val TextDim = Color(0x99FFFFFF)

private const val GLOBE_POINTS = 160

// ---------------------------------------------------------------------------------------------
// Header: globe + Bento cards
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SpatialHeader(
    uiState: MainUiState,
    statusText: String,
    topCount: Int,
    onAction: (MainAction) -> Unit,
    onOpenTop: () -> Unit,
    collapsedByScroll: Boolean = false,
    onExpandRequested: () -> Unit = {},
) {
    val expanded = uiState.spatialExpanded && !collapsedByScroll
    val reduceMotion = uiState.reduceMotion
    val selectedInfo = uiState.selectedInfo
    var showSpeedConfirm by remember { mutableStateOf(false) }
    val stats = uiState.runStats

    val accent = when {
        uiState.isRunning -> NeonCyan
        uiState.isTesting -> NeonAmber
        else -> NeonPurple
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title row: status + collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isRunning) stringResource(R.string.connection_connected)
                        else stringResource(R.string.connection_not_connected),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val toggleName = stringResource(R.string.spatial_toggle_header)
                TextButton(
                    onClick = {
                        if (collapsedByScroll) onExpandRequested()
                        else onAction(MainAction.SetSpatialExpanded(!uiState.spatialExpanded))
                    },
                    modifier = Modifier.semantics { contentDescription = toggleName },
                ) {
                    // Decorative glyph; the button's accessible name is toggleName.
                    Text(text = if (expanded) "▲" else "▼", color = TextDim)
                }
            }

            if (expanded) {
              // The expanded panel scrolls on its own so it never pushes the config list off small screens.
              Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                // Globe stage with the connect pill on top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (reduceMotion) {
                        GlobeCanvas(angle = { 0.6f }, active = uiState.isRunning, accent = accent)
                    } else {
                        AnimatedGlobe(active = uiState.isRunning, accent = accent)
                    }
                    ConnectPill(
                        isRunning = uiState.isRunning,
                        accent = accent,
                        onClick = { onAction(MainAction.ToggleService) },
                    )
                }

                Spacer(Modifier.height(8.dp))

                NeonButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.spatial_smart_connect),
                    tint = NeonEmerald,
                    enabled = !uiState.isTesting,
                    onClick = { onAction(MainAction.SmartConnect) },
                )

                Spacer(Modifier.height(8.dp))

                // Bento row: selected config + test/top status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BentoCard(
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MainAction.TestCurrentServer) },
                    ) {
                        Text(stringResource(R.string.spatial_selected), color = TextDim, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = selectedInfo?.remarks?.ifBlank { null }
                                ?: stringResource(R.string.spatial_no_selection),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        DelayLabel(selectedInfo?.delayMillis ?: 0L)
                    }

                    BentoCard(modifier = Modifier.weight(1f)) {
                        if (uiState.isTesting) {
                            Text(stringResource(R.string.title_real_ping_all_server), color = TextDim, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (stats.total > 0) {
                                    stringResource(R.string.spatial_test_stats, stats.done, stats.total, stats.ok, stats.failed)
                                } else {
                                    statusText
                                },
                                color = NeonAmber,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            NeonButton(
                                label = stringResource(R.string.action_cancel),
                                tint = NeonRose,
                                onClick = { onAction(MainAction.CancelTesting) },
                            )
                        } else {
                            Text(stringResource(R.string.spatial_top_configs), color = TextDim, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = topCount.toString(),
                                color = NeonEmerald,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            NeonButton(
                                label = stringResource(R.string.spatial_open),
                                tint = NeonCyan,
                                onClick = onOpenTop,
                            )
                            if (uiState.resumableCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                NeonButton(
                                    label = stringResource(R.string.spatial_resume, uiState.resumableCount),
                                    tint = NeonAmber,
                                    onClick = { onAction(MainAction.ResumeTesting) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.spatial_test_top),
                        tint = NeonCyan,
                        enabled = !uiState.isTesting,
                        onClick = { onAction(MainAction.TestTopConfigs) },
                    )
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.spatial_auto_select),
                        tint = NeonEmerald,
                        enabled = !uiState.isTesting,
                        onClick = { onAction(MainAction.AutoSelect) },
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Proxy-only traffic (the daemon reports it; it is not the phone's total traffic)
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.spatial_traffic), color = TextDim, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    !uiState.speedEnabled -> stringResource(R.string.spatial_traffic_disabled)
                                    !uiState.isRunning -> stringResource(R.string.spatial_traffic_idle)
                                    else -> stringResource(
                                        R.string.spatial_traffic_values,
                                        uiState.trafficDownBps.toSpeedString(),
                                        uiState.trafficUpBps.toSpeedString(),
                                    )
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                            )
                        }
                        if (uiState.speedEnabled && uiState.isRunning) {
                            Sparkline(
                                values = uiState.trafficHistory,
                                color = NeonCyan,
                                modifier = Modifier.width(96.dp).height(36.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Download speed of the current connection (on request only)
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (val test = uiState.speedTest) {
                                SpeedTestState.Idle -> stringResource(R.string.spatial_speed_test)
                                SpeedTestState.Running -> stringResource(R.string.spatial_speed_running)
                                SpeedTestState.Failed -> stringResource(R.string.spatial_speed_failed)
                                is SpeedTestState.Result -> stringResource(
                                    R.string.spatial_speed_result,
                                    test.megabitsPerSecond,
                                    test.bytes.toTrafficString(),
                                )
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        NeonButton(
                            label = stringResource(R.string.spatial_speed_start),
                            tint = NeonCyan,
                            enabled = uiState.isRunning && uiState.speedTest != SpeedTestState.Running,
                            onClick = { showSpeedConfirm = true },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reduceMotion,
                        onClick = { onAction(MainAction.SetReduceMotion(!reduceMotion)) },
                        label = { Text(stringResource(R.string.spatial_reduce_motion), fontSize = 11.sp) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val neonOn = LocalSpatialTheme.current
                    FilterChip(
                        selected = neonOn,
                        onClick = { onAction(MainAction.SetSpatialTheme(!neonOn)) },
                        label = { Text(stringResource(R.string.spatial_neon_theme), fontSize = 11.sp) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FilterChip(
                        selected = uiState.simpleMode,
                        onClick = { onAction(MainAction.SetSimpleMode(!uiState.simpleMode)) },
                        label = { Text(stringResource(R.string.spatial_simple_mode), fontSize = 11.sp) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
              }
            }
        }
    }

    if (showSpeedConfirm) {
        val host = remember { runCatching { java.net.URI(AppConfig.SPEED_TEST_URL).host }.getOrNull().orEmpty() }
        AlertDialog(
            onDismissRequest = { showSpeedConfirm = false },
            title = { Text(stringResource(R.string.spatial_speed_title)) },
            text = { Text(stringResource(R.string.spatial_speed_confirm, host)) },
            confirmButton = {
                TextButton(onClick = {
                    showSpeedConfirm = false
                    onAction(MainAction.SpeedTestCurrent)
                }) { Text(stringResource(R.string.spatial_speed_start)) }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Small line chart of recent download speeds (bytes per second), scaled to its own maximum. */
@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height * (1f - (v.toFloat() / max).coerceIn(0.03f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
private fun AnimatedGlobe(active: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "globeSpin")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 16_000 else 34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "globeAngle",
    )
    // The state is read inside the draw block, so only the canvas redraws per frame (no recomposition).
    GlobeCanvas(angle = { angle.value }, active = active, accent = accent)
}

/** Decorative globe (Fibonacci sphere). It does NOT show the real location of the server. */
@Composable
private fun GlobeCanvas(angle: () -> Float, active: Boolean, accent: Color) {
    val points = remember {
        val xs = FloatArray(GLOBE_POINTS)
        val ys = FloatArray(GLOBE_POINTS)
        val zs = FloatArray(GLOBE_POINTS)
        val golden = (1.0 + sqrt(5.0)) / 2.0
        for (i in 0 until GLOBE_POINTS) {
            val theta = (2.0 * PI * i / golden).toFloat()
            val phi = acos(1.0 - 2.0 * (i + 0.5) / GLOBE_POINTS).toFloat()
            xs[i] = cos(theta) * sin(phi)
            ys[i] = cos(phi)
            zs[i] = sin(theta) * sin(phi)
        }
        Triple(xs, ys, zs)
    }
    val secondary = if (active) NeonEmerald else Color(0xFF64748B)
    // Extra rotation from the user's finger (purely visual state, so it is local, not in the ViewModel).
    var dragRotation by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = Modifier
            .size(120.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    dragRotation += dragAmount * 0.012f
                }
            },
    ) {
        val (xs, ys, zs) = points
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.42f
        val rotY = angle() + dragRotation
        val tilt = 0.35f
        val cosY = cos(rotY)
        val sinY = sin(rotY)
        val cosX = cos(tilt)
        val sinX = sin(tilt)

        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = radius * 1.15f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f),
        )
        for (i in 0 until GLOBE_POINTS) {
            val x1 = xs[i] * cosY - zs[i] * sinY
            val z1 = zs[i] * cosY + xs[i] * sinY
            val y2 = ys[i] * cosX - z1 * sinX
            val z2 = z1 * cosX + ys[i] * sinX
            val alpha = ((z2 + 1.2f) / 2.2f).coerceIn(0.08f, 0.95f)
            drawCircle(
                color = (if (i % 17 == 0) secondary else accent).copy(alpha = alpha),
                radius = if (z2 > 0f) 2.6f else 1.2f,
                center = Offset(cx + x1 * radius, cy + y2 * radius),
            )
        }
    }
}

@Composable
private fun ConnectPill(isRunning: Boolean, accent: Color, onClick: () -> Unit) {
    val actionName = stringResource(if (isRunning) R.string.acc_stop else R.string.acc_start)
    Surface(
        onClick = onClick,
        // One focus node named by its action (stop/start); the icon and label below are merged into it.
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = actionName },
        color = Color(0xCC0F172A),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRunning) stringResource(R.string.connection_connected)
                else stringResource(R.string.spatial_tap_to_connect),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
    ) {
        content()
    }
}

@Composable
private fun NeonButton(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shown = if (enabled) tint else tint.copy(alpha = 0.35f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = shown.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, shown.copy(alpha = 0.5f)),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, color = shown, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun DelayLabel(delayMillis: Long) {
    val (color, text) = when {
        delayMillis > 0L -> (if (delayMillis < 250L) NeonEmerald else NeonAmber) to
                stringResource(R.string.server_test_delay_value, delayMillis)
        delayMillis < 0L -> NeonRose to stringResource(R.string.spatial_test_failed)
        else -> TextDim to stringResource(R.string.spatial_not_tested)
    }
    Text(text, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
}

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
