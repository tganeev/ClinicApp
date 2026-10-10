plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.clinic.clinicapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clinic.clinicapp"
        minSdk = 24                            // минимум для sherpa-onnx
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Java 17 — требуется современными AGP, Compose и sherpa-onnx
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Файлы .onnx и .txt не сжимаются — иначе sherpa-onnx не прочитает модель из assets
    androidResources {
        noCompress += listOf("onnx", "txt", "json")
    }

    buildFeatures {
        compose = true
    }




}

dependencies {

    // ---- Базовые зависимости ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ---- Compose BOM управляет версиями всех модулей Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ---- ViewModel для Compose ----
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ---- Иконка микрофона (Icons.Filled.Mic) ----
    implementation(libs.androidx.compose.material.icons.extended)

    // ---- Корутины для AudioRecorder, SherpaSttEngine, ViewModel ----
    implementation(libs.kotlinx.coroutines.android)

    implementation("com.xdcobra.sherpa:sherpa-onnx:1.12.24") {
        exclude(group = "com.xdcobra.sherpa", module = "onnxruntime")
    }

    // Microsoft ONNX Runtime — единая версия для NLU и sherpa


    // ---- Тестовые зависимости ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("org.apache.commons:commons-compress:1.27.1")

    // TensorFlow Lite для NLU-модели clinic_lm
    implementation("com.google.ai.edge.litert:litert:1.4.0")

    // JSON-парсер для tokenizer.json
    implementation(libs.kotlinx.serialization.json)

    // Retrofit + OkHttp для сетевых запросов
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // EncryptedSharedPreferences для безопасного хранения токена
    implementation(libs.androidx.security.crypto)

    // Navigation Compose для экранов логина и календаря
    implementation(libs.androidx.navigation.compose)
}