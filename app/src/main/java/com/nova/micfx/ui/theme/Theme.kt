package com.nova.micfx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Midnight = Color(0xFF04060B)
val Navy = Color(0xFF0F131C)
val NavyLight = Color(0xFF1B2232)
val ElectricBlue = Color(0xFF19D3F3)   // electric cyan accent
val Violet = Color(0xFF8B5CF6)
val DeepBlue = Color(0xFF2E8BFF)
val Cyan = Color(0xFF22D3EE)
val Warn = Color(0xFFFFB020)
val Danger = Color(0xFFFF5A6E)
val Good = Color(0xFF34D399)
val TextDim = Color(0xFF9FB0D0)

private val Scheme = darkColorScheme(
    primary = ElectricBlue, onPrimary = Color.White,
    secondary = Violet, onSecondary = Color.White,
    tertiary = Cyan,
    background = Midnight, onBackground = Color(0xFFE8EEFF),
    surface = Navy, onSurface = Color(0xFFE8EEFF),
    surfaceVariant = NavyLight, onSurfaceVariant = TextDim,
    error = Danger
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
