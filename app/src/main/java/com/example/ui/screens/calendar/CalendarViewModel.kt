package com.example.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val hasAnyForecastOnSelectedDate: Boolean = false
)

class CalendarViewModel(
    private val repository: LuzIslaRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now(ScheduleEngine.HAVANA_ZONE))
    private val viewMode = MutableStateFlow(CalendarViewMode.DAILY_COMPARATIVE)
    private val favoritesOnly = MutableStateFlow(false)
    private val selectedCircuitCode = MutableStateFlow<String?>(null)
    private val selectedSegment = MutableStateFlow<Pair<String, TimelineSegment>?>(null)

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.snapshotFlow,
        selectedDate,
        viewMode,
        favoritesOnly,
        selectedCircuitCode,
        selectedSegment
    ) { values ->
        val snapshot = values[0] as ScheduleSnapshot
        val date = values[1] as LocalDate
        val mode = values[2] as CalendarViewMode
        val favOnly = values[3] as Boolean
        val chosenCode = values[4] as String?
        @Suppress("UNCHECKED_CAST")
        val pickedSeg = values[5] as Pair<String, TimelineSegment>?

        val activeCircuits = snapshot.circuits.filter { it.isActive }
        val effectiveCode = chosenCode?.takeIf { code -> activeCircuits.any { it.code == code } }
            ?: activeCircuits.firstOrNull { it.isPrimaryHome }?.code
            ?: activeCircuits.firstOrNull { it.isFavorite }?.code
            ?: activeCircuits.firstOrNull()?.code

        val visibleCircuits = activeCircuits.filter { !favOnly || it.isFavorite || it.isPrimaryHome }
        val circuitRows = visibleCircuits.map { circuit ->
            CircuitTimelineRow(
                circuit = circuit,
                segments = ScheduleEngine.getTimelineForDay(circuit.code, snapshot, date)
            )
        }

        val groupRows = snapshot.groups.filter { it.isActive }.map { group ->
            val members = activeCircuits.filter { it.groupId == group.id }.map { it.code }
            GroupTimelineRow(
                group = group,
                memberCodes = members,
                segments = ScheduleEngine.getGroupTimelineForDay(group, date)
            )
        }

        val weekly = if (effectiveCode != null) {
            ScheduleEngine.getTimelineForNextDays(effectiveCode, snapshot, date, 7)
        } else {
            emptyMap()
        }

        val hasData = circuitRows.any { row -> row.segments.any { it.state != ScheduledState.UNKNOWN } }

        CalendarUiState(
            selectedDate = date,
            viewMode = mode,
            favoritesOnly = favOnly,
            selectedCircuitCode = effectiveCode,
            selectedSegment = pickedSeg?.second,
            selectedSegmentOwner = pickedSeg?.first.orEmpty(),
            activeCircuits = activeCircuits,
            circuitTimelines = circuitRows,
            groupTimelines = groupRows,
            weeklyTimeline = weekly,
            hasAnyForecastOnSelectedDate = hasData
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

    class Factory(private val repository: LuzIslaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(repository) as T
        }
    }
}
