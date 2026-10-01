package com.examplecomposeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    secondary = Teal200,
    onSecondary = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Color.Black,
    secondary = Teal700,
    onSecondary = Color.White,
)

@Composable
fun AdgeistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
