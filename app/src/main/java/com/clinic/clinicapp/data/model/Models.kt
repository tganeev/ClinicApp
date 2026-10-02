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
    val date: String,
    val time: String,
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