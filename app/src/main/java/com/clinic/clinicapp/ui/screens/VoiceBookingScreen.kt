package com.clinic.clinicapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clinic.clinicapp.ui.components.MicButton
import com.clinic.clinicapp.viewmodel.VoiceUiState

@Composable
fun VoiceBookingScreen(
    state: VoiceUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onConfirm: () -> Unit,
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
            text = "Скажите, например:\n«Запишите меня к Ивановой завтра в три часа дня»",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Кнопка блокируется во время инициализации и распознавания
        MicButton(
            isRecording = state is VoiceUiState.Recording,
            enabled = state !is VoiceUiState.Transcribing && state !is VoiceUiState.Initializing,
            onPress = onStartRecording,
            onRelease = onStopRecording
        )

        when (state) {
            is VoiceUiState.Initializing -> {
                CircularProgressIndicator()
                Text("Загрузка модели Whisper Tiny (может занять до 30 сек)…")
            }

            is VoiceUiState.Idle -> {
                Text("Нажмите и удерживайте для записи")
            }

            is VoiceUiState.Recording -> {
                Text("Идёт запись…", color = MaterialTheme.colorScheme.error)
            }

            is VoiceUiState.Transcribing -> {
                CircularProgressIndicator()
                Text("Распознавание…")
            }

            is VoiceUiState.Parsed -> {
                val cmd = state.command
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Распознано: «${cmd.rawText}»", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Врач: ${cmd.doctor?.name ?: "не найден"}")
                        Text("Дата: ${cmd.date ?: "не найдена"}")
                        Text("Время: ${cmd.time ?: "не найдено"}")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConfirm, enabled = cmd.isComplete) { Text("Подтвердить") }
                            OutlinedButton(onClick = onReset) { Text("Отмена") }
                        }
                    }
                }
            }

            is VoiceUiState.Booked -> {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Запись создана!", style = MaterialTheme.typography.titleLarge)
                        Text("${state.doctorName}, ${state.date} в ${state.time}")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReset) { Text("Ок") }
                    }
                }
            }

            is VoiceUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onReset) { Text("Повторить") }
            }

            VoiceUiState.Ready -> TODO()
        }
    }
}