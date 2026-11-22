package com.example.ui.screens.circuits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.notifications.ScheduleNotificationManager
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class AdminTab(val label: String) {
    CIRCUITS("Circuitos"),
    GROUPS("Grupos"),
    FIXED("Fijos"),
    FORECASTS("Pronósticos"),
    EXCEPTIONS("Excepciones"),
    RELATIONS("Relaciones")
}

data class CircuitsAdminUiState(
    val activeTab: AdminTab = AdminTab.CIRCUITS,
    val searchQuery: String = "",
    val selectedGroupFilterId: String? = null,
    val favoritesOnly: Boolean = false,
    val showInactive: Boolean = true,
    val circuits: List<Circuit> = emptyList(),
    val allCircuits: List<Circuit> = emptyList(),
    val groups: List<ScheduleGroup> = emptyList(),
    val fixedRules: List<FixedScheduleRule> = emptyList(),
    val explicitForecasts: List<ExplicitForecast> = emptyList(),
    val manualExceptions: List<ManualException> = emptyList(),
    val relations: List<CircuitRelation> = emptyList(),
    val statusMessage: String? = null
)

class CircuitsViewModel(
    private val appContext: Context,
    private val repository: LuzIslaRepository
) : ViewModel() {

    private val activeTab = MutableStateFlow(AdminTab.CIRCUITS)
    private val searchQuery = MutableStateFlow("")
    private val selectedGroupFilterId = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val showInactive = MutableStateFlow(true)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CircuitsAdminUiState> = combine(
        repository.snapshotFlow,
        activeTab,
        searchQuery,
        selectedGroupFilterId,
        favoritesOnly,
        showInactive,
        statusMessage
    ) { values ->
        val snapshot = values[0] as ScheduleSnapshot
        val tab = values[1] as AdminTab
        val query = values[2] as String
        val groupId = values[3] as String?
        val favOnly = values[4] as Boolean
        val showInact = values[5] as Boolean
        val msg = values[6] as String?

        val filteredCircuits = snapshot.circuits
            .filter { c ->
                val matchesActive = showInact || c.isActive
                val matchesFav = !favOnly || c.isFavorite || c.isPrimaryHome
                val matchesGroup = groupId == null || c.groupId == groupId
                val matchesQuery = query.isBlank() ||
                    c.code.contains(query, ignoreCase = true) ||
                    c.customName.contains(query, ignoreCase = true) ||
                    c.comment.contains(query, ignoreCase = true)
                matchesActive && matchesFav && matchesGroup && matchesQuery
            }
            .sortedWith(compareBy<Circuit> { !it.isActive }.thenBy { it.displayOrder }.thenBy { it.code })

        CircuitsAdminUiState(
            activeTab = tab,
            searchQuery = query,
            selectedGroupFilterId = groupId,
            favoritesOnly = favOnly,
            showInactive = showInact,
            circuits = filteredCircuits,
            allCircuits = snapshot.circuits,
            groups = snapshot.groups,
            fixedRules = snapshot.fixedRules,
            explicitForecasts = snapshot.explicitForecasts.sortedByDescending { it.dateEpochDay },
            manualExceptions = snapshot.manualExceptions.sortedByDescending { it.dateEpochDay },
            relations = snapshot.relations,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CircuitsAdminUiState()
    )

    fun selectTab(tab: AdminTab) {
        activeTab.value = tab
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun filterByGroup(groupId: String?) {
        selectedGroupFilterId.value = groupId
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun toggleShowInactive() {
        showInactive.value = !showInactive.value
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun saveCircuit(
        code: String,
        customName: String,
        comment: String,
        groupId: String?,
        scheduleType: ScheduleType,
        isActive: Boolean,
        isFavorite: Boolean,
        isPrimaryHome: Boolean,
        isNew: Boolean
    ) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isEmpty()) {
            statusMessage.value = "El código del circuito es obligatorio."
            return
        }
        if (isNew && uiState.value.allCircuits.any { it.code.equals(cleanCode, ignoreCase = true) }) {
            statusMessage.value = "Ya existe un circuito con el código $cleanCode."
            return
        }
        val existing = uiState.value.allCircuits.firstOrNull { it.code.equals(cleanCode, ignoreCase = true) }
        val nextOrder = existing?.displayOrder ?: (uiState.value.allCircuits.maxOfOrNull { it.displayOrder }?.plus(1) ?: 0)
        viewModelScope.launch {
            repository.upsertCircuit(
                Circuit(
                    code = cleanCode,
                    customName = customName.trim(),
                    comment = comment.trim(),
                    groupId = groupId,
                    scheduleType = scheduleType,
                    isActive = isActive,
                    isFavorite = isFavorite || isPrimaryHome,
                    isPrimaryHome = isPrimaryHome,
                    displayOrder = nextOrder,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Circuito $cleanCode guardado."
        }
    }

    fun toggleActive(code: String) {
        viewModelScope.launch {
            repository.toggleCircuitActive(code)
            refreshSystemHooks()
        }
    }

    fun toggleFavorite(code: String) {
        viewModelScope.launch {
            repository.toggleFavorite(code)
            refreshSystemHooks()
        }
    }

    fun moveOrder(code: String, up: Boolean) {
        viewModelScope.launch {
            repository.moveCircuitOrder(code, up)
        }
    }

    fun deleteCircuit(code: String) {
        viewModelScope.launch {
            repository.deleteCircuit(code)
            refreshSystemHooks()
            statusMessage.value = "Circuito $code eliminado."
        }
    }

    fun saveGroup(
        id: String?,
        name: String,
        description: String,
        serviceHours: Int,
        serviceMinutes: Int,
        outageHours: Int,
        outageMinutes: Int,
        refDateStr: String,
        refTimeStr: String,
        phaseOffsetMinutes: Int
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            statusMessage.value = "El nombre del grupo es obligatorio."
            return
        }
        val svcTotal = (serviceHours * 60 + serviceMinutes).coerceAtLeast(15)
        val outTotal = (outageHours * 60 + outageMinutes).coerceAtLeast(15)
        val refMillis = parseDateTimeMillis(refDateStr, refTimeStr) ?: run {
            statusMessage.value = "Fecha u hora de referencia inválida (usa AAAA-MM-DD y HH:mm)."
            return
        }

        viewModelScope.launch {
            repository.upsertGroup(
                ScheduleGroup(
                    id = id ?: "grp_${UUID.randomUUID().toString().take(8)}",
                    name = cleanName,
                    description = description.trim(),
                    serviceDurationMinutes = svcTotal,
                    outageDurationMinutes = outTotal,
                    referenceEpochMillis = refMillis,
                    phaseOffsetMinutes = phaseOffsetMinutes,
                    isActive = true
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Grupo \"$cleanName\" guardado."
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            repository.deleteGroup(id)
            refreshSystemHooks()
            statusMessage.value = "Grupo eliminado."
        }
    }

    fun saveFixedRule(
        id: String?,
        circuitCode: String,
        daysMask: Int,
        startTimeStr: String,
        endTimeStr: String,
        validFromStr: String,
        validToStr: String,
        label: String
    ) {
        val startMin = parseTimeMinutes(startTimeStr)
        val endMin = parseTimeMinutes(endTimeStr)
        if (startMin == null || endMin == null) {
            statusMessage.value = "Formato de hora inválido. Usa HH:mm (ej. 09:00)."
            return
        }
        if (daysMask == 0) {
            statusMessage.value = "Selecciona al menos un día de la semana."
            return
        }
        val validFrom = validFromStr.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() ?: run {
                statusMessage.value = "Fecha de inicio inválida (usa AAAA-MM-DD)."
                return
            }
        }
        val validTo = validToStr.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() ?: run {
                statusMessage.value = "Fecha de fin inválida (usa AAAA-MM-DD)."
                return
            }
        }
        viewModelScope.launch {
            repository.upsertFixedRule(
                FixedScheduleRule(
                    id = id ?: "fix_${UUID.randomUUID().toString().take(8)}",
                    circuitCode = circuitCode.trim().uppercase(),
                    daysOfWeekMask = daysMask,
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    validFromEpochDay = validFrom,
                    validToEpochDay = validTo,
                    label = label.trim()
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Horario fijo guardado para $circuitCode."
        }
    }

    fun deleteFixedRule(id: String) {
        viewModelScope.launch {
            repository.deleteFixedRule(id)
            refreshSystemHooks()
        }
    }

    fun saveExplicitForecast(
        id: String?,
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        circuitsCsv: String,
        intervalKind: com.example.domain.model.IntervalKind,
        notes: String
    ) {
        val date = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull() ?: run {
            statusMessage.value = "Fecha inválida (usa AAAA-MM-DD)."
            return
        }
        val startMin = parseTimeMinutes(startTimeStr)
        val endMin = parseTimeMinutes(endTimeStr)
        if (startMin == null || endMin == null) {
            statusMessage.value = "Hora inválida (usa HH:mm)."
            return
        }
        val codes = circuitsCsv.split(",", " ").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (codes.isEmpty()) {
            statusMessage.value = "Indica al menos un código de circuito."
            return
        }
        viewModelScope.launch {
            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = id ?: "fc_${UUID.randomUUID().toString().take(8)}",
                    dateEpochDay = date.toEpochDay(),
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    circuitCodes = codes,
                    intervalKind = intervalKind,
                    notes = notes.trim(),
                    isHistoricalTest = false
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Pronóstico guardado para ${ScheduleEngine.formatShortDate(date)}."
        }
    }

    fun deleteExplicitForecast(id: String) {
        viewModelScope.launch {
            repository.deleteExplicitForecast(id)
            refreshSystemHooks()
        }
    }

    fun saveManualException(
        id: String?,
        circuitCode: String,
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hasService: Boolean,
        reason: String
    ) {
        val date = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull() ?: run {
            statusMessage.value = "Fecha inválida (usa AAAA-MM-DD)."
            return
        }
        val startMin = parseTimeMinutes(startTimeStr)
        val endMin = parseTimeMinutes(endTimeStr)
        if (startMin == null || endMin == null) {
            statusMessage.value = "Hora inválida (usa HH:mm)."
            return
        }
        viewModelScope.launch {
            repository.upsertManualException(
                ManualException(
                    id = id ?: "ex_${UUID.randomUUID().toString().take(8)}",
                    circuitCode = circuitCode.trim().uppercase(),
                    dateEpochDay = date.toEpochDay(),
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    hasService = hasService,
                    reason = reason.trim()
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Excepción guardada para $circuitCode."
        }
    }

    fun deleteManualException(id: String) {
        viewModelScope.launch {
            repository.deleteManualException(id)
            refreshSystemHooks()
        }
    }

    fun saveRelation(
        id: String?,
        sourceCode: String,
        targetCode: String,
        type: com.example.domain.model.RelationType,
        syncEnabled: Boolean,
        notes: String
    ) {
        val src = sourceCode.trim().uppercase()
        val tgt = targetCode.trim().uppercase()
        if (src.isEmpty() || tgt.isEmpty() || src == tgt) {
            statusMessage.value = "Selecciona dos circuitos distintos para la relación."
            return
        }
        viewModelScope.launch {
            repository.upsertRelation(
                CircuitRelation(
                    id = id ?: "rel_${UUID.randomUUID().toString().take(8)}",
                    sourceCircuitCode = src,
                    targetCircuitCode = tgt,
                    relationType = type,
                    syncRuleEnabled = syncEnabled,
                    notes = notes.trim()
                )
            )
            refreshSystemHooks()
            statusMessage.value = "Relación $src → $tgt guardada."
        }
    }

    fun deleteRelation(id: String) {
        viewModelScope.launch {
            repository.deleteRelation(id)
            refreshSystemHooks()
        }
    }

    private suspend fun refreshSystemHooks() {
        ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
        WidgetUpdateHelper.refreshAllWidgets(appContext)
    }

    private fun parseTimeMinutes(raw: String): Int? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h == 24 && m == 0) return 1440
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun parseDateTimeMillis(dateStr: String, timeStr: String): Long? {
        val date = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull() ?: return null
        val mins = parseTimeMinutes(timeStr) ?: return null
        val safeMins = mins.coerceIn(0, 1439)
        return date.atTime(LocalTime.of(safeMins / 60, safeMins % 60))
            .atZone(ScheduleEngine.HAVANA_ZONE)
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        fun formatMinute24h(minuteOfDay: Int): String {
            val clamped = minuteOfDay.coerceIn(0, 1440)
            if (clamped == 1440) return "24:00"
            return String.format("%02d:%02d", clamped / 60, clamped % 60)
        }

        fun formatEpochDateStr(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).atZone(ScheduleEngine.HAVANA_ZONE).toLocalDate().toString()

        fun formatEpochTime24Str(epochMillis: Long): String {
            val lt = Instant.ofEpochMilli(epochMillis).atZone(ScheduleEngine.HAVANA_ZONE).toLocalTime()
            return String.format("%02d:%02d", lt.hour, lt.minute)
        }
    }

    class Factory(
        private val context: Context,
        private val repository: LuzIslaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CircuitsViewModel(context.applicationContext, repository) as T
        }
    }
}
