package com.v2ray.ang.ui.compose

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

/** The single colour palette of the neon "spatial" look. The theme, the home screen and the shared components read from here. */
object SpatialPalette {
    val SkyTop = Color(0xFF0A0F2E)
    val SkyIndigo = Color(0xFF1B1766)
    val SkyPurple = Color(0xFF4B1A73)
    val SkyMagenta = Color(0xFFB3235E)
    val Cyan = Color(0xFF22D3EE)
    val Magenta = Color(0xFFE879F9)
    val Purple = Color(0xFF9B5DE5)
    val Emerald = Color(0xFF34D399)
    val Amber = Color(0xFFFACC15)
    val Pink = Color(0xFFF472B6)
    val Rose = Color(0xFFF43F5E)
    val OceanBlue = Color(0xFF6C7DFF)
    val TextSoft = Color(0xB3FFFFFF)
    val DrawerSurface = Color(0xF20B1124)
}

private val SkyTop = SpatialPalette.SkyTop
private val SkyIndigo = SpatialPalette.SkyIndigo
private val SkyPurple = SpatialPalette.SkyPurple
private val SkyMagenta = SpatialPalette.SkyMagenta
private val Cyan = SpatialPalette.Cyan
private val Magenta = SpatialPalette.Magenta
private val Emerald = SpatialPalette.Emerald
private val Pink = SpatialPalette.Pink

private const val STAR_COUNT = 90

// Shared building blocks of the spatial look: used by the home screen and available to every other screen.

@Composable
fun SpatialBackground() {
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

/** Translucent card with a light border (glass look without a backdrop blur). */
@Composable
fun GlassCard(
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
fun GradientButton(label: String, running: Boolean, onClick: () -> Unit) {
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
fun RoundGlassButton(glyph: String, description: String, onClick: () -> Unit) {
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
fun GlassChip(
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
fun NeonSparkline(values: List<Long>, modifier: Modifier = Modifier) {
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
