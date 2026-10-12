package com.clinic.clinicapp.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    fun saveSession(
        token: String?,
        login: String,
        role: String,
        firstName: String? = null,
        lastName: String? = null
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token ?: "")
            .putString(KEY_LOGIN, login)
            .putString(KEY_ROLE, role)
            .putString(KEY_FIRST_NAME, firstName ?: "")
            .putString(KEY_LAST_NAME, lastName ?: "")
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getLogin(): String? = prefs.getString(KEY_LOGIN, null)
    fun getRole(): String? = prefs.getString(KEY_ROLE, null)
    fun getFirstName(): String? = prefs.getString(KEY_FIRST_NAME, null)
    fun getLastName(): String? = prefs.getString(KEY_LAST_NAME, null)

    /**
     * Возвращает отображаемое имя пользователя: «Имя Фамилия», если они есть.
     * Иначе — логин. Если и логина нет — «Пользователь».
     */
    fun getDisplayName(): String {
        val first = getFirstName().orEmpty()
        val last = getLastName().orEmpty()
        val fullName = "$first $last".trim()
        return when {
            fullName.isNotBlank() -> fullName
            !getLogin().isNullOrBlank() -> getLogin()!!
            else -> "Пользователь"
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LOGIN = "auth_login"
        private const val KEY_ROLE = "auth_role"
        private const val KEY_FIRST_NAME = "auth_first_name"
        private const val KEY_LAST_NAME = "auth_last_name"
    }
}