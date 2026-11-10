package com.topentris.game

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {
    @Test
    fun orientationsAreClockwiseAroundTheBoxCenter() {
        for (piece in Piece.entries) {
            if (piece == Piece.O) continue
            val center = if (piece == Piece.I) 1.5 else 1.0
            for (rotation in 0 until 4) {
                val turned = Rotation.cells(piece, rotation).map { clockwise(it, center) }.toSet()
                val expected = Rotation.cells(piece, (rotation + 1) and 3).toSet()
                assertEquals(piece.name + " rot " + rotation, expected, turned)
            }
        }
    }

    @Test
    fun srsKicksTheVerticalIOffTheLeftWall() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.I, rotation = 1, x = -2, y = 10)
        val after = engine.frame(0.0, FrameInput(rotateCw = true))
        assertTrue(after.feedback.contains(Feedback.ROTATE))
        assertEquals(
            setOf(Cell(0, 12), Cell(1, 12), Cell(2, 12), Cell(3, 12)),
            after.active.toSet(),
        )
    }

    @Test
    fun classicRotationIsRejectedAgainstTheWall() {
        val engine = GameEngine(GameConfig.nes())
        engine.debugPiece(Piece.I, rotation = 1, x = -2, y = 10)
        val before = engine.snapshot().active.toSet()
        val after = engine.frame(0.0, FrameInput(rotateCw = true))
        assertFalse(after.feedback.contains(Feedback.ROTATE))
        assertEquals(before, after.active.toSet())
    }

    @Test
    fun classicRotationWorksBothWaysInOpenSpace() {
        val clockwise = GameEngine(GameConfig.nes())
        clockwise.debugPiece(Piece.T, rotation = 0, x = 3, y = 8)
        val cw = clockwise.frame(0.0, FrameInput(rotateCw = true))
        assertTrue(cw.feedback.contains(Feedback.ROTATE))
        assertNotEquals(
            Rotation.cells(Piece.T, 0, 3, 8).toSet(),
            cw.active.toSet(),
        )

        val counter = GameEngine(GameConfig.nes())
        counter.debugPiece(Piece.T, rotation = 0, x = 3, y = 8)
        val ccw = counter.frame(0.0, FrameInput(rotateCcw = true))
        assertTrue(ccw.feedback.contains(Feedback.ROTATE))
        assertEquals(Rotation.cells(Piece.T, 3, 3, 8).toSet(), ccw.active.toSet())
    }

    @Test
    fun targetColumnIsReachedInTheSameFrame() {
        val engine = GameEngine(GameConfig.guideline())
        val spawned = engine.snapshot().active.minOf { it.x }
        assertTrue(spawned > 0)
        val moved = engine.frame(0.0, FrameInput(targetColumn = 0))
        assertEquals(0, moved.active.minOf { it.x })
    }

    private fun clockwise(cell: Cell, center: Double): Cell {
        val rx = cell.x - center
        val ry = cell.y - center
        return Cell((-ry + center).roundToInt(), (rx + center).roundToInt())
    }
}
