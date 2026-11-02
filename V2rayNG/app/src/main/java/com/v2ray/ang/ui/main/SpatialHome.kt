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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.TopConfigManager
import com.v2ray.ang.ui.compose.LocalSpatialTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SkyTop = Color(0xFF0A0F2E)
private val SkyIndigo = Color(0xFF1B1766)
private val SkyPurple = Color(0xFF4B1A73)
private val SkyMagenta = Color(0xFFB3235E)
private val Cyan = Color(0xFF22D3EE)
private val Magenta = Color(0xFFE879F9)
private val Emerald = Color(0xFF34D399)
private val Amber = Color(0xFFFACC15)
private val Pink = Color(0xFFF472B6)
private val OceanBlue = Color(0xFF6C7DFF)
private val TextSoft = Color(0xB3FFFFFF)

private const val HOME_GLOBE_POINTS = 1100
private const val STAR_COUNT = 90

/**
 * Full-screen "3D spatial glassmorphism" home: space background, big dotted globe with neon rings,
 * a glass connection card on top and a glass location card with the connect button at the bottom.
 * The config list opens from the location card. The globe is decoration only: it does not show the
 * server location, and the continents are coarse approximations.
 */
@Composable
internal fun SpatialHome(
    uiState: MainUiState,
    statusText: String,
    onAction: (MainAction) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenList: () -> Unit,
    onOpenTop: () -> Unit,
) {
    var showSpeedConfirm by remember { mutableStateOf(false) }
    val running = uiState.isRunning
    val stats = uiState.runStats

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundGlassButton(glyph = "☰", description = stringResource(R.string.spatial_menu), onClick = onOpenMenu)
                RoundGlassButton(glyph = "★", description = stringResource(R.string.spatial_top_configs), onClick = onOpenTop)
            }

            ConnectionCard(uiState = uiState)

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                HomeGlobe(size = side, running = running, reduceMotion = uiState.reduceMotion)
            }

            // Actions: discovery on the left, test progress replaces them while testing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.isTesting) {
                    Text(
                        text = if (stats.total > 0) {
                            stringResource(R.string.spatial_test_stats, stats.done, stats.total, stats.ok, stats.failed)
                        } else {
                            statusText
                        },
                        color = Amber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    GlassChip(
                        label = stringResource(R.string.action_cancel),
                        accent = Pink,
                        onClick = { onAction(MainAction.CancelTesting) },
                    )
                } else {
                    GlassChip(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.spatial_smart_connect),
                        accent = Emerald,
                        onClick = { onAction(MainAction.SmartConnect) },
                    )
                    if (uiState.resumableCount > 0) {
                        GlassChip(
                            label = stringResource(R.string.spatial_resume, uiState.resumableCount),
                            accent = Amber,
                            onClick = { onAction(MainAction.ResumeTesting) },
                        )
                    }
                    GlassChip(
                        label = stringResource(R.string.spatial_speed_test),
                        accent = Cyan,
                        enabled = running && uiState.speedTest != SpeedTestState.Running,
                        onClick = { showSpeedConfirm = true },
                    )
                }
            }

            LocationCard(uiState = uiState, onOpenList = onOpenList, onAction = onAction)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = uiState.reduceMotion,
                    onClick = { onAction(MainAction.SetReduceMotion(!uiState.reduceMotion)) },
                    label = { Text(stringResource(R.string.spatial_reduce_motion), fontSize = 11.sp) },
                )
                FilterChip(
                    selected = LocalSpatialTheme.current,
                    onClick = { onAction(MainAction.SetSpatialTheme(false)) },
                    label = { Text(stringResource(R.string.spatial_neon_theme), fontSize = 11.sp) },
                )
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
                TextButton(onClick = { showSpeedConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Background
// ---------------------------------------------------------------------------------------------

@Composable
private fun SpaceBackground() {
    val stars = remember {
        val random = Random(7)
        List(STAR_COUNT) {
            floatArrayOf(random.nextFloat(), random.nextFloat(), 0.6f + random.nextFloat() * 1.6f, 0.25f + random.nextFloat() * 0.6f)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(SkyTop, SkyIndigo, SkyPurple, SkyMagenta)))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Cyan.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.2f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.15f, size.height * 0.2f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Magenta.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.95f),
                radius = size.width * 0.9f,
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width * 0.9f, size.height * 0.95f),
        )
        stars.forEach { s ->
            drawCircle(Color.White.copy(alpha = s[3]), radius = s[2], center = Offset(s[0] * size.width, s[1] * size.height))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

/** Translucent card with a light border (glass look without a backdrop blur). */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f))),
                shape,
            )
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun ConnectionCard(uiState: MainUiState) {
    val running = uiState.isRunning
    val delay = uiState.selectedInfo?.delayMillis ?: 0L
    val pingText = when {
        delay > 0L -> stringResource(R.string.server_test_delay_value, delay)
        delay < 0L -> stringResource(R.string.spatial_test_failed)
        else -> stringResource(R.string.spatial_not_tested)
    }
    val speedText = when {
        running && uiState.speedEnabled -> uiState.trafficDownBps.toSpeedString()
        uiState.speedTest is SpeedTestState.Result ->
            stringResource(R.string.spatial_speed_mbps, (uiState.speedTest as SpeedTestState.Result).megabitsPerSecond)
        else -> stringResource(R.string.spatial_not_tested)
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.spatial_connection), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLine(
                    dot = if (running) Emerald else Color.Gray,
                    text = if (running) stringResource(R.string.connection_connected) else stringResource(R.string.connection_not_connected),
                )
                StatusLine(dot = Amber, text = stringResource(R.string.spatial_ping, pingText))
                StatusLine(dot = Pink, text = stringResource(R.string.spatial_speed_line, speedText))
            }
            NeonSparkline(
                values = uiState.trafficHistory,
                modifier = Modifier.width(120.dp).height(64.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(dot: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LocationCard(uiState: MainUiState, onOpenList: () -> Unit, onAction: (MainAction) -> Unit) {
    val running = uiState.isRunning
    val selected = uiState.selectedInfo
    val remarks = selected?.remarks.orEmpty()
    // Exit country learned from a connection test wins; otherwise the flag in the config name (an estimate).
    val countryCode = selected?.exitCountry ?: TopConfigManager.countryFromName(remarks)
    val countryName = remember(countryCode) {
        countryCode?.let {
            runCatching { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.getDefault()) }.getOrNull()
        }
    }
    val title = countryName?.takeIf { it.isNotBlank() }
        ?: remarks.ifBlank { null }
        ?: stringResource(R.string.spatial_no_selection)
    val subtitle = if (countryName != null && remarks.isNotBlank()) remarks else null
    val delay = selected?.delayMillis ?: 0L
    val pingText = when {
        delay > 0L -> stringResource(R.string.server_test_delay_value, delay)
        delay < 0L -> stringResource(R.string.spatial_test_failed)
        else -> stringResource(R.string.spatial_not_tested)
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenList) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.spatial_location), color = TextSoft, fontSize = 15.sp)
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, color = TextSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(TopConfigManager.flagEmoji(countryCode).ifEmpty { "\uD83C\uDF10" }, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.spatial_ping, ""), color = TextSoft, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(pingText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(
            label = stringResource(if (running) R.string.spatial_disconnect else R.string.spatial_connect),
            running = running,
            onClick = { onAction(MainAction.ToggleService) },
        )
    }
}

