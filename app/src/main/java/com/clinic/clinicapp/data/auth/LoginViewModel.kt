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

    fun isLoggedIn(): Boolean = repository.isLoggedIn()

    fun setTab(tab: AuthTab) {
        _tab.value = tab
        // Сбрасываем все состояния при переключении вкладок
        _state.value = AuthUiState.Idle
    }

    fun submitLogin(login: String, password: String) {
        if (_state.value is AuthUiState.Loading) return
        if (login.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Заполните логин и пароль")
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            val result = repository.login(login.trim(), password)
            _state.value = when (result) {
                is AuthResult.Success -> AuthUiState.Success(result.auth.login, result.auth.role)
                is AuthResult.Error -> AuthUiState.Error(result.message)
            }
        }
    }

    fun submitRegister(
        login: String,
        password: String,
        firstName: String,
        lastName: String
    ) {
        if (_state.value is AuthUiState.Loading) return
        when {
            login.isBlank() || password.isBlank() ->
            { _state.value = AuthUiState.Error("Заполните логин и пароль"); return }
            firstName.isBlank() ->
            { _state.value = AuthUiState.Error("Заполните имя"); return }
            lastName.isBlank() ->
            { _state.value = AuthUiState.Error("Заполните фамилию"); return }
        }
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            val result = repository.register(login.trim(), password, firstName.trim(), lastName.trim())
            when (result) {
                is AuthResult.Success -> {
                    _state.value = AuthUiState.Registered(result.auth.login)
                    _tab.value = AuthTab.LOGIN
                }
                is AuthResult.Error -> _state.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _state.value = AuthUiState.Idle
    }

    fun logout() {
        repository.logout()
        _state.value = AuthUiState.Idle
        _tab.value = AuthTab.LOGIN
    }
}