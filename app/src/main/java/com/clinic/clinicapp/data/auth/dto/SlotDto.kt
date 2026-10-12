package com.clinic.clinicapp.data.schedule.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO слота врача из /api/doctors/{id}/slots.
 */
data class SlotDto(
    @SerializedName("date") val date: String,            // "2026-10-16"
    @SerializedName("time") val time: String,            // "09:00"
    @SerializedName("available") val available: Boolean,
    @SerializedName("slotId") val slotId: String         // "d1-2026-10-16-0900"
)