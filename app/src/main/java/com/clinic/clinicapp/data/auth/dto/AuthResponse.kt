package com.clinic.clinicapp.data.auth.dto

data class AuthResponse(
    val token: String? = null,
    val login: String,
    val role: String,
    val firstName: String? = null,
    val lastName: String? = null
)