package com.topentris.ui

import android.app.Activity
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.topentris.R
import com.topentris.game.VisualStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.topentris.audio.Effects
import com.topentris.audio.PrikolEffects
import com.topentris.audio.Soundtrack
import com.topentris.data.SettingsRepository
import com.topentris.game.COLS
import com.topentris.game.Cell
import com.topentris.game.ControlConfig
import com.topentris.game.DasController
import com.topentris.game.Feedback
import com.topentris.game.FrameInput
import com.topentris.game.GameConfig
import com.topentris.game.GameEngine
import com.topentris.game.GameSnapshot
import com.topentris.game.MatchSave
import com.topentris.game.Piece
import com.topentris.game.PlayState
import com.topentris.game.ROWS
import com.topentris.game.Rotation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    rules: GameConfig,
    controls: ControlConfig,
    sessionId: Int,
    repository: SettingsRepository,
    soundtrack: Soundtrack,
    effects: Effects,
    scorePopups: Boolean,
    splitRotate: Boolean,
    showButtons: Boolean,
    prikolMode: Boolean,
    prikolVolume: Float,
    resume: MatchSave?,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    val visuals = LocalVisuals.current
    val tiles = neonTiles(visuals)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(sessionId) {
        if (resume == null) GameEngine(rules) else GameEngine(rules, restored = resume)
    }
    val live = remember(sessionId) { LiveInput() }
    val das = remember(sessionId) { DasController() }
    var snap by remember(sessionId) { mutableStateOf(engine.snapshot()) }
    var resumeSeconds by remember(sessionId) { mutableStateOf<Int?>(null) }
    val countingDown = resumeSeconds != null
    val playing = snap.state == PlayState.PLAYING
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val prikolEffects = remember(sessionId) {
        if (prikolMode) PrikolEffects(context.applicationContext) else null
    }
    val board = remember(sessionId) { BoardRef(rules, snap, splitRotate) }
    board.rules = rules
    board.snap = snap
    board.splitRotate = splitRotate
    val controlsState = rememberUpdatedState(controls)
    val popupsEnabled = rememberUpdatedState(scorePopups)
    var floaters by remember(sessionId) { mutableStateOf(emptyList<ScoreFloater>()) }
    var clock by remember(sessionId) { mutableLongStateOf(0L) }
    var bursts by remember(sessionId) { mutableStateOf(emptyList<PrikolLineBurst>()) }
    var burstClock by remember(sessionId) { mutableLongStateOf(0L) }
    var recordPlace by remember(sessionId) { mutableStateOf<Int?>(null) }
    var scoreSent by remember(sessionId) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(prikolEffects) {
        onDispose { prikolEffects?.release() }
    }
    SideEffect { prikolEffects?.setVolume(prikolVolume) }

    suspend fun storeMatch() {
        if (engine.playState == PlayState.GAME_OVER) repository.clearMatch()
        else repository.saveMatch(engine.capture(controls).copy(prikolMode = prikolMode))
    }

    fun persist() {
        scope.launch {
            withContext(NonCancellable) { storeMatch() }
        }
    }
    fun refreshSnapshot() {
        snap = engine.snapshot()
        board.snap = snap
    }
    fun pauseMatch() {
        engine.pause()
        prikolEffects?.setPaused(true)
        resumeSeconds = null
        live.clear()
        das.reset()
        refreshSnapshot()
    }
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    foreground = false
                    prikolEffects?.setPaused(true)
                    if (engine.playState != PlayState.GAME_OVER) pauseMatch()
                    persist()
                }
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    prikolEffects?.setPaused(engine.playState == PlayState.PAUSED)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engine, countingDown) {
        if (!countingDown) return@LaunchedEffect
        for (seconds in 2 downTo 0) {
            delay(1_000)
            if (resumeSeconds == null) return@LaunchedEffect
            if (seconds > 0) resumeSeconds = seconds
        }
        live.clear()
        engine.resume()
        prikolEffects?.setPaused(!foreground)
        refreshSnapshot()
        resumeSeconds = null
    }

    LaunchedEffect(engine) {
        var last = 0L
        var activeTime = 0L
        while (true) {
            withFrameNanos { now ->
                if (floaters.isNotEmpty()) {
                    clock = now
                    val alive = floaters.filter { now - it.born < POPUP_LIFE_NS }
                    if (alive.size != floaters.size) floaters = alive
                }
                if (engine.playState != PlayState.PLAYING) {
                    last = 0L
                    return@withFrameNanos
                }
                val delta = if (last == 0L) 16_000_000L else (now - last).coerceAtMost(100_000_000L)
                val dt = delta / 1_000_000.0
                activeTime += delta
                last = now
                val cfg = controlsState.value
                val next = engine.frame(dt, live.consume(das, dt, cfg), cfg.softDropMs.toDouble())
                snap = next
                board.snap = next
                if (cfg.vibration && next.feedback.isNotEmpty()) vibrate(context)
                if (Feedback.LOCK in next.feedback) {
                    if (prikolEffects == null) effects.playLock(next.clearedLines)
                    else prikolEffects.playLock(next.clearedLines)
                }
                if (next.state == PlayState.PLAYING) prikolEffects?.advance(dt)
                if (prikolMode && next.clearedRows.isNotEmpty()) {
                    bursts = bursts + PrikolLineBurst(next.clearedRows, activeTime)
                }
                if (bursts.isNotEmpty()) {
                    burstClock = activeTime
                    if (bursts.any { it.isFinished(activeTime) }) {
                        bursts = bursts.filterNot { it.isFinished(activeTime) }
                    }
                }
                if (popupsEnabled.value && next.popupPoints > 0) {
                    clock = now
                    floaters = floaters + ScoreFloater(
                        points = next.popupPoints,
                        tetris = next.clearedLines >= 4,
                        row = next.popupRow,
                        born = now,
                    )
                }
            }
        }
    }

    LaunchedEffect(prikolMode, snap.state) {
        soundtrack.setMatch(prikolMode, paused = snap.state == PlayState.PAUSED)
    }
    DisposableEffect(soundtrack) {
        onDispose { soundtrack.setMatch(prikolMode = false, paused = false) }
    }

    LaunchedEffect(snap.state, sessionId) {
        if (snap.state == PlayState.GAME_OVER && !scoreSent) {
            scoreSent = true
            prikolEffects?.playLoss()
            recordPlace = repository.submitScore(rules.preset, snap.score, prikolMode)
            repository.clearMatch()
        }
    }

    fun leave() {
        scope.launch {
            withContext(NonCancellable) { storeMatch() }
            onExit()
        }
    }

    BackHandler {
        if (engine.playState == PlayState.PLAYING || resumeSeconds != null) {
            pauseMatch()
            persist()
        } else {
            leave()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ConsoleFrame(Modifier.weight(1f).fillMaxWidth(), visuals) {
                InfoBar(
                    snap,
                    rules,
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    scoreGain = if (visuals.lcd && scorePopups) floaters.lastOrNull() else null,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = if (visuals.simple) 2.dp else 3.dp),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(sessionId, playing) { if (playing) watchField(live, board) },
                    ) {
                        drawField(snap, prikolMode, visuals, tiles)
                        if (bursts.isNotEmpty()) {
                            val field = fieldMetrics(size.width, size.height)
                            for (burst in bursts) {
                                burst.draw(this, burstClock, Offset(field.left, field.top), field.cell)
                            }
                        }
                    }
                    if (!visuals.lcd) ScoreFloaters(floaters, clock, Modifier.fillMaxSize())
                }
            }
            ControlPad(
                input = live,
                hardEnabled = rules.hardDrop,
                holdEnabled = rules.hold,
                showButtons = showButtons,
                onPause = {
                    pauseMatch()
                    persist()
                },
            )
        }
        if (prikolMode && snap.state != PlayState.GAME_OVER) {
            key(sessionId) {
                PrikolOverlay(running = playing && foreground, sfxVolume = prikolVolume)
            }
        }
        val seconds = resumeSeconds
        if (seconds != null) {
            ResumeCountdown(seconds)
        } else if (snap.state == PlayState.PAUSED) {
            Overlay(title = "Пауза", detail = "Партия на паузе") {
                Plate(label = "Продолжить", accent = true, onClick = { resumeSeconds = 3 })
                Plate(label = "Заново", onClick = onRestart)
                Plate(label = "В меню", onClick = { leave() })
            }
        } else if (snap.state == PlayState.GAME_OVER) {
            val place = recordPlace
            val detail = buildString {
                append("Счёт ")
                append(formatScore(snap.score))
                append("  ·  линии ")
                append(snap.lines)
                if (place != null) {
                    append("\nНовый рекорд, ")
                    append(place)
                    append(" место")
                }
            }
            Overlay(title = "Конец партии", detail = detail) {
                Plate(label = "Заново", accent = true, onClick = onRestart)
                Plate(label = "В меню", onClick = { leave() })
            }
        }
    }
}

