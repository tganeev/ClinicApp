// data/repository/AppointmentRepository.kt
package com.clinic.clinicapp.data.repository


import com.clinic.clinicapp.data.model.Appointment
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.model.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

interface AppointmentRepository {
    val doctors: StateFlow<List<Doctor>>
    val appointments: StateFlow<List<Appointment>>
    fun book(doctorId: String, slotId: String): Boolean
    fun findDoctorByName(query: String): Doctor?
}

class InMemoryAppointmentRepository : AppointmentRepository {

    private val _doctors = MutableStateFlow(
        listOf(
            Doctor(
                id = "d1",
                name = "Иванова Анна",
                specialty = "Терапевт",
                availableSlots = listOf(
                    TimeSlot("s1", "2026-10-01", "09:00"),
                    TimeSlot("s2", "2026-10-01", "15:00"),
                    TimeSlot("s3", "2026-10-02", "11:00")
                )
            ),
            Doctor(
                id = "d2",
                name = "Петров Сергей",
                specialty = "Кардиолог",
                availableSlots = listOf(
                    TimeSlot("s4", "2026-10-01", "10:30"),
                    TimeSlot("s5", "2026-10-03", "14:00")
                )
            ),
            Doctor(
                id = "d3",
                name = "Сидорова Елена",
                specialty = "Невролог",
                availableSlots = listOf(
                    TimeSlot("s6", "2026-10-02", "16:00")
                )
            )
        )
    )
    override val doctors: StateFlow<List<Doctor>> = _doctors

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    override val appointments: StateFlow<List<Appointment>> = _appointments

    override fun book(doctorId: String, slotId: String): Boolean {
        val doctor = _doctors.value.find { it.id == doctorId } ?: return false
        val slot = doctor.availableSlots.find { it.id == slotId } ?: return false
        if (!slot.isAvailable) return false

        _doctors.update { list ->
            list.map { d ->
                if (d.id == doctorId) {
                    d.copy(availableSlots = d.availableSlots.map { s ->
                        if (s.id == slotId) s.copy(isAvailable = false) else s
                    })
                } else d
            }
        }

        _appointments.update { current ->
            current + Appointment(
                id = "a${System.currentTimeMillis()}",
                doctorId = doctorId,
                doctorName = doctor.name,
                date = slot.date,
                time = slot.time
            )
        }
        return true
    }

    /**
     * Поиск врача по фамилии.
     * GigaAM может вернуть «ивановой», «иванова», «к ивановой» — ищем по корню фамилии.
     * Учитываем падежи: сравниваем по началу корня (без последних 1-2 букв).
     */
    override fun findDoctorByName(query: String): Doctor? {
        val q = query.lowercase().trim().replace("ё", "е")
        if (q.length < 3) return null

        return _doctors.value.find { doctor ->
            val name = doctor.name.lowercase().replace("ё", "е")
            // Берём фамилию (первое слово)
            val surname = name.split(" ").first()
            // Обрезаем окончание фамилии — «иванова»/«ивановой»/«иванову» -> «иванов»
            val surnameStem = surname.dropLast(2).takeIf { it.length >= 3 } ?: surname
            // Ищем совпадение корня фамилии в запросе
            q.contains(surnameStem) || q.contains(surname)
        }
    }
}