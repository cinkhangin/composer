package com.example.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandColors = lightColorScheme(
    primary = Color(0xFF6366F1),
)

@Composable
fun AppTheme(colorScheme: ColorScheme = BrandColors, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
