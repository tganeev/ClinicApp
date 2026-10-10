package com.clinic.clinicapp.data.auth.dto

data class AuthResponse(
    val token: String,
    val login: String,
    val role: String
)