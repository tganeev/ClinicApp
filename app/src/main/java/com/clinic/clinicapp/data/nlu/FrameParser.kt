package com.clinic.clinicapp.data.nlu

import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.domain.CommandType
import com.clinic.clinicapp.domain.ParsedCommand
import java.time.DayOfWeek
import java.time.LocalDate

object FrameParser {

    private val specialtyToName = mapOf(
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

    /** Точное время: T_H15_00 → 15:00. */
    private val exactTimeRegex = Regex("""T_H(\d{2})_(\d{2})""")

    fun parse(frame: String, repository: AppointmentRepository): ParsedCommand {
        val tokens = frame.split(" ").filter { it.isNotBlank() }

        var intent = CommandType.UNKNOWN
        var specialty: String? = null
        var date: String? = null
        var time: String? = null

        for (tok in tokens) {
            when {
                tok == "<BOOK>" -> intent = CommandType.BOOK_SPECIFIC
                tok == "<CANCEL>" -> intent = CommandType.CANCEL_ALL
                tok == "<RESCHEDULE>" -> intent = CommandType.RESCHEDULE
                tok == "<UNSUPPORTED>" -> intent = CommandType.UNKNOWN

                tok in specialtyToName -> specialty = specialtyToName[tok]

                tok == "T_TODAY" -> date = LocalDate.now().toString()
                tok == "T_TOMORROW" -> date = LocalDate.now().plusDays(1).toString()
                tok == "T_DAY_AFTER_TOMORROW" -> date = LocalDate.now().plusDays(2).toString()
                tok == "T_IN_2_DAYS" -> date = LocalDate.now().plusDays(2).toString()
                tok == "T_IN_3_DAYS" -> date = LocalDate.now().plusDays(3).toString()
                tok == "T_IN_WEEK" -> date = LocalDate.now().plusWeeks(1).toString()
                tok == "T_IN_2_WEEKS" -> date = LocalDate.now().plusWeeks(2).toString()
                tok == "T_NEXT_WEEK" -> date = LocalDate.now().plusWeeks(1).toString()
                tok == "T_NEXT_MONTH" -> date = LocalDate.now().plusMonths(1).toString()
                tok == "T_MON" -> date = nextWeekday(DayOfWeek.MONDAY).toString()
                tok == "T_TUE" -> date = nextWeekday(DayOfWeek.TUESDAY).toString()
                tok == "T_WED" -> date = nextWeekday(DayOfWeek.WEDNESDAY).toString()
                tok == "T_THU" -> date = nextWeekday(DayOfWeek.THURSDAY).toString()
                tok == "T_FRI" -> date = nextWeekday(DayOfWeek.FRIDAY).toString()
                tok == "T_SAT" -> date = nextWeekday(DayOfWeek.SATURDAY).toString()
                tok == "T_SUN" -> date = nextWeekday(DayOfWeek.SUNDAY).toString()

                else -> {
                    // Точное время
                    exactTimeRegex.find(tok)?.let { m ->
                        time = "${m.groupValues[1]}:${m.groupValues[2]}"
                        return@let
                    }
                    // Приблизительные периоды дня
                    when (tok) {
                        "T_MORNING" -> time = "09:00"
                        "T_BEFORE_NOON" -> time = "11:00"
                        "T_MIDDAY" -> time = "12:00"
                        "T_AFTERNOON" -> time = "14:00"
                        "T_EVENING" -> time = "18:00"
                        "T_AFTER_WORK" -> time = "19:00"
                        "T_AFTER_18" -> time = "18:30"
                    }
                }
            }
        }

        // Ищем врача по специальности
        val doctor: Doctor? = specialty?.let { spec ->
            repository.doctors.value.find {
                it.specialty.equals(spec, ignoreCase = true)
            }
        }

        return ParsedCommand(
            type = intent,
            doctor = doctor,
            date = date,
            time = time,
            rawText = frame
        )
    }

    private fun nextWeekday(target: DayOfWeek): LocalDate {
        var d = LocalDate.now().plusDays(1)
        while (d.dayOfWeek != target) d = d.plusDays(1)
        return d
    }
}