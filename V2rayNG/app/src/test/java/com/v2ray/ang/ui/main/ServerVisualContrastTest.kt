package com.v2ray.ang.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerVisualContrastTest {
    @Test
    fun pingColorsAreReadableAgainstDarkAndLightCards() {
        // Model the tinted badge over a translucent card at two extreme backdrop colors.
        val darkCard = Color(0xFF0F1630)
        val lightCard = Color(0xFFF1F1F1)
        val backdropColors = listOf(Color(0xFF0A0F2E), Color(0xFFB3235E))
        for (delay in listOf(-1L, 1L, 149L, 150L, 299L, 300L)) {
            val darkText = serverPingColor(delay, darkTheme = true, untestedColor = Color.White)
            val lightText = serverPingColor(delay, darkTheme = false, untestedColor = Color.Black)
            for (backdrop in backdropColors) {
                val card = darkCard.copy(alpha = 0.8f).compositeOver(backdrop)
                val badge = darkText.copy(alpha = 0.16f).compositeOver(card)
                assertTrue(contrast(darkText, badge) >= 4.5f, "dark delay=$delay")
            }
            val lightBadge = lightText.copy(alpha = 0.16f).compositeOver(lightCard)
            assertTrue(contrast(lightText, lightBadge) >= 4.5f, "light delay=$delay")
        }
    }

    @Test
    fun glassStatusTextContrastsEvenOverAWhiteBackdrop() {
        val surface = Color(0xFF0B1124)
        val onSurface = Color(0xFFE6EAF5)
        val tinted = surface.copy(alpha = CONNECTION_GLASS_TINT_ALPHA).compositeOver(Color.White)
        val highlighted = Color.White.copy(alpha = CONNECTION_GLASS_HIGHLIGHT_ALPHA_DARK).compositeOver(tinted)
        assertTrue(contrast(onSurface, highlighted) >= 4.5f)
    }

    @Test
    fun untestedUsesThemeColorAndThresholdsRemainDistinct() {
        val untested = Color(0xFFAAB3CC)
        assertEquals(untested, serverPingColor(0, darkTheme = true, untestedColor = untested))
        assertEquals(serverPingColor(1, true, untested), serverPingColor(149, true, untested))
        assertEquals(serverPingColor(150, true, untested), serverPingColor(299, true, untested))
        assertEquals(serverPingColor(300, true, untested), serverPingColor(-1, true, untested))
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
