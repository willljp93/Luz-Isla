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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.ui.components.Continuous3DTickerTimeline
import com.example.ui.components.Timeline24hBar
import com.example.ui.components.TimelineIntervalsSummaryList
import com.example.ui.components.TimelineSegmentLegend
import com.example.ui.screens.circuits.CircuitSelectorDialog
import com.example.ui.theme.LocalBottomOverlayPadding
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val today = remember(state.zoneId) { LocalDate.now(state.zoneId) }
    val nowMillis = remember(state.selectedDate, today) {
        if (state.selectedDate == today) System.currentTimeMillis() else null
    }
    var showCircuitPickerDialog by remember { mutableStateOf(false) }

    val bottomOverlayPadding = LocalBottomOverlayPadding.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 12.dp + bottomOverlayPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "calendar_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Calendario y Línea de Tiempo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isToday = state.selectedDate == today
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.selectDate(today) }
                                .testTag("calendar_today_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CalendarToday,
                                    contentDescription = "Ir a hoy",
                                    tint = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Hoy",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.shiftDays(-1) },
                                    modifier = Modifier.testTag("calendar_prev_day")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = ScheduleEngine.formatDateEs(state.selectedDate),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.selectedDate == LocalDate.of(2026, 11, 19) ||
                                        state.selectedDate == LocalDate.of(2026, 11, 20)
                                    ) {
                                        Text(
                                            text = "Histórico de prueba",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColors.favorite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                    }

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
                                label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                            )
                        }
                        if (state.viewMode != CalendarViewMode.GROUPS) {
                            FilterChip(
                                selected = state.favoritesOnly,
                                onClick = viewModel::toggleFavoritesOnly,
                                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Solo favoritos", maxLines = 1, softWrap = false) }
                            )
                        }
                    }

                    TimelineSegmentLegend()
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCircuit(row.circuit.code)
                                    viewModel.selectViewMode(CalendarViewMode.BY_CIRCUIT)
                                }
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
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Ver 3D",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
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
                                }
                                Timeline24hBar(
                                    date = state.selectedDate,
                                    segments = row.segments,
                                    barHeight = 28.dp,
                                    currentTimeMillis = nowMillis,
                                    use24h = state.use24h,
                                    zoneId = state.zoneId,
                                    sunriseMinutes = state.sunriseMinutes,
                                    sunsetMinutes = state.sunsetMinutes,
                                    enableElectricAnimations = state.enableElectricAnimations,
                                    onSegmentTap = {
                                        viewModel.selectCircuit(row.circuit.code)
                                        viewModel.selectViewMode(CalendarViewMode.BY_CIRCUIT)
                                    }
                                )
                            }
                        }
                    }
                }

                CalendarViewMode.BY_CIRCUIT -> {
                    item(key = "circuit_selector") {
                        CircuitDropdownStepperBar(
                            circuits = state.activeCircuits,
                            selectedCode = state.selectedCircuitCode,
                            favoriteColor = statusColors.favorite,
                            onSelectCode = viewModel::selectCircuit,
                            onOpenPicker = { showCircuitPickerDialog = true }
                        )
                    }
                    val selectedRow = state.circuitTimelines.firstOrNull { it.circuit.code == state.selectedCircuitCode }
                        ?: state.activeCircuits.firstOrNull { it.code == state.selectedCircuitCode }?.let { c ->
                            CircuitTimelineRow(c, state.weeklyTimeline[state.selectedDate].orEmpty())
                        }
                    if (selectedRow != null) {
                        item(key = "continuous_3d_wheel") {
                            Continuous3DTickerTimeline(
                                circuitCode = selectedRow.circuit.code,
                                snapshot = state.snapshot,
                                nowMillis = System.currentTimeMillis(),
                                use24h = state.use24h,
                                zoneId = state.zoneId
                            )
                        }
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
                                        text = "Detalle del día: ${selectedRow.circuit.displayTitle()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Timeline24hBar(
                                        date = state.selectedDate,
                                        segments = selectedRow.segments,
                                        barHeight = 40.dp,
                                        currentTimeMillis = nowMillis,
                                        use24h = state.use24h,
                                        zoneId = state.zoneId,
                                        sunriseMinutes = state.sunriseMinutes,
                                        sunsetMinutes = state.sunsetMinutes,
                                        enableElectricAnimations = state.enableElectricAnimations,
                                        selectedSegment = state.selectedSegment,
                                        onSegmentTap = { seg ->
                                            viewModel.selectSegment(selectedRow.circuit.displayTitle(), seg)
                                        }
                                    )
                                    TimelineIntervalsSummaryList(
                                        segments = selectedRow.segments,
                                        selectedSegment = state.selectedSegment,
                                        use24h = state.use24h,
                                        zoneId = state.zoneId,
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
                        CircuitDropdownStepperBar(
                            circuits = state.activeCircuits,
                            selectedCode = state.selectedCircuitCode,
                            favoriteColor = statusColors.favorite,
                            onSelectCode = viewModel::selectCircuit,
                            onOpenPicker = { showCircuitPickerDialog = true }
                        )
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
                                    use24h = state.use24h,
                                    zoneId = state.zoneId,
                                    sunriseMinutes = state.sunriseMinutes,
                                    sunsetMinutes = state.sunsetMinutes,
                                    enableElectricAnimations = state.enableElectricAnimations
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
                                        use24h = state.use24h,
                                        zoneId = state.zoneId,
                                        sunriseMinutes = state.sunriseMinutes,
                                        sunsetMinutes = state.sunsetMinutes,
                                        enableElectricAnimations = state.enableElectricAnimations
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCircuitPickerDialog) {
        CircuitSelectorDialog(
            title = "Seleccionar circuito",
            circuits = state.activeCircuits,
            selectedCode = state.selectedCircuitCode.orEmpty(),
            onSelect = { code ->
                viewModel.selectCircuit(code)
                showCircuitPickerDialog = false
            },
            onDismiss = { showCircuitPickerDialog = false }
        )
    }
}

@Composable
private fun CircuitDropdownStepperBar(
    circuits: List<Circuit>,
    selectedCode: String?,
    favoriteColor: androidx.compose.ui.graphics.Color,
    onSelectCode: (String) -> Unit,
    onOpenPicker: () -> Unit
) {
    val currentIndex = circuits.indexOfFirst { it.code.equals(selectedCode, ignoreCase = true) }
    val selectedCircuit = circuits.getOrNull(currentIndex) ?: circuits.firstOrNull()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = {
                    if (circuits.isNotEmpty()) {
                        val prev = if (currentIndex <= 0) circuits.lastIndex else currentIndex - 1
                        onSelectCode(circuits[prev].code)
                    }
                },
                enabled = circuits.size > 1,
                modifier = Modifier.testTag("calendar_prev_circuit")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Circuito anterior")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = circuits.isNotEmpty(), onClick = onOpenPicker)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("calendar_circuit_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCircuit?.displayTitle() ?: "Sin circuitos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (circuits.size > 1 && currentIndex >= 0) {
                        Text(
                            text = "Circuito ${currentIndex + 1} de ${circuits.size} • Toca para buscar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selectedCircuit?.isFavorite == true) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorito",
                        tint = favoriteColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Buscar circuito",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    if (circuits.isNotEmpty()) {
                        val next = if (currentIndex < 0 || currentIndex >= circuits.lastIndex) 0 else currentIndex + 1
                        onSelectCode(circuits[next].code)
                    }
                },
                enabled = circuits.size > 1,
                modifier = Modifier.testTag("calendar_next_circuit")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Siguiente circuito")
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
