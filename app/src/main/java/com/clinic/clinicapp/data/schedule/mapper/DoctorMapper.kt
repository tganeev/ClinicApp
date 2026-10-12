package com.clinic.clinicapp.data.schedule.mapper

import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.data.model.TimeSlot
import com.clinic.clinicapp.data.schedule.dto.DoctorDto
import com.clinic.clinicapp.data.schedule.dto.SlotDto

/**
 * Преобразование DTO ↔ доменные модели.
 */
object DoctorMapper {

    /**
     * DoctorDto → Doctor. Слоты добавляются отдельно (через getDoctorSlots).
     * Здесь создаём Doctor с пустым списком слотов.
     */
    fun toDoctor(dto: DoctorDto): Doctor {
        return Doctor(
            id = dto.id.toString(),           // в Doctor.id — String, в DTO — Int
            name = dto.fio,
            specialty = dto.specialty,
            availableSlots = emptyList()
        )
    }

    /**
     * SlotDto → TimeSlot. Проверяет доступность.
     */
    fun toTimeSlot(doctorId: String, dto: SlotDto): TimeSlot {
        return TimeSlot(
            id = dto.slotId,                  // slotId уже уникальный
            date = dto.date,
            time = dto.time,
            isAvailable = dto.available
        )
    }
}