package com.clinic.clinicapp.data.auth.dto

data class ErrorResponse(
    val message: String?,
    val error: String? = null,
    val status: Int? = null
)