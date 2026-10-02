// ui/screens/CalendarScreen.kt
package com.clinic.clinicapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clinic.clinicapp.data.model.DayItem
import com.clinic.clinicapp.ui.components.MicButton
import com.clinic.clinicapp.viewmodel.CalendarViewModel
import com.clinic.clinicapp.viewmodel.DayStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api

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

    // Первый день недели, содержащей выбранную дату
    var weekStart by remember { mutableStateOf(selectedDate.with(DayOfWeek.MONDAY)) }

    // Синхронизируем weekStart с выбранной датой
    LaunchedEffect(selectedDate) {
        if (selectedDate < weekStart || selectedDate > weekStart.plusDays(6)) {
            weekStart = selectedDate.with(DayOfWeek.MONDAY)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои записи") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onMicClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Mic,
                    contentDescription = "Записаться голосом",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Заголовок месяца со стрелками
            MonthSelector(
                month = displayedMonth,
                onPrev = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() }
            )

            // Полоса из 7 дней недели
            WeekStrip(
                weekStart = weekStart,
                selectedDate = selectedDate,
                dayStatuses = dayStatuses,
                onDateClick = { viewModel.selectDate(it) }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Список записей и свободных слотов на выбранный день
            if (dayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "На этот день ничего нет.\nНажмите на микрофон, чтобы записаться голосом.",
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
                                onBook = { viewModel.bookSlot(item.doctor.id, item.slot.id) }
                            )
                        }
                    }
                }
            }
        }
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

            // Цвет точки под датой:
            //  зелёный — есть запись, синий — есть свободный слот,
            //  жёлтый — и то и другое, прозрачный — ничего
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

/**
 * Карточка уже созданной записи (зелёная).
 */
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

/**
 * Карточка свободного слота — на неё можно записаться (синяя).
 */
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