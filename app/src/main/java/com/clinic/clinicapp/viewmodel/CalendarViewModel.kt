package com.clinic.clinicapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.model.DayItem
import com.clinic.clinicapp.data.repository.AppointmentRepository
import com.clinic.clinicapp.data.schedule.NetworkAppointmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarViewModel(
    private val repository: AppointmentRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        // Загружаем врачей и слоты при первом создании
        viewModelScope.launch {
            _isLoading.value = true
            if (repository is NetworkAppointmentRepository) {
                repository.loadDoctors()
                repository.loadAllSlots(days = 30)
            }
            _isLoading.value = false
        }
    }

    val doctors: StateFlow<List<com.clinic.clinicapp.data.model.Doctor>> =
        repository.doctors

    val dayStatuses: StateFlow<Map<LocalDate, DayStatus>> =
        combine(repository.doctors, repository.appointments, _displayedMonth) { doctors, appointments, month ->
            val statuses = mutableMapOf<LocalDate, DayStatus>()

            appointments.forEach { appt ->
                val date = runCatching { LocalDate.parse(appt.date) }.getOrNull() ?: return@forEach
                if (YearMonth.from(date) == month) {
                    val current = statuses[date] ?: DayStatus()
                    statuses[date] = current.copy(hasAppointment = true)
                }
            }

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

    val dayItems: StateFlow<List<DayItem>> =
        combine(repository.doctors, repository.appointments, _selectedDate) { doctors, appointments, date ->
            val dateStr = date.format(dateFormatter)
            val result = mutableListOf<DayItem>()

            appointments
                .filter { it.date == dateStr }
                .sortedBy { it.time }
                .forEach { appt ->
                    val doctor = doctors.find { it.id == appt.doctorId }
                    if (doctor != null) result += DayItem.Booked(appt, doctor)
                }

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

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _displayedMonth.value = YearMonth.from(date)
    }

    fun previousMonth() { _displayedMonth.value = _displayedMonth.value.minusMonths(1) }
    fun nextMonth() { _displayedMonth.value = _displayedMonth.value.plusMonths(1) }

    fun bookSlot(doctorId: String, slotId: String) {
        viewModelScope.launch {
            repository.book(doctorId, slotId)
        }
    }

    fun addSlot(doctorId: String, date: String, time: String) {
        // Пока не реализовано — нужен эндпоинт POST /api/slots
    }
}

data class DayStatus(
    val hasAppointment: Boolean = false,
    val hasFreeSlot: Boolean = false
) {
    val isEmpty: Boolean get() = !hasAppointment && !hasFreeSlot
}