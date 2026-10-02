package com.clinic.clinicapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Круглая кнопка микрофона.
 *
 * Реагирует на удержание (press) и отпускание (release).
 * В отличие от прошлой версии, здесь нет внешнего Box с pointerInput —
 * это приводило к тому, что IconButton перехватывал события, и они
 * не доходили до родителя.
 *
 * Теперь события ловятся через interactionSource внутри IconButton:
 *  - Press     -> onPress()
 *  - Release   -> onRelease()
 *  - Cancel    -> onRelease()  (если палец ушёл за пределы кнопки)
 */
@Composable
fun MicButton(
    isRecording: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    // Анимация «прижатия» кнопки во время записи
    val scale by animateFloatAsState(if (isRecording) 1.2f else 1f)

    // interactionSource — источник событий касания кнопки
    val interactionSource = remember { MutableInteractionSource() }

    // Слушаем события нажатия/отпускания и вызываем колбэки
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> onPress()
                is PressInteraction.Release -> onRelease()
                is PressInteraction.Cancel -> onRelease()
            }
        }
    }

    IconButton(
        onClick = { /* намеренно пусто: срабатывает только при клике без удержания */ },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.size(96.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Записать голосом",
            tint = if (isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
        )
    }
}