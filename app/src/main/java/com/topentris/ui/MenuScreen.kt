package com.topentris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onContinue: (() -> Unit)?,
    onSettings: () -> Unit,
    onRecords: () -> Unit,
) {
    val visuals = LocalVisuals.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (visuals.simple) {
            Text(
                text = "ТОПЕНТРИС",
                color = visuals.text,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("Классика с настраиваемыми правилами", color = visuals.muted, fontSize = 16.sp)
        } else {
            MenuMarquee(visuals)
        }
        Spacer(Modifier.height(36.dp))
        if (onContinue != null) {
            Plate(label = "Продолжить", accent = true, onClick = onContinue)
            Spacer(Modifier.height(12.dp))
            Plate(label = "Новая игра", onClick = onPlay)
        } else {
            Plate(label = "Играть", accent = true, onClick = onPlay)
        }
        Spacer(Modifier.height(12.dp))
        Plate(label = "Настройки", onClick = onSettings)
        Spacer(Modifier.height(12.dp))
        Plate(label = "Рекорды", onClick = onRecords)
    }
}

@Composable
private fun MenuMarquee(visuals: Visuals) {
    val shape = RoundedCornerShape(14.dp)
    val frame = if (visuals.lcd) Modifier.background(visuals.panel, shape)
        .border(1.dp, visuals.edge, shape).padding(7.dp)
        .background(visuals.field, RoundedCornerShape(9.dp)).padding(15.dp)
    else Modifier.padding(vertical = 12.dp)
    Column(Modifier.fillMaxWidth().then(frame), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GameWordmark(visuals, Modifier.fillMaxWidth(), large = true)
        Text("Классика с настраиваемыми правилами", color = visuals.muted,
            fontFamily = visuals.displayFont, fontSize = 14.sp)
    }
}

@Composable
internal fun GameWordmark(visuals: Visuals, modifier: Modifier, large: Boolean = false) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TOPENTRIS", color = visuals.text, fontFamily = visuals.displayFont,
                fontSize = if (large) 33.sp else 20.sp, fontWeight = FontWeight.Bold)
            if (large) {
                Canvas(Modifier.size(width = 28.dp, height = 17.dp)) {
                    val unit = size.height / 2
                    val tile = Size(unit * 0.84f, unit * 0.84f)
                    drawRect(visuals.accent, Offset(unit, 0f), tile)
                    drawRect(visuals.accent, Offset(unit * 2, 0f), tile)
                    drawRect(visuals.accent, Offset(0f, unit), tile)
                    drawRect(visuals.accent, Offset(unit, unit), tile)
                }
            }
        }
        if (!visuals.lcd) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth(0.25f).height(1.dp).background(visuals.accent))
                Box(Modifier.width(62.dp).height(1.dp).background(visuals.drop))
            }
        }
    }
}
