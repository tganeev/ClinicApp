// viewmodel/VoiceBookingViewModel.kt
package com.clinic.clinicapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.voice.AudioRecorder
import com.clinic.clinicapp.data.voice.SherpaSttEngine
import com.clinic.clinicapp.domain.CommandType
import com.clinic.clinicapp.domain.ParsedCommand
import com.clinic.clinicapp.domain.VoiceCommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VoiceUiState {
    data object Initializing : VoiceUiState
    data class DownloadingModel(val downloaded: Long, val total: Long) : VoiceUiState
    data object Idle : VoiceUiState
    data object Recording : VoiceUiState
    data object Transcribing : VoiceUiState
    data class Parsed(val command: ParsedCommand) : VoiceUiState
    data class Error(val message: String) : VoiceUiState

    /** Запись создана (обычная или на ближайшее время). */
    data class Booked(
        val doctorName: String,
        val date: String,
        val time: String
    ) : VoiceUiState

    /** Все записи отменены. */
    data class Cancelled(val count: Int) : VoiceUiState
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
                _state.value = VoiceUiState.DownloadingModel(downloaded, total)
            }
            _state.value = if (ok) {
                Log.d(TAG, "STT готов")
                VoiceUiState.Idle
            } else {
                VoiceUiState.Error("Не удалось загрузить модель распознавания")
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                _state.value = VoiceUiState.Recording
                recorder.start()
            } catch (t: Throwable) {
                Log.e(TAG, "error in startRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка старта записи")
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                _state.value = VoiceUiState.Transcribing
                val samples = recorder.stop()
                if (samples.isEmpty()) {
                    _state.value = VoiceUiState.Error("Не удалось записать аудио")
                    return@launch
                }

                val text = stt.transcribe(samples)
                Log.d(TAG, "transcribed: $text")
                if (text.isBlank()) {
                    _state.value = VoiceUiState.Error("Речь не распознана")
                    return@launch
                }

                val parsed = parser.parse(text)
                Log.d(TAG, "parsed: type=${parsed.type}, doctor=${parsed.doctor?.name}, " +
                        "date=${parsed.date}, time=${parsed.time}")
                _state.value = VoiceUiState.Parsed(parsed)
            } catch (t: Throwable) {
                Log.e(TAG, "error in stopRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка распознавания")
            }
        }
    }

    /**
     * Подтверждение распознанной команды.
     * В зависимости от типа — либо создаём запись, либо отменяем всё.
     */
    fun confirm() {
        val current = _state.value as? VoiceUiState.Parsed ?: return
        val cmd = current.command

        when (cmd.type) {
            CommandType.CANCEL_ALL -> {
                val count = repository.cancelAllAppointments()
                Log.d(TAG, "cancelled $count appointments")
                _state.value = VoiceUiState.Cancelled(count)
            }

            CommandType.BOOK_NEAREST -> {
                val nearest = repository.findNearestFreeSlot()
                if (nearest == null) {
                    _state.value = VoiceUiState.Error("Нет свободных слотов")
                    return
                }
                val (doctor, slot) = nearest
                val ok = repository.book(doctor.id, slot.id)
                _state.value = if (ok) {
                    VoiceUiState.Booked(doctor.name, slot.date, slot.time)
                } else {
                    VoiceUiState.Error("Не удалось записаться")
                }
            }

            CommandType.BOOK_SPECIFIC -> {
                val doctor = cmd.doctor
                val date = cmd.date
                val time = cmd.time
                if (doctor == null || date == null || time == null) {
                    _state.value = VoiceUiState.Error("Не хватает данных для записи")
                    return
                }
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

            CommandType.UNKNOWN -> {
                _state.value = VoiceUiState.Error(
                    "Голосовая запись на прием"
                )
            }
        }
    }

    fun reset() {
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        stt.release()
        super.onCleared()
    }
}