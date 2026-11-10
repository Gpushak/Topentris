package com.topentris.game

/**
 * Orientations are the Guideline bounding boxes. Row 0 is the top of the box,
 * y grows downward. SRS kick tables are stored with y pointing up, as in the
 * Guideline, and flipped when applied.
 */
object Rotation {
    fun cells(piece: Piece, rotation: Int, originX: Int = 0, originY: Int = 0): List<Cell> {
        val rot = if (piece == Piece.O) 0 else rotation and 3
        return SHAPES.getValue(piece)[rot].map { Cell(it.first + originX, it.second + originY) }
    }

    fun spawnX(piece: Piece): Int = if (piece == Piece.O) 4 else 3

    fun spawnY(piece: Piece): Int = -cells(piece, 0).minOf { it.y }

    /** Offsets in engine coordinates (y grows downward). First offset is the basic rotation. */
    fun kicks(piece: Piece, from: Int, to: Int): List<Cell> {
        if (piece == Piece.O) return listOf(Cell(0, 0))
        val table = if (piece == Piece.I) I_KICKS else JLSTZ_KICKS
        val raw = table[from to to] ?: listOf(0 to 0)
        return raw.map { (x, yUp) -> Cell(x, -yUp) }
    }

    private val JLSTZ_KICKS: Map<Pair<Int, Int>, List<Pair<Int, Int>>> = mapOf(
        (0 to 1) to listOf(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        (1 to 0) to listOf(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
        (1 to 2) to listOf(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
        (2 to 1) to listOf(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        (2 to 3) to listOf(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
        (3 to 2) to listOf(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        (3 to 0) to listOf(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        (0 to 3) to listOf(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
    )

    private val I_KICKS: Map<Pair<Int, Int>, List<Pair<Int, Int>>> = mapOf(
        (0 to 1) to listOf(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2),
        (1 to 0) to listOf(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2),
        (1 to 2) to listOf(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1),
        (2 to 1) to listOf(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1),
        (2 to 3) to listOf(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2),
        (3 to 2) to listOf(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2),
        (3 to 0) to listOf(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1),
        (0 to 3) to listOf(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1),
    )

    private val SHAPES: Map<Piece, List<List<Pair<Int, Int>>>> = mapOf(
        Piece.I to listOf(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3),
            listOf(0 to 2, 1 to 2, 2 to 2, 3 to 2),
            listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3),
        ),
        Piece.O to listOf(
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        ),
        Piece.T to listOf(
            listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 1 to 1, 2 to 1, 1 to 2),
            listOf(0 to 1, 1 to 1, 2 to 1, 1 to 2),
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2),
        ),
        Piece.S to listOf(
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
            listOf(1 to 0, 1 to 1, 2 to 1, 2 to 2),
            listOf(1 to 1, 2 to 1, 0 to 2, 1 to 2),
            listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
        ),
        Piece.Z to listOf(
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            listOf(2 to 0, 1 to 1, 2 to 1, 1 to 2),
            listOf(0 to 1, 1 to 1, 1 to 2, 2 to 2),
            listOf(1 to 0, 0 to 1, 1 to 1, 0 to 2),
        ),
        Piece.J to listOf(
            listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 2 to 0, 1 to 1, 1 to 2),
            listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2),
            listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2),
        ),
        Piece.L to listOf(
            listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2),
            listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2),
            listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
        ),
    )
}
