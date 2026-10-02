// viewmodel/CalendarViewModel.kt
package com.clinic.clinicapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.model.Appointment
import com.clinic.clinicapp.data.model.DayItem
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.repository.AppointmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * ViewModel главного экрана — календаря.
 * Показывает записи и свободные слоты по дням.
 */
class CalendarViewModel(
    private val repository: AppointmentRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Текущая выбранная дата (по умолчанию — сегодня)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    // Текущий отображаемый месяц (может не совпадать с выбранной датой)
    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth

    /**
     * Карта статусов дней: какие даты имеют записи и/или свободные слоты.
     * Используется для отрисовки точек под днями в WeekStrip.
     */
    val dayStatuses: StateFlow<Map<LocalDate, DayStatus>> =
        combine(repository.doctors, repository.appointments, _displayedMonth) { doctors, appointments, month ->
            val statuses = mutableMapOf<LocalDate, DayStatus>()

            // Считаем записи по датам
            appointments.forEach { appt ->
                val date = runCatching { LocalDate.parse(appt.date) }.getOrNull() ?: return@forEach
                if (YearMonth.from(date) == month) {
                    val current = statuses[date] ?: DayStatus()
                    statuses[date] = current.copy(hasAppointment = true)
                }
            }

            // Считаем свободные слоты по датам
            doctors.forEach { doctor ->
                doctor.availableSlots
                    .filter { it.isAvailable }
                    .forEach { slot ->
                        val date = runCatching { LocalDate.parse(slot.date) }.getOrNull() ?: return@forEach
                        if (YearMonth.from(date) == month) {
                            val current = statuses[date] ?: DayStatus()
                            statuses[date] = current.copy(hasFreeSlot = true)
                        }
                    }
            }

            statuses
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Список элементов на выбранную дату (записи + свободные слоты).
     */
    val dayItems: StateFlow<List<DayItem>> =
        combine(repository.doctors, repository.appointments, _selectedDate) { doctors, appointments, date ->
            val dateStr = date.format(dateFormatter)
            val result = mutableListOf<DayItem>()

            // Сначала записи (отсортированы по времени)
            appointments
                .filter { it.date == dateStr }
                .sortedBy { it.time }
                .forEach { appt ->
                    val doctor = doctors.find { it.id == appt.doctorId }
                    if (doctor != null) {
                        result += DayItem.Booked(appt, doctor)
                    }
                }

            // Потом свободные слоты (тоже по времени)
            doctors.flatMap { d ->
                d.availableSlots
                    .filter { it.date == dateStr && it.isAvailable }
                    .map { d to it }
            }
                .sortedBy { (_, slot) -> slot.time }
                .forEach { (doctor, slot) ->
                    result += DayItem.Free(doctor, slot)
                }

            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Переключиться на другую дату. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _displayedMonth.value = YearMonth.from(date)
    }

    /** Перейти на предыдущий месяц. */
    fun previousMonth() {
        _displayedMonth.value = _displayedMonth.value.minusMonths(1)
    }

    /** Перейти на следующий месяц. */
    fun nextMonth() {
        _displayedMonth.value = _displayedMonth.value.plusMonths(1)
    }

    /** Записаться на свободный слот (пользователь нажал на элемент Free). */
    fun bookSlot(doctorId: String, slotId: String) {
        viewModelScope.launch {
            repository.book(doctorId, slotId)
        }
    }
}

/**
 * Статус дня для отображения в календаре.
 */
data class DayStatus(
    val hasAppointment: Boolean = false,
    val hasFreeSlot: Boolean = false
) {
    val isEmpty: Boolean get() = !hasAppointment && !hasFreeSlot
}