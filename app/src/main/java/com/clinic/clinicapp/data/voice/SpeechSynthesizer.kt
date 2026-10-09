package com.clinic.clinicapp.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Обёртка над системным TTS (Text-to-Speech).
 * Использует русский язык, если он доступен.
 */
class SpeechSynthesizer(private val context: Context) {

    private val TAG = "SpeechSynthesizer"

    private var tts: TextToSpeech? = null
    private var isReady = false

    /**
     * Инициализация TTS. Вызывать один раз при старте приложения.
     */
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Русский язык не поддерживается, используем default")
                    tts?.setLanguage(Locale.getDefault())
                }

                // Настройки интонации: чуть медленнее — TTS лучше выделяет интонацию
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(0.95f)

                isReady = true
                Log.d(TAG, "TTS инициализирован")
                if (continuation.isActive) continuation.resume(true)
            } else {
                Log.e(TAG, "Ошибка инициализации TTS: $status")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    /**
     * Озвучить текст. Приостанавливает корутину до окончания речи.
     */
    suspend fun speak(text: String): Unit = suspendCancellableCoroutine { continuation ->
        val engine = tts
        if (engine == null || !isReady) {
            Log.e(TAG, "TTS не инициализирован, speak() пропущен")
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = "clinic_${System.currentTimeMillis()}"
        Log.d(TAG, "Озвучивание: «$text»")

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    Log.d(TAG, "TTS начал: $id")
                }
                override fun onDone(id: String?) {
                    Log.d(TAG, "TTS завершён: $id")
                    if (continuation.isActive) continuation.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    Log.e(TAG, "TTS ошибка: $id")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        )

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}