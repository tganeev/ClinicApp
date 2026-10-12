package com.clinic.clinicapp.data.auth

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response


class AuthInterceptor(private val tokenStorage: TokenStorage) : Interceptor {

    private val TAG = "AuthInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Публичные эндпоинты — без токена
        if (path.startsWith("/api/auth/")) {
            Log.d(TAG, "Публичный путь, без токена: $path")
            return chain.proceed(original)
        }

        val token = tokenStorage.getToken()
        Log.d(TAG, "Путь: $path, token=${token?.take(20)}...")

        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            Log.w(TAG, "Токен пустой — запрос без Authorization")
            original
        }
        return chain.proceed(request)
    }
}