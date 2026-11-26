package com.topentris.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.topentris.game.COLS
import com.topentris.game.ROWS
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal class PrikolLineBurst(clearedRows: List<Int>, private val born: Long) {
    private data class Spark(val start: Offset, val velocity: Offset, val color: Color, val size: Float)

    private val tetris = clearedRows.size >= 4
    private val rows = clearedRows.filter { it in 0 until ROWS }
    private val duration = if (tetris) 980_000_000L else 660_000_000L
    private val colors = listOf(Color(0xFFFF4D98), Color(0xFF32FFE3), Color(0xFFFFD659), Color.White)
    private val sparks = rows.flatMap { row ->
        List(if (tetris) 22 else 14) { index ->
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            val speed = Random.nextFloat() * 2.7f + 0.8f
            Spark(
                start = Offset(Random.nextFloat() * COLS, row + 0.5f),
                velocity = Offset(cos(angle) * speed, sin(angle) * speed - 1.1f),
                color = colors[index % colors.size],
                size = 0.05f + Random.nextFloat() * 0.07f,
            )
        }
    }

    fun isFinished(now: Long) = now - born >= duration

    fun draw(scope: DrawScope, now: Long, origin: Offset, cell: Float) = with(scope) {
        val progress = ((now - born).toFloat() / duration).coerceIn(0f, 1f)
        val remaining = 1f - progress
        val outward = 1f - remaining * remaining
        for ((index, row) in rows.withIndex()) {
            val center = origin + Offset(cell * COLS / 2f, cell * (row + 0.5f))
            val color = colors[index % colors.size]
            val band = cell * 0.75f * remaining
            drawRect(color.copy(alpha = 0.65f * remaining * remaining), Offset(origin.x, center.y - band / 2), Size(cell * COLS, band))
            val waveWidth = cell * (0.6f + (COLS - 0.6f) * outward)
            drawOval(
                color = color.copy(alpha = 0.83f * remaining),
                topLeft = center - Offset(waveWidth / 2f, cell * (0.15f + outward * 1.55f)),
                size = Size(waveWidth, cell * (0.3f + outward * 3.1f)),
                style = Stroke(cell * (0.025f + 0.06f * remaining)),
            )
            drawLine(Color.White.copy(alpha = remaining * remaining * 0.80f), center - Offset(waveWidth / 2f, 0f), center + Offset(waveWidth / 2f, 0f), cell * 0.06f)
        }
        for (spark in sparks) {
            val head = spark.start + spark.velocity * outward + Offset(0f, 2.6f * progress * progress)
            val tail = head - spark.velocity * (0.16f * remaining)
            drawLine(spark.color.copy(alpha = remaining), origin + tail * cell, origin + head * cell, cell * spark.size)
        }
    }
}
