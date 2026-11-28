package com.topentris.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class GameKeyKind(val description: String, val caption: String? = null) {
    ROTATE_LEFT("Против часовой"),
    ROTATE_RIGHT("По часовой"),
    HOLD("Запас", "Запас"),
    PAUSE("Пауза", "Пауза"),
    LEFT("Влево"),
    DOWN("Вниз"),
    RIGHT("Вправо"),
    DROP("Жёсткий сброс", "Сброс"),
}

@Composable
internal fun GameKey(
    kind: GameKeyKind,
    enabled: Boolean,
    modifier: Modifier,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
) {
    val visuals = LocalVisuals.current
    var pressed by remember { mutableStateOf(false) }
    val downState = rememberUpdatedState(onDown)
    val upState = rememberUpdatedState(onUp)
    val travel by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(85), label = "key travel")
    val drop = kind == GameKeyKind.DROP && enabled
    val icon = when {
        !enabled -> visuals.muted.copy(alpha = 0.55f)
        drop -> visuals.dropText
        !visuals.simple -> visuals.keyText
        kind == GameKeyKind.HOLD -> Color(0xFFA6DABA)
        kind == GameKeyKind.PAUSE -> visuals.accent
        kind == GameKeyKind.ROTATE_LEFT || kind == GameKeyKind.ROTATE_RIGHT -> Color(0xFFAED7FF)
        else -> visuals.keyText
    }
    Box(
        modifier
            .clearAndSetSemantics {
                contentDescription = kind.description
                role = Role.Button
                if (!enabled) disabled()
            }
            .drawWithCache {
                val depth = if (enabled) 4.dp.toPx() else 2.dp.toPx()
                val faceHeight = (size.height - depth).coerceAtLeast(0f)
                val radius = CornerRadius(if (visuals.simple) 11.dp.toPx() else visuals.plateRadius.toPx())
                val inset = 0.5.dp.toPx()
                val outline = when {
                    !enabled -> visuals.edge.copy(alpha = 0.42f)
                    drop -> if (visuals.simple) Color(0xFFC48944) else visuals.drop
                    pressed -> icon.copy(alpha = 0.74f)
                    else -> visuals.edge
                }
                val face = when {
                    !enabled -> listOf(visuals.panel, visuals.background)
                    visuals.simple && drop -> listOf(visuals.drop, Color(0xFF473018))
                    visuals.simple && pressed -> listOf(Color(0xFF42506A), visuals.keyPressed)
                    visuals.simple -> listOf(Color(0xFF323D51), visuals.key)
                    else -> {
                        val color = when {
                            drop && pressed -> lerp(visuals.drop, visuals.keyBase, 0.16f)
                            drop -> visuals.drop
                            pressed -> visuals.keyPressed
                            else -> visuals.key
                        }
                        listOf(lerp(color, Color.White, if (visuals.lcd) 0.12f else 0.04f), color)
                    }
                }
                val lowered = travel.toPx()
                val gradient = Brush.verticalGradient(face, startY = lowered, endY = lowered + faceHeight)
                val base = if (visuals.simple && drop) Color(0xFF382311) else visuals.keyBase
                onDrawBehind {
                    drawRoundRect(base, cornerRadius = radius)
                    drawRoundRect(gradient, Offset(0f, lowered), Size(size.width, faceHeight), radius)
                    drawRoundRect(
                        outline,
                        Offset(inset, lowered + inset),
                        Size((size.width - 2 * inset).coerceAtLeast(0f), (faceHeight - 2 * inset).coerceAtLeast(0f)),
                        radius,
                        style = Stroke(2 * inset),
                    )
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var held: PointerId? = null
                try {
                    awaitPointerEventScope {
                        while (true) {
                            for (change in awaitPointerEvent().changes) {
                                if (held == null && change.changedToDown()) {
                                    held = change.id
                                    pressed = true
                                    downState.value()
                                    change.consume()
                                } else if (change.id == held && !change.pressed) {
                                    held = null
                                    pressed = false
                                    upState.value()
                                    change.consume()
                                }
                            }
                        }
                    }
                } finally {
                    if (held != null) {
                        pressed = false
                        upState.value()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = travel - 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Canvas(Modifier.size(if (kind.caption == null) 25.dp else 21.dp)) {
                scale(size.width / 24f, pivot = Offset.Zero) { drawKeyIcon(kind, icon) }
            }
            kind.caption?.let { caption ->
                Text(caption, color = icon.copy(alpha = icon.alpha * 0.9f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val IconStroke = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val IconCenter = Offset(12f, 12f)

private fun DrawScope.drawKeyIcon(kind: GameKeyKind, color: Color) {
    when (kind) {
        GameKeyKind.LEFT -> rotate(90f, IconCenter) { downArrow(color) }
        GameKeyKind.RIGHT -> rotate(-90f, IconCenter) { downArrow(color) }
        GameKeyKind.DOWN -> downArrow(color)
        GameKeyKind.ROTATE_LEFT -> scale(-1f, 1f, IconCenter) { rotateArrow(color) }
        GameKeyKind.ROTATE_RIGHT -> rotateArrow(color)
        GameKeyKind.HOLD -> {
            drawRoundRect(color, Offset(3.5f, 9f), Size(17f, 11.5f), CornerRadius(2.2f), style = IconStroke)
            drawPath(Path().apply {
                moveTo(8f, 9f)
                lineTo(8f, 5f)
                lineTo(16f, 5f)
                lineTo(16f, 9f)
            }, color, style = IconStroke)
            drawLine(color, Offset(10f, 13.5f), Offset(14f, 13.5f), 2f, StrokeCap.Round)
        }
        GameKeyKind.PAUSE -> {
            drawRoundRect(color, Offset(6.5f, 4f), Size(3.5f, 16f), CornerRadius(1f))
            drawRoundRect(color, Offset(14f, 4f), Size(3.5f, 16f), CornerRadius(1f))
        }
        GameKeyKind.DROP -> {
            chevron(color, 5f)
            chevron(color, 11f)
            drawLine(color, Offset(6f, 20.5f), Offset(18f, 20.5f), 2f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.downArrow(color: Color) {
    drawLine(color, Offset(12f, 4.5f), Offset(12f, 19f), 2f, StrokeCap.Round)
    chevron(color, 13f)
}

private fun DrawScope.chevron(color: Color, y: Float) {
    drawPath(Path().apply {
        moveTo(6f, y)
        lineTo(12f, y + 6f)
        lineTo(18f, y)
    }, color, style = IconStroke)
}

private fun DrawScope.rotateArrow(color: Color) {
    drawArc(color, startAngle = 12f, sweepAngle = 300f, useCenter = false, topLeft = Offset(4f, 4f), size = Size(16f, 16f), style = IconStroke)
    drawPath(Path().apply {
        moveTo(12.5f, 6.1f)
        lineTo(17.4f, 6.1f)
        lineTo(17.4f, 1.8f)
    }, color, style = IconStroke)
}
