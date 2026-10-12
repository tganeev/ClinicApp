package com.clinic.clinicapp.data.schedule.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO врача из /api/doctors или /api/schedule.
 * Поля совпадают с форматом бэкенда.
 */
data class DoctorDto(
    @SerializedName("id") val id: Int,
    @SerializedName("fio") val fio: String,              // ФИО полностью
    @SerializedName("specialty") val specialty: String,
    @SerializedName("time") val time: String,            // "09:00"
    @SerializedName("days") val days: List<Int>          // [1, 3, 5] — дни недели (1=Пн..7=Вс)
)