package com.clinic.clinicapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clinic.clinicapp.data.repository.InMemoryAppointmentRepository
import com.clinic.clinicapp.ui.auth.LoginScreen
import com.clinic.clinicapp.ui.screens.CalendarScreen
import com.clinic.clinicapp.viewmodel.CalendarViewModel

/**
 * Маршруты навигации.
 */
object Routes {
    const val LOGIN = "login"
    const val CALENDAR = "calendar"
}

/**
 * Корневой NavHost приложения.
 *
 * @param repository общий репозиторий записей для всего приложения
 * @param startDestination стартовый экран (login или calendar — зависит от авторизации)
 */
@Composable
fun AppNavigation(
    repository: InMemoryAppointmentRepository,
    startDestination: String
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
                        // Не возвращаемся на логин по кнопке «Назад»
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Главный экран — календарь
        composable(Routes.CALENDAR) {
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

            val calendarVm: CalendarViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = calendarVmFactory
            )

            CalendarScreen(
                viewModel = calendarVm,
                onMicClick = {
                    // Пока голосовой экран вызывается из MainActivity как модальный диалог.
                    // Ничего не делаем — заглушка.
                }
            )
        }
    }
}