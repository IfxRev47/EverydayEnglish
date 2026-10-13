package com.oxford.a1.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF24675D), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFE5), onPrimaryContainer = Color(0xFF183E36),
    secondary = Color(0xFF596A61), background = Color(0xFFFAFBF7),
    surface = Color(0xFFFAFBF7), surfaceContainerLow = Color(0xFFF0F3ED),
    onSurface = Color(0xFF202B26), onSurfaceVariant = Color(0xFF526058)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD5C5), primaryContainer = Color(0xFF244C41),
    onPrimaryContainer = Color(0xFFD7F3E8), background = Color(0xFF111814),
    surface = Color(0xFF111814), surfaceContainerLow = Color(0xFF1B251F)
)

@Composable
fun A1Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
