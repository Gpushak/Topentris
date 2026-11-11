package com.topentris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MatchSaveTest {
    @Test
    fun aSavedMatchOpensOnTheSameBoard() {
        val engine = GameEngine(GameConfig.author(), nextInt = { 0 })
        engine.debugFill(0, 19, Piece.J)
        engine.debugPiece(Piece.T, rotation = 1, x = 4, y = 6)
        engine.pause()
        val saved = MatchSave.decode(engine.capture(ControlConfig(softDropMs = 99)).encode())
        assertNotNull(saved)
        val restored = GameEngine(saved!!.rules, nextInt = { 0 }, restored = saved)
        val snap = restored.snapshot()
        assertEquals(Piece.T, snap.activeType)
        assertEquals(Piece.J, snap.cellAt(0, 19))
        assertEquals(engine.snapshot().queue, snap.queue)
        assertEquals(engine.snapshot().score, snap.score)
        assertEquals(PlayState.PAUSED, snap.state)
        assertEquals(99, saved.controls.softDropMs)
    }
}
