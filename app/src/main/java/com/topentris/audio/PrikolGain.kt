package com.topentris.audio

import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.log10
import kotlin.math.roundToInt

/** A regular MediaPlayer volume tops out at unity. Boost only this player's audio session. */
internal class PrikolGain {
    private var enhancer: LoudnessEnhancer? = null
    private var supported = true

    fun apply(player: MediaPlayer, level: Float) {
        val volume = level.coerceIn(0f, 2f)
        val playerVolume = volume.coerceAtMost(1f)
        player.setVolume(playerVolume, playerVolume)
        if (!supported) return

        try {
            if (volume > 1f) {
                val effect = enhancer ?: LoudnessEnhancer(player.audioSessionId).also { enhancer = it }
                effect.setTargetGain((2_000f * log10(volume)).roundToInt())
                check(effect.setEnabled(true) == AudioEffect.SUCCESS)
            } else {
                enhancer?.setEnabled(false)
            }
        } catch (error: RuntimeException) {
            release()
            supported = false
            Log.w("PrikolGain", "Audio session does not support boost", error)
        }
    }

    fun release() {
        enhancer?.release()
        enhancer = null
    }
}
