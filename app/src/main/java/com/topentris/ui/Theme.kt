package com.topentris.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.topentris.game.Piece

val Ink = Color(0xFF101218)
val Panel = Color(0xFF161A24)
val Field = Color(0xFF1A1F2C)
val GridLine = Color(0xFF2C3344)
val Amber = Color(0xFFE2A23A)
val Paper = Color(0xFFE7ECF5)
val Muted = Color(0xFF8E98AB)
val KeyIdle = Color(0xFF222733)
val KeyPressed = Color(0xFF343C4E)
val KeyEdge = Color(0xFF3C4558)

fun pieceColor(piece: Piece): Color = when (piece) {
    Piece.I -> Color(0xFF3FCFDE)
    Piece.O -> Color(0xFFE6C14A)
    Piece.T -> Color(0xFFB06AD8)
    Piece.S -> Color(0xFF5CBF63)
    Piece.Z -> Color(0xFFDE5A5A)
    Piece.J -> Color(0xFF5A7FDE)
    Piece.L -> Color(0xFFE2923A)
}

@Composable
fun TopentrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            onPrimary = Ink,
            background = Ink,
            onBackground = Paper,
            surface = Panel,
            onSurface = Paper,
            secondary = Paper,
        ),
        content = content,
    )
}
