package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DateSelectorField(
    label: String,
    date: LocalDate?,
    placeholder: String = "Seleccionar fecha",
    onDateSelected: (LocalDate) -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPicker = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = if (onClear != null && date != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = date?.let { ScheduleEngine.formatShortDate(it) } ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (date != null) JetBrainsMonoFontFamily else null,
                    fontWeight = if (date != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (date != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClear != null && date != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Quitar fecha",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        MaterialDatePickerModal(
            initialDate = date ?: LocalDate.now(),
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                onDateSelected(picked)
                showPicker = false
            }
        )
    }
}

@Composable
fun TimeSelectorField(
    label: String,
    minuteOfDay: Int,
    use24h: Boolean,
    onTimeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val displayText = if (minuteOfDay == 1440) {
        if (use24h) "24:00" else "12:00 a. m. (fin)"
    } else {
        ScheduleEngine.formatMinuteOfDay(minuteOfDay, use24h)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPicker = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showPicker) {
        val clamped = minuteOfDay.coerceIn(0, 1439)
        MaterialTimePickerModal(
            title = label,
            initialHour = clamped / 60,
            initialMinute = clamped % 60,
            use24h = use24h,
            onDismiss = { showPicker = false },
            onConfirm = { h, m ->
                onTimeSelected(h * 60 + m)
                showPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDatePickerModal(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialUtcMillis = remember(initialDate) {
        initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val pickedMillis = datePickerState.selectedDateMillis
                    if (pickedMillis != null) {
                        val pickedDate = Instant.ofEpochMilli(pickedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onConfirm(pickedDate)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTimePickerModal(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = use24h
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
