package com.clinic.clinicapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clinic.clinicapp.data.auth.AuthRepository
import com.clinic.clinicapp.data.auth.TokenStorage
import com.clinic.clinicapp.data.repository.InMemoryAppointmentRepository
import com.clinic.clinicapp.data.schedule.NetworkAppointmentRepository
import com.clinic.clinicapp.ui.auth.LoginScreen
import com.clinic.clinicapp.ui.screens.CalendarScreen
import com.clinic.clinicapp.viewmodel.CalendarViewModel

object Routes {
    const val LOGIN = "login"
    const val CALENDAR = "calendar"
}

@Composable
fun AppNavigation(
    repository: NetworkAppointmentRepository,
    startDestination: String,
    onMicClick: () -> Unit     // ← новый параметр
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран авторизации
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthorized = {
                    navController.navigate(Routes.CALENDAR) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Календарь
        composable(Routes.CALENDAR) {
            val appContext = LocalContext.current.applicationContext

            val calendarVmFactory = remember {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(
                        modelClass: Class<T>
                    ): T {
                        @Suppress("UNCHECKED_CAST")
                        return CalendarViewModel(repository) as T
                    }
                }
            }

            val calendarVm: CalendarViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = calendarVmFactory
                )

            val tokenStorage = remember { TokenStorage(appContext) }

            CalendarScreen(
                viewModel = calendarVm,
                userName = tokenStorage.getDisplayName(),
                onLogout = {
                    AuthRepository(appContext).logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CALENDAR) { inclusive = true }
                    }
                },
                onMicClick = onMicClick   // ← прокидываем наверх
            )
        }
    }
}