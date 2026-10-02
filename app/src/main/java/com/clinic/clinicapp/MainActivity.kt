// MainActivity.kt
package com.clinic.clinicapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clinic.clinicapp.data.repository.InMemoryAppointmentRepository
import com.clinic.clinicapp.ui.screens.VoiceBookingScreen

import com.clinic.clinicapp.ui.theme.ClinicTheme
import com.clinic.clinicapp.viewmodel.VoiceBookingViewModel

class MainActivity : ComponentActivity() {

    // Единственный экземпляр репозитория на всё приложение
    private val repository = InMemoryAppointmentRepository()

    // Фабрика ViewModel — прокидываем репозиторий внутрь
    private val viewModel: VoiceBookingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceBookingViewModel(application, repository) as T
            }
        }
    }

    // Запрос разрешения на микрофон в рантайме
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Можно показать снекбар, здесь просто игнорируем
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Сразу просим разрешение при старте
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            ClinicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.state.collectAsState()
                    VoiceBookingScreen(
                        state = state,
                        onStartRecording = viewModel::startRecording,
                        onStopRecording = viewModel::stopRecording,
                        onConfirm = viewModel::confirmBooking,
                        onReset = viewModel::reset
                    )
                }
            }
        }
    }
}