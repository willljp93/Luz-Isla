package com.example.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.CircuitStatusEvaluation
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduledState
import com.example.domain.model.ThemeMode
import com.example.notifications.ScheduleNotificationManager
import com.example.sync.ShareHelper
import com.example.widget.LauncherIconManager
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

enum class HomeFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Mis favoritos"),
    WITH_SERVICE("Con servicio"),
    OUTAGE("Sin servicio"),
    UNKNOWN("Sin info")
}

data class HomeUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val lastUpdateMillis: Long = 0L,
    val searchQuery: String = "",
    val selectedFilter: HomeFilter = HomeFilter.ALL,
    val compactMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val use24h: Boolean = false,
    val zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    val sunriseMinutes: Int = 6 * 60 + 30,
    val sunsetMinutes: Int = 18 * 60 + 30,
    val isDaytime: Boolean = true,
    val enableElectricAnimations: Boolean = true,
    val dynamicLauncherIconEnabled: Boolean = false,
    val primaryHomeState: ScheduledState? = null,
    val snapshot: ScheduleSnapshot = ScheduleSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
    val evaluations: List<CircuitStatusEvaluation> = emptyList(),
    val totalServiceCount: Int = 0,
    val totalOutageCount: Int = 0,
    val totalUnknownCount: Int = 0,
    val selectedDetailCode: String? = null
) {
    val selectedEvaluation: CircuitStatusEvaluation?
        get() = selectedDetailCode?.let { code ->
            evaluations.firstOrNull { it.circuit.code.equals(code, ignoreCase = true) }
        }
}

