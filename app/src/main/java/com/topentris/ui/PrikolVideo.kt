package com.topentris.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.topentris.audio.PrikolGain

@Composable
internal fun PrikolVideo(
    path: String,
    running: Boolean,
    sfxVolume: Float,
    modifier: Modifier,
    onFinished: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val finished = rememberUpdatedState(onFinished)
    AndroidView(
        factory = { context -> MemeTextureView(context, path, lifecycle) { finished.value() } },
        modifier = modifier,
        update = { it.present(running, sfxVolume) },
        onRelease = { it.close() },
    )
}

private class MemeTextureView(
    context: Context,
    private val path: String,
    private val lifecycle: Lifecycle,
    private val onFinished: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var output: Surface? = null
    private var player: MediaPlayer? = null
    private var ready = false
    private var playing = false
    private var running = false
    private var foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private var volume = 0.8f
    private var closed = false
    private val transform = Matrix()
    private val gain = PrikolGain()
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> foreground = true
            Lifecycle.Event.ON_PAUSE -> foreground = false
            else -> return@LifecycleEventObserver
        }
        sync()
    }

    init {
        isOpaque = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
        lifecycle.addObserver(lifecycleObserver)
    }

    override fun dispatchTouchEvent(event: MotionEvent) = false

    fun present(running: Boolean, volume: Float) {
        this.running = running
        val next = volume.coerceIn(0f, 2f)
        if (this.volume != next) {
            this.volume = next
            if (ready) player?.let { gain.apply(it, next) }
        }
        sync()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        val surface = Surface(texture)
        output = surface
        try {
            val previous = player
            if (previous != null) {
                previous.setSurface(surface)
                if (ready) fitVideo(previous.videoWidth, previous.videoHeight)
                sync()
            } else {
                prepare(surface)
            }
        } catch (error: Exception) {
            Log.w("PrikolVideo", "Could not open $path", error)
            finish()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        if (ready) player?.let { fitVideo(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val surface = output
        output = null
        sync()
        try {
            player?.setSurface(null)
        } catch (error: IllegalStateException) {
            Log.w("PrikolVideo", "Could not detach $path", error)
            finish()
        } finally {
            surface?.release()
        }
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun close() {
        if (closed) return
        closed = true
        lifecycle.removeObserver(lifecycleObserver)
        gain.release()
        player?.release()
        player = null
        output?.release()
        output = null
    }

    private fun prepare(surface: Surface) {
        val video = MediaPlayer()
        player = video
        video.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        context.assets.openFd(path).use { video.setDataSource(it) }
        video.setSurface(surface)
        video.setOnPreparedListener { prepared ->
            if (player !== prepared) return@setOnPreparedListener
            ready = true
            gain.apply(prepared, volume)
            fitVideo(prepared.videoWidth, prepared.videoHeight)
            sync()
        }
        video.setOnVideoSizeChangedListener { _, width, height -> fitVideo(width, height) }
        video.setOnCompletionListener { finish() }
        video.setOnErrorListener { _, what, extra ->
            Log.w("PrikolVideo", "Could not play $path ($what, $extra)")
            finish()
            true
        }
        video.prepareAsync()
    }

    private fun fitVideo(videoWidth: Int, videoHeight: Int) {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val relativeAspect = (videoWidth.toFloat() / videoHeight) / (width.toFloat() / height)
        transform.setScale(minOf(1f, relativeAspect), minOf(1f, 1f / relativeAspect), width / 2f, height / 2f)
        setTransform(transform)
    }

    private fun sync() {
        val video = player ?: return
        val shouldPlay = running && foreground && output != null
        if (!ready || playing == shouldPlay) return
        try {
            if (shouldPlay) video.start() else video.pause()
            playing = shouldPlay
        } catch (error: IllegalStateException) {
            Log.w("PrikolVideo", "Could not update $path", error)
            finish()
        }
    }

    private fun finish() {
        if (closed) return
        close()
        onFinished()
    }
}
