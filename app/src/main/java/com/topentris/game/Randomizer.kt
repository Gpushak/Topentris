package com.topentris.game

fun interface Randomizer {
    fun next(): Piece
}

class BagRandomizer(private val nextInt: (Int) -> Int) : Randomizer {
    private val bag = ArrayDeque<Piece>()

    override fun next(): Piece {
        if (bag.isEmpty()) refill()
        return bag.removeFirst()
    }

    private fun refill() {
        val items = Piece.entries.toMutableList()
        for (i in items.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val swapped = items[i]
            items[i] = items[j]
            items[j] = swapped
        }
        bag.addAll(items)
    }
}

/** NES-style: one reroll when the draw matches the previous piece. The second draw is kept. */
class RerollRandomizer(private val nextInt: (Int) -> Int) : Randomizer {
    private var previous: Piece? = null

    override fun next(): Piece {
        val pieces = Piece.entries
        var pick = pieces[nextInt(pieces.size)]
        if (pick == previous) pick = pieces[nextInt(pieces.size)]
        previous = pick
        return pick
    }
}

class UniformRandomizer(private val nextInt: (Int) -> Int) : Randomizer {
    override fun next(): Piece = Piece.entries[nextInt(Piece.entries.size)]
}
