package com.topentris.ui

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

private const val MEME_FOLDER = "prikol/VisualMemes"
private const val MAX_MEMES = 4
private const val MAX_VIDEOS = 2

private data class MemeAsset(val path: String) {
    val isVideo = path.endsWith(".mp4", ignoreCase = true)
}

private data class FlyingMeme(
    val asset: MemeAsset,
    val startMs: Long,
    val widthFraction: Float = Random.nextFloat() * 0.20f + 0.27f,
    val phaseX: Float = Random.nextFloat() * (2 * Math.PI).toFloat(),
    val phaseY: Float = Random.nextFloat() * (2 * Math.PI).toFloat(),
    val lifetimeMs: Long = Random.nextLong(3_600, 6_000),
)

@Composable
internal fun PrikolOverlay(running: Boolean, sfxVolume: Float) {
    val assets = LocalContext.current.assets
    val catalog = remember(assets) {
        assets.list(MEME_FOLDER).orEmpty().map { MemeAsset("$MEME_FOLDER/$it") }
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    var active by remember { mutableStateOf(emptyList<FlyingMeme>()) }
    var nextSpawnMs by remember { mutableLongStateOf(Random.nextLong(400, 850)) }
    LaunchedEffect(running, catalog) {
        if (!running || catalog.isEmpty()) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val time = elapsed.longValue + (now - lastFrame).coerceIn(0L, 100_000_000L) / 1_000_000L
                elapsed.longValue = time
                lastFrame = now
                if (active.any { !it.asset.isVideo && time - it.startMs >= it.lifetimeMs }) {
                    active = active.filter { it.asset.isVideo || time - it.startMs < it.lifetimeMs }
                }
                if (active.size < MAX_MEMES && time >= nextSpawnMs) {
                    val videoSlotsOpen = active.count { it.asset.isVideo } < MAX_VIDEOS
                    val asset = catalog.filter { candidate ->
                        (!candidate.isVideo || videoSlotsOpen) && active.none { it.asset == candidate }
                    }.randomOrNull()
                    if (asset != null) active = active + FlyingMeme(asset, time)
                    nextSpawnMs = time + Random.nextLong(1_300, 2_800)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().then(ShareMemeTouches)) {
        val density = LocalDensity.current
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        PrikolFrame(elapsed, Modifier.fillMaxSize())
        active.forEach { meme ->
            key(meme.startMs) {
                val side = maxWidth * meme.widthFraction
                val placement = Modifier.size(side).graphicsLayer {
                    val age = (elapsed.longValue - meme.startMs) / 1_000f
                    translationX = screenWidth * (0.5f + 0.52f * sin(age * 1.55f + meme.phaseX)) - size.width / 2f
                    translationY = screenHeight * (0.5f + 0.48f * sin(age * 1.12f + meme.phaseY)) - size.height / 2f
                    rotationZ = sin(age * 4.3f + meme.phaseY) * 24f
                    val pop = (age * 7f).coerceIn(0.35f, 1f)
                    scaleX = pop * (1f + 0.14f * sin(age * 7f))
                    scaleY = pop * (1f + 0.14f * sin(age * 7f + 0.8f))
                    alpha = 0.84f
                }
                if (meme.asset.isVideo) {
                    PrikolVideo(meme.asset.path, running, sfxVolume, placement) {
                        active = active.filterNot { it.startMs == meme.startMs }
                    }
                } else {
                    PrikolImage(meme, elapsed, placement)
                }
            }
        }
    }
}

@Composable
private fun PrikolFrame(elapsed: LongState, modifier: Modifier) {
    Canvas(modifier) {
        val time = elapsed.longValue
        val phase = (time % 1_200L) / 1_200f * (2 * Math.PI).toFloat()
        val jitter = 8.dp.toPx() * sin(phase * 3f)
        val inset = 5.dp.toPx()
        drawRect(
            Color(0xFFFF3CAF).copy(alpha = 0.75f),
            topLeft = Offset(inset + jitter, inset),
            size = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
            style = Stroke(2.dp.toPx()),
        )
        drawRect(
            Color(0xFF3FFFD6).copy(alpha = 0.55f),
            topLeft = Offset(inset - jitter, inset * 1.6f),
            size = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 3 * inset).coerceAtLeast(0f)),
            style = Stroke(1.dp.toPx()),
        )
        val stride = 15.dp.toPx()
        var y = (time % 600L) / 600f * stride
        while (y < size.height) {
            drawLine(Color(0xFFFF67C1).copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y))
            y += stride
        }
        val sweep = (time % 2_700L) / 2_700f * size.height
        drawRect(Color(0xFF00F5FF).copy(alpha = 0.09f), Offset(0f, sweep), Size(size.width, 18.dp.toPx()))
        drawLine(Color(0xFFFF3CAF).copy(alpha = 0.25f), Offset(0f, sweep), Offset(size.width, sweep), 2.dp.toPx())
        for (index in 0 until 6) {
            val movingY = (sweep + index * size.height / 6f) % size.height
            val length = size.width * (0.03f + 0.075f * (1f + sin(phase + index)))
            val x = if (index % 2 == 0) 0f else size.width - length
            val color = if (index % 2 == 0) Color(0xFF3FFFD6) else Color(0xFFFF3CAF)
            drawRect(color.copy(alpha = 0.56f), Offset(x, movingY), Size(length, (3 + index % 3 * 2).dp.toPx()))
        }
    }
}