class HomeViewModel(
    private val appContext: Context,
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(HomeFilter.ALL)
    private val selectedDetailCode = MutableStateFlow<String?>(null)
    private var lastSyncedIconState: Pair<Boolean, ScheduledState?>? = null

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        tickerFlow,
        repository.snapshotFlow,
        prefsRepo.preferencesFlow,
        searchQuery,
        selectedFilter,
        selectedDetailCode
    ) { values ->
        val nowMillis = values[0] as Long
        val snapshot = values[1] as ScheduleSnapshot
        val prefs = values[2] as UserPreferences
        val query = values[3] as String
        val filter = values[4] as HomeFilter
        val detailCode = values[5] as String?

        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val nowInstant = Instant.ofEpochMilli(nowMillis)
        val activeEvaluations = snapshot.circuits
            .filter { it.isActive }
            .map { circuit -> ScheduleEngine.evaluateCircuit(circuit, snapshot, nowInstant, zoneId) }

        val serviceCount = activeEvaluations.count { it.state == ScheduledState.SERVICE_SCHEDULED }
        val outageCount = activeEvaluations.count { it.state == ScheduledState.OUTAGE_SCHEDULED }
        val unknownCount = activeEvaluations.count { it.state == ScheduledState.UNKNOWN }

        val primaryHomeEval = activeEvaluations.firstOrNull { it.circuit.isPrimaryHome }
        val primaryState = primaryHomeEval?.state

        val iconKey = prefs.dynamicLauncherIconEnabled to primaryState
        if (lastSyncedIconState != iconKey) {
            lastSyncedIconState = iconKey
            LauncherIconManager.syncLauncherIcon(
                context = appContext,
                dynamicEnabled = prefs.dynamicLauncherIconEnabled,
                primaryHomeState = primaryState
            )
        }

        val filtered = activeEvaluations
            .filter { eval ->
                val matchesQuery = query.isBlank() ||
                    eval.circuit.code.contains(query, ignoreCase = true) ||
                    eval.circuit.customName.contains(query, ignoreCase = true) ||
                    eval.circuit.comment.contains(query, ignoreCase = true)

                val matchesFilter = when (filter) {
                    HomeFilter.ALL -> true
                    HomeFilter.FAVORITES -> eval.circuit.isFavorite || eval.circuit.isPrimaryHome
                    HomeFilter.WITH_SERVICE -> eval.state == ScheduledState.SERVICE_SCHEDULED
                    HomeFilter.OUTAGE -> eval.state == ScheduledState.OUTAGE_SCHEDULED
                    HomeFilter.UNKNOWN -> eval.state == ScheduledState.UNKNOWN
                }
                matchesQuery && matchesFilter
            }
            .sortedWith(
                compareByDescending<CircuitStatusEvaluation> { it.circuit.isPrimaryHome }
                    .thenByDescending { it.circuit.isFavorite }
                    .thenBy { it.circuit.displayOrder }
                    .thenBy { it.circuit.code }
            )

        val isDay = ScheduleEngine.isDaytime(
            nowMillis = nowMillis,
            sunriseMinutes = prefs.sunriseMinutes,
            sunsetMinutes = prefs.sunsetMinutes,
            zoneId = zoneId
        )

        HomeUiState(
            nowMillis = nowMillis,
            lastUpdateMillis = prefs.lastForecastUpdateEpochMillis,
            searchQuery = query,
            selectedFilter = filter,
            compactMode = prefs.compactHomeCards,
            themeMode = prefs.themeMode,
            use24h = prefs.use24HourFormat,
            zoneId = zoneId,
            sunriseMinutes = prefs.sunriseMinutes,
            sunsetMinutes = prefs.sunsetMinutes,
            isDaytime = isDay,
            enableElectricAnimations = prefs.enableElectricAnimations,
            dynamicLauncherIconEnabled = prefs.dynamicLauncherIconEnabled,
            primaryHomeState = primaryState,
            snapshot = snapshot,
            evaluations = filtered,
            totalServiceCount = serviceCount,
            totalOutageCount = outageCount,
            totalUnknownCount = unknownCount,
            selectedDetailCode = detailCode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: HomeFilter) {
        selectedFilter.value = filter
    }

    fun toggleCompactMode() {
        viewModelScope.launch {
            prefsRepo.setCompactHomeCards(!uiState.value.compactMode)
        }
    }

    fun openCircuitDetail(code: String) {
        selectedDetailCode.value = code.trim().uppercase()
    }

    fun closeCircuitDetail() {
        selectedDetailCode.value = null
    }

    fun toggleFavorite(code: String) {
        viewModelScope.launch {
            repository.toggleFavorite(code)
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
        }
    }

    fun setPrimaryHome(code: String) {
        viewModelScope.launch {
            repository.setPrimaryHome(code)
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
        }
    }

    fun shareSingleCircuit(eval: CircuitStatusEvaluation) {
        val st = uiState.value
        val msg = ShareHelper.buildSingleCircuitMessage(eval, st.use24h, st.zoneId)
        ShareHelper.shareText(appContext, "Compartir ${eval.circuit.displayTitle()}", msg)
    }

    fun shareSummaryText(mode: String) {
        val st = uiState.value
        val all = st.evaluations
        val (title, text) = when (mode) {
            "FAVORITES" -> {
                val favs = all.filter { it.circuit.isFavorite || it.circuit.isPrimaryHome }
                "Favoritos" to ShareHelper.buildListMessage("Mis circuitos favoritos", favs, st.use24h, st.zoneId)
            }
            "WITH_SERVICE" -> {
                val on = all.filter { it.state == ScheduledState.SERVICE_SCHEDULED }
                "Con servicio" to ShareHelper.buildListMessage("Circuitos con servicio programado", on, st.use24h, st.zoneId)
            }
            "UPCOMING" -> "Próximos horarios" to ShareHelper.buildUpcomingMessage(all, st.use24h, st.zoneId)
            else -> "Horarios de hoy" to ShareHelper.buildListMessage("Todos los circuitos de hoy", all, st.use24h, st.zoneId)
        }
        ShareHelper.shareText(appContext, "Compartir $title", text)
    }

    fun shareSummaryImage() {
        val st = uiState.value
        ShareHelper.shareSummaryImage(appContext, "Resumen de circuitos", st.evaluations, st.use24h, st.zoneId)
    }

    class Factory(
        private val context: Context,
        private val repository: LuzIslaRepository,
        private val prefsRepo: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(context.applicationContext, repository, prefsRepo) as T
        }
    }
}
