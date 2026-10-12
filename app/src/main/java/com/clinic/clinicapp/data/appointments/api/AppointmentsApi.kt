package com.clinic.clinicapp.data.appointments.api

import com.clinic.clinicapp.data.appointments.dto.AppointmentDto
import com.clinic.clinicapp.data.appointments.dto.CreateAppointmentRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AppointmentsApi {

    /** Список записей пациента. */
    @GET("api/appointments")
    suspend fun getAppointments(): List<AppointmentDto>

    /** Создать запись. */
    @POST("api/appointments")
    suspend fun createAppointment(
        @Body request: CreateAppointmentRequest
    ): Response<Unit>

    /** Удалить запись по id. */
    @DELETE("api/appointments/{id}")
    suspend fun deleteAppointment(@Path("id") id: Int): Response<Unit>
}