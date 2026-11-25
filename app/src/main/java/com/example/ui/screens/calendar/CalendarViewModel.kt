package com.example.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import com.example.domain.model.TimelineSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarViewMode(val label: String) {
    DAILY_COMPARATIVE("Circuitos 24h"),
    WEEKLY_7_DAYS("7 días"),
    BY_CIRCUIT("Detallado"),
    GROUPS("Grupos")
}

enum class CalendarCircuitFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Mis favoritos"),
    WITH_SERVICE("Con servicio"),
    OUTAGE("Sin servicio"),
    UNKNOWN("Sin info"),
    FIXED("Fijos")
}

data class CircuitTimelineRow(
    val circuit: Circuit,
    val segments: List<TimelineSegment>
)

data class GroupTimelineRow(
    val group: ScheduleGroup,
    val memberCodes: List<String>,
    val segments: List<TimelineSegment>
)

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(ScheduleEngine.HAVANA_ZONE),
    val nowMillis: Long = System.currentTimeMillis(),
    val windowStartMillis: Long? = null,
    val windowEndMillis: Long? = null,
    val centerTimelineOnNow: Boolean = false,
    val viewMode: CalendarViewMode = CalendarViewMode.DAILY_COMPARATIVE,
    val selectedFilter: CalendarCircuitFilter = CalendarCircuitFilter.ALL,
    val filterCounts: Map<CalendarCircuitFilter, Int> = emptyMap(),
    val selectedCircuitCode: String? = null,
    val selectedSegment: TimelineSegment? = null,
    val selectedSegmentOwner: String = "",
    val activeCircuits: List<Circuit> = emptyList(),
    val filteredCircuits: List<Circuit> = emptyList(),
    val circuitTimelines: List<CircuitTimelineRow> = emptyList(),
    val groupTimelines: List<GroupTimelineRow> = emptyList(),
    val weeklyTimeline: Map<LocalDate, List<TimelineSegment>> = emptyMap(),
    val hasAnyForecastOnSelectedDate: Boolean = false,
    val use24h: Boolean = false,
    val zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    val sunriseMinutes: Int = 6 * 60 + 30,
    val sunsetMinutes: Int = 18 * 60 + 30,
    val enableElectricAnimations: Boolean = true,
    val snapshot: ScheduleSnapshot = ScheduleSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
)

