// data/model/Models.kt
package com.clinic.clinicapp.data.model

data class Doctor(
    val id: String,
    val name: String,
    val specialty: String,
    val availableSlots: List<TimeSlot>
)

data class TimeSlot(
    val id: String,
    val date: String,        // "2026-10-01"
    val time: String,        // "15:00"
    val isAvailable: Boolean = true
)

data class Appointment(
    val id: String,
    val doctorId: String,
    val doctorName: String,
    val date: String,
    val time: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Элемент для отображения в списке на выбранный день.
 * Может быть либо уже созданной записью, либо свободным слотом врача.
 */
sealed interface DayItem {
    /** Уже созданная запись пациента. */
    data class Booked(
        val appointment: Appointment,
        val doctor: Doctor
    ) : DayItem

    /** Свободный слот — можно записаться. */
    data class Free(
        val doctor: Doctor,
        val slot: TimeSlot
    ) : DayItem
}