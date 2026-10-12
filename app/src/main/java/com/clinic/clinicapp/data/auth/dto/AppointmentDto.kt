package com.clinic.clinicapp.data.appointments.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO записи из GET /api/appointments.
 * status: "CONFIRMED" — активная, "CANCELLED" — отменённая.
 */
data class AppointmentDto(
    @SerializedName("id") val id: Int,
    @SerializedName("doctorId") val doctorId: Int? = null,
    @SerializedName("doctorName") val doctorName: String? = null,
    @SerializedName("specialty") val specialty: String? = null,
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String,
    @SerializedName("status") val status: String? = null
) {
    /** Запись активна только если статус CONFIRMED. */
    val isActive: Boolean
        get() = status == null || status.equals("CONFIRMED", ignoreCase = true)
}