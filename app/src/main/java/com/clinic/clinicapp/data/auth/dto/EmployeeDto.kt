package com.clinic.clinicapp.data.schedule.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO из /api/employees. Расширенная версия DoctorDto
 * с полем nextAppointment.
 */
data class EmployeeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("fio") val fio: String,
    @SerializedName("specialty") val specialty: String,
    @SerializedName("nextAppointment") val nextAppointment: String?   // "2026-10-15 14:00"
)