package com.clinic.clinicapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.repository.AppointmentRepository

import com.clinic.clinicapp.data.voice.AudioRecorder

import com.clinic.clinicapp.data.voice.SherpaSttEngine
import com.clinic.clinicapp.domain.ParsedCommand
import com.clinic.clinicapp.domain.VoiceCommandParser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VoiceUiState {
    data object Initializing : VoiceUiState
    data class DownloadingModel(val downloaded: Long, val total: Long) : VoiceUiState  // ← новое
    data object Idle : VoiceUiState
    data object Recording : VoiceUiState
    data object Transcribing : VoiceUiState
    data class Parsed(val command: ParsedCommand) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
    data class Booked(val doctorName: String, val date: String, val time: String) : VoiceUiState
}

class VoiceBookingViewModel(
    app: Application,
    private val repository: AppointmentRepository
) : AndroidViewModel(app) {

    private val TAG = "VoiceBooking"

    private val recorder = AudioRecorder()
    private val stt = SherpaSttEngine(app)
    private val parser = VoiceCommandParser(repository)

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Initializing)
    val state: StateFlow<VoiceUiState> = _state

    init {
        viewModelScope.launch {
            Log.d(TAG, "Инициализация STT...")
            val ok = stt.initialize { downloaded, total ->
                // callback вызывается из фонового потока — обновляем StateFlow
                _state.value = VoiceUiState.DownloadingModel(downloaded, total)
            }
            _state.value = if (ok) {
                Log.d(TAG, "STT готов")
                VoiceUiState.Idle
            } else {
                Log.e(TAG, "STT не удалось инициализировать")
                VoiceUiState.Error("Не удалось загрузить модель распознавания")
            }
        }
    }

    fun startRecording() {
        Log.d(TAG, "startRecording called")
        viewModelScope.launch {
            try {
                _state.value = VoiceUiState.Recording
                recorder.start()
                Log.d(TAG, "recorder.start() completed")
            } catch (t: Throwable) {
                Log.e(TAG, "error in startRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка старта записи")
            }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording called")
        viewModelScope.launch {
            try {
                _state.value = VoiceUiState.Transcribing
                val samples = recorder.stop()
                Log.d(TAG, "samples captured: ${samples.size}")

                if (samples.isEmpty()) {
                    _state.value = VoiceUiState.Error("Не удалось записать аудио")
                    return@launch
                }

                val text = stt.transcribe(samples)
                Log.d(TAG, "transcribed: $text")

                if (text.isBlank()) {
                    _state.value = VoiceUiState.Error("Речь не распознана. Говорите по-английски.")
                    return@launch
                }

                val parsed = parser.parse(text)
                _state.value = VoiceUiState.Parsed(parsed)
            } catch (t: Throwable) {
                Log.e(TAG, "error in stopRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка распознавания")
            }
        }
    }

    fun confirmBooking() {
        val current = _state.value
        if (current !is VoiceUiState.Parsed) return
        val cmd = current.command
        val doctor = cmd.doctor ?: return
        val date = cmd.date ?: return
        val time = cmd.time ?: return

        val slot = doctor.availableSlots.find {
            it.date == date && it.time == time && it.isAvailable
        }
        if (slot == null) {
            _state.value = VoiceUiState.Error("Слот $date $time недоступен")
            return
        }

        val ok = repository.book(doctor.id, slot.id)
        _state.value = if (ok) {
            VoiceUiState.Booked(doctor.name, date, time)
        } else {
            VoiceUiState.Error("Не удалось создать запись")
        }
    }

    fun reset() {
        Log.d(TAG, "reset")
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        stt.release()
        super.onCleared()
    }
}