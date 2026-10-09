package com.clinic.clinicapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.nlu.NluEngine
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.voice.AudioRecorder
import com.clinic.clinicapp.data.voice.SherpaSttEngine
import com.clinic.clinicapp.data.voice.SpeechSynthesizer
import com.clinic.clinicapp.domain.CommandType
import com.clinic.clinicapp.domain.ParsedCommand
import com.clinic.clinicapp.domain.VoiceCommandParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Состояния экрана голосовой записи.
 */
sealed interface VoiceUiState {
    data object Initializing : VoiceUiState
    data class DownloadingModel(val downloaded: Long, val total: Long) : VoiceUiState
    data object Idle : VoiceUiState
    data object Recording : VoiceUiState
    data object Transcribing : VoiceUiState

    /** Команда распознана, ждём голосового подтверждения. */
    data class AwaitingConfirmation(
        val command: ParsedCommand,
        val question: String
    ) : VoiceUiState

    /** Система слушает ответ «да/нет». */
    data object ListeningConfirmation : VoiceUiState

    data class Booked(
        val doctorName: String,
        val date: String,
        val time: String
    ) : VoiceUiState

    data class Cancelled(val count: Int) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

class VoiceBookingViewModel(
    app: Application,
    private val repository: AppointmentRepository
) : AndroidViewModel(app) {

    private val TAG = "VoiceBooking"

    private val recorder = AudioRecorder()
    private val stt = SherpaSttEngine(app)
    private val tts = SpeechSynthesizer(app)
    private val nlu = NluEngine(app)
    private val regexParser = VoiceCommandParser(repository)

    /** Распознанная команда, ожидающая голосового подтверждения. */
    private var pendingCommand: ParsedCommand? = null

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

            Log.d(TAG, "Инициализация TTS...")
            val ttsOk = tts.initialize()

            // STT критичен. NLU и TTS — опциональны.
            _state.value = if (sttOk) {
                Log.d(TAG, "Все модели готовы: STT=$sttOk, NLU=$nluOk, TTS=$ttsOk")
                VoiceUiState.Idle
            } else {
                Log.e(TAG, "STT не загрузился")
                VoiceUiState.Error("Не удалось загрузить модель распознавания речи")
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
                Log.d(TAG, "Распознанный текст: $text")

                if (text.isBlank()) {
                    _state.value = VoiceUiState.Error("Речь не распознана")
                    return@launch
                }

                val parsed = parseText(text)
                Log.d(TAG, "Parsed: type=${parsed.type}, doctor=${parsed.doctor?.name}, " +
                        "date=${parsed.date}, time=${parsed.time}")

                // Формируем вопрос для голосового подтверждения
                val question = buildConfirmationQuestion(parsed)
                if (question == null) {
                    _state.value = VoiceUiState.Error(
                        "Команда не распознана. Скажите: «Запишите меня к терапевту завтра в 15:00»"
                    )
                    return@launch
                }

                pendingCommand = parsed
                _state.value = VoiceUiState.AwaitingConfirmation(parsed, question)

                // Озвучиваем вопрос и слушаем ответ
                Log.d(TAG, "Озвучивание вопроса: $question")
                tts.speak(question)

                // Небольшая пауза перед записью, чтобы TTS точно закончил
                delay(500)

                listenForConfirmation()

            } catch (t: Throwable) {
                Log.e(TAG, "error in stopRecording", t)
                _state.value = VoiceUiState.Error(t.message ?: "Ошибка распознавания")
            }
        }
    }

    /**
     * Записывает ответ пользователя («да»/«нет») и обрабатывает его.
     */
    private suspend fun listenForConfirmation() {
        _state.value = VoiceUiState.ListeningConfirmation

        try {
            Log.d(TAG, "Начинаем слушать ответ...")
            recorder.start()

            // Слушаем 3 секунды
            delay(3000)

            val answerSamples = recorder.stop()
            Log.d(TAG, "Сэмплов ответа: ${answerSamples.size}")

            if (answerSamples.isEmpty()) {
                _state.value = VoiceUiState.Error("Не услышал ответ")
                return
            }

            val answerText = stt.transcribe(answerSamples).lowercase().trim()
            Log.d(TAG, "Ответ пользователя: «$answerText»")

            when {
                isAffirmative(answerText) -> confirmVoice()
                isNegative(answerText) -> {
                    Log.d(TAG, "Пользователь отказался")
                    _state.value = VoiceUiState.Error("Действие отменено")
                }
                else -> {
                    Log.w(TAG, "Ответ не распознан: $answerText")
                    _state.value = VoiceUiState.Error("Не понял ответ. Скажите «да» или «нет»")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "error in listenForConfirmation", t)
            _state.value = VoiceUiState.Error(t.message ?: "Ошибка прослушивания ответа")
        }
    }

    /** Проверяет, что ответ — «да». */
    private fun isAffirmative(text: String): Boolean {
        val yesWords = listOf(
            "да", "ага", "угу", "конечно", "верно", "правильно",
            "подтверждаю", "подтвердить", "хорошо", "ок", "окей",
            "yes", "ok"
        )
        return yesWords.any { text.contains(it) }
    }

    /** Проверяет, что ответ — «нет». */
    private fun isNegative(text: String): Boolean {
        val noWords = listOf(
            "нет", "не надо", "не нужно", "отмена", "отменить",
            "отменяю", "стоп", "no", "cancel"
        )
        return noWords.any { text.contains(it) }
    }

    /**
     * Формирует текст вопроса для TTS.
     * Возвращает null, если команда не распознана или не поддерживается.
     */
    private fun buildConfirmationQuestion(cmd: ParsedCommand): String? {
        return when (cmd.type) {
            CommandType.CANCEL_ALL -> "Отменить все ваши записи? Скажите да или нет."

            CommandType.BOOK_NEAREST -> "Записать вас на ближайшее свободное время? Скажите да или нет."

            CommandType.BOOK_SPECIFIC -> {
                val doctor = cmd.doctor?.name
                val date = cmd.date
                val time = cmd.time
                if (doctor == null || date == null || time == null) {
                    Log.w(TAG, "Не хватает данных: doctor=$doctor, date=$date, time=$time")
                    null
                } else {
                    "Записать вас к $doctor на $date в $time? Скажите да или нет."
                }
            }

            else -> null
        }
    }

    /**
     * Парсит текст: сначала NLU, при неудаче — regex-fallback.
     */
    private suspend fun parseText(text: String): ParsedCommand {
        val nluResult = try {
            nlu.parse(text)
        } catch (t: Throwable) {
            Log.e(TAG, "NLU упал, используем regex-fallback", t)
            null
        }

        if (nluResult != null && nluResult.intent != CommandType.UNKNOWN) {
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

        Log.d(TAG, "NLU не справился, используем regex")
        return regexParser.parse(text)
    }

    /**
     * Выполняет распознанную команду после подтверждения «да».
     */
    private fun confirmVoice() {
        val cmd = pendingCommand
        if (cmd == null) {
            Log.e(TAG, "pendingCommand == null, нечего подтверждать")
            return
        }
        pendingCommand = null

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
                _state.value = VoiceUiState.Error("Перенос записи пока не поддерживается")
            }

            CommandType.UNKNOWN -> {
                _state.value = VoiceUiState.Error("Команда не распознана")
            }
        }
    }

    /** Сбросить состояние. */
    fun reset() {
        Log.d(TAG, "reset")
        pendingCommand = null
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        stt.release()
        nlu.release()
        tts.release()
        super.onCleared()
    }
}