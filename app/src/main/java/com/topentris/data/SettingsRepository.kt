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
import com.topentris.game.GameplaySettings
import com.topentris.game.Generator
import com.topentris.game.MatchSave
import com.topentris.game.Preset
import com.topentris.game.RotationSystem
import com.topentris.game.SavedPreset
import com.topentris.game.ScoreTable
import com.topentris.game.Settings
import com.topentris.game.SettingsState
import com.topentris.game.decodeControls
import com.topentris.game.decodeRules
import com.topentris.game.encodeControls
import com.topentris.game.encodeRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.store by preferencesDataStore(name = "topentris")

data class Records(
    val byPreset: Map<Preset, List<Int>> = emptyMap(),
    val prikol: List<Int> = emptyList(),
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<SettingsState> = context.store.data.map { prefs ->
        SettingsState(prefs.toSettings(), prefs.savedPresets(), prefs[Keys.preferredPreset])
    }
    val records: Flow<Records> = context.store.data.map { prefs ->
        Records(
            byPreset = Preset.entries.associateWith { prefs.scores(scoresKey(it)) },
            prikol = prefs.scores(Keys.prikolScores),
        )
    }

    val savedMatch: Flow<MatchSave?> = context.store.data.map { prefs ->
        prefs[Keys.savedMatch]?.let { MatchSave.decode(it) }
    }

    suspend fun saveMatch(save: MatchSave) {
        context.store.edit { prefs -> prefs[Keys.savedMatch] = save.encode() }
    }

    suspend fun clearMatch() {
        context.store.edit { prefs -> prefs.remove(Keys.savedMatch) }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun selectBuiltIn(preset: Preset) {
        require(preset in Preset.builtIns)
        context.store.edit { prefs ->
            prefs.write(prefs.toSettings().applyConfiguration(GameplaySettings(GameConfig.of(preset))))
            prefs.remove(Keys.preferredPreset)
        }
    }

    suspend fun savePreset(name: String) {
        val title = name.trim()
        if (title.isEmpty()) return
        context.store.edit { prefs ->
            val presets = prefs.savedPresets()
            if (presets.any { it.name.equals(title, ignoreCase = true) }) return@edit
            prefs.writePresets(presets + SavedPreset(title, prefs.toSettings().configuration))
            prefs[Keys.preferredPreset] = title
        }
    }

    suspend fun selectSavedPreset(name: String) {
        context.store.edit { prefs ->
            val preset = prefs.savedPresets().firstOrNull { it.name == name } ?: return@edit
            prefs.write(prefs.toSettings().applyConfiguration(preset.configuration))
            prefs[Keys.preferredPreset] = name
        }
    }

    suspend fun deletePreset(name: String) {
        context.store.edit { prefs ->
            prefs.writePresets(prefs.savedPresets().filterNot { it.name == name })
            if (prefs[Keys.preferredPreset] == name) prefs.remove(Keys.preferredPreset)
        }
    }

    /** Switches a still-default Guideline save to the author set once. Leaves any other preset alone. */
    suspend fun applyAuthorDefaultOnce() {
        context.store.edit { prefs ->
            if (prefs[Keys.authorDefault] == true) return@edit
            val stored = prefs[Keys.preset]
            if (stored == null || stored == Preset.GUIDELINE.name) {
                prefs.write(
                    prefs.toSettings().copy(
                        rules = GameConfig.author(),
                        controls = ControlConfig(),
                    ),
                )
            }
            prefs[Keys.authorDefault] = true
        }
    }

    /** @return 1-based place in the top five, or null when the score stays off the table. */
    suspend fun submitScore(preset: Preset, score: Int, prikolMode: Boolean = false): Int? {
        if (score <= 0) return null
        val key = if (prikolMode) Keys.prikolScores else scoresKey(preset)
        var place: Int? = null
        context.store.edit { prefs ->
            val before = prefs.scores(key)
            val after = (before + score).sortedDescending().take(5)
            prefs[key] = after.joinToString(",")
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
                twoStateSz = this[Keys.twoStateSz] ?: false,
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
        val musicVolume = (this[Keys.musicVolume] ?: 80).coerceIn(0, 100)
        val sfxVolume = (this[Keys.sfxVolume] ?: musicVolume).coerceIn(0, 100)
        return Settings(
            rules = rules.detectPreset(),
            controls = controls,
            music = this[Keys.music] ?: true,
            musicVolume = musicVolume,
            sfxVolume = sfxVolume,
            scorePopups = this[Keys.scorePopups] ?: true,
            splitRotate = this[Keys.splitRotate] ?: true,
            showButtons = this[Keys.showButtons] ?: true,
            prikolMode = this[Keys.prikolMode] ?: false,
            prikolSfxVolume = (this[Keys.prikolSfxVolume] ?: sfxVolume * 3 / 2).coerceIn(0, 200),
        )
    }

    private fun MutablePreferences.write(settings: Settings) {
        val rules = settings.rules.detectPreset()
        this[Keys.preset] = rules.preset.name
        this[Keys.rotation] = rules.rotation.name
        this[Keys.twoStateSz] = rules.twoStateSz
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
        this[Keys.musicVolume] = settings.musicVolume.coerceIn(0, 100)
        this[Keys.sfxVolume] = settings.sfxVolume.coerceIn(0, 100)
        this[Keys.scorePopups] = settings.scorePopups
        this[Keys.splitRotate] = settings.splitRotate
        this[Keys.showButtons] = settings.showButtons
        this[Keys.prikolMode] = settings.prikolMode
        this[Keys.prikolSfxVolume] = settings.prikolSfxVolume.coerceIn(0, 200)
    }

    private fun Preferences.savedPresets(): List<SavedPreset> {
        val raw = this[Keys.savedPresets] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val entry = array.getJSONObject(index)
                SavedPreset(
                    name = entry.getString("name"),
                    configuration = GameplaySettings(
                        rules = decodeRules(entry.getString("rules")).detectPreset(),
                        controls = decodeControls(entry.getString("controls")),
                        scorePopups = entry.getBoolean("scorePopups"),
                        splitRotate = entry.getBoolean("splitRotate"),
                        showButtons = entry.getBoolean("showButtons"),
                    ),
                )
            }.getOrNull()
        }
    }

    private fun MutablePreferences.writePresets(presets: List<SavedPreset>) {
        val array = JSONArray()
        for ((name, configuration) in presets) {
            array.put(
                JSONObject()
                    .put("name", name)
                    .put("rules", encodeRules(configuration.rules))
                    .put("controls", encodeControls(configuration.controls))
                    .put("scorePopups", configuration.scorePopups)
                    .put("splitRotate", configuration.splitRotate)
                    .put("showButtons", configuration.showButtons),
            )
        }
        this[Keys.savedPresets] = array.toString()
    }

    private fun Preferences.scores(key: Preferences.Key<String>): List<Int> {
        return this[key]
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
        val twoStateSz = booleanPreferencesKey("two_state_sz")
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
        val musicVolume = intPreferencesKey("volume")
        val sfxVolume = intPreferencesKey("sfx_volume")
        val scorePopups = booleanPreferencesKey("score_popups")
        val splitRotate = booleanPreferencesKey("split_rotate")
        val showButtons = booleanPreferencesKey("show_buttons")
        val prikolMode = booleanPreferencesKey("prikol_mode")
        val prikolSfxVolume = intPreferencesKey("prikol_sfx_volume")
        val prikolScores = stringPreferencesKey("scores_prikol")
        val savedPresets = stringPreferencesKey("saved_presets")
        val preferredPreset = stringPreferencesKey("preferred_preset")
        val savedMatch = stringPreferencesKey("saved_match")
        val authorDefault = booleanPreferencesKey("author_default")
    }
}
