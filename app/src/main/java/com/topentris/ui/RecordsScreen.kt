package com.topentris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topentris.data.SettingsRepository
import com.topentris.game.Preset

@Composable
fun RecordsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val records by repository.records.collectAsState(initial = emptyMap())
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Рекорды", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Топ-5 отдельно для каждого пресета", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Preset.entries.forEach { preset ->
            Text(
                presetTitle(preset),
                color = Amber,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            val scores = records[preset].orEmpty()
            if (scores.isEmpty()) {
                Text("Пока пусто", color = Muted, fontSize = 14.sp)
            } else {
                scores.forEachIndexed { index, score ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}", color = Muted, fontFamily = FontFamily.Monospace)
                        Text(formatScore(score), color = Paper, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Plate(label = "Назад", onClick = onBack)
    }
}
