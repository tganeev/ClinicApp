package com.clinic.clinicapp

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinic.clinicapp.data.auth.AuthRepository
import com.clinic.clinicapp.data.repository.InMemoryAppointmentRepository
import com.clinic.clinicapp.navigation.AppNavigation
import com.clinic.clinicapp.navigation.Routes
import com.clinic.clinicapp.ui.screens.VoiceBookingScreen
import com.clinic.clinicapp.ui.theme.ClinicTheme
import com.clinic.clinicapp.viewmodel.VoiceBookingViewModel

class MainActivity : ComponentActivity() {

    private val repository = InMemoryAppointmentRepository()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* не блокируем */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            ClinicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(repository)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(repository: InMemoryAppointmentRepository) {
    val appContext = LocalContext.current.applicationContext as Application

    // Проверяем авторизацию один раз при старте
    val startDestination = remember {
        val auth = AuthRepository(appContext)
        if (auth.isLoggedIn()) Routes.CALENDAR else Routes.LOGIN
    }

    // Модальный голосовой диалог — как был
    var showVoiceSheet by remember { mutableStateOf(false) }

    val voiceVmFactory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceBookingViewModel(appContext, repository) as T
            }
        }
    }

    // Навигация
    AppNavigation(
        repository = repository,
        startDestination = startDestination
    )

    // Модальный голосовой экран — оставляем поверх
    // ВАЖНО: пока открывается по FAB из CalendarScreen — нужно прокинуть коллбэк.
    // Пока FAB на Calendare не открывает голосовой — просто оставляем заготовку.
    if (showVoiceSheet) {
        val voiceVm: VoiceBookingViewModel = viewModel(factory = voiceVmFactory)
        val voiceState by voiceVm.state.collectAsState()

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showVoiceSheet = false },
            confirmButton = { /* кнопки внутри VoiceBookingScreen */ },
            text = {
                VoiceBookingScreen(
                    state = voiceState,
                    onStartRecording = voiceVm::startRecording,
                    onStopRecording = voiceVm::stopRecording,
                    onReset = {
                        voiceVm.reset()
                        showVoiceSheet = false
                    }
                )
            }
        )
    }
}