package com.topentris.game

enum class Preset {
    AUTHOR, GUIDELINE, GAME_BOY, NES, CUSTOM;

    companion object {
        val builtIns = entries.filter { it != CUSTOM }
    }
}

enum class RotationSystem { SRS, CLASSIC }

enum class Generator { BAG, REROLL, UNIFORM }

enum class ScoreTable { CLASSIC, GUIDELINE }

enum class Gravity { GUIDELINE, NES }

data class GameConfig(
    val preset: Preset,
    val rotation: RotationSystem,
    val twoStateSz: Boolean,
    val generator: Generator,
    val hold: Boolean,
    val ghost: Boolean,
    val hardDrop: Boolean,
    val lockDelayMs: Int,
    val maxLockResets: Int,
    val queueSize: Int,
    val scoreTable: ScoreTable,
) {
    val startLevel: Int
        get() = if (scoreTable == ScoreTable.GUIDELINE) 1 else 0

    val gravity: Gravity
        get() = when (preset) {
            Preset.AUTHOR, Preset.GUIDELINE -> Gravity.GUIDELINE
            Preset.GAME_BOY, Preset.NES -> Gravity.NES
            Preset.CUSTOM -> if (scoreTable == ScoreTable.GUIDELINE) Gravity.GUIDELINE else Gravity.NES
        }

    fun detectPreset(): GameConfig {
        val custom = copy(preset = Preset.CUSTOM)
        val match = Preset.builtIns.firstOrNull { of(it).copy(preset = Preset.CUSTOM) == custom }
        return custom.copy(preset = match ?: Preset.CUSTOM)
    }

    companion object {
        fun of(preset: Preset): GameConfig = when (preset) {
            Preset.AUTHOR -> author()
            Preset.GUIDELINE -> guideline()
            Preset.GAME_BOY -> gameBoy()
            Preset.NES -> nes()
            Preset.CUSTOM -> guideline().copy(preset = Preset.CUSTOM)
        }

        fun author() = GameConfig(
            preset = Preset.AUTHOR,
            rotation = RotationSystem.SRS,
            twoStateSz = false,
            generator = Generator.REROLL,
            hold = true,
            ghost = true,
            hardDrop = true,
            lockDelayMs = 500,
            maxLockResets = 15,
            queueSize = 1,
            scoreTable = ScoreTable.GUIDELINE,
        )

        fun guideline() = GameConfig(
            preset = Preset.GUIDELINE,
            rotation = RotationSystem.SRS,
            twoStateSz = false,
            generator = Generator.BAG,
            hold = true,
            ghost = true,
            hardDrop = true,
            lockDelayMs = 500,
            maxLockResets = 15,
            queueSize = 5,
            scoreTable = ScoreTable.GUIDELINE,
        )

        fun gameBoy() = GameConfig(
            preset = Preset.GAME_BOY,
            rotation = RotationSystem.CLASSIC,
            twoStateSz = true,
            generator = Generator.REROLL,
            hold = false,
            ghost = false,
            hardDrop = false,
            lockDelayMs = 0,
            maxLockResets = 0,
            queueSize = 1,
            scoreTable = ScoreTable.CLASSIC,
        )

        fun nes() = GameConfig(
            preset = Preset.NES,
            rotation = RotationSystem.CLASSIC,
            twoStateSz = false,
            generator = Generator.REROLL,
            hold = false,
            ghost = false,
            hardDrop = false,
            lockDelayMs = 0,
            maxLockResets = 0,
            queueSize = 1,
            scoreTable = ScoreTable.CLASSIC,
        )
    }
}

data class ControlConfig(
    val dasMs: Int = 133,
    val arrMs: Int = 0,
    val softDropMs: Int = 99,
    val vibration: Boolean = true,
)

enum class VisualStyle { SIMPLE, NEON, LCD }

data class Settings(
    val rules: GameConfig = GameConfig.author(),
    val controls: ControlConfig = ControlConfig(),
    val music: Boolean = true,
    val musicVolume: Int = 80,
    val sfxVolume: Int = 80,
    val scorePopups: Boolean = true,
    val splitRotate: Boolean = true,
    val showButtons: Boolean = true,
    val prikolMode: Boolean = false,
    val prikolSfxVolume: Int = 120,
    val visualStyle: VisualStyle = VisualStyle.SIMPLE,
) {
    val configuration: GameplaySettings
        get() = GameplaySettings(rules, controls, scorePopups)

    fun applyConfiguration(configuration: GameplaySettings): Settings = copy(
        rules = configuration.rules,
        controls = configuration.controls,
        scorePopups = configuration.scorePopups,
    )
}

data class GameplaySettings(
    val rules: GameConfig,
    val controls: ControlConfig = ControlConfig(),
    val scorePopups: Boolean = true,
)

data class SavedPreset(val name: String, val configuration: GameplaySettings)

data class SettingsState(
    val settings: Settings = Settings(),
    val savedPresets: List<SavedPreset> = emptyList(),
    val preferredPresetName: String? = null,
) {
    private val configuration = settings.configuration
    private val matchingBuiltIn = settings.rules.preset.takeIf { preset ->
        preset != Preset.CUSTOM && configuration == GameplaySettings(GameConfig.of(preset))
    }
    val activeSavedPreset = savedPresets.firstOrNull {
        it.name == preferredPresetName && it.configuration == configuration
    } ?: if (matchingBuiltIn == null) {
        savedPresets.firstOrNull { it.configuration == configuration }
    } else {
        null
    }
    val activeBuiltIn = matchingBuiltIn.takeIf { activeSavedPreset == null }
}
