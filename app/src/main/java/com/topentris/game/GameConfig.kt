package com.topentris.game

enum class Preset { AUTHOR, GUIDELINE, GAME_BOY, NES, CUSTOM }

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

    fun asCustom(): GameConfig = copy(preset = Preset.CUSTOM, twoStateSz = false)

    companion object {
        fun of(preset: Preset): GameConfig = when (preset) {
            Preset.AUTHOR -> author()
            Preset.GUIDELINE -> guideline()
            Preset.GAME_BOY -> gameBoy()
            Preset.NES -> nes()
            Preset.CUSTOM -> guideline().asCustom()
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

data class Settings(
    val rules: GameConfig = GameConfig.author(),
    val controls: ControlConfig = ControlConfig(),
    val music: Boolean = true,
    val volume: Int = 80,
    val scorePopups: Boolean = true,
    val splitRotate: Boolean = true,
    val showButtons: Boolean = true,
)
