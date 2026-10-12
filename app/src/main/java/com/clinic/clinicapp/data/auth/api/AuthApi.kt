package com.clinic.clinicapp.data.auth.api

import com.clinic.clinicapp.data.auth.dto.AuthResponse
import com.clinic.clinicapp.data.auth.dto.LoginRequest
import com.clinic.clinicapp.data.auth.dto.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // Правильный путь — /register (без опечатки)
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
}