@Composable
private fun ConsoleFrame(modifier: Modifier, visuals: Visuals, content: @Composable ColumnScope.() -> Unit) {
    if (visuals.simple) {
        Column(modifier, content = content)
        return
    }
    BoxWithConstraints(modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, (maxHeight - 108.dp).coerceAtLeast(0.dp) / 2 + 20.dp)
        Column(Modifier.width(width).fillMaxHeight()) {
            GameWordmark(visuals, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp))
            val shape = RoundedCornerShape(if (visuals.lcd) 16.dp else 6.dp)
            val bezel = if (visuals.lcd) Modifier
                .background(Color(0xFFADA482), shape)
                .border(1.dp, visuals.edge, shape)
                .padding(5.dp)
                .background(Color(0xFFA5A575), RoundedCornerShape(11.dp))
                .padding(3.dp) else Modifier
            Column(Modifier.fillMaxWidth().weight(1f).then(bezel), content = content)
        }
    }
}

@Composable
private fun InfoBar(snap: GameSnapshot, rules: GameConfig, modifier: Modifier, scoreGain: ScoreFloater?) {
    val visuals = LocalVisuals.current
    if (!visuals.simple) {
        val shape = RoundedCornerShape(if (visuals.lcd) 7.dp else 4.dp)
        val panel = Modifier.background(if (visuals.lcd) visuals.field else visuals.panel, shape)
            .border(1.dp, visuals.edge, shape)
        val labelColor = if (visuals.lcd) visuals.text else visuals.muted
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (rules.hold) {
                    Column(Modifier.fillMaxWidth().then(panel), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ЗАПАС", color = labelColor, fontSize = 10.sp, fontFamily = visuals.displayFont)
                        MiniPiece(snap.hold, snap.holdUsed, Modifier.width(28.dp).height(23.dp))
                    }
                }
            }
            Column(Modifier.weight(1.5f).then(if (visuals.lcd) panel else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("СЧЁТ", color = labelColor, fontSize = 10.sp, fontFamily = visuals.displayFont)
                Text(formatScore(snap.score), color = visuals.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                if (visuals.lcd) {
                    val gainText = scoreGain?.let {
                        "+${formatScore(it.points)}" + if (it.tetris) "\nТетрис!" else ""
                    }.orEmpty()
                    Text(
                        text = gainText,
                        color = visuals.text,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = visuals.displayFont,
                        textAlign = TextAlign.Center,
                        minLines = 2,
                        maxLines = 2,
                    )
                }
                Text("УР. ${snap.level}  ЛИН. ${snap.lines}", color = labelColor, fontSize = 10.sp, fontFamily = visuals.displayFont)
            }
            Column(Modifier.weight(1f).then(panel), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ДАЛЬШЕ", color = labelColor, fontSize = 10.sp, fontFamily = visuals.displayFont)
                Row(Modifier.fillMaxWidth().height(23.dp), horizontalArrangement = Arrangement.Center) {
                    snap.queue.forEach { piece ->
                        val slot = if (snap.queue.size == 1) Modifier.width(32.dp) else Modifier.weight(1f)
                        MiniPiece(piece, false, slot.fillMaxHeight())
                    }
                }
            }
        }
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (rules.hold) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Запас", color = visuals.muted, fontSize = 11.sp, fontFamily = visuals.displayFont)
                MiniPiece(
                    piece = snap.hold,
                    dim = snap.holdUsed,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .height(36.dp)
                        .fillMaxWidth(),
                )
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatScore(snap.score),
                color = visuals.text,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "уровень ${snap.level}  ·  линии ${snap.lines}",
                color = visuals.muted,
                fontSize = 12.sp,
                fontFamily = visuals.displayFont,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                snap.queue.forEach { piece ->
                    MiniPiece(
                        piece = piece,
                        dim = false,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .width(34.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreFloaters(floaters: List<ScoreFloater>, now: Long, modifier: Modifier) {
    if (floaters.isEmpty()) return
    val visuals = LocalVisuals.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val metrics = fieldMetrics(widthPx, heightPx)
        floaters.forEach { floater ->
            val age = ((now - floater.born).coerceAtLeast(0L) / 1_000_000f) / (POPUP_LIFE_NS / 1_000_000f)
            val t = age.coerceIn(0f, 1f)
            val fade = if (t < 0.4f) 1f else ((1f - t) / 0.6f).coerceIn(0f, 1f)
            val pop = if (t < 0.12f) 0.72f + 0.28f * (t / 0.12f) else 1f
            val rise = t * metrics.cell * 1.8f
            val top = metrics.top + floater.row.coerceIn(0, ROWS - 1) * metrics.cell - rise
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, top.roundToInt()) }
                    .graphicsLayer {
                        alpha = fade
                        scaleX = pop
                        scaleY = pop
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (floater.tetris) {
                    Text("Тетрис!", color = visuals.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "+${formatScore(floater.points)}",
                    color = visuals.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private data class ScoreFloater(
    val points: Int,
    val tetris: Boolean,
    val row: Int,
    val born: Long,
)

private const val POPUP_LIFE_NS = 1_000_000_000L

@Composable
private fun neonTiles(visuals: Visuals): ImageBitmap? =
    if (visuals.style == VisualStyle.NEON) ImageBitmap.imageResource(R.drawable.neon_tiles) else null

@Composable
private fun MiniPiece(piece: Piece?, dim: Boolean, modifier: Modifier) {
    val visuals = LocalVisuals.current
    val tiles = neonTiles(visuals)
    Canvas(modifier) {
        val shown = piece ?: return@Canvas
        val cells = Rotation.cells(shown, 0)
        val minX = cells.minOf { it.x }
        val maxX = cells.maxOf { it.x }
        val minY = cells.minOf { it.y }
        val maxY = cells.maxOf { it.y }
        val spanX = maxX - minX + 1
        val spanY = maxY - minY + 1
        val cell = minOf(size.width / (spanX + 0.6f), size.height / (spanY + 0.6f))
        val left = (size.width - spanX * cell) / 2f
        val top = (size.height - spanY * cell) / 2f
        for (block in cells) {
            block(left + (block.x - minX) * cell, top + (block.y - minY) * cell, cell, shown, visuals, tiles, opacity = if (dim) 0.35f else 1f)
        }
    }
}

@Composable
private fun ControlPad(
    input: LiveInput,
    hardEnabled: Boolean,
    holdEnabled: Boolean,
    showButtons: Boolean,
    onPause: () -> Unit,
) {
    val simple = LocalVisuals.current.simple
    val margin = if (simple) 10.dp else 24.dp
    val upperHeight = if (simple) 52.dp else 48.dp
    val lowerHeight = if (simple) 56.dp else 48.dp
    if (!showButtons) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (holdEnabled) {
                GameKey(GameKeyKind.HOLD, true, Modifier.weight(1f).height(48.dp), onDown = { input.hold = true })
            }
            GameKey(GameKeyKind.PAUSE, true, Modifier.weight(1f).height(48.dp), onDown = onPause)
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = margin, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameKey(GameKeyKind.ROTATE_LEFT, true, Modifier.weight(1f).height(upperHeight), onDown = { input.ccw = true })
            GameKey(GameKeyKind.ROTATE_RIGHT, true, Modifier.weight(1f).height(upperHeight), onDown = { input.cw = true })
            GameKey(GameKeyKind.HOLD, holdEnabled, Modifier.weight(1f).height(upperHeight), onDown = { input.hold = true })
            GameKey(GameKeyKind.PAUSE, true, Modifier.weight(1f).height(upperHeight), onDown = onPause)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameKey(
                GameKeyKind.LEFT,
                true,
                Modifier.weight(1f).height(lowerHeight),
                onDown = { input.pressLeft(true) },
                onUp = { input.pressLeft(false) },
            )
            GameKey(
                GameKeyKind.DOWN,
                true,
                Modifier.weight(1f).height(lowerHeight),
                onDown = { input.soft = true },
                onUp = { input.soft = false },
            )
            GameKey(
                GameKeyKind.RIGHT,
                true,
                Modifier.weight(1f).height(lowerHeight),
                onDown = { input.pressRight(true) },
                onUp = { input.pressRight(false) },
            )
            GameKey(GameKeyKind.DROP, hardEnabled, Modifier.weight(1f).height(lowerHeight), onDown = { input.hard = true })
        }
    }
}

@Composable
private fun ResumeCountdown(seconds: Int) {
    val visuals = LocalVisuals.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BlockingScrim()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Продолжение через", color = visuals.text, fontSize = 18.sp)
            Text("$seconds", color = visuals.accent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Overlay(
    title: String,
    detail: String,
    actions: @Composable () -> Unit,
) {
    val visuals = LocalVisuals.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BlockingScrim()
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(visuals.panel, RoundedCornerShape(if (visuals.simple) 16.dp else visuals.plateRadius))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = visuals.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = visuals.muted, fontSize = 15.sp)
            actions()
        }
    }
}

@Composable
private fun BlockingScrim() {
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalVisuals.current.scrim)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    )
}

private fun DrawScope.drawField(snap: GameSnapshot, prikolMode: Boolean, visuals: Visuals, tiles: ImageBitmap?) {
    val metrics = fieldMetrics(size.width, size.height)
    if (metrics.cell <= 1f) return
    val origin = Offset(metrics.left, metrics.top)
    val dimensions = Size(metrics.cell * COLS, metrics.cell * ROWS)
    val usePrikolColors = prikolMode && visuals.simple
    drawRoundRect(
        color = if (usePrikolColors) Color(0xFF301034) else visuals.field,
        topLeft = origin,
        size = dimensions,
        cornerRadius = CornerRadius(6f),
    )
    val grid = if (usePrikolColors) Color(0xFF6C2769) else visuals.grid
    for (column in 0..COLS) {
        val x = metrics.left + column * metrics.cell
        drawLine(grid, Offset(x, metrics.top), Offset(x, metrics.top + metrics.cell * ROWS), strokeWidth = 1f)
    }
    for (row in 0..ROWS) {
        val y = metrics.top + row * metrics.cell
        drawLine(grid, Offset(metrics.left, y), Offset(metrics.left + metrics.cell * COLS, y), strokeWidth = 1f)
    }
    if (!visuals.simple) {
        val stroke = if (visuals.lcd) 3.dp.toPx() else 1.5.dp.toPx()
        val inset = stroke / 2f
        drawRoundRect(
            visuals.edge,
            origin + Offset(inset, inset),
            Size(dimensions.width - stroke, dimensions.height - stroke),
            CornerRadius(6f),
            style = Stroke(stroke),
        )
    }
    val falling = snap.activeType
    if (falling != null) {
        for (cell in snap.ghost) {
            if (cell.y !in 0 until ROWS || cell in snap.active) continue
            paint(metrics, cell, falling, visuals, tiles, ghost = true)
        }
    }
    for (y in 0 until ROWS) {
        for (x in 0 until COLS) {
            val type = snap.cellAt(x, y) ?: continue
            paint(metrics, Cell(x, y), type, visuals, tiles)
        }
    }
    if (falling != null) {
        for (cell in snap.active) {
            if (cell.y !in 0 until ROWS) continue
            paint(metrics, cell, falling, visuals, tiles, active = true)
        }
    }
}

private fun DrawScope.paint(
    metrics: FieldMetrics,
    cell: Cell,
    piece: Piece,
    visuals: Visuals,
    tiles: ImageBitmap?,
    ghost: Boolean = false,
    active: Boolean = false,
) {
    block(
        metrics.left + cell.x * metrics.cell, metrics.top + cell.y * metrics.cell,
        metrics.cell, piece, visuals, tiles, ghost, active,
    )
}

private fun DrawScope.block(
    x: Float,
    y: Float,
    cell: Float,
    piece: Piece,
    visuals: Visuals,
    tiles: ImageBitmap?,
    ghost: Boolean = false,
    active: Boolean = false,
    opacity: Float = 1f,
) {
    if (cell <= 1f) return
    val color = visuals.piece(piece).copy(alpha = opacity)
    val gap = (cell * 0.08f).coerceAtLeast(1f)
    val side = cell - gap
    val origin = Offset(x + gap / 2f, y + gap / 2f)
    val dimensions = Size(side, side)
    when {
        visuals.simple -> {
            drawRect(color.copy(alpha = if (ghost) 0.28f else color.alpha), origin, dimensions)
            drawRect(Color.White.copy(alpha = 0.24f), origin, Size(side, side * 0.16f))
        }
        ghost -> {
            val inset = side * 0.12f
            val leg = side * 0.22f
            val stroke = side * 0.06f
            val outline = color.copy(alpha = 0.76f)
            for (dx in listOf(1f, -1f)) {
                for (dy in listOf(1f, -1f)) {
                    val corner = origin + Offset(
                        if (dx > 0) inset else side - inset,
                        if (dy > 0) inset else side - inset,
                    )
                    drawLine(outline, corner, corner + Offset(dx * leg, 0f), stroke)
                    drawLine(outline, corner, corner + Offset(0f, dy * leg), stroke)
                }
            }
        }
        visuals.lcd -> {
            drawRect(color, origin, dimensions)
            val inset = side * 0.18f
            drawRect(
                visuals.field.copy(alpha = color.alpha * 0.58f),
                origin + Offset(inset, inset),
                Size(side - 2 * inset, side - 2 * inset),
                style = Stroke(side * 0.055f),
            )
        }
        tiles != null -> {
            // The atlas uses Piece order across columns, with the brighter falling pieces below.
            val sprite = tiles.height / 2
            val extent = (cell * 44f / 48f).roundToInt().coerceAtLeast(1)
            val inset = (cell - extent) / 2f
            drawImage(
                tiles,
                srcOffset = IntOffset(piece.ordinal * sprite, if (active) sprite else 0),
                srcSize = IntSize(sprite, sprite),
                dstOffset = IntOffset((x + inset).roundToInt(), (y + inset).roundToInt()),
                dstSize = IntSize(extent, extent),
                alpha = opacity,
            )
        }
    }
}

private data class FieldMetrics(val cell: Float, val left: Float, val top: Float)

private fun fieldMetrics(width: Float, height: Float): FieldMetrics {
    val cell = minOf(width / COLS, height / ROWS)
    return FieldMetrics(
        cell = cell,
        left = (width - cell * COLS) / 2f,
        top = (height - cell * ROWS) / 2f,
    )
}

/**
 * Horizontal distance that should move the piece.
 * A downward swipe drops its sideways wobble. A sideways move, including a tuck while falling, is kept.
 */
internal class SidewaysIntent(private val windowPx: Float) {
    var travel: Float = 0f
        private set
    private var pendingX = 0f
    private var pendingY = 0f

    fun sample(dx: Float, dy: Float) {
        pendingX += dx
        pendingY += dy
        if (windowPx > 0f && abs(pendingX) + abs(pendingY) < windowPx) return
        if (abs(pendingX) > abs(pendingY) * 1.25f) travel += pendingX
        pendingX = 0f
        pendingY = 0f
    }

    fun reset() {
        travel = 0f
        pendingX = 0f
        pendingY = 0f
    }
}

/**
 * During a downward swipe, ignore a small sideways arc. A real sideways tuck still comes through.
 * A sideways swipe is left unchanged.
 */
internal fun committedSideTravel(aimedPx: Float, travelX: Float, travelY: Float, cellPx: Float): Float {
    if (cellPx <= 0f) return aimedPx
    val dropping = travelY > abs(travelX) && travelY >= cellPx * 0.5f
    if (dropping && abs(aimedPx) < cellPx * 1.35f) return 0f
    return aimedPx
}

/** Column the piece should occupy after a horizontal drag, measured from where it already stood. */
internal fun relativeDragColumn(anchor: Int, travelPx: Float, cellPx: Float): Int {
    if (cellPx <= 0f) return anchor.coerceIn(0, COLS - 1)
    return (anchor + (travelPx / cellPx).roundToInt()).coerceIn(0, COLS - 1)
}

/** How many cells a slow downward swipe has earned. The piece never gets ahead of the finger. */
internal fun softDropCells(downPx: Float, cellPx: Float): Int {
    if (cellPx <= 0f || downPx <= 0f) return 0
    return (downPx / cellPx).toInt()
}

/**
 * A hard drop is a quick downward flick, not a slow drag you can still steer sideways.
 * [instantDownPx] is the latest sample, so a late flick still counts.
 */
internal fun shouldHardFlick(
    totalDownPx: Float,
    instantDownPx: Float,
    instantDtMs: Long,
    cellPx: Float,
): Boolean {
    if (cellPx <= 0f || instantDtMs <= 0L) return false
    if (totalDownPx < cellPx * 1.1f) return false
    if (instantDownPx < cellPx * 0.45f) return false
    return instantDownPx / instantDtMs.toFloat() >= cellPx / 80f
}

/** A short tap on the right half turns clockwise. The left half turns the other way when [split] is on. */
internal fun tapIsClockwise(x: Float, mid: Float, split: Boolean): Boolean {
    if (!split) return true
    return x >= mid
}

private fun pieceCenter(snap: GameSnapshot): Int {
    val cells = snap.active
    if (cells.isEmpty()) return COLS / 2
    return (cells.minOf { it.x } + cells.maxOf { it.x }) / 2
}

private class BoardRef(var rules: GameConfig, var snap: GameSnapshot, var splitRotate: Boolean)

private class LiveInput {
    var left = false
    var right = false
    var soft = false
    var drag: Int? = null
    var hard = false
    var cw = false
    var ccw = false
    var hold = false
    private var recent = 0
    private var aimedCells = 0
    private var pendingCells = 0

    fun pressLeft(down: Boolean) {
        left = down
        if (down) recent = -1
    }

    fun pressRight(down: Boolean) {
        right = down
        if (down) recent = 1
    }

    fun followDrop(cells: Int) {
        val next = cells.coerceAtLeast(0)
        if (next > aimedCells) {
            pendingCells += next - aimedCells
            aimedCells = next
        }
    }

    fun resetGestureDrop() {
        aimedCells = 0
        pendingCells = 0
    }

    fun clear() {
        left = false
        right = false
        soft = false
        drag = null
        hard = false
        cw = false
        ccw = false
        hold = false
        recent = 0
        resetGestureDrop()
    }

    fun consume(das: DasController, dtMs: Double, controls: ControlConfig): FrameInput {
        val direction = when {
            left && right -> recent
            left -> -1
            right -> 1
            else -> 0
        }
        val steps = if (drag != null) 0 else das.advance(direction, dtMs, controls.dasMs, controls.arrMs)
        val softSteps = if (pendingCells > 0) 1 else 0
        if (softSteps == 1) pendingCells -= 1
        val frame = FrameInput(
            shift = if (steps > 0) direction else 0,
            shiftSteps = steps,
            softDrop = soft,
            hardDrop = hard,
            rotateCw = cw,
            rotateCcw = ccw,
            hold = hold,
            targetColumn = drag,
            softSteps = softSteps,
        )
        hard = false
        cw = false
        ccw = false
        hold = false
        return frame
    }
}

private suspend fun PointerInputScope.watchField(input: LiveInput, board: BoardRef) {
    val slop = viewConfiguration.touchSlop
    val swipe = slop * 3f
    awaitPointerEventScope {
        var pointer: PointerId? = null
        var start = Offset.Zero
        var steered = false
        var flung = false
        var anchorColumn = 0
        var anchorType: Piece? = null
        var lastX = 0f
        var lastY = 0f
        var lastMillis = 0L
        var aiming = false
        var sideways = SidewaysIntent(1f)
        fun release(rotateIfTap: Boolean) {
            if (rotateIfTap && !steered && !flung) {
                val metrics = fieldMetrics(size.width.toFloat(), size.height.toFloat())
                val mid = metrics.left + metrics.cell * COLS / 2f
                if (tapIsClockwise(start.x, mid, board.splitRotate)) input.cw = true else input.ccw = true
            }
            pointer = null
            steered = false
            flung = false
            aiming = false
            anchorType = null
            sideways.reset()
            input.drag = null
            input.resetGestureDrop()
        }
        while (true) {
            val event = awaitPointerEvent()
            val metrics = fieldMetrics(size.width.toFloat(), size.height.toFloat())
            event.changes.forEach { change ->
                if (pointer == null) {
                    if (!change.changedToDown()) return@forEach
                    val x = change.position.x
                    val y = change.position.y
                    val inside = x in metrics.left..(metrics.left + metrics.cell * COLS) &&
                        y in metrics.top..(metrics.top + metrics.cell * ROWS)
                    if (!inside) return@forEach
                    pointer = change.id
                    start = change.position
                    lastX = change.position.x
                    lastY = change.position.y
                    lastMillis = change.uptimeMillis
                    steered = false
                    flung = false
                    aiming = false
                    sideways = SidewaysIntent((metrics.cell * 0.45f).coerceAtLeast(1f))
                    anchorColumn = pieceCenter(board.snap)
                    anchorType = board.snap.activeType
                    input.drag = null
                    input.resetGestureDrop()
                    change.consume()
                    return@forEach
                }
                if (change.id != pointer) return@forEach
                if (!change.pressed) {
                    release(rotateIfTap = true)
                    change.consume()
                    return@forEach
                }
                if (flung) {
                    change.consume()
                    return@forEach
                }
                val dx = change.position.x - start.x
                val dy = change.position.y - start.y
                val instantDt = (change.uptimeMillis - lastMillis).coerceAtLeast(1L)
                val instantDown = change.position.y - lastY
                val instantDx = change.position.x - lastX
                val instantDy = change.position.y - lastY
                lastX = change.position.x
                lastY = change.position.y
                lastMillis = change.uptimeMillis
                if (!steered && abs(dx) < slop && abs(dy) < slop) {
                    change.consume()
                    return@forEach
                }
                if (!steered && -dy > swipe && -dy > abs(dx)) {
                    steered = true
                    aiming = true
                    sideways.reset()
                    start = change.position
                    if (board.rules.hold) input.hold = true
                    change.consume()
                    return@forEach
                }
                steered = true
                val snap = board.snap
                if (snap.activeType != anchorType) {
                    anchorType = snap.activeType
                    anchorColumn = pieceCenter(snap)
                    start = change.position
                    sideways.reset()
                    aiming = false
                    input.resetGestureDrop()
                }
                val travelX = change.position.x - start.x
                val travelY = change.position.y - start.y
                if (!aiming) {
                    sideways.sample(travelX, travelY)
                    aiming = true
                } else {
                    sideways.sample(instantDx, instantDy)
                }
                val aimed = committedSideTravel(sideways.travel, travelX, travelY, metrics.cell)
                input.drag = relativeDragColumn(anchorColumn, aimed, metrics.cell)
                val flick = board.rules.hardDrop && shouldHardFlick(
                    totalDownPx = (change.position.y - start.y).coerceAtLeast(0f),
                    instantDownPx = instantDown,
                    instantDtMs = instantDt,
                    cellPx = metrics.cell,
                )
                if (flick) {
                    flung = true
                    input.hard = true
                    input.resetGestureDrop()
                } else {
                    input.followDrop(softDropCells((change.position.y - start.y).coerceAtLeast(0f), metrics.cell))
                }
                change.consume()
            }
        }
    }
}

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
}
