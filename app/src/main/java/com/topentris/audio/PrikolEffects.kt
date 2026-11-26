package com.topentris.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlin.random.Random

private enum class Cue(val folder: String, val admissionLimit: Int) {
    LAND("figuredown", 5),
    LINES("1-3linescleared", 6),
    FOUR_LINES("4linescleared", 7),
    RANDOM_TICK("randomtick", 7),
    LOSS("losing", 8),
}

/** Keeps separate players for overlapping clips. Admission limits reserve room for important cues. */
internal class PrikolEffects(private val context: Context) {
    private class Clip(val player: MediaPlayer, val cue: Cue) {
        val gain = PrikolGain()
        var ready = false
        var playing = false
    }

    private val files = Cue.entries.associateWith { cue ->
        val path = "prikol/SFX/${cue.folder}"
        context.assets.list(path).orEmpty()
            .filter { it.endsWith(".mp3", ignoreCase = true) }
            .map { "$path/$it" }
    }
    private val clips = mutableMapOf<MediaPlayer, Clip>()
    private val randomTick = RandomTickTimer()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var paused = true
    private var volume = 0.8f

    fun playLock(clearedLines: Int) = play(
        when {
            clearedLines >= 4 -> Cue.FOUR_LINES
            clearedLines > 0 -> Cue.LINES
            else -> Cue.LAND
        },
    )

    fun playLoss() = play(Cue.LOSS)

    fun advance(deltaMs: Double) {
        if (!paused && randomTick.advance(deltaMs, clips.values.any { it.cue == Cue.RANDOM_TICK })) {
            play(Cue.RANDOM_TICK)
        }
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        clips.values.toList().forEach { sync(it) }
    }

    fun setVolume(value: Float) {
        val next = value.coerceIn(0f, 2f)
        if (volume == next) return
        volume = next
        clips.values.filter { it.ready }.forEach { it.gain.apply(it.player, volume) }
    }

    fun release() {
        paused = true
        clips.keys.toList().forEach { finish(it) }
    }

    private fun play(cue: Cue) {
        if (paused || volume == 0f || clips.size >= cue.admissionLimit) return
        val path = files.getValue(cue).randomOrNull() ?: return
        val player = MediaPlayer()
        clips[player] = Clip(player, cue)
        try {
            player.setAudioAttributes(attributes)
            context.assets.openFd(path).use { player.setDataSource(it) }
            player.setOnPreparedListener { prepared ->
                clips[prepared]?.let { clip ->
                    clip.ready = true
                    clip.gain.apply(prepared, volume)
                    sync(clip)
                }
            }
            player.setOnCompletionListener { finish(it) }
            player.setOnErrorListener { failed, what, extra ->
                Log.w("PrikolEffects", "Could not play $path ($what, $extra)")
                finish(failed)
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.w("PrikolEffects", "Could not open $path", error)
            finish(player)
        }
    }

    private fun sync(clip: Clip) {
        val shouldPlay = !paused
        if (!clip.ready || shouldPlay == clip.playing) return
        try {
            if (shouldPlay) clip.player.start() else clip.player.pause()
            clip.playing = shouldPlay
        } catch (error: IllegalStateException) {
            Log.w("PrikolEffects", "Could not update ${clip.cue.folder}", error)
            finish(clip.player)
        }
    }

    private fun finish(player: MediaPlayer) {
        val clip = clips.remove(player) ?: return
        clip.gain.release()
        player.release()
    }
}

internal class RandomTickTimer(
    private val nextDelayMs: () -> Long = { Random.nextLong(15_000, 35_001) },
) {
    private var remainingMs = nextDelayMs().toDouble()

    fun advance(activeDeltaMs: Double, clipPlaying: Boolean): Boolean {
        if (clipPlaying) return false
        remainingMs -= activeDeltaMs
        if (remainingMs > 0) return false
        remainingMs = nextDelayMs().toDouble()
        return true
    }
}
