package com.clinic.clinicapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinic.clinicapp.ui.components.MicButton
import com.clinic.clinicapp.viewmodel.VoiceUiState

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp

/**
 * Экран голосовой записи.
 *
 * Сценарий:
 *  - пользователь удерживает микрофон → идёт запись команды;
 *  - отпускает → распознавание и парсинг;
 *  - система озвучивает вопрос через TTS → переходит в ListeningConfirmation;
 *  - автоматически записывает ответ «да/нет» → выполняет или отменяет действие.
 */
@Composable
fun VoiceBookingScreen(
    state: VoiceUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Голосовая запись на приём",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        // Кнопка активна только в состоянии Idle
        Button(
            onClick = {
                if (state is VoiceUiState.Recording) {
                    onStopRecording()
                } else if (state is VoiceUiState.Idle) {
                    onStartRecording()
                }
            },
            enabled = state is VoiceUiState.Idle || state is VoiceUiState.Recording,
            modifier = Modifier
                .size(120.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = if (state is VoiceUiState.Recording) "Остановить" else "Начать запись",
                tint = if (state is VoiceUiState.Recording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp)
            )
        }

        when (state) {
            is VoiceUiState.Initializing -> {
                CircularProgressIndicator()
                Text("Инициализация…")
            }

            is VoiceUiState.DownloadingModel -> {
                CircularProgressIndicator()
                if (state.total > 0) {
                    val percent = (state.downloaded * 100 / state.total).toInt()
                    Text("Загрузка модели: $percent%")
                } else {
                    Text("Загрузка модели…")
                }
            }

            is VoiceUiState.Idle -> {
                Text("Нажмите, чтобы начать запись. Нажмите ещё раз, чтобы остановить.")
            }

            is VoiceUiState.Recording -> {
                Text(
                    text = "Идёт запись… Нажмите ещё раз, чтобы остановить",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            is VoiceUiState.Transcribing -> {
                CircularProgressIndicator()
                Text("Распознавание…")
            }

            is VoiceUiState.AwaitingConfirmation -> {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.question,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Слушаю ответ…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is VoiceUiState.ListeningConfirmation -> {
                CircularProgressIndicator()
                Text(
                    text = "Скажите «да» или «нет»",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is VoiceUiState.Booked -> {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Запись создана!",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${state.doctorName}, ${state.date} в ${state.time}")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReset) { Text("Ок") }
                    }
                }
            }

            is VoiceUiState.Cancelled -> {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Записи отменены",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Отменено записей: ${state.count}")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReset) { Text("Ок") }
                    }
                }
            }

            is VoiceUiState.Error -> {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReset) { Text("Повторить") }
                    }
                }
            }
        }
    }
}