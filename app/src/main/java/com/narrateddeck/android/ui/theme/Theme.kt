package com.narrateddeck.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4D89),
    onPrimary = Color.White,
    secondary = Color(0xFF4A90A4),
    tertiary = Color(0xFFC45C26),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BB8F0),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFF8EC8D8),
    tertiary = Color(0xFFFFB68A),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1F26),
)

@Composable
fun NarratedDeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
