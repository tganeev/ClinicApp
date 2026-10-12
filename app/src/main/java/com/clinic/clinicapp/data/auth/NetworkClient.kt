package com.clinic.clinicapp.data.auth

import android.content.Context
import com.clinic.clinicapp.data.auth.api.AuthApi
import com.clinic.clinicapp.data.schedule.api.ScheduleApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit-клиента для всего приложения.
 *
 * ВАЖНО: NetworkClient.init(context) должен быть вызван ОДИН РАЗ в MainActivity.onCreate
 * ДО первого обращения к authApi / scheduleApi. Иначе interceptor не добавится.
 */
object NetworkClient {

    private const val BASE_URL = "http://159.194.246.91:8090/"

    @Volatile
    private var initialized = false

    private lateinit var tokenStorage: TokenStorage
    private lateinit var authInterceptor: AuthInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofit: Retrofit

    /**
     * Инициализация. Вызывать один раз при старте приложения.
     * Всегда вызывается из MainActivity.onCreate перед setContent.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return

        tokenStorage = TokenStorage(context.applicationContext)
        authInterceptor = AuthInterceptor(tokenStorage)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)   // ← добавляем ВСЕГДА
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        initialized = true
    }

    val authApi: AuthApi
        get() {
            check(initialized) { "NetworkClient.init(context) не вызван!" }
            return retrofit.create(AuthApi::class.java)
        }

    val scheduleApi: ScheduleApi
        get() {
            check(initialized) { "NetworkClient.init(context) не вызван!" }
            return retrofit.create(ScheduleApi::class.java)
        }
}