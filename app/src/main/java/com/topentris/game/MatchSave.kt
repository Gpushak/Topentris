package com.topentris.game

data class ActiveState(val type: Piece, val rotation: Int, val x: Int, val y: Int)

/** A match that can be written to disk and opened again. */
data class MatchSave(
    val rules: GameConfig,
    val controls: ControlConfig,
    val score: Int,
    val lines: Int,
    val level: Int,
    val combo: Int,
    val paused: Boolean,
    val lastRotation: Boolean,
    val locking: Boolean,
    val lockMs: Double,
    val lockResets: Int,
    val dropMs: Double,
    val softMs: Double,
    val hold: Piece?,
    val holdUsed: Boolean,
    val active: ActiveState?,
    val queue: List<Piece>,
    val bag: List<Piece>,
    val rerollPrevious: Piece?,
    val grid: List<String>,
    val prikolMode: Boolean = false,
) {
    fun encode(): String = buildString {
        appendLine("1")
        appendLine(encodeRules(rules))
        appendLine(encodeControls(controls))
        appendLine(
            listOf(
                score, lines, level, combo,
                bit(paused), bit(lastRotation), bit(locking),
                lockMs, lockResets, dropMs, softMs, bit(prikolMode),
            ).joinToString(","),
        )
        appendLine(hold?.name ?: "-")
        appendLine(if (holdUsed) "1" else "0")
        val piece = active
        appendLine(if (piece == null) "-" else "${piece.type.name},${piece.rotation},${piece.x},${piece.y}")
        appendLine(queue.joinToString("") { it.name })
        appendLine(if (bag.isEmpty()) "-" else bag.joinToString("") { it.name })
        appendLine(rerollPrevious?.name ?: "-")
        grid.forEach { appendLine(it) }
    }

    companion object {
        fun decode(raw: String): MatchSave? = runCatching { parse(raw) }.getOrNull()

        private fun parse(raw: String): MatchSave {
            val lines = raw.trim().lines()
            require(lines.first() == "1")
            var index = 1
            val rules = decodeRules(lines[index++])
            val controls = decodeControls(lines[index++])
            val stats = lines[index++].split(',')
            val hold = pieceOrNull(lines[index++])
            val holdUsed = lines[index++] == "1"
            val active = activeOrNull(lines[index++])
            val queue = pieces(lines[index++])
            val bagLine = lines[index++]
            val bag = if (bagLine == "-") emptyList() else pieces(bagLine)
            val reroll = pieceOrNull(lines[index++])
            val grid = lines.subList(index, lines.size).filter { it.isNotEmpty() }
            require(grid.size == ROWS + 8)
            require(grid.all { it.length == COLS })
            return MatchSave(
                rules = rules,
                controls = controls,
                score = stats[0].toInt(),
                lines = stats[1].toInt(),
                level = stats[2].toInt(),
                combo = stats[3].toInt(),
                paused = stats[4] == "1",
                lastRotation = stats[5] == "1",
                locking = stats[6] == "1",
                lockMs = stats[7].toDouble(),
                lockResets = stats[8].toInt(),
                dropMs = stats[9].toDouble(),
                softMs = stats[10].toDouble(),
                hold = hold,
                holdUsed = holdUsed,
                active = active,
                queue = queue,
                bag = bag,
                rerollPrevious = reroll,
                grid = grid,
                prikolMode = stats.getOrNull(11) == "1",
            )
        }
    }
}

private fun bit(value: Boolean) = if (value) "1" else "0"

private fun pieceOrNull(raw: String): Piece? {
    if (raw == "-") return null
    return Piece.entries.first { it.name == raw }
}

private fun pieces(raw: String): List<Piece> = raw.map { char -> Piece.entries.first { it.name == char.toString() } }

private fun activeOrNull(raw: String): ActiveState? {
    if (raw == "-") return null
    val parts = raw.split(',')
    return ActiveState(
        type = Piece.entries.first { it.name == parts[0] },
        rotation = parts[1].toInt(),
        x = parts[2].toInt(),
        y = parts[3].toInt(),
    )
}

internal fun encodeRules(rules: GameConfig): String = listOf(
    rules.preset.name,
    rules.rotation.name,
    bit(rules.twoStateSz),
    rules.generator.name,
    bit(rules.hold),
    bit(rules.ghost),
    bit(rules.hardDrop),
    rules.lockDelayMs,
    rules.maxLockResets,
    rules.queueSize,
    rules.scoreTable.name,
).joinToString(",")

internal fun decodeRules(raw: String): GameConfig {
    val parts = raw.split(',')
    return GameConfig(
        preset = enumValueOf(parts[0]),
        rotation = enumValueOf(parts[1]),
        twoStateSz = parts[2] == "1",
        generator = enumValueOf(parts[3]),
        hold = parts[4] == "1",
        ghost = parts[5] == "1",
        hardDrop = parts[6] == "1",
        lockDelayMs = parts[7].toInt(),
        maxLockResets = parts[8].toInt(),
        queueSize = parts[9].toInt(),
        scoreTable = enumValueOf(parts[10]),
    )
}

internal fun encodeControls(controls: ControlConfig): String =
    listOf(controls.dasMs, controls.arrMs, controls.softDropMs, bit(controls.vibration)).joinToString(",")

internal fun decodeControls(raw: String): ControlConfig {
    val parts = raw.split(',')
    return ControlConfig(
        dasMs = parts[0].toInt(),
        arrMs = parts[1].toInt(),
        softDropMs = parts[2].toInt(),
        vibration = parts[3] == "1",
    )
}
