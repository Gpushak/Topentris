package com.topentris.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

/** Loops the current soundtrack. Quiet while a match is paused or the app is in the background. */
class Soundtrack(private val context: Context) {
    private val player = MediaPlayer()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val normalTracks = tracksIn("music", ".flac")
    private val metalTracks = tracksIn("prikol/MetalOst", ".mp3")
    private var tracks = normalTracks
    private var index = 0
    private var normalIndex = 0
    private var normalPosition = 0
    private var seekOnPrepare = 0
    private var enabled = false
    private var level = 0.8f
    private var foreground = false
    private var matchPaused = false
    private var wantPlay = false
    private var preparing = false
    private var ready = false
    private var failures = 0
    private var released = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(musicAttributes())
        .setOnAudioFocusChangeListener { change -> onFocus(change) }
        .build()

    init {
        player.setAudioAttributes(musicAttributes())
        player.setOnPreparedListener { prepared ->
            preparing = false
            ready = true
            failures = 0
            prepared.setVolume(level, level)
            if (seekOnPrepare > 0) prepared.seekTo(seekOnPrepare)
            seekOnPrepare = 0
            if (wantPlay) prepared.start()
        }
        player.setOnCompletionListener {
            ready = false
            if (!released && wantPlay) open(index + 1)
        }
        player.setOnErrorListener { _, _, _ ->
            preparing = false
            ready = false
            failures += 1
            if (!released && wantPlay && failures <= tracks.size) open(index + 1)
            true
        }
    }

    fun setVolume(fraction: Float) {
        level = fraction.coerceIn(0f, 1f)
        runCatching { if (ready) player.setVolume(level, level) }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        sync()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        sync()
    }

    fun setMatch(prikolMode: Boolean, paused: Boolean) {
        if (released) return
        matchPaused = paused
        val next = if (prikolMode && metalTracks.isNotEmpty()) metalTracks else normalTracks
        if (next !== tracks) {
            if (tracks === normalTracks) {
                normalIndex = index
                normalPosition = if (ready) runCatching { player.currentPosition }.getOrDefault(0) else seekOnPrepare
            }
            player.reset()
            ready = false
            preparing = false
            failures = 0
            tracks = next
            index = if (next === normalTracks) normalIndex else 0
            seekOnPrepare = if (next === normalTracks) normalPosition else 0
        }
        sync()
    }

    fun release() {
        released = true
        wantPlay = false
        abandonFocus()
        runCatching { player.release() }
    }

    private fun sync() {
        if (released) return
        wantPlay = enabled && foreground && !matchPaused && tracks.isNotEmpty()
        if (!wantPlay) {
            pausePlayer()
            abandonFocus()
            return
        }
        requestFocus()
        when {
            preparing -> Unit
            ready -> startPlayer()
            else -> open(index)
        }
    }

    private fun open(next: Int) {
        if (released || tracks.isEmpty() || failures > tracks.size) return
        index = Math.floorMod(next, tracks.size)
        ready = false
        preparing = true
        runCatching {
            player.reset()
            player.setAudioAttributes(musicAttributes())
            context.assets.openFd(tracks[index]).use { player.setDataSource(it) }
            player.prepareAsync()
        }.onFailure {
            preparing = false
            ready = false
            failures += 1
            if (failures <= tracks.size) open(index + 1)
        }
    }

    private fun startPlayer() {
        runCatching {
            if (!player.isPlaying) player.start()
        }
    }

    private fun pausePlayer() {
        runCatching {
            if (ready && player.isPlaying) player.pause()
        }
    }

    private fun requestFocus() {
        audio?.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        audio?.abandonAudioFocusRequest(focusRequest)
    }

    private fun onFocus(change: Int) {
        if (released) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                runCatching { player.setVolume(level, level) }
                if (wantPlay) startPlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val ducked = level * 0.35f
                runCatching { player.setVolume(ducked, ducked) }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> pausePlayer()
        }
    }

    private fun musicAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun tracksIn(folder: String, extension: String): List<String> = context.assets.list(folder)
        .orEmpty()
        .filter { it.endsWith(extension, ignoreCase = true) }
        .sorted()
        .map { "$folder/$it" }
}
