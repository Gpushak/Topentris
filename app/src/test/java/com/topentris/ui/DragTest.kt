package com.topentris.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DragTest {
    @Test
    fun horizontalDragKeepsThePieceAndFollowsTheSwipe() {
        val cell = 40f
        assertEquals(4, relativeDragColumn(anchor = 4, travelPx = 0f, cellPx = cell))
        assertEquals(4, relativeDragColumn(anchor = 4, travelPx = 12f, cellPx = cell))
        assertEquals(6, relativeDragColumn(anchor = 4, travelPx = 80f, cellPx = cell))
        assertEquals(2, relativeDragColumn(anchor = 4, travelPx = -80f, cellPx = cell))
    }

    @Test
    fun dragStopsAtTheWall() {
        assertEquals(0, relativeDragColumn(anchor = 1, travelPx = -200f, cellPx = 40f))
        assertEquals(9, relativeDragColumn(anchor = 8, travelPx = 200f, cellPx = 40f))
    }

    @Test
    fun slowSwipeEarnsOneCellPerCellOfTravel() {
        assertEquals(0, softDropCells(downPx = 39f, cellPx = 40f))
        assertEquals(1, softDropCells(downPx = 40f, cellPx = 40f))
        assertEquals(2, softDropCells(downPx = 90f, cellPx = 40f))
    }

    @Test
    fun downwardSwipeDropsTheSidewaysWobble() {
        val aim = SidewaysIntent(windowPx = 18f)
        aim.sample(18f, 220f)
        assertEquals(0f, aim.travel, 0.01f)
        val shaky = SidewaysIntent(windowPx = 18f)
        repeat(20) { shaky.sample(3f, 14f) }
        assertEquals(0f, shaky.travel, 0.01f)
    }

    @Test
    fun sidewaysTuckWhileFallingStillMovesThePiece() {
        val aim = SidewaysIntent(windowPx = 18f)
        aim.sample(4f, 160f)
        aim.sample(40f, 6f)
        aim.sample(36f, 4f)
        assertEquals(76f, aim.travel, 0.01f)
        assertEquals(6, relativeDragColumn(anchor = 4, travelPx = aim.travel, cellPx = 40f))
    }

    @Test
    fun aSmallSidewaysArcDuringADropDoesNotMoveThePiece() {
        val cell = 40f
        assertEquals(0f, committedSideTravel(aimedPx = 40f, travelX = 40f, travelY = 220f, cellPx = cell), 0.01f)
        assertEquals(4, relativeDragColumn(4, committedSideTravel(40f, 40f, 220f, cell), cell))
        assertEquals(80f, committedSideTravel(aimedPx = 80f, travelX = 80f, travelY = 220f, cellPx = cell), 0.01f)
        assertEquals(80f, committedSideTravel(aimedPx = 80f, travelX = 80f, travelY = 10f, cellPx = cell), 0.01f)
    }

    @Test
    fun horizontalSwipeStillFollowsTheFinger() {
        val aim = SidewaysIntent(windowPx = 18f)
        aim.sample(30f, 4f)
        aim.sample(50f, 6f)
        assertEquals(80f, aim.travel, 0.01f)
        assertEquals(6, relativeDragColumn(anchor = 4, travelPx = aim.travel, cellPx = 40f))
    }

    @Test
    fun onlyAQuickDownwardFlickIsAHardDrop() {
        val cell = 40f
        assertEquals(false, shouldHardFlick(20f, 20f, 16L, cell))
        assertEquals(false, shouldHardFlick(80f, 6f, 40L, cell))
        assertEquals(true, shouldHardFlick(80f, 36f, 20L, cell))
    }
}
