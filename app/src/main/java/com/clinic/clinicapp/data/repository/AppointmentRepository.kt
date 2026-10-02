// data/repository/AppointmentRepository.kt
package com.clinic.clinicapp.data.repository

import com.clinic.clinicapp.data.model.Appointment
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.model.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

interface AppointmentRepository {
    val doctors: StateFlow<List<Doctor>>
    val appointments: StateFlow<List<Appointment>>

    fun book(doctorId: String, slotId: String): Boolean
    fun findDoctorByName(query: String): Doctor?

    fun getAppointmentsOn(date: String): List<Appointment>
    fun getFreeSlotsOn(date: String): List<Pair<Doctor, TimeSlot>>

    /** Отменяет все существующие записи. Возвращает количество отменённых. */
    fun cancelAllAppointments(): Int

    /** Находит ближайший свободный слот (по дате и времени). */
    fun findNearestFreeSlot(): Pair<Doctor, TimeSlot>?
}

class InMemoryAppointmentRepository : AppointmentRepository {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _doctors = MutableStateFlow(
        listOf(
            Doctor("d1", "Иванова Анна", "Терапевт", listOf(
                TimeSlot("s1", "2026-10-01", "09:00"),
                TimeSlot("s2", "2026-10-01", "15:00"),
                TimeSlot("s3", "2026-10-02", "11:00"),
                TimeSlot("s7", "2026-10-03", "10:00"),
                TimeSlot("s8", "2026-10-03", "14:00")
            )),
            Doctor("d2", "Петров Сергей", "Кардиолог", listOf(
                TimeSlot("s4", "2026-10-01", "10:30"),
                TimeSlot("s5", "2026-10-03", "14:00"),
                TimeSlot("s9", "2026-10-05", "11:30")
            )),
            Doctor("d3", "Сидорова Елена", "Невролог", listOf(
                TimeSlot("s6", "2026-10-02", "16:00"),
                TimeSlot("s10", "2026-10-04", "09:30")
            ))
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

    override fun findDoctorByName(query: String): Doctor? {
        val q = query.lowercase().trim().replace("ё", "е")
        if (q.length < 3) return null
        return _doctors.value.find { doctor ->
            val name = doctor.name.lowercase().replace("ё", "е")
            val surname = name.split(" ").first()
            val stem = surname.dropLast(2).takeIf { it.length >= 3 } ?: surname
            q.contains(stem) || q.contains(surname)
        }
    }

    override fun getAppointmentsOn(date: String): List<Appointment> =
        _appointments.value.filter { it.date == date }

    override fun getFreeSlotsOn(date: String): List<Pair<Doctor, TimeSlot>> =
        _doctors.value.flatMap { doctor ->
            doctor.availableSlots
                .filter { it.date == date && it.isAvailable }
                .map { doctor to it }
        }

    /**
     * Отменяет все записи и освобождает соответствующие слоты.
     */
    override fun cancelAllAppointments(): Int {
        val count = _appointments.value.size
        if (count == 0) return 0

        // Собираем id слотов, которые нужно освободить
        val slotsToFree = _appointments.value
            .mapNotNull { appt ->
                _doctors.value
                    .find { it.id == appt.doctorId }
                    ?.availableSlots
                    ?.find { it.date == appt.date && it.time == appt.time }
                    ?.id
            }
            .toSet()

        // Освобождаем слоты
        _doctors.update { list ->
            list.map { doctor ->
                doctor.copy(
                    availableSlots = doctor.availableSlots.map { slot ->
                        if (slot.id in slotsToFree) slot.copy(isAvailable = true) else slot
                    }
                )
            }
        }

        // Очищаем список записей
        _appointments.value = emptyList()

        return count
    }

    /**
     * Ищет ближайший свободный слот: минимальная дата, а при равной дате —
     * минимальное время.
     */
    override fun findNearestFreeSlot(): Pair<Doctor, TimeSlot>? {
        val now = LocalDateTime.now()

        return _doctors.value
            .flatMap { doctor ->
                doctor.availableSlots
                    .filter { it.isAvailable }
                    .map { doctor to it }
            }
            .mapNotNull { pair ->
                val dateTime = parseDateTime(pair.second.date, pair.second.time)
                    ?: return@mapNotNull null
                Triple(pair.first, pair.second, dateTime)
            }
            .filter { (_, _, dateTime) -> dateTime.isAfter(now) }
            .minByOrNull { (_, _, dateTime) -> dateTime }
            ?.let { (doctor, slot, _) -> doctor to slot }
    }

    /**
     * Преобразует "2026-10-01" + "15:00" в LocalDateTime.
     * Возвращает null, если дата в прошлом.
     */
    private fun parseDateTime(date: String, time: String): LocalDateTime? {
        return try {
            val localDate = LocalDate.parse(date, dateFormatter)
            val localTime = LocalTime.parse(time)
            LocalDateTime.of(localDate, localTime)
        } catch (t: Throwable) {
            null
        }
    }
}