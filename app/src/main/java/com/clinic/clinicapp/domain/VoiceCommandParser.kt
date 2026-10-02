// domain/VoiceCommandParser.kt
package com.clinic.clinicapp.domain


import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.repository.AppointmentRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Результат разбора голосовой команды.
 */
data class ParsedCommand(
    val doctor: Doctor?,
    val date: String?,      // "2026-10-01"
    val time: String?,      // "15:00"
    val rawText: String
) {
    val isComplete: Boolean get() = doctor != null && date != null && time != null
}

/**
 * Парсер русских голосовых команд вида:
 *   «Запишите меня к Ивановой завтра в три часа дня»
 *   «Хочу записаться к Петрову послезавтра в 10:30»
 *   «Запись к Сидоровой на 2 октября в 16:00»
 *
 * Работает на регулярках и словарях. Для продакшена можно заменить на LLM,
 * но для демо этого достаточно.
 */
class VoiceCommandParser(private val repository: AppointmentRepository) {

    // Словарь словесных числительных -> цифры (для часов)
    private val hourWords = mapOf(
        "час" to 1, "один" to 1, "одна" to 1,
        "два" to 2, "две" to 2,
        "три" to 3,
        "четыре" to 4,
        "пять" to 5,
        "шесть" to 6,
        "семь" to 7,
        "восемь" to 8,
        "девять" to 9,
        "десять" to 10,
        "одиннадцать" to 11,
        "двенадцать" to 12
    )

    // Словарь месяцев -> номер
    private val monthWords = mapOf(
        "январ" to 1, "феврал" to 2, "март" to 3,
        "апрел" to 4, "ма" to 5, "июн" to 6,
        "июл" to 7, "август" to 8, "сентябр" to 9,
        "октябр" to 10, "ноябр" to 11, "декабр" to 12
    )

    /**
     * Главный метод: принимает распознанный текст, возвращает разобранную команду.
     */
    fun parse(text: String): ParsedCommand {
        val normalized = text.lowercase().replace("ё", "е")
        val doctor = extractDoctor(normalized)
        val date = extractDate(normalized)
        val time = extractTime(normalized)
        return ParsedCommand(doctor, date, time, text)
    }

    /**
     * Извлекаем фамилию врача.
     * Ищем шаблоны: «к Ивановой», «к врачу Ивановой», «к доктору Петрову».
     * Если фамилия не указана — вернём null.
     */
    private fun extractDoctor(text: String): Doctor? {
        // Ищем предлог «к» + слово в дательном падеже
        val regexToDoctor = Regex(
            """(?:к|у)\s+(?:врачу\s+|доктору\s+)?([а-я]+)""",
            RegexOption.IGNORE_CASE
        )
        val match = regexToDoctor.find(text)
        if (match != null) {
            val candidate = match.groupValues[1]
            val doctor = repository.findDoctorByName(candidate)
            if (doctor != null) return doctor
        }

        // Если не нашли через «к» — просто ищем фамилию врача в тексте
        return repository.doctors.value.find { doctor ->
            val surname = doctor.name.lowercase().split(" ").first()
            val stem = surname.dropLast(2).takeIf { it.length >= 3 } ?: surname
            text.contains(stem)
        }
    }

    /**
     * Извлекаем дату: «сегодня», «завтра», «послезавтра», «через N дней», «2 октября».
     */
    private fun extractDate(text: String): String? {
        val today = LocalDate.now()

        // Относительные дни
        when {
            text.contains("послезавтра") -> return today.plusDays(2).toString()
            text.contains("завтра") -> return today.plusDays(1).toString()
            text.contains("сегодня") -> return today.toString()
        }

        // «через 3 дня», «через 2 недели»
        val regexInDays = Regex("""через\s+(\d+)\s+(день|дня|дней)""")
        regexInDays.find(text)?.let {
            val days = it.groupValues[1].toIntOrNull() ?: return@let
            return today.plusDays(days.toLong()).toString()
        }

        // «2 октября», «второе октября»
        val regexDate = Regex("""(\d{1,2})\s+([а-я]+)""")
        regexDate.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthPart = match.groupValues[2]
            val month = monthWords.entries
                .firstOrNull { monthPart.startsWith(it.key) }
                ?.value
            if (day != null && month != null && day in 1..31) {
                // Определяем год: если месяц уже прошёл — берём следующий год
                var year = today.year
                if (month < today.monthValue) year++
                return LocalDate.of(year, month, day).toString()
            }
        }

        return null
    }

    /**
     * Извлекаем время: «в 15:00», «в три часа», «в 3 часа дня», «в половине четвёртого» (не поддерживается).
     */
    private fun extractTime(text: String): String? {
        // Формат «15:00» / «9:30»
        val regexDigits = Regex("""(\d{1,2})[:.](\d{2})""")
        regexDigits.find(text)?.let { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@let
            val m = match.groupValues[2]
            if (h in 0..23) {
                return "%02d:%s".format(h, m)
            }
        }

        // Формат «в 15 часов», «в 3 часа»
        val regexHour = Regex("""в\s+(\d{1,2})\s+час""")
        regexHour.find(text)?.let { match ->
            var h = match.groupValues[1].toIntOrNull() ?: return@let
            h = applyAmPm(text, h)
            if (h in 0..23) return "%02d:00".format(h)
        }

        // Формат «в три часа», «в пять»
        val regexWord = Regex("""в\s+([а-я]+)""")
        regexWord.find(text)?.let { match ->
            val word = match.groupValues[1]
            val h = hourWords[word] ?: return@let
            val h24 = applyAmPm(text, h)
            return "%02d:00".format(h24)
        }

        return null
    }

    /**
     * Применяем «дня»/«вечера»/«утра»/«ночи» к 12-часовому формату.
     * Без контекста оставляем как есть.
     */
    private fun applyAmPm(text: String, hour12: Int): Int {
        return when {
            text.contains("вечера") || text.contains("дня") -> {
                if (hour12 in 1..11) hour12 + 12 else hour12
            }
            text.contains("ночи") || text.contains("утра") -> hour12
            else -> hour12
        }
    }
}