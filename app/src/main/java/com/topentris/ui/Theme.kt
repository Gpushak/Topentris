package com.topentris.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.topentris.game.Piece
import com.topentris.game.VisualStyle

internal val VisualStyle.title: String
    get() = when (this) {
        VisualStyle.SIMPLE -> "Простой"
        VisualStyle.NEON -> "Ночной неон"
        VisualStyle.LCD -> "Карманный ЖК"
    }

internal class Visuals(
    val style: VisualStyle,
    val background: Color,
    val panel: Color,
    val field: Color,
    val grid: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val muted: Color,
    val key: Color,
    val keyPressed: Color,
    val edge: Color,
    val keyBase: Color,
    val keyText: Color,
    val drop: Color,
    val dropText: Color,
    val scrim: Color,
) {
    val lcd = style == VisualStyle.LCD
    val simple = style == VisualStyle.SIMPLE
    val plateRadius = when (style) {
        VisualStyle.SIMPLE -> 12.dp
        VisualStyle.NEON -> 7.dp
        VisualStyle.LCD -> 10.dp
    }
    val displayFont = if (lcd) FontFamily.Monospace else FontFamily.Default

    fun piece(type: Piece): Color = when (style) {
        VisualStyle.SIMPLE -> when (type) {
            Piece.I -> Color(0xFF3FCFDE)
            Piece.O -> Color(0xFFE6C14A)
            Piece.T -> Color(0xFFB06AD8)
            Piece.S -> Color(0xFF5CBF63)
            Piece.Z -> Color(0xFFDE5A5A)
            Piece.J -> Color(0xFF5A7FDE)
            Piece.L -> Color(0xFFE2923A)
        }
        VisualStyle.NEON -> when (type) {
            Piece.I -> Color(0xFF69DFF0)
            Piece.O -> Color(0xFFF2D973)
            Piece.T -> Color(0xFFB095FF)
            Piece.S -> Color(0xFF73E6A7)
            Piece.Z -> Color(0xFFF582B1)
            Piece.J -> Color(0xFF739CFF)
            Piece.L -> Color(0xFFFFAF69)
        }
        VisualStyle.LCD -> when (type) {
            Piece.I, Piece.O -> Color(0xFF516034)
            Piece.T, Piece.J -> Color(0xFF303D22)
            Piece.S, Piece.Z, Piece.L -> Color(0xFF434E29)
        }
    }
}

private val Simple = Visuals(
    style = VisualStyle.SIMPLE,
    background = Color(0xFF101218),
    panel = Color(0xFF161A24),
    field = Color(0xFF1A1F2C),
    grid = Color(0xFF2C3344),
    accent = Color(0xFFE2A23A),
    onAccent = Color(0xFF101218),
    text = Color(0xFFE7ECF5),
    muted = Color(0xFF8E98AB),
    key = Color(0xFF222733),
    keyPressed = Color(0xFF343C4E),
    edge = Color(0xFF3C4558),
    keyBase = Color(0xFF0B1018),
    keyText = Color(0xFFE7ECF5),
    drop = Color(0xFF795022),
    dropText = Color(0xFFFFDEA2),
    scrim = Color(0xC0101218),
)

private val Neon = Visuals(
    style = VisualStyle.NEON,
    background = Color(0xFF10101E),
    panel = Color(0xFF18182C),
    field = Color(0xFF0D1020),
    grid = Color(0xFF242744),
    accent = Color(0xFF73F6EC),
    onAccent = Color(0xFF10212B),
    text = Color(0xFFF4F4FF),
    muted = Color(0xFFB0ACD0),
    key = Color(0xFF22223B),
    keyPressed = Color(0xFF36365A),
    edge = Color(0xFF635485),
    keyBase = Color(0xFF080813),
    keyText = Color(0xFFF4F4FF),
    drop = Color(0xFFA48BFF),
    dropText = Color(0xFF1A0A3F),
    scrim = Color(0xDC10101E),
)

private val Lcd = Visuals(
    style = VisualStyle.LCD,
    background = Color(0xFFE5D9BF),
    panel = Color(0xFFD8C9A9),
    field = Color(0xFFBAC384),
    grid = Color(0xFFA8B377),
    accent = Color(0xFF4D562F),
    onAccent = Color(0xFFFFF3D6),
    text = Color(0xFF302E25),
    muted = Color(0xFF5B513C),
    key = Color(0xFF634438),
    keyPressed = Color(0xFF50362D),
    edge = Color(0xFF79754E),
    keyBase = Color(0xFFAB9977),
    keyText = Color(0xFFFFF3D6),
    drop = Color(0xFF4D562F),
    dropText = Color(0xFFFFF3D6),
    scrim = Color(0xEDE5D9BF),
)

internal val LocalVisuals = staticCompositionLocalOf { Simple }

@Composable
internal fun TopentrisTheme(style: VisualStyle, content: @Composable () -> Unit) {
    val visuals = when (style) {
        VisualStyle.SIMPLE -> Simple
        VisualStyle.NEON -> Neon
        VisualStyle.LCD -> Lcd
    }
    val scheme = if (visuals.lcd) {
        lightColorScheme(
            primary = visuals.accent,
            onPrimary = visuals.onAccent,
            secondary = visuals.accent,
            secondaryContainer = visuals.field,
            onSecondaryContainer = visuals.text,
            background = visuals.background,
            onBackground = visuals.text,
            surface = visuals.panel,
            surfaceContainerHigh = visuals.panel,
            onSurface = visuals.text,
            surfaceVariant = visuals.panel,
            onSurfaceVariant = visuals.muted,
            outline = visuals.edge,
        )
    } else {
        darkColorScheme(
            primary = visuals.accent,
            onPrimary = visuals.onAccent,
            background = visuals.background,
            onBackground = visuals.text,
            surface = visuals.panel,
            onSurface = visuals.text,
            secondary = visuals.text,
        )
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = visuals.background.toArgb()
            window.navigationBarColor = visuals.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = visuals.lcd
                isAppearanceLightNavigationBars = visuals.lcd
            }
        }
    }
    CompositionLocalProvider(LocalVisuals provides visuals) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
