package com.clinic.clinicapp.data.auth.dto

data class RegisterRequest(
    val login: String,
    val password: String,
    val role: String
)