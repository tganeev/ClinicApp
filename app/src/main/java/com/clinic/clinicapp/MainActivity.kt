// MainActivity.kt
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
import com.clinic.clinicapp.data.repository.InMemoryAppointmentRepository
import com.clinic.clinicapp.ui.screens.CalendarScreen
import com.clinic.clinicapp.ui.screens.VoiceBookingScreen
import com.clinic.clinicapp.ui.theme.ClinicTheme
import com.clinic.clinicapp.viewmodel.CalendarViewModel
import com.clinic.clinicapp.viewmodel.VoiceBookingViewModel

class MainActivity : ComponentActivity() {

    private val repository = InMemoryAppointmentRepository()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат обрабатывается самим приложением */ }

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

    // LocalContext читаем ЗДЕСЬ, в @Composable-контексте
    val appContext = LocalContext.current.applicationContext as Application

    // Фабрика для календаря
    val calendarVmFactory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CalendarViewModel(repository) as T
            }
        }
    }

    // Фабрика для голосового экрана
    val voiceVmFactory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceBookingViewModel(appContext, repository) as T
            }
        }
    }

    val calendarVm: CalendarViewModel = viewModel(factory = calendarVmFactory)

    var showVoiceSheet by remember { mutableStateOf(false) }

    CalendarScreen(
        viewModel = calendarVm,
        onMicClick = { showVoiceSheet = true }
    )

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
                    onConfirm = {
                        voiceVm.confirm()
                        showVoiceSheet = false
                    },
                    onReset = voiceVm::reset
                )
            }
        )
    }
}