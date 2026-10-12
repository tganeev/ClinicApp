package com.clinic.clinicapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран авторизации с двумя вкладками: «Вход» и «Регистрация».
 *
 * Логика:
 *   - Вход: логин + пароль → при успехе onAuthorized() (переход в календарь).
 *   - Регистрация: логин + пароль + имя + фамилия → при успехе показываем
 *     снекбар «Пользователь ... успешно зарегистрирован», автоматически
 *     переключаемся на вкладку «Вход» и очищаем поля. Пользователь должен
 *     ввести логин/пароль заново вручную.
 */
@Composable
fun LoginScreen(
    onAuthorized: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    // Успешный вход — переход в календарь
    LaunchedEffect(state) {
        if (state is AuthUiState.Success) {
            onAuthorized()
        }
    }

    // Успешная регистрация — показать снекбар и сбросить состояние
    LaunchedEffect(state) {
        if (state is AuthUiState.Registered) {
            val registeredLogin = (state as AuthUiState.Registered).login
            snackbarHostState.showSnackbar(
                message = "Пользователь $registeredLogin успешно зарегистрирован. Войдите, используя свои данные.",
                duration = SnackbarDuration.Long
            )
            viewModel.resetState()
        }
    }

    // Сброс полей при переключении вкладок
    LaunchedEffect(tab) {
        login = ""
        password = ""
        firstName = ""
        lastName = ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Клиника",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.height(24.dp))

                // Вкладки «Вход» / «Регистрация»
                TabRow(selectedTabIndex = if (tab == AuthTab.LOGIN) 0 else 1) {
                    Tab(
                        selected = tab == AuthTab.LOGIN,
                        onClick = { viewModel.setTab(AuthTab.LOGIN) },
                        text = { Text("Вход") }
                    )
                    Tab(
                        selected = tab == AuthTab.REGISTER,
                        onClick = { viewModel.setTab(AuthTab.REGISTER) },
                        text = { Text("Регистрация") }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Поле «Логин»
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    singleLine = true,
                    enabled = state !is AuthUiState.Loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Поле «Пароль»
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    enabled = state !is AuthUiState.Loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (tab == AuthTab.LOGIN) ImeAction.Done else ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Поля «Имя» и «Фамилия» — только на вкладке регистрации
                if (tab == AuthTab.REGISTER) {
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Имя") },
                        singleLine = true,
                        enabled = state !is AuthUiState.Loading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Фамилия") },
                        singleLine = true,
                        enabled = state !is AuthUiState.Loading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Кнопка действия / индикатор загрузки
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (state) {
                        is AuthUiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        else -> {
                            Button(
                                onClick = {
                                    if (tab == AuthTab.LOGIN) {
                                        viewModel.submitLogin(login, password)
                                    } else {
                                        viewModel.submitRegister(
                                            login = login,
                                            password = password,
                                            firstName = firstName,
                                            lastName = lastName
                                        )
                                    }
                                },
                                enabled = when (tab) {
                                    AuthTab.LOGIN ->
                                        login.isNotBlank() && password.isNotBlank()
                                    AuthTab.REGISTER ->
                                        login.isNotBlank() && password.isNotBlank()
                                                && firstName.isNotBlank()
                                                && lastName.isNotBlank()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (tab == AuthTab.LOGIN) "Войти"
                                    else "Зарегистрироваться"
                                )
                            }
                        }
                    }
                }

                // Ошибка
                if (state is AuthUiState.Error) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = (state as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}