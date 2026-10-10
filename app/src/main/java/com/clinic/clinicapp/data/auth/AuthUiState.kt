package com.clinic.clinicapp.ui.auth

/**
 * Состояние экрана авторизации.
 */
sealed interface AuthUiState {
    /** Начальное состояние, ждём ввода. */
    data object Idle : AuthUiState

    /** Идёт запрос на сервер. */
    data object Loading : AuthUiState

    /** Ошибка (сеть / неверные данные / сервер). */
    data class Error(val message: String) : AuthUiState

    /** Успех — пользователь авторизован. */
    data class Success(val login: String, val role: String) : AuthUiState
}

/**
 * Вкладки на экране авторизации.
 */
enum class AuthTab {
    LOGIN, REGISTER
}