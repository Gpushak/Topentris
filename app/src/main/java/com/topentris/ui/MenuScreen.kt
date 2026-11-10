package com.topentris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onRecords: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ТОПЕНТРИС",
            color = Paper,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Классика с настраиваемыми правилами",
            color = Muted,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(36.dp))
        Plate(label = "Играть", accent = true, onClick = onPlay)
        Spacer(Modifier.height(12.dp))
        Plate(label = "Настройки", onClick = onSettings)
        Spacer(Modifier.height(12.dp))
        Plate(label = "Рекорды", onClick = onRecords)
    }
}
