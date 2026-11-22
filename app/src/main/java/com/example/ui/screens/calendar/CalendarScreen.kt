package com.example.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.ui.components.Timeline24hBar
import com.example.ui.components.TimelineIntervalsSummaryList
import com.example.ui.components.TimelineSegmentDetailCard
import com.example.ui.components.TimelineSegmentLegend
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val today = remember { LocalDate.now(ScheduleEngine.HAVANA_ZONE) }
    val nowMillis = remember(state.selectedDate) {
        if (state.selectedDate == today) System.currentTimeMillis() else null
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "calendar_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Calendario y Línea de Tiempo 24h",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Date navigation bar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.shiftDays(-1) },
                                modifier = Modifier.testTag("calendar_prev_day")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = ScheduleEngine.formatDateEs(state.selectedDate),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.selectedDate == LocalDate.of(2026, 11, 19) ||
                                    state.selectedDate == LocalDate.of(2026, 11, 20)
                                ) {
                                    Text(
                                        text = "Datos históricos de prueba (19–20 nov 2026)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColors.favorite
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.shiftDays(1) },
                                modifier = Modifier.testTag("calendar_next_day")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
                            }
                        }
                    }

                    // Quick date shortcuts (7 days + Nov 19-20 historical test)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { viewModel.selectDate(today) },
                            leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Hoy") }
                        )
                        (0L..6L).forEach { offset ->
                            val d = today.plusDays(offset)
                            FilterChip(
                                selected = state.selectedDate == d,
                                onClick = { viewModel.selectDate(d) },
                                label = { Text(ScheduleEngine.formatShortDate(d).substring(0, 5)) }
                            )
                        }
                        FilterChip(
                            selected = state.selectedDate == LocalDate.of(2026, 11, 19),
                            onClick = { viewModel.selectDate(LocalDate.of(2026, 11, 19)) },
                            label = { Text("Prueba 19/11/26") }
                        )
                        FilterChip(
                            selected = state.selectedDate == LocalDate.of(2026, 11, 20),
                            onClick = { viewModel.selectDate(LocalDate.of(2026, 11, 20)) },
                            label = { Text("Prueba 20/11/26") }
                        )
                    }

                    // View mode chips + favorites toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CalendarViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.viewMode == mode,
                                onClick = { viewModel.selectViewMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                            )
                        }
                        FilterChip(
                            selected = state.favoritesOnly,
                            onClick = viewModel::toggleFavoritesOnly,
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Solo favoritos") }
                        )
                    }

                    TimelineSegmentLegend()
                }
            }

            // Selected segment detail callout
            state.selectedSegment?.let { seg ->
                item(key = "selected_segment_detail") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.selectedSegmentOwner.isNotBlank()) {
                            Text(
                                text = "Detalle seleccionado — ${state.selectedSegmentOwner}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TimelineSegmentDetailCard(segment = seg)
                    }
                }
            }

            when (state.viewMode) {
                CalendarViewMode.DAILY_COMPARATIVE -> {
                    if (!state.hasAnyForecastOnSelectedDate) {
                        item(key = "no_forecast_banner") {
                            NoForecastNoticeCard(date = state.selectedDate)
                        }
                    }
                    items(state.circuitTimelines, key = { it.circuit.code }) { row ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = row.circuit.displayTitle(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (row.circuit.isFavorite) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = "Favorito",
                                            tint = statusColors.favorite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Timeline24hBar(
                                    date = state.selectedDate,
                                    segments = row.segments,
                                    barHeight = 28.dp,
                                    currentTimeMillis = nowMillis,
                                    selectedSegment = if (state.selectedSegmentOwner == row.circuit.displayTitle()) state.selectedSegment else null,
                                    onSegmentTap = { seg ->
                                        viewModel.selectSegment(row.circuit.displayTitle(), seg)
                                    }
                                )
                            }
                        }
                    }
                }

                CalendarViewMode.BY_CIRCUIT -> {
                    item(key = "circuit_selector") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.activeCircuits.forEach { circuit ->
                                FilterChip(
                                    selected = state.selectedCircuitCode == circuit.code,
                                    onClick = { viewModel.selectCircuit(circuit.code) },
                                    label = { Text(circuit.displayTitle()) }
                                )
                            }
                        }
                    }
                    val selectedRow = state.circuitTimelines.firstOrNull { it.circuit.code == state.selectedCircuitCode }
                        ?: state.activeCircuits.firstOrNull { it.code == state.selectedCircuitCode }?.let { c ->
                            CircuitTimelineRow(c, state.weeklyTimeline[state.selectedDate].orEmpty())
                        }
                    if (selectedRow != null) {
                        item(key = "individual_timeline_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Línea de tiempo individual: ${selectedRow.circuit.displayTitle()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Timeline24hBar(
                                        date = state.selectedDate,
                                        segments = selectedRow.segments,
                                        barHeight = 40.dp,
                                        currentTimeMillis = nowMillis,
                                        selectedSegment = state.selectedSegment,
                                        onSegmentTap = { seg ->
                                            viewModel.selectSegment(selectedRow.circuit.displayTitle(), seg)
                                        }
                                    )
                                    TimelineIntervalsSummaryList(
                                        segments = selectedRow.segments,
                                        selectedSegment = state.selectedSegment,
                                        onSelect = { seg ->
                                            viewModel.selectSegment(selectedRow.circuit.displayTitle(), seg)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                CalendarViewMode.WEEKLY_7_DAYS -> {
                    item(key = "weekly_circuit_picker") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.activeCircuits.forEach { circuit ->
                                FilterChip(
                                    selected = state.selectedCircuitCode == circuit.code,
                                    onClick = { viewModel.selectCircuit(circuit.code) },
                                    label = { Text(circuit.displayTitle()) }
                                )
                            }
                        }
                    }
                    items(state.weeklyTimeline.entries.toList(), key = { it.key.toEpochDay() }) { (day, segments) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectDate(day) }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = ScheduleEngine.formatDateEs(day),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Timeline24hBar(
                                    date = day,
                                    segments = segments,
                                    barHeight = 26.dp,
                                    currentTimeMillis = if (day == today) System.currentTimeMillis() else null,
                                    onSegmentTap = { seg ->
                                        viewModel.selectSegment(ScheduleEngine.formatDateEs(day), seg)
                                    }
                                )
                            }
                        }
                    }
                }

                CalendarViewMode.GROUPS -> {
                    if (state.groupTimelines.isEmpty()) {
                        item(key = "no_groups") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No hay grupos rotativos configurados. Crea grupos en la pestaña Circuitos.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(18.dp)
                                )
                            }
                        }
                    } else {
                        items(state.groupTimelines, key = { it.group.id }) { row ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = row.group.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ciclo: ${row.group.serviceDurationMinutes / 60}h servicio / ${row.group.outageDurationMinutes / 60}h sin servicio • Circuitos: ${row.memberCodes.ifEmpty { listOf("Ninguno asignado") }.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Timeline24hBar(
                                        date = state.selectedDate,
                                        segments = row.segments,
                                        barHeight = 28.dp,
                                        currentTimeMillis = nowMillis,
                                        onSegmentTap = { seg ->
                                            viewModel.selectSegment(row.group.name, seg)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoForecastNoticeCard(date: LocalDate) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                Icons.Filled.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "No existe pronóstico registrado para el ${ScheduleEngine.formatShortDate(date)} en los circuitos sin horario fijo o grupo asignado. La app no inventa horarios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
