package com.clinic.clinicapp.data.auth

import android.content.Context
import android.util.Log
import com.clinic.clinicapp.data.auth.dto.AuthResponse
import com.clinic.clinicapp.data.auth.dto.ErrorResponse
import com.clinic.clinicapp.data.auth.dto.LoginRequest
import com.clinic.clinicapp.data.auth.dto.RegisterRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Результат сетевой операции авторизации.
 */
sealed interface AuthResult {
    data class Success(val auth: AuthResponse) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Репозиторий авторизации.
 * Объединяет сеть (Retrofit) и хранение (TokenStorage).
 */
class AuthRepository(context: Context) {

    private val TAG = "AuthRepository"
    private val api = NetworkClient.authApi
    private val storage = TokenStorage(context)
    private val gson = Gson()

    /** Войти по логину и паролю. */
    suspend fun login(login: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(login, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        storage.saveSession(body.token, body.login, body.role)
                        Log.d(TAG, "Login OK: ${body.login}, role=${body.role}")
                        AuthResult.Success(body)
                    } else {
                        AuthResult.Error("Пустой ответ сервера")
                    }
                } else {
                    AuthResult.Error(parseError(response.code(), response.errorBody()?.string()))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Login failed", t)
                AuthResult.Error(networkErrorMessage(t))
            }
        }

    /** Зарегистрироваться. Роль фиксирована — PATIENT. */
    suspend fun register(login: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.register(RegisterRequest(login, password, ROLE_PATIENT))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        storage.saveSession(body.token, body.login, body.role)
                        Log.d(TAG, "Register OK: ${body.login}, role=${body.role}")
                        AuthResult.Success(body)
                    } else {
                        AuthResult.Error("Пустой ответ сервера")
                    }
                } else {
                    AuthResult.Error(parseError(response.code(), response.errorBody()?.string()))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Register failed", t)
                AuthResult.Error(networkErrorMessage(t))
            }
        }

    /** Выход из аккаунта. */
    fun logout() {
        storage.clear()
        Log.d(TAG, "Logged out")
    }

    /** Проверка, авторизован ли пользователь. */
    fun isLoggedIn(): Boolean = storage.isLoggedIn()

    /** Текущий токен (для интерцептора в будущем). */
    fun getToken(): String? = storage.getToken()

    /** Текущая роль. */
    fun getRole(): String? = storage.getRole()

    // ---- helpers ----

    private fun parseError(code: Int, body: String?): String {
        Log.w(TAG, "HTTP $code: $body")

        // Пытаемся распарсить JSON бэкенда (у него {message: "..."}).
        val message = try {
            body?.let { gson.fromJson(it, ErrorResponse::class.java)?.message }
        } catch (t: Throwable) {
            null
        }

        return when {
            !message.isNullOrBlank() -> message
            code == 401 -> "Неверный логин или пароль"
            code == 400 -> "Некорректные данные"
            code == 409 -> "Пользователь с таким логином уже существует"
            code in 500..599 -> "Ошибка на сервере"
            else -> "Ошибка $code"
        }
    }

    private fun networkErrorMessage(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Нет подключения к интернету"
        is java.net.SocketTimeoutException -> "Превышено время ожидания"
        is java.net.ConnectException -> "Сервер недоступен"
        else -> t.message ?: "Неизвестная ошибка"
    }

    companion object {
        const val ROLE_PATIENT = "PATIENT"
    }
}