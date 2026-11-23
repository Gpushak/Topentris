package com.topentris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topentris.game.Generator
import com.topentris.game.Preset
import com.topentris.game.RotationSystem
import com.topentris.game.ScoreTable
import com.topentris.game.Settings
import com.topentris.game.SettingsState

@Composable
fun SettingsScreen(
    state: SettingsState,
    edit: ((Settings) -> Settings) -> Unit,
    onSelectBuiltIn: (Preset) -> Unit,
    onSelectSaved: (String) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val rules = settings.rules
    val activePresetName = state.activeSavedPreset?.name ?: presetTitle(state.activeBuiltIn ?: Preset.CUSTOM)
    var openSection by remember { mutableStateOf<String?>(null) }

    @Composable
    fun Fold(title: String, summary: String? = null, content: @Composable () -> Unit) {
        Section(
            title = if (summary == null) title else "$title · $summary",
            expanded = openSection == title,
            onToggle = { openSection = if (openSection == title) null else title },
            content = content,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Настройки", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Fold("Пресет", summary = activePresetName) {
            PresetSettings(state, onSelectBuiltIn, onSelectSaved, onSave, onDelete)
        }
        Spacer(Modifier.height(18.dp))
        Fold("Правила") {
            RuleBlock(
                title = "Поворот у стены",
                hint = "Современный сдвигает фигуру и всё равно поворачивает. Классический отменяет поворот, если она не влезает.",
            ) {
                ChipRow {
                    ChoiceChip("Современный", rules.rotation == RotationSystem.SRS) {
                        edit { it.copy(rules = it.rules.copy(rotation = RotationSystem.SRS)) }
                    }
                    ChoiceChip("Классический", rules.rotation == RotationSystem.CLASSIC) {
                        edit { it.copy(rules = it.rules.copy(rotation = RotationSystem.CLASSIC)) }
                    }
                }
            }
            RuleBlock(
                title = "Какие фигуры выпадают",
                hint = "«Все семь по очереди» не повторяет фигуру, пока не выпадут остальные. «Реже повтор» перебрасывает такую же один раз.",
            ) {
                ChipRow {
                    ChoiceChip("Все семь по очереди", rules.generator == Generator.BAG) {
                        edit { it.copy(rules = it.rules.copy(generator = Generator.BAG)) }
                    }
                    ChoiceChip("Реже повтор", rules.generator == Generator.REROLL) {
                        edit { it.copy(rules = it.rules.copy(generator = Generator.REROLL)) }
                    }
                    ChoiceChip("Как повезёт", rules.generator == Generator.UNIFORM) {
                        edit { it.copy(rules = it.rules.copy(generator = Generator.UNIFORM)) }
                    }
                }
            }
            RuleBlock(
                title = "Сколько фигур видно дальше",
                hint = "Стопка справа от поля.",
            ) {
                ChipRow {
                    ChoiceChip("Одна", rules.queueSize == 1) {
                        edit { it.copy(rules = it.rules.copy(queueSize = 1)) }
                    }
                    ChoiceChip("Пять", rules.queueSize == 5) {
                        edit { it.copy(rules = it.rules.copy(queueSize = 5)) }
                    }
                }
            }
            RuleBlock(
                title = "Когда фигура фиксируется",
                hint = "«Можно подвинуть» даёт полсекунды после приземления, чтобы завести её в нишу.",
            ) {
                ChipRow {
                    ChoiceChip("Сразу", rules.lockDelayMs == 0) {
                        edit { it.copy(rules = it.rules.copy(lockDelayMs = 0, maxLockResets = 0)) }
                    }
                    ChoiceChip("Можно подвинуть", rules.lockDelayMs > 0) {
                        edit { it.copy(rules = it.rules.copy(lockDelayMs = 500, maxLockResets = 15)) }
                    }
                }
            }
            RuleBlock(
                title = "Подсчёт очков",
                hint = "Классический — как в старых тетрисах. Современный учитывает комбо и T-spin.",
            ) {
                ChipRow {
                    ChoiceChip("Классический", rules.scoreTable == ScoreTable.CLASSIC) {
                        edit { it.copy(rules = it.rules.copy(scoreTable = ScoreTable.CLASSIC)) }
                    }
                    ChoiceChip("Современный", rules.scoreTable == ScoreTable.GUIDELINE) {
                        edit { it.copy(rules = it.rules.copy(scoreTable = ScoreTable.GUIDELINE)) }
                    }
                }
            }
            ToggleRow(
                label = "Запас",
                hint = "Отложить фигуру и вернуть её позже. Кнопка H и свайп вверх.",
                checked = rules.hold,
            ) { value ->
                edit { it.copy(rules = it.rules.copy(hold = value)) }
            }
            ToggleRow(
                label = "Призрак",
                hint = "Показывает, куда фигура упадёт.",
                checked = rules.ghost,
            ) { value ->
                edit { it.copy(rules = it.rules.copy(ghost = value)) }
            }
            ToggleRow(
                label = "Жёсткий сброс",
                hint = "Резкий свайп вниз роняет фигуру до конца. Медленный свайп опускает её плавно.",
                checked = rules.hardDrop,
            ) { value ->
                edit { it.copy(rules = it.rules.copy(hardDrop = value)) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Fold("Звук") {
            ToggleRow(
                label = "Музыка",
                hint = "Саундтрек в меню и во время партии. На паузе и когда игра свёрнута — тихо.",
                checked = settings.music,
            ) { value ->
                edit { it.copy(music = value) }
            }
            SliderRow(
                title = "Громкость музыки",
                valueLabel = "${settings.musicVolume}%",
                value = settings.musicVolume,
                range = 0f..100f,
            ) { value ->
                edit { it.copy(musicVolume = value) }
            }
            SliderRow(
                title = "Громкость звуковых эффектов",
                valueLabel = "${settings.sfxVolume}%",
                value = settings.sfxVolume,
                range = 0f..100f,
            ) { value ->
                edit { it.copy(sfxVolume = value) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Fold("Экран") {
            ToggleRow(
                label = "Всплывающие очки",
                hint = "Цифра поднимается и гаснет. Если закрыто четыре линии, рядом появляется «Тетрис!».",
                checked = settings.scorePopups,
            ) { value ->
                edit { it.copy(scorePopups = value) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Fold("Управление") {
            Text(
                "По полю ведите палец в сторону — фигура едет за ним, не прыгая под палец. Свайп вниз её не уводит вбок: чтобы зайти в нишу, сдвиньте палец в сторону. Резкий свайп вниз — жёсткий сброс.",
                color = Muted,
                fontSize = 13.sp,
            )
            ToggleRow(
                label = "Кнопки снизу",
                hint = "Стрелки, повороты и сброс. Пауза и запас, если он включён, остаются.",
                checked = settings.showButtons,
            ) { value ->
                edit { it.copy(showButtons = value) }
            }
            ToggleRow(
                label = "Поворот по сторонам",
                hint = "Короткое касание слева крутит против часовой, справа — по часовой. Если выключить, любое касание крутит по часовой.",
                checked = settings.splitRotate,
            ) { value ->
                edit { it.copy(splitRotate = value) }
            }
            SliderRow(
                title = "Пауза перед автосдвигом",
                valueLabel = "${settings.controls.dasMs} мс",
                hint = "После первого шага влево или вправо фигура ждёт, потом едет сама, пока кнопка зажата.",
                value = settings.controls.dasMs,
                range = 0f..300f,
            ) { value ->
                edit { it.copy(controls = it.controls.copy(dasMs = value)) }
            }
            SliderRow(
                title = "Как часто повторять шаг",
                valueLabel = if (settings.controls.arrMs == 0) "каждый кадр" else "каждые ${settings.controls.arrMs} мс",
                hint = "0 — фигура едет без пауз. Больше — медленнее и точнее.",
                value = settings.controls.arrMs,
                range = 0f..100f,
            ) { value ->
                edit { it.copy(controls = it.controls.copy(arrMs = value)) }
            }
            SliderRow(
                title = "Плавность мягкого сброса",
                valueLabel = if (settings.controls.softDropMs == 0) {
                    "клетка каждый кадр"
                } else {
                    "клетка за ${settings.controls.softDropMs} мс"
                },
                hint = "Кнопка вниз и медленный свайп. Больше миллисекунд — спокойнее, успеваете завести фигуру в нишу.",
                value = settings.controls.softDropMs,
                range = 0f..200f,
            ) { value ->
                edit { it.copy(controls = it.controls.copy(softDropMs = value)) }
            }
            ToggleRow(
                label = "Вибрация",
                hint = "Короткий отклик на поворот, фиксацию и жёсткий сброс.",
                checked = settings.controls.vibration,
            ) { value ->
                edit { it.copy(controls = it.controls.copy(vibration = value)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Plate(label = "Назад", onClick = onBack)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PresetSettings(
    state: SettingsState,
    onSelectBuiltIn: (Preset) -> Unit,
    onSelectSaved: (String) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var newName by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    ChipRow {
        Preset.builtIns.forEach { preset ->
            ChoiceChip(presetTitle(preset), state.activeBuiltIn == preset) {
                onSelectBuiltIn(preset)
            }
        }
    }
    if (state.activeBuiltIn != null) {
        Text(
            text = presetBlurb(state.activeBuiltIn),
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    state.savedPresets.forEach { preset ->
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(preset.name, state.activeSavedPreset == preset, Modifier.weight(1f)) {
                onSelectSaved(preset.name)
            }
            TextButton(onClick = { deleting = preset.name }) { Text("Удалить") }
        }
    }
    TextButton(onClick = { newName = "" }) { Text("Сохранить текущий набор") }

    newName?.let { name ->
        val duplicate = state.savedPresets.any { it.name.equals(name.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("Сохранить набор") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName = it },
                    label = { Text("Название") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) { { Text("Набор с таким названием уже есть") } } else null,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !duplicate,
                    onClick = {
                        onSave(name)
                        newName = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Отмена") } },
        )
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить набор?") },
            text = { Text(name) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(name)
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun RuleBlock(title: String, hint: String, content: @Composable () -> Unit) {
    Text(title, color = Paper, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
    Text(hint, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
    content()
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Ink else Paper,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Amber else KeyIdle)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Paper, fontSize = 16.sp)
            Text(hint, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    hint: String? = null,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Text(title, color = Paper, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
    Text(valueLabel, color = Amber, fontSize = 13.sp)
    if (hint != null) Text(hint, color = Muted, fontSize = 12.sp)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range,
    )
}

private fun presetBlurb(preset: Preset): String = when (preset) {
    Preset.AUTHOR -> "Набор по умолчанию: современный поворот и очки, повтор реже, одна следующая фигура, можно подвинуть после приземления."
    Preset.GUIDELINE -> "Современные правила: поворот у стены, запас, призрак и пауза после приземления."
    Preset.GAME_BOY -> "Как на Game Boy: без выталкивания из стены, одна следующая фигура, фиксация сразу."
    Preset.NES -> "Как на NES: четыре поворота, повтор выпадает реже, скорость старой приставки."
    Preset.CUSTOM -> ""
}
