package com.clinic.clinicapp.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.clinicapp.data.auth.AuthRepository
import com.clinic.clinicapp.data.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана авторизации.
 *
 * Отвечает за:
 *  - ввод логина и пароля;
 *  - переключение между входом и регистрацией;
 *  - вызов AuthRepository;
 *  - отображение состояния (Idle / Loading / Error / Success).
 */
class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "LoginViewModel"

    private val repository = AuthRepository(app)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state

    private val _tab = MutableStateFlow(AuthTab.LOGIN)
    val tab: StateFlow<AuthTab> = _tab

    /** Проверить, авторизован ли пользователь (для навигации при старте). */
    fun isLoggedIn(): Boolean = repository.isLoggedIn()

    /** Переключить вкладку «Вход» / «Регистрация». */
    fun setTab(tab: AuthTab) {
        _tab.value = tab
        // Сбрасываем ошибки при переключении
        if (_state.value is AuthUiState.Error) {
            _state.value = AuthUiState.Idle
        }
    }

    /** Выполнить вход или регистрацию в зависимости от активной вкладки. */
    fun submit(login: String, password: String) {
        if (_state.value is AuthUiState.Loading) return

        // Простая валидация
        if (login.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Заполните логин и пароль")
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading

            val result = when (_tab.value) {
                AuthTab.LOGIN -> repository.login(login.trim(), password)
                AuthTab.REGISTER -> repository.register(login.trim(), password)
            }

            _state.value = when (result) {
                is AuthResult.Success -> {
                    Log.d(TAG, "Auth success: ${result.auth.login}, role=${result.auth.role}")
                    AuthUiState.Success(result.auth.login, result.auth.role)
                }
                is AuthResult.Error -> {
                    Log.w(TAG, "Auth error: ${result.message}")
                    AuthUiState.Error(result.message)
                }
            }
        }
    }

    /** Сбросить состояние (например, после ошибки). */
    fun resetState() {
        _state.value = AuthUiState.Idle
    }

    /** Выход. */
    fun logout() {
        repository.logout()
        _state.value = AuthUiState.Idle
    }
}