package com.clinic.clinicapp.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Безопасное хранение токена и данных пользователя.
 * Использует EncryptedSharedPreferences — токен зашифрован в keystore.
 */
class TokenStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "clinic_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(token: String, login: String, role: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_LOGIN, login)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getLogin(): String? = prefs.getString(KEY_LOGIN, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LOGIN = "auth_login"
        private const val KEY_ROLE = "auth_role"
    }
}