class CalendarViewModel(
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now(ScheduleEngine.HAVANA_ZONE))
    private val viewMode = MutableStateFlow(CalendarViewMode.DAILY_COMPARATIVE)
    private val selectedFilter = MutableStateFlow(CalendarCircuitFilter.ALL)
    private val selectedCircuitCode = MutableStateFlow<String?>(null)
    private val selectedSegment = MutableStateFlow<Pair<String, TimelineSegment>?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000L)
        }
    }

    private val controlsFlow = combine(
        selectedDate,
        viewMode,
        selectedFilter,
        selectedCircuitCode,
        selectedSegment
    ) { date, mode, filter, chosenCode, pickedSeg ->
        CalendarControls(date, mode, filter, chosenCode, pickedSeg)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.snapshotFlow,
        prefsRepo.preferencesFlow,
        controlsFlow,
        tickerFlow
    ) { snapshot, prefs, controls, nowMillis ->
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val date = controls.date
        val isToday = date == today
        val centeredWindow = isToday && prefs.centerTimelineOnNow
        val halfDayMillis = 12L * 60L * 60L * 1000L
        val windowStart = if (centeredWindow) nowMillis - halfDayMillis else null
        val windowEnd = if (centeredWindow) nowMillis + halfDayMillis else null

        val activeCircuits = snapshot.circuits.filter { it.isActive }

        // Evaluate full-day (and live `nowMillis` state when viewing Hoy) per circuit for consistent filtering
        val daySegmentsByCode = activeCircuits.associate { circuit ->
            circuit.code to ScheduleEngine.getTimelineForDay(circuit.code, snapshot, date, zoneId)
        }

        fun matchesFilter(circuit: Circuit, filter: CalendarCircuitFilter): Boolean {
            val daySegs = daySegmentsByCode[circuit.code].orEmpty()
            return when (filter) {
                CalendarCircuitFilter.ALL -> true
                CalendarCircuitFilter.FAVORITES -> circuit.isFavorite || circuit.isPrimaryHome
                CalendarCircuitFilter.FIXED -> circuit.scheduleType == ScheduleType.FIXED
                CalendarCircuitFilter.WITH_SERVICE -> {
                    if (isToday) {
                        daySegs.firstOrNull { nowMillis in it.startMillis until it.endMillis }?.state == ScheduledState.SERVICE_SCHEDULED
                    } else {
                        daySegs.any { it.state == ScheduledState.SERVICE_SCHEDULED }
                    }
                }
                CalendarCircuitFilter.OUTAGE -> {
                    if (isToday) {
                        daySegs.firstOrNull { nowMillis in it.startMillis until it.endMillis }?.state == ScheduledState.OUTAGE_SCHEDULED
                    } else {
                        daySegs.any { it.state == ScheduledState.OUTAGE_SCHEDULED }
                    }
                }
                CalendarCircuitFilter.UNKNOWN -> {
                    if (isToday) {
                        val st = daySegs.firstOrNull { nowMillis in it.startMillis until it.endMillis }?.state
                        st == null || st == ScheduledState.UNKNOWN
                    } else {
                        daySegs.all { it.state == ScheduledState.UNKNOWN }
                    }
                }
            }
        }

        val counts = CalendarCircuitFilter.entries.associateWith { filter ->
            activeCircuits.count { circuit -> matchesFilter(circuit, filter) }
        }

        val visibleCircuits = activeCircuits.filter { circuit ->
            matchesFilter(circuit, controls.filter)
        }

        val pickerCircuits = visibleCircuits.ifEmpty { activeCircuits }
        val effectiveCode = controls.chosenCode?.takeIf { code -> pickerCircuits.any { it.code == code } }
            ?: pickerCircuits.firstOrNull { it.isPrimaryHome }?.code
            ?: pickerCircuits.firstOrNull { it.isFavorite }?.code
            ?: pickerCircuits.firstOrNull()?.code

        val circuitRows = visibleCircuits.map { circuit ->
            val segs = if (windowStart != null && windowEnd != null) {
                ScheduleEngine.buildTimelineForRange(
                    circuitCode = circuit.code,
                    snapshot = snapshot,
                    rangeStartMillis = windowStart,
                    rangeEndMillis = windowEnd,
                    zoneId = zoneId
                )
            } else {
                daySegmentsByCode[circuit.code].orEmpty()
            }
            CircuitTimelineRow(circuit = circuit, segments = segs)
        }

        val groupRows = snapshot.groups.filter { it.isActive }.map { group ->
            val members = activeCircuits.filter { it.groupId == group.id }.map { it.code }
            val segs = if (windowStart != null && windowEnd != null) {
                ScheduleEngine.getGroupTimelineForRange(group, windowStart, windowEnd)
            } else {
                ScheduleEngine.getGroupTimelineForDay(group, date, zoneId)
            }
            GroupTimelineRow(
                group = group,
                memberCodes = members,
                segments = segs
            )
        }

        val weekly = if (effectiveCode != null) {
            ScheduleEngine.getTimelineForNextDays(effectiveCode, snapshot, date, 7, zoneId)
        } else {
            emptyMap()
        }

        val hasData = circuitRows.any { row -> row.segments.any { it.state != ScheduledState.UNKNOWN } }

        CalendarUiState(
            selectedDate = date,
            nowMillis = nowMillis,
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            centerTimelineOnNow = centeredWindow,
            viewMode = controls.mode,
            selectedFilter = controls.filter,
            filterCounts = counts,
            selectedCircuitCode = effectiveCode,
            selectedSegment = controls.pickedSeg?.second,
            selectedSegmentOwner = controls.pickedSeg?.first.orEmpty(),
            activeCircuits = activeCircuits,
            filteredCircuits = visibleCircuits,
            circuitTimelines = circuitRows,
            groupTimelines = groupRows,
            weeklyTimeline = weekly,
            hasAnyForecastOnSelectedDate = hasData,
            use24h = prefs.use24HourFormat,
            zoneId = zoneId,
            sunriseMinutes = prefs.sunriseMinutes,
            sunsetMinutes = prefs.sunsetMinutes,
            enableElectricAnimations = prefs.enableElectricAnimations,
            snapshot = snapshot
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        selectedSegment.value = null
    }

    fun shiftDays(delta: Long) {
        selectedDate.value = selectedDate.value.plusDays(delta)
        selectedSegment.value = null
    }

    fun selectViewMode(mode: CalendarViewMode) {
        viewMode.value = mode
        selectedSegment.value = null
    }

    fun selectFilter(filter: CalendarCircuitFilter) {
        selectedFilter.value = filter
        selectedSegment.value = null
    }

    fun selectCircuit(code: String) {
        selectedCircuitCode.value = code
        selectedSegment.value = null
    }

    fun selectSegment(owner: String, segment: TimelineSegment) {
        selectedSegment.value = owner to segment
    }

    private data class CalendarControls(
        val date: LocalDate,
        val mode: CalendarViewMode,
        val filter: CalendarCircuitFilter,
        val chosenCode: String?,
        val pickedSeg: Pair<String, TimelineSegment>?
    )

    class Factory(
        private val repository: LuzIslaRepository,
        private val prefsRepo: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(repository, prefsRepo) as T
        }
    }
}
