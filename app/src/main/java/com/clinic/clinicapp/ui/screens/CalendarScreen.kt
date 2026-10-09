package com.clinic.clinicapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clinic.clinicapp.data.model.DayItem
import com.clinic.clinicapp.data.model.Doctor
import com.clinic.clinicapp.viewmodel.CalendarViewModel
import com.clinic.clinicapp.viewmodel.DayStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ColorGreen = Color(0xFF4CAF50)
private val ColorYellow = Color(0xFFFFC107)
private val ColorBlue = Color(0xFF2196F3)

/**
 * Главный экран приложения — календарь записей.
 *
 * @param onMicClick вызывается при нажатии на кнопку микрофона —
 *        открывает голосовой экран (модально).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onMicClick: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val displayedMonth by viewModel.displayedMonth.collectAsState()
    val dayStatuses by viewModel.dayStatuses.collectAsState()
    val dayItems by viewModel.dayItems.collectAsState()
    val doctors by viewModel.doctors.collectAsState()

    var weekStart by remember { mutableStateOf(selectedDate.with(DayOfWeek.MONDAY)) }
    var showAddSlotDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) {
        if (selectedDate < weekStart || selectedDate > weekStart.plusDays(6)) {
            weekStart = selectedDate.with(DayOfWeek.MONDAY)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ---- Основной контент ----
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Мои записи") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            // floatingActionButton НЕ передаём — кнопки разместим отдельно ниже,
            // чтобы обе были на одной высоте и с симметричными отступами.
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                MonthSelector(
                    month = displayedMonth,
                    onPrev = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() }
                )

                WeekStrip(
                    weekStart = weekStart,
                    selectedDate = selectedDate,
                    dayStatuses = dayStatuses,
                    onDateClick = { viewModel.selectDate(it) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (dayItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "На этот день ничего нет.\n" +
                                    "Нажмите на микрофон, чтобы записаться голосом,\n" +
                                    "или на «+», чтобы добавить свободный слот.",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dayItems) { item ->
                            when (item) {
                                is DayItem.Booked -> BookedCard(item)
                                is DayItem.Free -> FreeSlotCard(
                                    item = item,
                                    onBook = {
                                        viewModel.bookSlot(item.doctor.id, item.slot.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Две кнопки внизу экрана: «+» слева, микрофон справа ----
        // safeDrawingPadding() учитывает statusBars, navigationBars, displayCutout,
        // поэтому кнопки не уходят за пределы видимой области.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = { showAddSlotDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить слот",
                    modifier = Modifier.size(28.dp)
                )
            }

            FloatingActionButton(
                onClick = onMicClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Записаться голосом",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // ---- Диалог добавления слота ----
    if (showAddSlotDialog) {
        AddSlotDialog(
            doctors = doctors,
            onDismiss = { showAddSlotDialog = false },
            onSave = { doctorId, date, time ->
                viewModel.addSlot(doctorId, date, time)
                showAddSlotDialog = false
            }
        )
    }
}

@Composable
private fun MonthSelector(
    month: java.time.YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
        }
        Text(
            text = month.month
                .getDisplayName(TextStyle.FULL, Locale("ru"))
                .replaceFirstChar { it.uppercase() } + " " + month.year,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    dayStatuses: Map<LocalDate, DayStatus>,
    onDateClick: (LocalDate) -> Unit
) {
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { date ->
            val isSelected = date == selectedDate
            val status = dayStatuses[date]

            val dotColor = when {
                status == null || status.isEmpty -> Color.Transparent
                status.hasAppointment && status.hasFreeSlot -> ColorYellow
                status.hasAppointment -> ColorGreen
                status.hasFreeSlot -> ColorBlue
                else -> Color.Transparent
            }

            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDateClick(date) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale("ru"))
                        .take(2)
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun BookedCard(item: DayItem.Booked) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = ColorGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.doctor.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.doctor.specialty} · ${item.appointment.time}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "Запись",
                fontSize = 12.sp,
                color = ColorGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FreeSlotCard(
    item: DayItem.Free,
    onBook: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBook() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = ColorBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.doctor.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.doctor.specialty} · ${item.slot.time}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Свободно",
                fontSize = 12.sp,
                color = ColorBlue,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Диалог добавления нового свободного слота.
 * Позволяет выбрать врача, ввести дату и время.
 */
@Composable
private fun AddSlotDialog(
    doctors: List<Doctor>,
    onDismiss: () -> Unit,
    onSave: (doctorId: String, date: String, time: String) -> Unit
) {
    var selectedDoctorId by remember {
        mutableStateOf(doctors.firstOrNull()?.id ?: "")
    }
    var date by remember {
        mutableStateOf(LocalDate.now().plusDays(1).toString())
    }
    var time by remember { mutableStateOf("10:00") }
    var doctorMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить слот") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Врач:", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(
                        onClick = { doctorMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val doctorName = doctors
                            .find { it.id == selectedDoctorId }?.name
                            ?: "Выберите врача"
                        Text(doctorName)
                    }
                    DropdownMenu(
                        expanded = doctorMenuExpanded,
                        onDismissRequest = { doctorMenuExpanded = false }
                    ) {
                        doctors.forEach { doctor ->
                            DropdownMenuItem(
                                text = {
                                    Text("${doctor.name} (${doctor.specialty})")
                                },
                                onClick = {
                                    selectedDoctorId = doctor.id
                                    doctorMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Дата (гггг-ММ-дд):", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Время (ЧЧ:мм):", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedDoctorId.isNotBlank() &&
                        date.isNotBlank() &&
                        time.isNotBlank()
                    ) {
                        onSave(selectedDoctorId, date, time)
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}