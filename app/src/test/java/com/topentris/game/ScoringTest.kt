package com.topentris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringTest {
    @Test
    fun singleClearsTheRowAndLetsTheStackFall() {
        val engine = GameEngine(GameConfig.guideline())
        for (x in intArrayOf(0, 1, 2, 7, 8, 9)) engine.debugFill(x, 19)
        engine.debugFill(0, 18, Piece.L)
        engine.debugPiece(Piece.I, rotation = 0, x = 3, y = 18)

        val snap = engine.frame(0.0, FrameInput(hardDrop = true))
        assertEquals(1, snap.lines)
        assertEquals(1, snap.clearedLines)
        assertEquals(100, snap.popupPoints)
        assertEquals(100, snap.score)
        assertEquals(Piece.L, snap.cellAt(0, 19))
        assertNull(snap.cellAt(0, 18))
        assertNull(snap.cellAt(1, 19))
    }

    @Test
    fun tetrisScoresEightHundredAtLevelOne() {
        val engine = GameEngine(GameConfig.guideline())
        for (y in 16..19) {
            for (x in 0 until COLS) {
                if (x != 2) engine.debugFill(x, y)
            }
        }
        engine.debugPiece(Piece.I, rotation = 1, x = 0, y = 16)
        val snap = engine.frame(0.0, FrameInput(hardDrop = true))
        assertEquals(4, snap.lines)
        assertEquals(4, snap.clearedLines)
        assertEquals(listOf(16, 17, 18, 19), snap.clearedRows)
        assertEquals(800, snap.popupPoints)
        assertEquals(800, snap.score)
        assertEquals(0, snap.cells.count { it != null })
    }

    @Test
    fun separatedClearsKeepTheirOriginalRowsForOneFrame() {
        val engine = GameEngine(GameConfig.guideline())
        for (row in listOf(17, 19)) {
            for (x in 0 until COLS) if (x != 2) engine.debugFill(x, row)
        }
        engine.debugPiece(Piece.I, rotation = 1, x = 0, y = 16)

        assertEquals(listOf(17, 19), engine.frame(0.0, FrameInput(hardDrop = true)).clearedRows)
        assertEquals(emptyList<Int>(), engine.frame(0.0).clearedRows)
    }

    @Test
    fun classicSingleUsesLevelPlusOne() {
        val engine = GameEngine(GameConfig.nes())
        for (x in intArrayOf(0, 1, 2, 7, 8, 9)) engine.debugFill(x, 19)
        engine.debugPiece(Piece.I, rotation = 0, x = 3, y = 18)
        val snap = engine.frame(1000.0)
        assertEquals(1, snap.lines)
        assertEquals(40, snap.score)
    }

    @Test
    fun consecutiveClearsAddAComboBonus() {
        val engine = GameEngine(GameConfig.guideline())
        clearSingle(engine)
        assertEquals(100, engine.snapshot().score)
        clearSingle(engine)
        assertEquals(250, engine.snapshot().score)
        assertEquals(2, engine.snapshot().lines)
    }

    @Test
    fun softDropDoesNotDumpGravityTimeAsAJump() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 0)
        engine.frame(800.0)
        val snap = engine.frame(40.0, FrameInput(softDrop = true), softDropMs = 100.0)
        assertEquals(
            setOf(Cell(4, 0), Cell(5, 0), Cell(4, 1), Cell(5, 1)),
            snap.active.toSet(),
        )
    }

    @Test
    fun fingerSoftDropAdvancesOneCellPerFrame() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 5)
        val snap = engine.frame(0.0, FrameInput(softSteps = 4))
        assertEquals(6, snap.active.minOf { it.y })
    }

    @Test
    fun softDropScoresOnePerCell() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 0)
        val snap = engine.frame(300.0, FrameInput(softDrop = true), softDropMs = 100.0)
        assertEquals(3, snap.score)
        assertEquals(setOf(Cell(4, 3), Cell(5, 3), Cell(4, 4), Cell(5, 4)), snap.active.toSet())
    }

    @Test
    fun hardDropScoresTwoPerCellAndLocks() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 0, y = 0)
        val snap = engine.frame(0.0, FrameInput(hardDrop = true))
        assertEquals(36, snap.score)
        assertEquals(4, snap.cells.count { it != null })
        assertTrueLock(snap)
    }

    @Test
    fun threeCornerTSpinScoresFourHundred() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugFill(0, 17)
        engine.debugFill(2, 17)
        engine.debugFill(0, 19)
        engine.debugPiece(Piece.T, rotation = 3, x = 0, y = 17)

        val spun = engine.frame(0.0, FrameInput(rotateCw = true))
        assertEquals(setOf(Cell(1, 17), Cell(0, 18), Cell(1, 18), Cell(2, 18)), spun.active.toSet())
        assertEquals(0, spun.score)

        val waiting = engine.frame(499.0)
        assertEquals(0, waiting.score)

        val locked = engine.frame(1.0)
        assertEquals(400, locked.score)
        assertEquals(0, locked.lines)
    }

    private fun clearSingle(engine: GameEngine) {
        for (x in intArrayOf(0, 1, 2, 7, 8, 9)) engine.debugFill(x, 19)
        engine.debugPiece(Piece.I, rotation = 0, x = 3, y = 18)
        engine.frame(0.0, FrameInput(hardDrop = true))
    }

    private fun assertTrueLock(snap: GameSnapshot) {
        assertEquals(PlayState.PLAYING, snap.state)
        assertEquals(true, snap.feedback.contains(Feedback.HARD_DROP))
        assertEquals(true, snap.feedback.contains(Feedback.LOCK))
    }
}