@Composable
private fun PrikolImage(meme: FlyingMeme, elapsed: LongState, modifier: Modifier) {
    val assets = LocalContext.current.assets
    val path = meme.asset.path
    val image by produceState<MemePicture?>(null, assets, path) {
        value = withContext(Dispatchers.IO) { MemePicture.load(assets, path) }
    }
    Canvas(modifier) {
        val frame = image ?: return@Canvas
        drawIntoCanvas { frame.draw(it.nativeCanvas, size, elapsed.longValue - meme.startMs) }
    }
}

@Suppress("DEPRECATION") // Movie supports GIF playback on API 26 and 27.
private class MemePicture(private val bitmap: Bitmap, private val movie: Movie? = null) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val frameCanvas = movie?.let { AndroidCanvas(bitmap) }
    private var drawnMs = -1

    fun draw(canvas: AndroidCanvas, frame: Size, ageMs: Long) {
        if (movie != null && frameCanvas != null) {
            val time = ((ageMs / 33L * 33L) % movie.duration().coerceAtLeast(1)).toInt()
            if (time != drawnMs) {
                drawnMs = time
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                frameCanvas.save()
                frameCanvas.scale(bitmap.width.toFloat() / movie.width(), bitmap.height.toFloat() / movie.height())
                movie.setTime(time)
                movie.draw(frameCanvas, 0f, 0f)
                frameCanvas.restore()
            }
        }
        val scale = minOf(frame.width / bitmap.width, frame.height / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (frame.width - width) / 2f
        val top = (frame.height - height) / 2f
        destination.set(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    companion object {
        fun load(assets: AssetManager, path: String): MemePicture? = runCatching {
            val movie = if (path.endsWith(".gif", ignoreCase = true)) {
                assets.open(path).use { Movie.decodeStream(it) }
            } else {
                null
            }
            if (movie != null && movie.width() > 0 && movie.height() > 0 && movie.duration() > 0) {
                val scale = minOf(1f, 320f / maxOf(movie.width(), movie.height()))
                MemePicture(
                    Bitmap.createBitmap(
                        (movie.width() * scale).toInt().coerceAtLeast(1),
                        (movie.height() * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    ),
                    movie,
                )
            } else {
                assets.open(path).use { BitmapFactory.decodeStream(it) }?.let { MemePicture(it) }
            }
        }.getOrNull()
    }
}

// AndroidView has a pointer interop node even when the native video view declines all touches.
private data object ShareMemeTouches : ModifierNodeElement<MemeTouchNode>() {
    override fun create() = MemeTouchNode()
    override fun update(node: MemeTouchNode) = Unit
}

private class MemeTouchNode : Modifier.Node(), PointerInputModifierNode {
    override fun sharePointerInputWithSiblings() = true
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit
    override fun onCancelPointerInput() = Unit
}
