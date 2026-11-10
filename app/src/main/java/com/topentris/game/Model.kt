package com.topentris.game

const val COLS = 10
const val ROWS = 20

enum class Piece { I, O, T, S, Z, J, L }

enum class PlayState { PLAYING, PAUSED, GAME_OVER }

enum class Feedback { ROTATE, LOCK, HARD_DROP }

data class Cell(val x: Int, val y: Int)

data class FrameInput(
    val shift: Int = 0,
    val shiftSteps: Int = 0,
    val softDrop: Boolean = false,
    val hardDrop: Boolean = false,
    val rotateCw: Boolean = false,
    val rotateCcw: Boolean = false,
    val hold: Boolean = false,
    val targetColumn: Int? = null,
    val softSteps: Int = 0,
)

data class GameSnapshot(
    val cells: List<Piece?>,
    val active: List<Cell>,
    val activeType: Piece?,
    val ghost: List<Cell>,
    val hold: Piece?,
    val holdUsed: Boolean,
    val queue: List<Piece>,
    val score: Int,
    val level: Int,
    val lines: Int,
    val state: PlayState,
    val feedback: Set<Feedback>,
    val clearedLines: Int = 0,
) {
    fun cellAt(x: Int, y: Int): Piece? = cells[y * COLS + x]
}
