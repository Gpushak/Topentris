package com.topentris.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class BagTest {
    @Test
    fun sevenBagDealsEachPieceOncePerBag() {
        val first = Random(1)
        val second = Random(1)
        val bags = BagRandomizer { first.nextInt(it) }
        val again = BagRandomizer { second.nextInt(it) }
        val dealt = List(14) { bags.next() }
        assertEquals(dealt, List(14) { again.next() })
        assertEquals(Piece.entries.toSet(), dealt.take(7).toSet())
        assertEquals(Piece.entries.toSet(), dealt.drop(7).take(7).toSet())
    }

    @Test
    fun rerollDrawsAgainWhenThePieceRepeats() {
        val rolls = ArrayDeque(listOf(0, 0, 2))
        val randomizer = RerollRandomizer { rolls.removeFirst() }
        assertEquals(Piece.I, randomizer.next())
        assertEquals(Piece.T, randomizer.next())
    }
}
