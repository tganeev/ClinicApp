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

    /** Все записи на указанную дату ("2026-10-01"). */
    fun getAppointmentsOn(date: String): List<Appointment>

    /** Все свободные слоты на указанную дату, с указанием врача. */
    fun getFreeSlotsOn(date: String): List<Pair<Doctor, TimeSlot>>
}

class InMemoryAppointmentRepository : AppointmentRepository {

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
}