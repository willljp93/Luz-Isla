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
import com.example.domain.model.ScheduledState
import com.example.domain.model.TimelineSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarViewMode(val label: String) {
    DAILY_COMPARATIVE("Línea 24h"),
    WEEKLY_7_DAYS("7 días"),
    BY_CIRCUIT("Por circuito"),
    GROUPS("Grupos")
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
    val viewMode: CalendarViewMode = CalendarViewMode.DAILY_COMPARATIVE,
    val favoritesOnly: Boolean = false,
    val selectedCircuitCode: String? = null,
    val selectedSegment: TimelineSegment? = null,
    val selectedSegmentOwner: String = "",
    val activeCircuits: List<Circuit> = emptyList(),
    val circuitTimelines: List<CircuitTimelineRow> = emptyList(),
    val groupTimelines: List<GroupTimelineRow> = emptyList(),
    val weeklyTimeline: Map<LocalDate, List<TimelineSegment>> = emptyMap(),
    val hasAnyForecastOnSelectedDate: Boolean = false,
    val use24h: Boolean = false,
    val zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    val snapshot: ScheduleSnapshot = ScheduleSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
)

class CalendarViewModel(
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now(ScheduleEngine.HAVANA_ZONE))
    private val viewMode = MutableStateFlow(CalendarViewMode.DAILY_COMPARATIVE)
    private val favoritesOnly = MutableStateFlow(false)
    private val selectedCircuitCode = MutableStateFlow<String?>(null)
    private val selectedSegment = MutableStateFlow<Pair<String, TimelineSegment>?>(null)

    private val controlsFlow = combine(
        selectedDate,
        viewMode,
        favoritesOnly,
        selectedCircuitCode,
        selectedSegment
    ) { date, mode, favOnly, chosenCode, pickedSeg ->
        CalendarControls(date, mode, favOnly, chosenCode, pickedSeg)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.snapshotFlow,
        prefsRepo.preferencesFlow,
        controlsFlow
    ) { snapshot, prefs, controls ->
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val date = controls.date
        val activeCircuits = snapshot.circuits.filter { it.isActive }
        val effectiveCode = controls.chosenCode?.takeIf { code -> activeCircuits.any { it.code == code } }
            ?: activeCircuits.firstOrNull { it.isPrimaryHome }?.code
            ?: activeCircuits.firstOrNull { it.isFavorite }?.code
            ?: activeCircuits.firstOrNull()?.code

        val visibleCircuits = activeCircuits.filter { !controls.favOnly || it.isFavorite || it.isPrimaryHome }
        val circuitRows = visibleCircuits.map { circuit ->
            CircuitTimelineRow(
                circuit = circuit,
                segments = ScheduleEngine.getTimelineForDay(circuit.code, snapshot, date, zoneId)
            )
        }

        val groupRows = snapshot.groups.filter { it.isActive }.map { group ->
            val members = activeCircuits.filter { it.groupId == group.id }.map { it.code }
            GroupTimelineRow(
                group = group,
                memberCodes = members,
                segments = ScheduleEngine.getGroupTimelineForDay(group, date, zoneId)
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
            viewMode = controls.mode,
            favoritesOnly = controls.favOnly,
            selectedCircuitCode = effectiveCode,
            selectedSegment = controls.pickedSeg?.second,
            selectedSegmentOwner = controls.pickedSeg?.first.orEmpty(),
            activeCircuits = activeCircuits,
            circuitTimelines = circuitRows,
            groupTimelines = groupRows,
            weeklyTimeline = weekly,
            hasAnyForecastOnSelectedDate = hasData,
            use24h = prefs.use24HourFormat,
            zoneId = zoneId,
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

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
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
        val favOnly: Boolean,
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
