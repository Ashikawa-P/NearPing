package de.gabriel.nearping.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NearPingColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F1DF),
    onPrimaryContainer = Color(0xFF062219),
    secondary = Color(0xFF4E635A),
    background = Color(0xFFF5F7F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E9E5),
    error = Color(0xFFBA1A1A),
)

@Composable
fun NearPingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NearPingColors,
        content = content,
    )
}
