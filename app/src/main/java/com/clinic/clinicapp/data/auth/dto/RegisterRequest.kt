package com.clinic.clinicapp.data.auth.dto

data class RegisterRequest(
    val login: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val role: String
)