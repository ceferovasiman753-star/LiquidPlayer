package com.liquid.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0B0F)

val TitleStyle = TextStyle(
    fontSize = 56.sp,
    fontWeight = FontWeight.Thin,
    color = Color.White,
    letterSpacing = (-1).sp,
)

@Composable
fun LiquidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Ink,
            background = Ink,
            surface = Ink,
            onSurface = Color.White,
        ),
        content = content,
    )
}
