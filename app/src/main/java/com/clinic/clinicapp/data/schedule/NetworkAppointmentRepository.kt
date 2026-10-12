package com.clinic.clinicapp.data.schedule

import android.util.Log
import com.clinic.clinicapp.data.auth.NetworkClient
import com.clinic.clinicapp.data.model.Appointment
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.model.TimeSlot
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.schedule.mapper.DoctorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Репозиторий, который тянет врачей и слоты с бэкенда.
 * Заменяет InMemoryAppointmentRepository.
 */
class NetworkAppointmentRepository : AppointmentRepository {

    private val TAG = "NetworkAppointmentRepo"

    private val api = NetworkClient.scheduleApi

    private val _doctors = MutableStateFlow<List<Doctor>>(emptyList())
    override val doctors: StateFlow<List<Doctor>> = _doctors

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    override val appointments: StateFlow<List<Appointment>> = _appointments

    /** Загрузить всех врачей с бэкенда. */
    suspend fun loadDoctors(): Boolean {
        return try {
            Log.d(TAG, "Загрузка врачей…")
            val dtos = api.getDoctors()
            val mapped = dtos.map { DoctorMapper.toDoctor(it) }
            _doctors.value = mapped
            Log.d(TAG, "Загружено врачей: ${mapped.size}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка загрузки врачей", t)
            false
        }
    }

    /** Загрузить слоты конкретного врача. */
    suspend fun loadSlots(doctorId: Int, from: LocalDate = LocalDate.now(), days: Int = 14): Boolean {
        return try {
            Log.d(TAG, "Загрузка слотов врача $doctorId…")
            val dtos = api.getDoctorSlots(doctorId, from.toString(), days)
            val slots = dtos.map { DoctorMapper.toTimeSlot(doctorId.toString(), it) }

            // Обновляем врача в списке
            _doctors.value = _doctors.value.map { d ->
                if (d.id == doctorId.toString()) d.copy(availableSlots = slots) else d
            }
            Log.d(TAG, "Загружено слотов: ${slots.size}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка загрузки слотов", t)
            false
        }
    }

    /** Загрузить слоты для всех врачей сразу. */
    suspend fun loadAllSlots(days: Int = 14): Boolean {
        var allOk = true
        _doctors.value.forEach { doctor ->
            val idInt = doctor.id.toIntOrNull() ?: return@forEach
            if (!loadSlots(idInt, days = days)) allOk = false
        }
        return allOk
    }

    /** Заглушка — создание записи пока не подключено к бэкенду. */
    override fun book(doctorId: String, slotId: String): Boolean {
        Log.w(TAG, "book() пока не подключён к бэкенду")
        return false
    }

    /** Поиск врача по специальности (для голосового парсера). */
    override fun findDoctorByName(query: String): Doctor? {
        val q = query.lowercase().trim()
        return _doctors.value.find { doctor ->
            doctor.name.lowercase().contains(q) ||
                    doctor.specialty.lowercase().contains(q)
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

    override fun cancelAllAppointments(): Int {
        val count = _appointments.value.size
        _appointments.value = emptyList()
        return count
    }

    override fun findNearestFreeSlot(): Pair<Doctor, TimeSlot>? {
        val all = _doctors.value.flatMap { d ->
            d.availableSlots.filter { it.isAvailable }.map { d to it }
        }
        return all.minByOrNull { (_, slot) -> "${slot.date} ${slot.time}" }
    }

    override fun addSlot(
        doctorId: String,
        date: String,
        time: String
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun findNearestFreeSlotForDoctor(doctorId: String): Pair<Doctor, TimeSlot>? {
        val doctor = _doctors.value.find { it.id == doctorId } ?: return null
        val slot = doctor.availableSlots
            .filter { it.isAvailable }
            .minByOrNull { it.date + " " + it.time }
        return if (slot != null) doctor to slot else null
    }
}