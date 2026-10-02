package com.clinic.clinicapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Цветовые схемы приложения.
 * Пока используем стандартные цвета Material 3 без кастомизации.
 * Позже можно заменить на брендовые цвета клиники.
 */
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Обёртка темы приложения.
 * Все экраны Compose должны вызываться внутри ClinicTheme { ... }
 * — это обеспечивает единый стиль Material 3 на всех экранах.
 */
@Composable
fun ClinicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}