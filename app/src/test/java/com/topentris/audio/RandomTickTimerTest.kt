package com.topentris.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomTickTimerTest {
    @Test
    fun waitsTheFullNextDelayAfterALongClipFinishes() {
        val delays = listOf(15_000L, 35_000L, 20_000L).iterator()
        val timer = RandomTickTimer { delays.next() }

        assertFalse(timer.advance(14_999.0, clipPlaying = false))
        assertTrue(timer.advance(1.0, clipPlaying = false))
        assertFalse(timer.advance(123_000.0, clipPlaying = true))
        assertFalse(timer.advance(34_999.0, clipPlaying = false))
        assertTrue(timer.advance(1.0, clipPlaying = false))
    }
}
