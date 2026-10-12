package com.clinic.clinicapp.data.appointments.dto

import com.google.gson.annotations.SerializedName

/**
 * Тело POST /api/appointments.
 * Слот определяется по комбинации doctorId + date + time.
 */
data class CreateAppointmentRequest(
    @SerializedName("doctorId") val doctorId: Int,
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String
)