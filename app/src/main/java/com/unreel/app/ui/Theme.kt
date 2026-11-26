package com.unreel.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002F65),
    secondary = Color(0xFF81C995),
    background = Color(0xFF101216),
    surface = Color(0xFF191C22),
    surfaceVariant = Color(0xFF242933),
    error = Color(0xFFF28B82)
)

@Composable
fun UnreelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
