package com.clinic.clinicapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.nlu.NluEngine
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.voice.AudioRecorder
import com.clinic.clinicapp.data.voice.SherpaSttEngine
import com.clinic.clinicapp.domain.CommandType
import com.clinic.clinicapp.domain.ParsedCommand
import com.clinic.clinicapp.domain.VoiceCommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Состояния экрана голосовой записи.
 */
sealed interface VoiceUiState {
    /** Идёт инициализация (загрузка моделей STT/NLU). */
    data object Initializing : VoiceUiState

    /** Скачивание модели с показом прогресса. */
    data class DownloadingModel(val downloaded: Long, val total: Long) : VoiceUiState

    /** Готово к записи. */
    data object Idle : VoiceUiState

    /** Идёт запись голоса. */
    data object Recording : VoiceUiState

    /** Идёт распознавание и парсинг. */
    data object Transcribing : VoiceUiState

    /** Команда распознана, ждём подтверждения. */
    data class Parsed(val command: ParsedCommand) : VoiceUiState

    /** Запись создана (обычная или на ближайшее время). */
    data class Booked(
        val doctorName: String,
        val date: String,
        val time: String
    ) : VoiceUiState

    /** Все записи отменены. */
    data class Cancelled(val count: Int) : VoiceUiState

    /** Ошибка. */
    data class Error(val message: String) : VoiceUiState
}

/**
 * ViewModel голосового экрана.
 * Управляет:
 *  - записью аудио через AudioRecorder;
 *  - распознаванием речи через SherpaSttEngine (GigaAM);
 *  - парсингом текста через NluEngine (clinic_lm);
 *  - fallback на regex-парсер VoiceCommandParser;
 *  - созданием/отменой записей в AppointmentRepository.
 */
class VoiceBookingViewModel(
    app: Application,
    private val repository: AppointmentRepository
) : AndroidViewModel(app) {

    private val TAG = "VoiceBooking"

    private val recorder = AudioRecorder()
    private val stt = SherpaSttEngine(app)
    private val nlu = NluEngine(app)
    private val regexParser = VoiceCommandParser(repository)

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Initializing)
    val state: StateFlow<VoiceUiState> = _state

    init {
        viewModelScope.launch {
            Log.d(TAG, "Инициализация STT...")
            val sttOk = stt.initialize { downloaded, total ->
                _state.value = VoiceUiState.DownloadingModel(downloaded, total)
            }

            Log.d(TAG, "Инициализация NLU...")
            val nluOk = nlu.initialize { downloaded, total ->
                _state.value = VoiceUiState.DownloadingModel(downloaded, total)
            }

            _state.value = if (sttOk && nluOk) {
                Log.d(TAG, "Все модели загружены")
                VoiceUiState.Idle
            } else {
                Log.e(TAG, "Не удалось загрузить модели: stt=$sttOk, nlu=$nluOk")
                VoiceUiState.Error("Не удалось загрузить модели распознавания")
            }
        }
    }

    /** Начать запись — вызывается при нажатии на микрофон. */
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

    /** Остановить запись, распознать текст, распарсить — вызывается при отпускании микрофона. */
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
                Log.d(TAG, "Распознанный текст: $text")

                if (text.isBlank()) {
                    _state.value = VoiceUiState.Error("Речь не распознана")
                    return@launch
                }

                val parsed = parseText(text)
                Log.d(TAG, "Parsed: type=${parsed.type}, doctor=${parsed.doctor?.name}, " +
                        "date=${parsed.date}, time=${parsed.time}")
                _state.value = VoiceUiState.Parsed(parsed)
            } catch (t: Throwable) {
                Log.e(TAG, "error in stopRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка распознавания")
            }
        }
    }

    /**
     * Парсит текст: сначала пробует NLU-модель, при неудаче — regex-fallback.
     */
    private suspend fun parseText(text: String): ParsedCommand {
        // Основной путь — NLU-модель
        val nluResult = try {
            nlu.parse(text)
        } catch (t: Throwable) {
            Log.e(TAG, "NLU упал, используем regex-fallback", t)
            null
        }

        if (nluResult != null && nluResult.intent != CommandType.UNKNOWN) {
            // Ищем врача по специальности
            val doctor = nluResult.specialty?.let { spec ->
                repository.doctors.value.find {
                    it.specialty.equals(spec, ignoreCase = true)
                }
            }
            return ParsedCommand(
                type = nluResult.intent,
                doctor = doctor,
                date = nluResult.date,
                time = nluResult.time,
                rawText = text
            )
        }

        // Fallback: regex-парсер
        Log.d(TAG, "NLU не справился, используем regex")
        return regexParser.parse(text)
    }

    /**
     * Подтверждение распознанной команды.
     * В зависимости от типа — либо создаём запись, либо отменяем всё.
     */
    fun confirm() {
        val current = _state.value as? VoiceUiState.Parsed ?: return
        val cmd = current.command

        when (cmd.type) {
            CommandType.CANCEL_ALL, CommandType.CANCEL_ONE -> {
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

            CommandType.RESCHEDULE -> {
                // Пока не реализовано — сообщаем пользователю
                _state.value = VoiceUiState.Error("Перенос записи пока не поддерживается")
            }

            CommandType.UNKNOWN -> {
                _state.value = VoiceUiState.Error(
                    "Команда не распознана. Скажите: «Запишите меня к терапевту завтра в 15:00»"
                )
            }
        }
    }

    /** Сбросить состояние — например, после ошибки или завершения. */
    fun reset() {
        Log.d(TAG, "reset")
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        stt.release()
        nlu.release()
        super.onCleared()
    }
}