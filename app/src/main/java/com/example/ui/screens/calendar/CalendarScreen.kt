package com.example.ui.screens.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.ui.components.CenteredScrollableChipRow
import com.example.ui.components.Continuous3DTickerTimeline
import com.example.ui.components.ScreenLoadingCard
import com.example.ui.components.StateNoticeCard
import com.example.ui.components.Timeline24hBar
import com.example.ui.components.TimelineIntervalsSummaryList
import com.example.ui.components.TimelineSegmentLegend
import com.example.ui.screens.circuits.CircuitSelectorDialog
import com.example.ui.theme.LocalBottomOverlayPadding
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val today = remember(state.nowMillis, state.zoneId) {
        java.time.Instant.ofEpochMilli(state.nowMillis).atZone(state.zoneId).toLocalDate()
    }
    val nowMillis = remember(state.selectedDate, today, state.nowMillis) {
        if (state.selectedDate == today) state.nowMillis else null
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
                bottom = 16.dp + bottomOverlayPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "calendar_title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Calendario y Línea de Tiempo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Explora horarios por día, semana, circuito o bloque rotativo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            stickyHeader(key = "calendar_date_bar") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            val isToday = state.selectedDate == today
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.selectDate(today) }
                                    .testTag("calendar_today_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
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
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.shiftDays(-1) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("calendar_prev_day")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                                    }
                                    AnimatedContent(
                                        targetState = state.selectedDate,
                                        transitionSpec = {
                                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                        },
                                        label = "calendar_date_title",
                                        modifier = Modifier.weight(1f)
                                    ) { targetDate ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = ScheduleEngine.formatDateEs(targetDate),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (targetDate == LocalDate.of(2026, 11, 19) ||
                                                targetDate == LocalDate.of(2026, 11, 20)
                                            ) {
                                                Text(
                                                    text = "Histórico de prueba",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = statusColors.favorite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.shiftDays(1) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("calendar_next_day")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    }
                }
            }

            item(key = "calendar_header_controls") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Modo de vista",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            CenteredScrollableChipRow(spacing = 6.dp) {
                                CalendarViewMode.entries.forEach { mode ->
                                    val modeIcon = when (mode) {
                                        CalendarViewMode.DAILY_COMPARATIVE -> Icons.AutoMirrored.Filled.ViewList
                                        CalendarViewMode.WEEKLY_7_DAYS -> Icons.Filled.DateRange
                                        CalendarViewMode.BY_CIRCUIT -> Icons.Filled.Timeline
                                        CalendarViewMode.GROUPS -> Icons.Filled.Groups
                                    }
                                    FilterChip(
                                        selected = state.viewMode == mode,
                                        onClick = { viewModel.selectViewMode(mode) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = modeIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("calendar_mode_${mode.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        if (state.viewMode != CalendarViewMode.GROUPS) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Filtrar circuitos",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val visibleFilters = CalendarCircuitFilter.entries.filter { filter ->
                                    val count = state.filterCounts[filter] ?: 0
                                    filter == CalendarCircuitFilter.ALL || state.selectedFilter == filter || count > 0
                                }
                                CenteredScrollableChipRow(spacing = 6.dp) {
                                    visibleFilters.forEach { filter ->
                                        val filterIcon = when (filter) {
                                            CalendarCircuitFilter.ALL -> Icons.Filled.Apps
                                            CalendarCircuitFilter.FAVORITES -> Icons.Filled.Star
                                            CalendarCircuitFilter.WITH_SERVICE -> Icons.Filled.Bolt
                                            CalendarCircuitFilter.OUTAGE -> Icons.Filled.PowerOff
                                            CalendarCircuitFilter.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
                                            CalendarCircuitFilter.FIXED -> Icons.Filled.LockClock
                                        }
                                        FilterChip(
                                            selected = state.selectedFilter == filter,
                                            onClick = { viewModel.selectFilter(filter) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = filterIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(filter.label, maxLines = 1, softWrap = false) },
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("calendar_filter_${filter.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        TimelineSegmentLegend()
                    }
                }
            }

            if (state.isLoading) {
                item(key = "calendar_loading") {
                    ScreenLoadingCard(
                        title = "Cargando calendario…",
                        subtitle = "Preparando líneas de tiempo y bloques de rotación.",
                        testTag = "calendar_loading_card"
                    )
                }
            } else {
                when (state.viewMode) {
                    CalendarViewMode.DAILY_COMPARATIVE -> {
                        if (state.circuitTimelines.isEmpty()) {
                            item(key = "calendar_empty_filter") {
                                if (state.activeCircuits.isNotEmpty()) {
                                    StateNoticeCard(
                                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                                        title = "Sin circuitos en este filtro",
                                        subtitle = "Ningún circuito coincide con el filtro seleccionado para esta fecha.",
                                        actionLabel = "Ver todos",
                                        onAction = { viewModel.selectFilter(CalendarCircuitFilter.ALL) },
                                        testTag = "calendar_empty_filtered_card"
                                    )
                                } else {
                                    StateNoticeCard(
                                        icon = Icons.Filled.EventBusy,
                                        title = "Sin circuitos activos",
                                        subtitle = "Activa o agrega circuitos en la pestaña Circuitos para ver sus horarios.",
                                        testTag = "calendar_empty_catalog_card"
                                    )
                                }
                            }
                        } else {
                            if (!state.hasAnyForecastOnSelectedDate) {
                                item(key = "no_forecast_banner") {
                                    NoForecastNoticeCard(date = state.selectedDate)
                                }
                            }
                            items(state.circuitTimelines, key = { it.circuit.code }) { row ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (row.circuit.isPrimaryHome) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    border = BorderStroke(
                                        width = if (row.circuit.isPrimaryHome) 1.6.dp else 1.dp,
                                        color = if (row.circuit.isPrimaryHome) statusColors.favorite else MaterialTheme.colorScheme.outlineVariant
                                    ),
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (row.circuit.isPrimaryHome) {
                                                    Icon(
                                                        Icons.Filled.Home,
                                                        contentDescription = "Mi vivienda",
                                                        tint = statusColors.favorite,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = row.circuit.displayTitle(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (row.circuit.isFavorite) {
                                                    Icon(
                                                        Icons.Filled.Star,
                                                        contentDescription = "Favorito",
                                                        tint = statusColors.favorite,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Detalles",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Timeline24hBar(
                                            date = state.selectedDate,
                                            segments = row.segments,
                                            barHeight = 28.dp,
                                            currentTimeMillis = nowMillis,
                                            windowStartMillis = state.windowStartMillis,
                                            windowEndMillis = state.windowEndMillis,
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
                    }

                    CalendarViewMode.BY_CIRCUIT -> {
                        val pickerCircuits = state.filteredCircuits.ifEmpty { state.activeCircuits }
                        item(key = "circuit_selector") {
                            CircuitDropdownStepperBar(
                                circuits = pickerCircuits,
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
                        if (selectedRow == null) {
                            item(key = "no_circuit_selected") {
                                StateNoticeCard(
                                    icon = Icons.Filled.EventBusy,
                                    title = "No hay circuito seleccionado",
                                    subtitle = "Agrega o activa un circuito para ver su línea de tiempo detallada."
                                )
                            }
                        } else {
                            item(key = "continuous_3d_wheel") {
                                val initialWheelMillis = remember(selectedRow.circuit.code) {
                                    if (state.selectedDate == today) {
                                        state.nowMillis
                                    } else {
                                        state.selectedDate.atTime(12, 0).atZone(state.zoneId).toInstant().toEpochMilli()
                                    }
                                }
                                Continuous3DTickerTimeline(
                                    circuitCode = selectedRow.circuit.code,
                                    snapshot = state.snapshot,
                                    nowMillis = state.nowMillis,
                                    use24h = state.use24h,
                                    zoneId = state.zoneId,
                                    initialFocusMillis = initialWheelMillis,
                                    targetDate = state.selectedDate,
                                    onFocusedDateChanged = { focusedDate ->
                                        if (focusedDate != state.selectedDate) {
                                            viewModel.selectDate(focusedDate)
                                        }
                                    }
                                )
                            }
                            item(key = "individual_timeline_card") {
                                AnimatedContent(
                                    targetState = state.selectedDate to selectedRow,
                                    transitionSpec = {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                    },
                                    label = "calendar_day_detail"
                                ) { (targetDate, dayRow) ->
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = if (state.centerTimelineOnNow && targetDate == today) {
                                                    "Ventana actual (±12h): ${dayRow.circuit.displayTitle()}"
                                                } else {
                                                    "Detalle del día: ${dayRow.circuit.displayTitle()}"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Timeline24hBar(
                                                date = targetDate,
                                                segments = dayRow.segments,
                                                barHeight = 40.dp,
                                                currentTimeMillis = if (targetDate == today) state.nowMillis else null,
                                                windowStartMillis = state.windowStartMillis,
                                                windowEndMillis = state.windowEndMillis,
                                                use24h = state.use24h,
                                                zoneId = state.zoneId,
                                                sunriseMinutes = state.sunriseMinutes,
                                                sunsetMinutes = state.sunsetMinutes,
                                                enableElectricAnimations = state.enableElectricAnimations,
                                                selectedSegment = state.selectedSegment,
                                                onSegmentTap = { seg ->
                                                    viewModel.selectSegment(dayRow.circuit.displayTitle(), seg)
                                                }
                                            )
                                            TimelineIntervalsSummaryList(
                                                segments = dayRow.segments,
                                                selectedSegment = state.selectedSegment,
                                                use24h = state.use24h,
                                                zoneId = state.zoneId,
                                                onSelect = { seg ->
                                                    viewModel.selectSegment(dayRow.circuit.displayTitle(), seg)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CalendarViewMode.WEEKLY_7_DAYS -> {
                        val pickerCircuits = state.filteredCircuits.ifEmpty { state.activeCircuits }
                        item(key = "weekly_circuit_picker") {
                            CircuitDropdownStepperBar(
                                circuits = pickerCircuits,
                                selectedCode = state.selectedCircuitCode,
                                favoriteColor = statusColors.favorite,
                                onSelectCode = viewModel::selectCircuit,
                                onOpenPicker = { showCircuitPickerDialog = true }
                            )
                        }
                        if (state.weeklyTimeline.isEmpty()) {
                            item(key = "weekly_empty") {
                                StateNoticeCard(
                                    icon = Icons.Filled.EventBusy,
                                    title = "Sin vista semanal disponible",
                                    subtitle = "Selecciona o activa un circuito para ver los próximos 7 días."
                                )
                            }
                        } else {
                            items(state.weeklyTimeline.entries.toList(), key = { it.key.toEpochDay() }) { (day, segments) ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (day == today) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    border = BorderStroke(
                                        width = if (day == today) 1.5.dp else 1.dp,
                                        color = if (day == today) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectDate(day) }
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
                                                text = ScheduleEngine.formatDateEs(day),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (day == today) {
                                                Text(
                                                    text = "Hoy",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Timeline24hBar(
                                            date = day,
                                            segments = segments,
                                            barHeight = 26.dp,
                                            currentTimeMillis = if (day == today) state.nowMillis else null,
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

                    CalendarViewMode.GROUPS -> {
                        if (state.groupTimelines.isEmpty()) {
                            item(key = "no_groups") {
                                StateNoticeCard(
                                    icon = Icons.Filled.Groups,
                                    title = "No hay grupos rotativos",
                                    subtitle = "Crea grupos en la pestaña Circuitos para visualizar sus turnos rotativos aquí."
                                )
                            }
                        } else {
                            items(state.groupTimelines, key = { it.group.id }) { row ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            windowStartMillis = state.windowStartMillis,
                                            windowEndMillis = state.windowEndMillis,
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                modifier = Modifier
                    .size(48.dp)
                    .testTag("calendar_prev_circuit")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Circuito anterior")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
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
                            style = MaterialTheme.typography.labelMedium,
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
                modifier = Modifier
                    .size(48.dp)
                    .testTag("calendar_next_circuit")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Siguiente circuito")
            }
        }
    }
}

@Composable
private fun NoForecastNoticeCard(date: LocalDate) {
    StateNoticeCard(
        icon = Icons.Filled.EventBusy,
        title = "Sin programación para el ${ScheduleEngine.formatShortDate(date)}",
        subtitle = "Los circuitos sin regla fija o grupo rotativo aparecen sin información. La aplicación no inventa horarios.",
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        centered = false,
        testTag = "calendar_no_forecast_notice"
    )
}
