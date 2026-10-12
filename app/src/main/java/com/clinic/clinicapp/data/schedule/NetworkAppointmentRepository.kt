package com.clinic.clinicapp.data.schedule

import android.util.Log
import com.clinic.clinicapp.data.appointments.dto.CreateAppointmentRequest
import com.clinic.clinicapp.data.auth.NetworkClient
import com.clinic.clinicapp.data.model.Appointment
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.model.TimeSlot
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.schedule.api.ScheduleApi
import com.clinic.clinicapp.data.schedule.mapper.DoctorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class NetworkAppointmentRepository : AppointmentRepository {

    private val TAG = "NetworkAppointmentRepo"

    private val scheduleApi: ScheduleApi by lazy { NetworkClient.scheduleApi }
    private val appointmentsApi by lazy { NetworkClient.appointmentsApi }

    private val _doctors = MutableStateFlow<List<Doctor>>(emptyList())
    override val doctors: StateFlow<List<Doctor>> = _doctors

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    override val appointments: StateFlow<List<Appointment>> = _appointments

    // ---------------------------------------------------------------------
    // Загрузка врачей и слотов
    // ---------------------------------------------------------------------

    suspend fun loadDoctors(): Boolean = try {
        Log.d(TAG, "Загрузка врачей…")
        val dtos = scheduleApi.getDoctors()
        _doctors.value = dtos.map { DoctorMapper.toDoctor(it) }
        Log.d(TAG, "Загружено врачей: ${_doctors.value.size}")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Ошибка загрузки врачей", t)
        false
    }

    suspend fun loadSlots(doctorId: Int, from: LocalDate = LocalDate.now(), days: Int = 14): Boolean =
        try {
            Log.d(TAG, "Загрузка слотов врача $doctorId…")
            val dtos = scheduleApi.getDoctorSlots(doctorId, from.toString(), days)
            val slots = dtos.map { DoctorMapper.toTimeSlot(doctorId.toString(), it) }
            _doctors.value = _doctors.value.map { d ->
                if (d.id == doctorId.toString()) d.copy(availableSlots = slots) else d
            }
            Log.d(TAG, "Загружено слотов: ${slots.size}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка загрузки слотов", t)
            false
        }

    suspend fun loadAllSlots(days: Int = 14): Boolean {
        var allOk = true
        _doctors.value.forEach { doctor ->
            val idInt = doctor.id.toIntOrNull() ?: return@forEach
            if (!loadSlots(idInt, days = days)) allOk = false
        }
        return allOk
    }

    // ---------------------------------------------------------------------
    // Записи
    // ---------------------------------------------------------------------

    /** Загрузить все записи пациента с бэкенда. */
    suspend fun loadAppointments(): Boolean = try {
        Log.d(TAG, "Загрузка записей…")
        val dtos = appointmentsApi.getAppointments()
        Log.d(TAG, "Сырых записей: ${dtos.size}")

        // Фильтруем: оставляем только активные (CONFIRMED)
        val activeDtos = dtos.filter { it.isActive }
        Log.d(TAG, "Активных записей: ${activeDtos.size} (отфильтровано ${dtos.size - activeDtos.size})")

        _appointments.value = activeDtos.map { dto ->
            Appointment(
                id = dto.id.toString(),
                doctorId = dto.doctorId?.toString() ?: "",
                doctorName = dto.doctorName ?: "",
                date = dto.date,
                time = dto.time
            )
        }
        markBookedSlotsAsUnavailable()
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Ошибка загрузки записей", t)
        false
    }

    /**
     * После загрузки записей — проходим по слотам врачей
     * и помечаем занятые как isAvailable = false.
     */
    private fun markBookedSlotsAsUnavailable() {
        val bookedKeys = _appointments.value.map { "${it.doctorId}|${it.date}|${it.time}" }.toSet()
        _doctors.value = _doctors.value.map { doctor ->
            doctor.copy(
                availableSlots = doctor.availableSlots.map { slot ->
                    val key = "${doctor.id}|${slot.date}|${slot.time}"
                    if (key in bookedKeys) slot.copy(isAvailable = false) else slot
                }
            )
        }
    }

    /**
     * Создать запись через API.
     * @return true, если бэкенд ответил 2xx.
     */
    suspend fun createAppointment(doctorId: String, date: String, time: String): Boolean {
        return try {
            val idInt = doctorId.toIntOrNull()
            if (idInt == null) {
                Log.e(TAG, "doctorId не число: $doctorId")
                return false
            }
            Log.d(TAG, "Создаём запись: doctorId=$idInt, date=$date, time=$time")
            val response = appointmentsApi.createAppointment(
                CreateAppointmentRequest(doctorId = idInt, date = date, time = time)
            )
            if (response.isSuccessful) {
                Log.d(TAG, "Запись создана успешно")
                // Перезагружаем записи, чтобы получить актуальное состояние
                loadAppointments()
                // И слоты, потому что один слот мог стать занятым
                loadSlots(idInt)
                true
            } else {
                Log.e(TAG, "Ошибка создания: ${response.code()}")
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка создания записи", t)
            false
        }
    }

    /**
     * Синхронный интерфейс AppointmentRepository для совместимости.
     * Используется в голосовом сценарии.
     */
    override fun book(doctorId: String, slotId: String): Boolean {
        // Синхронная версия не используется — голосовой сценарий
        // вызывает createAppointment напрямую через suspend-метод.
        Log.w(TAG, "book() синхронный — не используется, вызывайте createAppointment()")
        return false
    }

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

    /**
     * Удалить все записи (по одной через DELETE).
     */
    override fun cancelAllAppointments(): Int {
        // Синхронная версия — не используется.
        Log.w(TAG, "cancelAllAppointments() синхронный — вызывайте cancelAllAppointmentsSuspend()")
        return 0
    }

    /** Suspend-версия для отмены всех записей. */

    suspend fun cancelAllAppointmentsSuspend(): Int {
        val list = _appointments.value
        Log.d(TAG, "Отмена ${list.size} записей…")

        var success = 0
        list.forEach { appt ->
            val idInt = appt.id.toIntOrNull() ?: return@forEach
            try {
                val resp = appointmentsApi.deleteAppointment(idInt)
                if (resp.isSuccessful) {
                    success++
                    Log.d(TAG, "Удалено id=$idInt (${resp.code()})")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Ошибка удаления id=$idInt", t)
            }
        }

        Log.d(TAG, "Перезагрузка записей и слотов после отмены…")
        loadAppointments()      // перезагрузит и отфильтрует
        loadAllSlots(days = 30) // вернёт available=true для отменённых слотов

        return success
    }

    override fun findNearestFreeSlot(): Pair<Doctor, TimeSlot>? {
        val all = _doctors.value.flatMap { d ->
            d.availableSlots.filter { it.isAvailable }.map { d to it }
        }
        return all.minByOrNull { (_, slot) -> "${slot.date} ${slot.time}" }
    }


    override fun findNearestFreeSlotForDoctor(doctorId: String): Pair<Doctor, TimeSlot>? {
        val doctor = _doctors.value.find { it.id == doctorId } ?: return null
        val slot = doctor.availableSlots
            .filter { it.isAvailable }
            .minByOrNull { it.date + " " + it.time }
        return if (slot != null) doctor to slot else null
    }
}