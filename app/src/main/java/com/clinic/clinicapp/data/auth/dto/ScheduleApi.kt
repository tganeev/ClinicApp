package com.clinic.clinicapp.data.schedule.api

import com.clinic.clinicapp.data.schedule.dto.DoctorDto
import com.clinic.clinicapp.data.schedule.dto.EmployeeDto
import com.clinic.clinicapp.data.schedule.dto.SlotDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit-интерфейс для эндпоинтов расписания.
 * Все эндпоинты требуют Authorization: Bearer <token>
 * (добавляется автоматически AuthInterceptor'ом).
 */
interface ScheduleApi {

    /** Список всех врачей. */
    @GET("api/doctors")
    suspend fun getDoctors(): List<DoctorDto>

    /** Расписание на конкретную дату. */
    @GET("api/schedule")
    suspend fun getSchedule(@Query("date") date: String): List<DoctorDto>

    /** Слоты врача на ближайшие `days` дней от `from`. */
    @GET("api/doctors/{id}/slots")
    suspend fun getDoctorSlots(
        @Path("id") doctorId: Int,
        @Query("from") from: String,
        @Query("days") days: Int = 14
    ): List<SlotDto>

    /** Расширенный список сотрудников с nextAppointment. */
    @GET("api/employees")
    suspend fun getEmployees(): List<EmployeeDto>
}