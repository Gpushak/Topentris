package com.topentris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {
    @Test
    fun pieceStaysForTheLockDelayThenLocks() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 18)

        val early = engine.frame(499.0)
        assertEquals(0, early.cells.count { it != null })
        assertEquals(PlayState.PLAYING, early.state)

        val locked = engine.frame(1.0)
        assertEquals(4, locked.cells.count { it != null })
        assertTrue(locked.feedback.contains(Feedback.LOCK))
        assertEquals(0, locked.clearedLines)
    }

    @Test
    fun landingDoesNotSpendTimeThatPassedInTheAir() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 17)

        val landed = engine.frame(1000.0)
        assertEquals(0, landed.cells.count { it != null })

        val still = engine.frame(499.0)
        assertEquals(0, still.cells.count { it != null })

        val locked = engine.frame(1.0)
        assertEquals(4, locked.cells.count { it != null })
    }

    @Test
    fun shiftResetsTheLockDelay() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 18)
        engine.frame(400.0)
        engine.frame(0.0, FrameInput(shift = -1, shiftSteps = 1))

        val held = engine.frame(499.0)
        assertEquals(0, held.cells.count { it != null })

        val locked = engine.frame(1.0)
        assertEquals(4, locked.cells.count { it != null })
    }

    @Test
    fun sixteenthGroundedMoveLocksImmediately() {
        val engine = GameEngine(GameConfig.guideline())
        engine.debugPiece(Piece.O, rotation = 0, x = 4, y = 18)
        engine.frame(0.0)

        repeat(15) { index ->
            val direction = if (index % 2 == 0) -1 else 1
            val snap = engine.frame(0.0, FrameInput(shift = direction, shiftSteps = 1))
            assertEquals(0, snap.cells.count { it != null })
        }

        val direction = if (15 % 2 == 0) -1 else 1
        val locked = engine.frame(0.0, FrameInput(shift = direction, shiftSteps = 1))
        assertEquals(4, locked.cells.count { it != null })
        assertTrue(locked.feedback.contains(Feedback.LOCK))
    }
}
