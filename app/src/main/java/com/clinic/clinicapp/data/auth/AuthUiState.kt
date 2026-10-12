package com.clinic.clinicapp.ui.auth

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState

    /** Успешный вход — переход в календарь. */
    data class Success(val login: String, val role: String) : AuthUiState

    /** Успешная регистрация — показать сообщение и вернуть на вкладку входа. */
    data class Registered(val login: String) : AuthUiState
}

enum class AuthTab {
    LOGIN, REGISTER
}