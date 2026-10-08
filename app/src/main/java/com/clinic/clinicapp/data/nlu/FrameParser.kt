package com.clinic.clinicapp.data.nlu

import com.clinic.clinicapp.domain.CommandType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Превращает строку фрейма (например,
 * "<BOS> <BOOK> <GP> <T_TOMORROW> <T_H15_00> <EOS>")
 * в структуру слотов.
 */
class FrameParser {

    data class Result(
        val intent: CommandType,
        val specialty: String?,
        val date: String?,
        val time: String?
    )

    fun parse(frame: String): Result {
        val tokens = frame.split(" ").filter { it.isNotBlank() }

        var intent = CommandType.UNKNOWN
        var specialty: String? = null
        var date: String? = null
        var time: String? = null

        for (token in tokens) {
            when {
                token == "<BOOK>" -> intent = CommandType.BOOK_SPECIFIC
                token == "<CANCEL>" -> intent = CommandType.CANCEL_ALL
                token == "<RESCHEDULE>" -> intent = CommandType.RESCHEDULE
                token == "<UNSUPPORTED>" -> intent = CommandType.UNKNOWN

                token in specialtyMap -> specialty = specialtyMap[token]

                token.startsWith("<T_") -> {
                    // Токены времени: могут быть датой или часом
                    val parsedDate = mapDate(token)
                    val parsedTime = mapTime(token)
                    if (parsedDate != null) date = parsedDate
                    if (parsedTime != null) time = parsedTime
                }
            }
        }

        return Result(intent, specialty, date, time)
    }

    private val specialtyMap: Map<String, String> = mapOf(
        "<GP>" to "Терапевт",
        "<DENTIST>" to "Стоматолог",
        "<DERMATOLOGIST>" to "Дерматолог",
        "<GYNECOLOGIST>" to "Гинеколог",
        "<OPHTHALMOLOGIST>" to "Офтальмолог",
        "<NEUROLOGIST>" to "Невролог",
        "<PSYCHOLOGIST>" to "Психолог",
        "<SURGEON>" to "Хирург",
        "<ENT>" to "ЛОР"
    )

    /**
     * Возвращает дату в формате "yyyy-MM-dd" или null.
     */
    private fun mapDate(token: String): String? {
        val today = LocalDate.now()
        return when (token) {
            "<T_TODAY>" -> today.toString()
            "<T_TOMORROW>" -> today.plusDays(1).toString()
            "<T_DAY_AFTER_TOMORROW>" -> today.plusDays(2).toString()
            "<T_IN_2_DAYS>" -> today.plusDays(2).toString()
            "<T_IN_3_DAYS>" -> today.plusDays(3).toString()
            "<T_IN_WEEK>" -> today.plusWeeks(1).toString()
            "<T_IN_2_WEEKS>" -> today.plusWeeks(2).toString()
            "<T_NEXT_WEEK>" -> today.plusWeeks(1).toString()
            "<T_NEXT_MONTH>" -> today.plusMonths(1).toString()
            "<T_THIS_WEEK>" -> today.toString()
            "<T_THIS_MONTH>" -> today.toString()
            "<T_MON>" -> nextWeekday(today, 1)
            "<T_TUE>" -> nextWeekday(today, 2)
            "<T_WED>" -> nextWeekday(today, 3)
            "<T_THU>" -> nextWeekday(today, 4)
            "<T_FRI>" -> nextWeekday(today, 5)
            "<T_SAT>" -> nextWeekday(today, 6)
            "<T_SUN>" -> nextWeekday(today, 7)
            else -> null
        }
    }

    private fun nextWeekday(from: LocalDate, dayOfWeek: Int): String {
        var date = from.plusDays(1)
        while (date.dayOfWeek.value != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date.toString()
    }

    /**
     * Возвращает время в формате "HH:mm" или null.
     * Учитывает как точные часы (T_H15_00), так и обобщённые
     * (T_AFTERNOON, T_MORNING, T_EVENING).
     */
    private fun mapTime(token: String): String? {
        // Точное время: T_H15_00 → 15:00
        val exact = Regex("""<T_H(\d{2})_(\d{2})>""").find(token)
        if (exact != null) {
            val h = exact.groupValues[1]
            val m = exact.groupValues[2]
            return "$h:$m"
        }

        // Обобщённые периоды дня
        return when (token) {
            "<T_MORNING>" -> "09:00"
            "<T_BEFORE_NOON>" -> "11:00"
            "<T_MIDDAY>" -> "12:00"
            "<T_AFTERNOON>" -> "14:00"
            "<T_EVENING>" -> "18:00"
            "<T_AFTER_WORK>" -> "19:00"
            "<T_AFTER_18>" -> "18:30"
            else -> null
        }
    }
}