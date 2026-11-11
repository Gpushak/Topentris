package com.topentris.game

import kotlin.math.pow

/**
 * Pure rules. Time is supplied by the caller, so a 60 Hz frame and a unit test
 * drive the same path: input, then gravity and lock, then line clear and spawn.
 */
class GameEngine(
    val config: GameConfig,
    private val nextInt: (Int) -> Int = { bound -> kotlin.random.Random.nextInt(bound) },
) {
    private val buffer = 8
    private val grid = Array(ROWS + buffer) { arrayOfNulls<Piece>(COLS) }
    private val queue = ArrayDeque<Piece>()
    private val randomizer: Randomizer = when (config.generator) {
        Generator.BAG -> BagRandomizer(nextInt)
        Generator.REROLL -> RerollRandomizer(nextInt)
        Generator.UNIFORM -> UniformRandomizer(nextInt)
    }

    private var active: Active? = null
    private var holdPiece: Piece? = null
    private var holdUsed = false
    private var score = 0
    private var lines = 0
    private var level = config.startLevel
    private var combo = -1
    private var state = PlayState.PLAYING
    private var lastRotation = false
    private var locking = false
    private var lockMs = 0.0
    private var lockResets = 0
    private var dropMs = 0.0
    private var softMs = 0.0
    private var landedThisFrame = false
    private var lockedThisAction = false
    private val feedback = linkedSetOf<Feedback>()
    private var clearedLines = 0
    private var popupPoints = 0
    private var popupRow = ROWS / 2
    private var hardDropPoints = 0

    init {
        repeat(config.queueSize) { queue.addLast(randomizer.next()) }
        spawn(keepHoldUsed = false)
    }

    fun frame(dtMillis: Double, input: FrameInput = FrameInput(), softDropMs: Double = 50.0): GameSnapshot {
        feedback.clear()
        clearedLines = 0
        popupPoints = 0
        hardDropPoints = 0
        landedThisFrame = false
        if (state != PlayState.PLAYING) return snapshot()

        if (input.hold) tryHold()
        if (state != PlayState.PLAYING) return snapshot()

        when {
            input.rotateCw -> tryRotate(1)
            input.rotateCcw -> tryRotate(-1)
        }
        if (state != PlayState.PLAYING) return snapshot()

        if (input.targetColumn != null) {
            alignToColumn(input.targetColumn)
        } else if (input.shift != 0 && input.shiftSteps > 0) {
            val dir = if (input.shift > 0) 1 else -1
            var remaining = input.shiftSteps
            while (remaining > 0 && state == PlayState.PLAYING && tryShift(dir)) remaining--
        }
        if (state != PlayState.PLAYING) return snapshot()

        if (input.hardDrop && config.hardDrop) {
            hardDrop()
            return snapshot()
        }

        val followedFinger = nudgeWithFinger(input.softSteps)
        if (state == PlayState.PLAYING && !followedFinger) {
            applyDrop(dtMillis, input.softDrop, softDropMs)
        }
        applyLock(dtMillis)
        return snapshot()
    }

    /** One cell per call, so a finger soft drop catches up smoothly instead of jumping. */
    private fun nudgeWithFinger(requested: Int): Boolean {
        if (requested <= 0 || state != PlayState.PLAYING || !canMoveDown()) return false
        stepDown(scoreSoft = true, lockCredit = 0.0)
        return true
    }

    val playState: PlayState get() = state

    fun pause() {
        if (state == PlayState.PLAYING) state = PlayState.PAUSED
    }

    fun resume() {
        if (state == PlayState.PAUSED) state = PlayState.PLAYING
    }

    fun snapshot(): GameSnapshot {
        val locked = List(ROWS * COLS) { index ->
            val x = index % COLS
            val y = index / COLS
            grid[y + buffer][x]
        }
        val piece = active
        val activeCells = piece?.let { cellsOf(it) }.orEmpty()
        val ghost = if (config.ghost && piece != null && state != PlayState.GAME_OVER) {
            val dropped = piece.copy(y = ghostY(piece))
            val ghosts = cellsOf(dropped)
            if (ghosts.toSet() == activeCells.toSet()) emptyList() else ghosts
        } else {
            emptyList()
        }
        return GameSnapshot(
            cells = locked,
            active = activeCells,
            activeType = piece?.type,
            ghost = ghost,
            hold = holdPiece,
            holdUsed = holdUsed,
            queue = queue.toList(),
            score = score,
            level = level,
            lines = lines,
            state = state,
            feedback = feedback.toSet(),
            clearedLines = clearedLines,
            popupPoints = popupPoints,
            popupRow = popupRow,
        )
    }

    internal fun debugPiece(type: Piece, rotation: Int, x: Int, y: Int) {
        active = Active(type, rotation, x, y)
        lastRotation = false
        locking = false
        lockMs = 0.0
        lockResets = 0
        dropMs = 0.0
        softMs = 0.0
        state = PlayState.PLAYING
    }

    internal fun debugFill(x: Int, y: Int, type: Piece = Piece.J) {
        grid[y + buffer][x] = type
    }

    private fun tryHold() {
        if (!config.hold || holdUsed || state != PlayState.PLAYING) return
        val current = active ?: return
        val swapped = holdPiece
        holdPiece = current.type
        holdUsed = true
        lastRotation = false
        if (swapped == null) {
            spawn(keepHoldUsed = true)
        } else {
            val next = Active(swapped, 0, Rotation.spawnX(swapped), Rotation.spawnY(swapped))
            active = next
            locking = false
            lockMs = 0.0
            lockResets = 0
            dropMs = 0.0
            softMs = 0.0
            if (!fits(next)) state = PlayState.GAME_OVER
        }
    }

    private fun tryRotate(direction: Int): Boolean {
        val piece = active ?: return false
        if (state != PlayState.PLAYING) return false
        val to = nextRotation(piece, direction)
        val kicks = if (config.rotation == RotationSystem.SRS && !twoState(piece.type)) {
            Rotation.kicks(piece.type, piece.rotation, to)
        } else {
            listOf(Cell(0, 0))
        }
        for (kick in kicks) {
            val moved = piece.copy(rotation = to, x = piece.x + kick.x, y = piece.y + kick.y)
            if (!fits(moved)) continue
            val wasLocking = locking
            lockedThisAction = false
            active = moved
            lastRotation = true
            feedback.add(Feedback.ROTATE)
            noteGroundAction(wasLocking)
            return state == PlayState.PLAYING && !lockedThisAction
        }
        return false
    }

    private fun tryShift(direction: Int): Boolean {
        val piece = active ?: return false
        val moved = piece.copy(x = piece.x + direction)
        if (!fits(moved)) return false
        val wasLocking = locking
        lockedThisAction = false
        active = moved
        lastRotation = false
        noteGroundAction(wasLocking)
        return state == PlayState.PLAYING && !lockedThisAction
    }

    private fun alignToColumn(target: Int) {
        repeat(COLS) {
            if (state != PlayState.PLAYING) return
            val cells = cellsOf(active ?: return)
            val center = (cells.minOf { it.x } + cells.maxOf { it.x }) / 2
            if (center == target) return
            val direction = if (target > center) 1 else -1
            if (!tryShift(direction)) return
        }
    }

    private fun hardDrop() {
        val piece = active ?: return
        var distance = 0
        var cursor = piece
        while (true) {
            val below = cursor.copy(y = cursor.y + 1)
            if (!fits(below)) break
            cursor = below
            distance++
        }
        active = cursor
        if (config.scoreTable == ScoreTable.GUIDELINE) {
            val bonus = distance * 2
            addScore(bonus)
            hardDropPoints = bonus
        }
        feedback.add(Feedback.HARD_DROP)
        lockPiece()
    }

    private fun applyDrop(dtMillis: Double, soft: Boolean, softDropMs: Double) {
        if (state != PlayState.PLAYING || active == null) return
        val gravityMs = gravityIntervalMs()
        if (soft && softDropMs < gravityMs) {
            dropByTimer(dtMillis, softDropMs, scoreSoft = true, accumulator = Accumulator.SOFT)
            return
        }
        softMs = 0.0
        dropByTimer(dtMillis, gravityMs, scoreSoft = false, accumulator = Accumulator.GRAVITY)
    }

    private enum class Accumulator { SOFT, GRAVITY }

    private fun dropByTimer(dtMillis: Double, step: Double, scoreSoft: Boolean, accumulator: Accumulator) {
        fun read(): Double = if (accumulator == Accumulator.SOFT) softMs else dropMs
        fun write(value: Double) {
            if (accumulator == Accumulator.SOFT) softMs = value else dropMs = value
        }
        if (!canMoveDown()) {
            if (config.lockDelayMs <= 0) {
                write(read() + dtMillis)
                if (step <= 0.0 || read() >= step) {
                    write(0.0)
                    lockPiece()
                }
            } else if (!locking) {
                locking = true
                lockMs = 0.0
                lockResets = 0
            }
            return
        }
        write(read() + dtMillis)
        if (step <= 0.0) {
            stepDown(scoreSoft = scoreSoft, lockCredit = 0.0)
            write(0.0)
            return
        }
        var steps = 0
        while (read() >= step && steps < 20 && state == PlayState.PLAYING) {
            if (!canMoveDown()) {
                if (config.lockDelayMs <= 0) lockPiece()
                return
            }
            write(read() - step)
            if (!stepDown(scoreSoft = scoreSoft, lockCredit = read())) return
            steps++
        }
    }

    private fun stepDown(scoreSoft: Boolean, lockCredit: Double): Boolean {
        val piece = active ?: return false
        val moved = piece.copy(y = piece.y + 1)
        if (!fits(moved)) return false
        active = moved
        if (scoreSoft) addScore(1)
        lastRotation = false
        lockResets = 0
        if (!canMoveDown()) {
            if (config.lockDelayMs <= 0) {
                lockPiece()
                return false
            }
            locking = true
            lockMs = lockCredit.coerceAtLeast(0.0)
            landedThisFrame = true
            if (lockMs >= config.lockDelayMs) {
                lockPiece()
                return false
            }
        } else {
            locking = false
            lockMs = 0.0
        }
        return true
    }

    private fun applyLock(dtMillis: Double) {
        if (!locking || state != PlayState.PLAYING || config.lockDelayMs <= 0) return
        if (landedThisFrame) return
        lockMs += dtMillis
        if (lockMs >= config.lockDelayMs) lockPiece()
    }

    private fun noteGroundAction(wasLocking: Boolean) {
        if (state != PlayState.PLAYING) return
        if (!canMoveDown()) {
            if (config.lockDelayMs <= 0) return
            if (!wasLocking) {
                locking = true
                lockMs = 0.0
                return
            }
            if (lockResets >= config.maxLockResets) {
                lockPiece()
            } else {
                lockResets += 1
                lockMs = 0.0
                locking = true
            }
        } else {
            locking = false
            lockMs = 0.0
            lockResets = 0
            dropMs = 0.0
            softMs = 0.0
        }
    }

    private fun lockPiece() {
        val piece = active ?: return
        if (state != PlayState.PLAYING) return
        val spin = isTSpin(piece)
        for (cell in cellsOf(piece)) {
            if (cell.y in -buffer until ROWS && cell.x in 0 until COLS) {
                grid[cell.y + buffer][cell.x] = piece.type
            }
        }
        val cleared = clearFullRows()
        val scoringLevel = level
        var gained = if (spin) {
            tSpinBase(cleared) * scoringLevel
        } else if (cleared > 0) {
            lineBase(cleared, scoringLevel)
        } else {
            0
        }
        if (cleared > 0) {
            combo += 1
            if (config.scoreTable == ScoreTable.GUIDELINE && combo > 0) {
                gained += 50 * combo * scoringLevel
            }
            lines += cleared
            level = config.startLevel + lines / 10
        } else {
            combo = -1
        }
        addScore(gained)
        if (gained > 0) popupPoints = gained + hardDropPoints
        popupRow = if (cleared > 0) {
            clearedAnchor
        } else {
            val rows = cellsOf(piece).map { it.y }.filter { it in 0 until ROWS }
            if (rows.isEmpty()) ROWS / 2 else rows.sum() / rows.size
        }
        clearedLines = cleared
        feedback.add(Feedback.LOCK)
        lastRotation = false
        lockedThisAction = true
        locking = false
        lockMs = 0.0
        lockResets = 0
        active = null
        spawn(keepHoldUsed = false)
    }

    private var clearedAnchor = ROWS / 2

    private fun clearFullRows(): Int {
        val full = mutableListOf<Int>()
        for (y in -buffer until ROWS) {
            if ((0 until COLS).all { x -> grid[y + buffer][x] != null }) full.add(y)
        }
        if (full.isEmpty()) return 0
        clearedAnchor = full.sum() / full.size
        val kept = mutableListOf<Triple<Int, Int, Piece>>()
        for (y in -buffer until ROWS) {
            if (y in full) continue
            for (x in 0 until COLS) {
                val type = grid[y + buffer][x] ?: continue
                kept.add(Triple(x, y, type))
            }
        }
        for (row in grid) row.fill(null)
        for ((x, y, type) in kept) {
            val fall = full.count { it > y }
            val nextY = y + fall
            if (nextY in -buffer until ROWS) grid[nextY + buffer][x] = type
        }
        return full.size
    }

    private fun spawn(keepHoldUsed: Boolean) {
        while (queue.size < config.queueSize) queue.addLast(randomizer.next())
        val type = if (queue.isEmpty()) randomizer.next() else queue.removeFirst()
        while (queue.size < config.queueSize) queue.addLast(randomizer.next())
        val spawned = Active(type, 0, Rotation.spawnX(type), Rotation.spawnY(type))
        active = spawned
        if (!keepHoldUsed) holdUsed = false
        lastRotation = false
        locking = false
        lockMs = 0.0
        lockResets = 0
        dropMs = 0.0
        softMs = 0.0
        if (!fits(spawned)) state = PlayState.GAME_OVER
    }

    private fun isTSpin(piece: Active): Boolean {
        if (piece.type != Piece.T || !lastRotation || config.scoreTable != ScoreTable.GUIDELINE) return false
        val corners = listOf(
            piece.x to piece.y,
            piece.x + 2 to piece.y,
            piece.x to piece.y + 2,
            piece.x + 2 to piece.y + 2,
        )
        return corners.count { (x, y) -> solid(x, y) } >= 3
    }

    private fun tSpinBase(cleared: Int): Int = when (cleared) {
        0 -> 400
        1 -> 800
        2 -> 1200
        else -> 1600
    }

    private fun lineBase(cleared: Int, scoringLevel: Int): Int {
        return if (config.scoreTable == ScoreTable.GUIDELINE) {
            val base = when (cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
            base * scoringLevel
        } else {
            val base = when (cleared) {
                1 -> 40
                2 -> 100
                3 -> 300
                else -> 1200
            }
            base * (scoringLevel + 1)
        }
    }

    private fun gravityIntervalMs(): Double {
        return if (config.gravity == Gravity.NES) {
            nesFrames(level) * (1000.0 / 60.0)
        } else {
            val steps = (level - 1).coerceAtLeast(0)
            val base = (0.8 - 0.007 * steps).coerceAtLeast(0.001)
            val seconds = base.pow(steps)
            val ms = seconds * 1000.0
            val fastest = 1000.0 / 60.0 / 20.0
            ms.coerceAtLeast(fastest)
        }
    }

    private fun nesFrames(level: Int): Int = when {
        level <= 0 -> 48
        level == 1 -> 43
        level == 2 -> 38
        level == 3 -> 33
        level == 4 -> 28
        level == 5 -> 23
        level == 6 -> 18
        level == 7 -> 13
        level == 8 -> 8
        level == 9 -> 6
        level <= 12 -> 5
        level <= 15 -> 4
        level <= 18 -> 3
        level <= 28 -> 2
        else -> 1
    }

    private fun nextRotation(piece: Active, direction: Int): Int {
        if (piece.type == Piece.O) return 0
        if (twoState(piece.type)) return if (piece.rotation == 0) 1 else 0
        return if (direction > 0) (piece.rotation + 1) and 3 else (piece.rotation + 3) and 3
    }

    private fun twoState(type: Piece): Boolean {
        return config.twoStateSz && (type == Piece.S || type == Piece.Z)
    }

    private fun canMoveDown(): Boolean {
        val piece = active ?: return false
        return fits(piece.copy(y = piece.y + 1))
    }

    private fun ghostY(piece: Active): Int {
        var y = piece.y
        while (fits(piece.copy(y = y + 1))) y++
        return y
    }

    private fun fits(piece: Active): Boolean = cellsOf(piece).none { solid(it.x, it.y) }

    private fun solid(x: Int, y: Int): Boolean {
        if (x !in 0 until COLS || y >= ROWS || y < -buffer) return true
        return grid[y + buffer][x] != null
    }

    private fun cellsOf(piece: Active): List<Cell> {
        return Rotation.cells(piece.type, piece.rotation, piece.x, piece.y)
    }

    private fun addScore(points: Int) {
        if (points <= 0) return
        score = (score.toLong() + points).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private data class Active(val type: Piece, val rotation: Int, val x: Int, val y: Int)
}
