// domain/VoiceCommandParser.kt
package com.clinic.clinicapp.domain

import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.repository.AppointmentRepository
import java.time.LocalDate

/**
 * Тип распознанной команды.
 */
enum class CommandType {
    /** Запись к конкретному врачу на конкретную дату/время. */
    BOOK_SPECIFIC,

    /** Запись на ближайшее свободное время. */
    BOOK_NEAREST,

    /** Отмена всех записей. */
    CANCEL_ALL,

    /** Команда не распознана. */
    UNKNOWN,

    RESCHEDULE,      // перенос записи
    CANCEL_ONE
}

/**
 * Результат разбора голосовой команды.
 */
data class ParsedCommand(
    val type: CommandType,
    val doctor: Doctor?,
    val date: String?,
    val time: String?,
    val rawText: String
) {
    /** Готова ли команда к исполнению (у всех нужных полей есть значения). */
    val isComplete: Boolean
        get() = when (type) {
            CommandType.BOOK_SPECIFIC -> doctor != null && date != null && time != null
            CommandType.BOOK_NEAREST -> true
            CommandType.CANCEL_ALL -> true
            CommandType.UNKNOWN -> false
            CommandType.RESCHEDULE -> TODO()
            CommandType.CANCEL_ONE -> TODO()
        }
}

class VoiceCommandParser(private val repository: AppointmentRepository) {

    private val hourWords = mapOf(
        "час" to 1, "один" to 1, "одна" to 1,
        "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8,
        "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12
    )

    private val monthWords = mapOf(
        "январ" to 1, "феврал" to 2, "март" to 3,
        "апрел" to 4, "ма" to 5, "июн" to 6,
        "июл" to 7, "август" to 8, "сентябр" to 9,
        "октябр" to 10, "ноябр" to 11, "декабр" to 12
    )

    fun parse(text: String): ParsedCommand {
        val normalized = text.lowercase().replace("ё", "е")

        // 1. Отмена всех записей
        if (isCancelAll(normalized)) {
            return ParsedCommand(CommandType.CANCEL_ALL, null, null, null, text)
        }

        // 2. Запись на ближайшее время
        if (isNearestTime(normalized)) {
            return ParsedCommand(CommandType.BOOK_NEAREST, null, null, null, text)
        }

        // 3. Обычная запись — врач + дата + время
        val doctor = extractDoctor(normalized)
        val date = extractDate(normalized)
        val time = extractTime(normalized)

        return if (doctor != null || date != null || time != null) {
            ParsedCommand(CommandType.BOOK_SPECIFIC, doctor, date, time, text)
        } else {
            ParsedCommand(CommandType.UNKNOWN, null, null, null, text)
        }
    }

    /**
     * Проверяет, что команда — отмена всех записей.
     * Примеры: «отмени все мои записи», «удали все записи», «отменить записи».
     */
    private fun isCancelAll(text: String): Boolean {
        val cancelWords = listOf("отмен", "удал", "удали", "убрать", "очист")
        val allWords = listOf("все", "всю", "всё")
        val recordWords = listOf("запис", "прием", "приём", " appointment")

        val hasCancel = cancelWords.any { text.contains(it) }
        val hasAll = allWords.any { text.contains(it) }
        val hasRecords = recordWords.any { text.contains(it) }

        return hasCancel && (hasAll || hasRecords)
    }

    /**
     * Проверяет, что команда — запись на ближайшее свободное время.
     * Примеры: «запиши на свободное время», «ближайшее время», «любое время».
     */
    private fun isNearestTime(text: String): Boolean {
        val phrases = listOf(
            "на свободное время",
            "в свободное время",
            "свободное время",
            "ближайшее время",
            "ближайшую дату",
            "любое время",
            "любой день",
            "как можно скорее",
            "пораньше",
            "по раньше"
        )
        return phrases.any { text.contains(it) }
    }

    private fun extractDoctor(text: String): Doctor? {
        val regexToDoctor = Regex(
            """(?:к|у)\s+(?:врачу\s+|доктору\s+)?([а-я]+)""",
            RegexOption.IGNORE_CASE
        )
        regexToDoctor.find(text)?.let { match ->
            val candidate = match.groupValues[1]
            repository.findDoctorByName(candidate)?.let { return it }
        }

        return repository.doctors.value.find { doctor ->
            val surname = doctor.name.lowercase().split(" ").first()
            val stem = surname.dropLast(2).takeIf { it.length >= 3 } ?: surname
            text.contains(stem)
        }
    }

    private fun extractDate(text: String): String? {
        val today = LocalDate.now()

        when {
            text.contains("послезавтра") -> return today.plusDays(2).toString()
            text.contains("завтра") -> return today.plusDays(1).toString()
            text.contains("сегодня") -> return today.toString()
        }

        Regex("""через\s+(\d+)\s+(день|дня|дней)""").find(text)?.let {
            val days = it.groupValues[1].toIntOrNull() ?: return@let
            return today.plusDays(days.toLong()).toString()
        }

        Regex("""(\d{1,2})\s+([а-я]+)""").find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthPart = match.groupValues[2]
            val month = monthWords.entries
                .firstOrNull { monthPart.startsWith(it.key) }
                ?.value
            if (day != null && month != null && day in 1..31) {
                var year = today.year
                if (month < today.monthValue) year++
                return LocalDate.of(year, month, day).toString()
            }
        }

        return null
    }

    private fun extractTime(text: String): String? {
        Regex("""(\d{1,2})[:.](\d{2})""").find(text)?.let { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@let
            val m = match.groupValues[2]
            if (h in 0..23) return "%02d:%s".format(h, m)
        }

        Regex("""в\s+(\d{1,2})\s+час""").find(text)?.let { match ->
            var h = match.groupValues[1].toIntOrNull() ?: return@let
            h = applyAmPm(text, h)
            if (h in 0..23) return "%02d:00".format(h)
        }

        Regex("""в\s+([а-я]+)""").find(text)?.let { match ->
            val word = match.groupValues[1]
            val h = hourWords[word] ?: return@let
            val h24 = applyAmPm(text, h)
            return "%02d:00".format(h24)
        }

        return null
    }

    private fun applyAmPm(text: String, hour12: Int): Int {
        return when {
            text.contains("вечера") || text.contains("дня") ->
                if (hour12 in 1..11) hour12 + 12 else hour12
            text.contains("ночи") || text.contains("утра") -> hour12
            else -> hour12
        }
    }
}