package com.topentris.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.SoundPool

/** Short cues for a lock, a line clear, and a tetris. Uses the same volume as the music. */
class Effects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sources = HashMap<Int, AssetFileDescriptor>()
    private val ids = HashMap<String, Int>()
    private val ready = HashSet<Int>()
    private var level = 0.8f
    private var released = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready.add(sampleId)
            sources.remove(sampleId)?.let { runCatching { it.close() } }
        }
        load(context, "land")
        load(context, "line")
        load(context, "tetris")
    }

    fun setVolume(fraction: Float) {
        level = fraction.coerceIn(0f, 1f)
    }

    fun playLock(clearedLines: Int) {
        val name = when {
            clearedLines >= 4 -> "tetris"
            clearedLines >= 1 -> "line"
            else -> "land"
        }
        val id = ids[name] ?: return
        if (released || level <= 0f || id == 0 || id !in ready) return
        pool.play(id, level, level, 1, 0, 1f)
    }

    fun release() {
        released = true
        sources.values.forEach { runCatching { it.close() } }
        sources.clear()
        runCatching { pool.release() }
    }

    private fun load(context: Context, name: String) {
        val descriptor = context.assets.openFd("sfx/$name.ogg")
        val id = pool.load(descriptor, 1)
        ids[name] = id
        if (id != 0) sources[id] = descriptor else runCatching { descriptor.close() }
    }
}
