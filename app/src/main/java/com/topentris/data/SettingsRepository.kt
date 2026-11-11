package com.topentris.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.topentris.game.ControlConfig
import com.topentris.game.GameConfig
import com.topentris.game.Generator
import com.topentris.game.Preset
import com.topentris.game.RotationSystem
import com.topentris.game.ScoreTable
import com.topentris.game.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "topentris")

class SettingsRepository(private val context: Context) {
    val settings: Flow<Settings> = context.store.data.map { it.toSettings() }
    val records: Flow<Map<Preset, List<Int>>> = context.store.data.map { prefs ->
        Preset.entries.associateWith { prefs.scores(it) }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    /** Switches a still-default Guideline save to the author set once. Leaves any other preset alone. */
    suspend fun applyAuthorDefaultOnce() {
        context.store.edit { prefs ->
            if (prefs[Keys.authorDefault] == true) return@edit
            val stored = prefs[Keys.preset]
            if (stored == null || stored == Preset.GUIDELINE.name) {
                prefs.write(
                    Settings(
                        rules = GameConfig.author(),
                        controls = ControlConfig(
                            dasMs = 133,
                            arrMs = 0,
                            softDropMs = 99,
                            vibration = true,
                        ),
                        music = prefs[Keys.music] ?: true,
                        volume = (prefs[Keys.volume] ?: 80).coerceIn(0, 100),
                    ),
                )
            }
            prefs[Keys.authorDefault] = true
        }
    }

    /** @return 1-based place in the top five, or null when the score stays off the table. */
    suspend fun submitScore(preset: Preset, score: Int): Int? {
        if (score <= 0) return null
        var place: Int? = null
        context.store.edit { prefs ->
            val before = prefs.scores(preset)
            val after = (before + score).sortedDescending().take(5)
            prefs[scoresKey(preset)] = after.joinToString(",")
            place = if (after != before) after.indexOf(score) + 1 else null
        }
        return place
    }

    private fun Preferences.toSettings(): Settings {
        val preset = enumOr(this[Keys.preset], Preset.AUTHOR)
        val rules = if (preset == Preset.CUSTOM) {
            GameConfig(
                preset = Preset.CUSTOM,
                rotation = enumOr(this[Keys.rotation], RotationSystem.SRS),
                twoStateSz = false,
                generator = enumOr(this[Keys.generator], Generator.BAG),
                hold = this[Keys.hold] ?: true,
                ghost = this[Keys.ghost] ?: true,
                hardDrop = this[Keys.hardDrop] ?: true,
                lockDelayMs = if ((this[Keys.lockDelay] ?: 500) <= 0) 0 else 500,
                maxLockResets = if ((this[Keys.lockDelay] ?: 500) <= 0) 0 else 15,
                queueSize = if ((this[Keys.queue] ?: 5) <= 1) 1 else 5,
                scoreTable = enumOr(this[Keys.score], ScoreTable.GUIDELINE),
            )
        } else {
            GameConfig.of(preset)
        }
        val controls = ControlConfig(
            dasMs = (this[Keys.das] ?: 133).coerceIn(0, 300),
            arrMs = (this[Keys.arr] ?: 0).coerceIn(0, 100),
            softDropMs = (this[Keys.soft] ?: 99).coerceIn(0, 200),
            vibration = this[Keys.vibration] ?: true,
        )
        return Settings(
            rules,
            controls,
            music = this[Keys.music] ?: true,
            volume = (this[Keys.volume] ?: 80).coerceIn(0, 100),
            scorePopups = this[Keys.scorePopups] ?: true,
        )
    }

    private fun MutablePreferences.write(settings: Settings) {
        val rules = settings.rules
        this[Keys.preset] = rules.preset.name
        this[Keys.rotation] = rules.rotation.name
        this[Keys.generator] = rules.generator.name
        this[Keys.hold] = rules.hold
        this[Keys.ghost] = rules.ghost
        this[Keys.hardDrop] = rules.hardDrop
        this[Keys.lockDelay] = rules.lockDelayMs
        this[Keys.queue] = rules.queueSize
        this[Keys.score] = rules.scoreTable.name
        this[Keys.das] = settings.controls.dasMs
        this[Keys.arr] = settings.controls.arrMs
        this[Keys.soft] = settings.controls.softDropMs
        this[Keys.vibration] = settings.controls.vibration
        this[Keys.music] = settings.music
        this[Keys.volume] = settings.volume.coerceIn(0, 100)
        this[Keys.scorePopups] = settings.scorePopups
    }

    private fun Preferences.scores(preset: Preset): List<Int> {
        return this[scoresKey(preset)]
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .sortedDescending()
            .take(5)
    }

    private fun scoresKey(preset: Preset) = stringPreferencesKey("scores_${preset.name}")

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
        return raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    }

    private object Keys {
        val preset = stringPreferencesKey("preset")
        val rotation = stringPreferencesKey("rotation")
        val generator = stringPreferencesKey("generator")
        val hold = booleanPreferencesKey("hold")
        val ghost = booleanPreferencesKey("ghost")
        val hardDrop = booleanPreferencesKey("hard_drop")
        val lockDelay = intPreferencesKey("lock_delay")
        val queue = intPreferencesKey("queue")
        val score = stringPreferencesKey("score_table")
        val das = intPreferencesKey("das")
        val arr = intPreferencesKey("arr")
        val soft = intPreferencesKey("soft_drop")
        val vibration = booleanPreferencesKey("vibration")
        val music = booleanPreferencesKey("music")
        val volume = intPreferencesKey("volume")
        val scorePopups = booleanPreferencesKey("score_popups")
        val authorDefault = booleanPreferencesKey("author_default")
    }
}