@Composable
private fun GradientButton(label: String, running: Boolean, onClick: () -> Unit) {
    val colors = if (running) {
        listOf(Color(0xFF2DD4BF), Color(0xFF8B5CF6), Color(0xFFF87171))
    } else {
        listOf(Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFFEC4899))
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundGlassButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative glyph: the box is named by [description].
        Text(glyph, color = Color.White, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun GlassChip(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (enabled) accent else accent.copy(alpha = 0.35f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.6f)),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Live line chart of the proxy download speed (pink to cyan), with a soft fill. */
@Composable
private fun NeonSparkline(values: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val points = if (values.size >= 2) values else listOf(0L, 0L)
        val max = (points.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val step = size.width / (points.size - 1)
        val line = Path()
        val fill = Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height * (1f - (v.toFloat() / max).coerceIn(0.06f, 0.96f))
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        val brush = Brush.horizontalGradient(listOf(Pink, Magenta, Cyan, Emerald))
        drawPath(fill, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(line, brush, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Globe
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeGlobe(size: androidx.compose.ui.unit.Dp, running: Boolean, reduceMotion: Boolean) {
    val globe = remember { GlobePoints.build(HOME_GLOBE_POINTS) }
    var drag by remember { mutableFloatStateOf(0f) }
    // Longitude -75 (the Americas) faces the viewer at the start: visible centre longitude = -spin.
    val startSpin = 1.31f
    val modifier = Modifier
        .size(size)
        .pointerInput(Unit) {
            detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                drag += dragAmount * 0.01f
            }
        }
    if (reduceMotion) {
        Canvas(modifier = modifier) { drawHomeGlobe(globe, startSpin + drag, 0.12f, running) }
    } else {
        val transition = rememberInfiniteTransition(label = "homeGlobe")
        val spin = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(if (running) 26_000 else 52_000, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )
        val rings = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
            label = "rings",
        )
        // Both states are read in the draw block, so only the canvas redraws each frame.
        Canvas(modifier = modifier) { drawHomeGlobe(globe, startSpin + spin.value + drag, rings.value, running) }
    }
}

private fun DrawScope.drawHomeGlobe(globe: GlobePoints, spin: Float, ringProgress: Float, running: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.34f
    val center = Offset(cx, cy)

    // Volume: soft outer glow and a dark core so the dots stand out
    drawCircle(
        brush = Brush.radialGradient(
            listOf(OceanBlue.copy(alpha = 0.40f), Magenta.copy(alpha = 0.16f), Color.Transparent),
            center = center,
            radius = radius * 1.3f,
        ),
        radius = radius * 1.3f,
        center = center,
    )
    drawCircle(Color(0xFF0B1240).copy(alpha = 0.55f), radius, center)

    val cosY = cos(spin)
    val sinY = sin(spin)
    val tilt = 0.38f
    val cosX = cos(tilt)
    val sinX = sin(tilt)
    for (i in 0 until globe.size) {
        val x0 = globe.x[i]
        val y0 = globe.y[i]
        val z0 = globe.z[i]
        // Positive spin moves the front of the globe to the right (the Earth turns west to east)
        val x1 = x0 * cosY + z0 * sinY
        val z1 = z0 * cosY - x0 * sinY
        val y2 = y0 * cosX - z1 * sinX
        val z2 = z1 * cosX + y0 * sinX
        val depth = ((z2 + 1.1f) / 2.1f).coerceIn(0.05f, 1f)
        val land = globe.land[i]
        val color = if (land) {
            lerp(Cyan, Magenta, (globe.lonDeg[i] + 180f) / 360f).copy(alpha = depth)
        } else {
            OceanBlue.copy(alpha = depth * 0.5f)
        }
        val dotRadius = (if (land) 3.4f else 1.5f) * (0.6f + 0.6f * depth)
        drawCircle(color, dotRadius, Offset(cx + x1 * radius, cy - y2 * radius))
    }
    drawCircle(Color.White.copy(alpha = 0.22f), radius, center, style = Stroke(width = 2f))

    // Neon orbit rings (a wide faint pass for the glow, then a thin bright one)
    val ringColors = listOf(
        listOf(Emerald, Cyan, Color(0xFF60A5FA), Emerald),
        listOf(Magenta, Color(0xFF8B5CF6), Pink, Magenta),
        listOf(Amber, Pink, Cyan, Amber),
    )
    val baseAngles = floatArrayOf(18f, -38f, 72f)
    val flatness = floatArrayOf(0.46f, 0.40f, 0.34f)
    val directions = floatArrayOf(1f, -1f, 1f)
    for (r in 0 until 3) {
        val w = radius * 2.5f
        val h = w * flatness[r]
        withTransform({ rotate(baseAngles[r] + directions[r] * ringProgress * 360f * 0.5f, center) }) {
            val brush = Brush.sweepGradient(ringColors[r], center)
            val topLeft = Offset(cx - w / 2f, cy - h / 2f)
            drawOval(brush = brush, topLeft = topLeft, size = Size(w, h), alpha = 0.18f, style = Stroke(width = 12f))
            drawOval(brush = brush, topLeft = topLeft, size = Size(w, h), style = Stroke(width = 3.5f))
        }
    }

    // While connected: a glowing arc across the face with a travelling packet
    if (running) {
        val start = Offset(cx - radius * 0.55f, cy + radius * 0.25f)
        val end = Offset(cx + radius * 0.55f, cy - radius * 0.2f)
        val control = Offset(cx, cy - radius * 0.95f)
        val arc = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, end.x, end.y)
        }
        drawPath(arc, Brush.horizontalGradient(listOf(Emerald, Cyan)), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
        val t = ringProgress
        val u = 1f - t
        val packet = Offset(
            u * u * start.x + 2f * u * t * control.x + t * t * end.x,
            u * u * start.y + 2f * u * t * control.y + t * t * end.y,
        )
        drawCircle(Cyan.copy(alpha = 0.4f), 14f, packet)
        drawCircle(Color.White, 5.5f, packet)
    }
}
