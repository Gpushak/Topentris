package com.topentris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topentris.game.Preset

fun Modifier.tap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val release = waitForUpOrCancellation()
        if (release != null) onTap()
    }
}

@Composable
fun Plate(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val visuals = LocalVisuals.current
    val shape = RoundedCornerShape(visuals.plateRadius)
    Box(
        modifier
            .fillMaxWidth()
            .then(if (visuals.lcd) Modifier.shadow(2.dp, shape) else Modifier)
            .clip(shape)
            .background(if (accent) visuals.accent else visuals.panel)
            .then(if (!visuals.simple && !accent) Modifier.border(1.dp, visuals.edge, shape) else Modifier)
            .tap(onClick)
            .padding(vertical = 16.dp, horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (accent) visuals.onAccent else visuals.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Section(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val visuals = LocalVisuals.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .tap(onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = visuals.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(text = if (expanded) "–" else "+", color = visuals.text, fontSize = 20.sp)
        }
        if (expanded) content()
    }
}

internal const val PrikolModeTitle = "Режим Прикола"

fun presetTitle(preset: Preset): String = when (preset) {
    Preset.AUTHOR -> "Авторский"
    Preset.GUIDELINE -> "Guideline"
    Preset.GAME_BOY -> "Game Boy"
    Preset.NES -> "NES"
    Preset.CUSTOM -> "Свои"
}

fun formatScore(value: Int): String {
    val digits = value.toString()
    return digits.reversed().chunked(3).joinToString(" ").reversed()
}
