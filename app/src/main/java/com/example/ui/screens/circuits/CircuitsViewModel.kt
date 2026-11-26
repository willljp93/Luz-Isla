package com.example.ui.screens.circuits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.RotationCycle
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
import java.time.ZoneId
import java.util.UUID

enum class CircuitsPrimaryTab(val label: String) {
    CIRCUIT_EDITOR("Edición"),
    PROGRAMMING("Programación")
}

enum class ProgrammingSubTab(val label: String) {
    CYCLES("Ciclos"),
    GROUPS("Grupos"),
    FIXED("Fijos"),
    EXCEPTIONS("Excepciones"),
    RELATIONS("Relaciones")
}

data class CircuitsAdminUiState(
    val primaryTab: CircuitsPrimaryTab = CircuitsPrimaryTab.CIRCUIT_EDITOR,
    val programmingSubTab: ProgrammingSubTab = ProgrammingSubTab.CYCLES,
    val searchQuery: String = "",
    val selectedGroupFilterId: String? = null,
    val fixedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val showInactive: Boolean = true,
    val favoritesCount: Int = 0,
    val inactiveCount: Int = 0,
    val fixedCount: Int = 0,
    val groupMemberCounts: Map<String, Int> = emptyMap(),
    val circuits: List<Circuit> = emptyList(),
    val allCircuits: List<Circuit> = emptyList(),
    val cycles: List<RotationCycle> = emptyList(),
    val groups: List<ScheduleGroup> = emptyList(),
    val fixedRules: List<FixedScheduleRule> = emptyList(),
    val manualExceptions: List<ManualException> = emptyList(),
    val relations: List<CircuitRelation> = emptyList(),
    val use24h: Boolean = false,
    val zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    val statusMessage: String? = null
)

class CircuitsViewModel(
    private val appContext: Context,
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val primaryTab = MutableStateFlow(CircuitsPrimaryTab.CIRCUIT_EDITOR)
    private val programmingSubTab = MutableStateFlow(ProgrammingSubTab.CYCLES)
    private val searchQuery = MutableStateFlow("")
    private val selectedGroupFilterId = MutableStateFlow<String?>(null)
    private val fixedOnly = MutableStateFlow(false)
    private val favoritesOnly = MutableStateFlow(false)
    private val showInactive = MutableStateFlow(true)
    private val statusMessage = MutableStateFlow<String?>(null)

    private val selectionFilterFlow = combine(
        selectedGroupFilterId,
        fixedOnly,
        favoritesOnly
    ) { grpId, fixOnly, fav ->
        Triple(grpId, fixOnly, fav)
    }

    private val filterControlsFlow = combine(
        primaryTab,
        programmingSubTab,
        searchQuery,
        selectionFilterFlow
    ) { pTab, subTab, query, sel ->
        FilterControls(pTab, subTab, query, sel.first, sel.second, sel.third)
    }

    val uiState: StateFlow<CircuitsAdminUiState> = combine(
        repository.snapshotFlow,
        prefsRepo.preferencesFlow,
        filterControlsFlow,
        showInactive,
        statusMessage
    ) { snapshot, prefs, controls, showInact, msg ->
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val filteredCircuits = snapshot.circuits
            .filter { c ->
                val matchesActive = showInact || c.isActive
                val matchesFav = !controls.favoritesOnly || c.isFavorite || c.isPrimaryHome
                val matchesFixed = !controls.fixedOnly || c.scheduleType == ScheduleType.FIXED
                val matchesGroup = controls.selectedGroupFilterId == null || c.groupId == controls.selectedGroupFilterId
                val matchesQuery = controls.searchQuery.isBlank() ||
                    c.code.contains(controls.searchQuery, ignoreCase = true) ||
                    c.customName.contains(controls.searchQuery, ignoreCase = true) ||
                    c.comment.contains(controls.searchQuery, ignoreCase = true)
                matchesActive && matchesFav && matchesFixed && matchesGroup && matchesQuery
            }
            .sortedWith(compareBy<Circuit> { !it.isActive }.thenBy { it.displayOrder }.thenBy { it.code })

        val baseVisibleCircuits = snapshot.circuits.filter { showInact || it.isActive }
        val favoritesCount = baseVisibleCircuits.count { it.isFavorite || it.isPrimaryHome }
        val inactiveCount = snapshot.circuits.count { !it.isActive }
        val fixedCount = baseVisibleCircuits.count { it.scheduleType == ScheduleType.FIXED }
        val groupMemberCounts = snapshot.groups.associate { grp ->
            grp.id to baseVisibleCircuits.count { it.groupId == grp.id }
        }

        CircuitsAdminUiState(
            primaryTab = controls.primaryTab,
            programmingSubTab = controls.programmingSubTab,
            searchQuery = controls.searchQuery,
            selectedGroupFilterId = controls.selectedGroupFilterId,
            fixedOnly = controls.fixedOnly,
            favoritesOnly = controls.favoritesOnly,
            showInactive = showInact,
            favoritesCount = favoritesCount,
            inactiveCount = inactiveCount,
            fixedCount = fixedCount,
            groupMemberCounts = groupMemberCounts,
            circuits = filteredCircuits,
            allCircuits = snapshot.circuits,
            cycles = snapshot.cycles,
            groups = snapshot.groups,
            fixedRules = snapshot.fixedRules,
            manualExceptions = snapshot.manualExceptions.sortedByDescending { it.dateEpochDay },
            relations = snapshot.relations,
            use24h = prefs.use24HourFormat,
            zoneId = zoneId,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CircuitsAdminUiState()
    )

    fun selectPrimaryTab(tab: CircuitsPrimaryTab) {
        primaryTab.value = tab
    }

    fun selectProgrammingSubTab(subTab: ProgrammingSubTab) {
        programmingSubTab.value = subTab
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun filterByGroup(groupId: String?) {
        selectedGroupFilterId.value = groupId
        fixedOnly.value = false
    }

    fun toggleFixedOnly() {
        val next = !fixedOnly.value
        fixedOnly.value = next
        if (next) {
            selectedGroupFilterId.value = null
        }
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

    fun saveCycle(
        id: String?,
        name: String,
        description: String,
        serviceHours: Int,
        serviceMinutes: Int,
        outageHours: Int,
        outageMinutes: Int,
        refDateStr: String,
        refTimeStr: String,
        orderedGroupIds: List<String>,
        isActive: Boolean
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            statusMessage.value = "El nombre del ciclo es obligatorio."
            return
        }
        val svcTotal = (serviceHours * 60 + serviceMinutes).coerceAtLeast(15)
        val outTotal = (outageHours * 60 + outageMinutes).coerceAtLeast(15)
        val zone = uiState.value.zoneId
        val refMillis = parseDateTimeMillis(refDateStr, refTimeStr, zone) ?: run {
            statusMessage.value = "Fecha u hora de referencia inválida (usa AAAA-MM-DD y HH:mm)."
            return
        }

        val cycle = RotationCycle(
            id = id ?: "cyc_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            description = description.trim(),
            serviceDurationMinutes = svcTotal,
            outageDurationMinutes = outTotal,
            referenceEpochMillis = refMillis,
            orderedGroupIds = orderedGroupIds,
            isActive = isActive
        )

        viewModelScope.launch {
            repository.upsertCycle(cycle)
            refreshSystemHooks()
            val extraNote = if (orderedGroupIds.size > cycle.maxGroupsSupported) {
                " (afectación ajustada a ${cycle.effectiveOutageMinutes / 60}h para rotar ${orderedGroupIds.size} grupos)"
            } else {
                ""
            }
            statusMessage.value = "Ciclo \"$cleanName\" guardado y aplicado a ${orderedGroupIds.size} grupo(s)$extraNote."
        }
    }

    fun deleteCycle(id: String) {
        viewModelScope.launch {
            repository.deleteCycle(id)
            refreshSystemHooks()
            statusMessage.value = "Ciclo eliminado."
        }
    }

    fun saveGroup(
        id: String?,
        name: String,
        description: String,
        selectedCircuitCodes: Set<String>
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            statusMessage.value = "El nombre del grupo es obligatorio."
            return
        }
        val existing = id?.let { gid -> uiState.value.groups.firstOrNull { it.id == gid } }
        val groupId = id ?: "grp_${UUID.randomUUID().toString().take(8)}"
        val defaultRef = LocalDate.now(uiState.value.zoneId)
            .atStartOfDay(uiState.value.zoneId)
            .toInstant()
            .toEpochMilli()

        val group = existing?.copy(
            name = cleanName,
            description = description.trim()
        ) ?: ScheduleGroup(
            id = groupId,
            name = cleanName,
            description = description.trim(),
            serviceDurationMinutes = 240,
            outageDurationMinutes = 720,
            referenceEpochMillis = defaultRef,
            phaseOffsetMinutes = 0,
            isActive = true
        )

        viewModelScope.launch {
            repository.upsertGroup(group)
            repository.updateGroupMembers(group.id, selectedCircuitCodes)
            refreshSystemHooks()
            statusMessage.value = "Grupo \"$cleanName\" guardado (${selectedCircuitCodes.size} circuito(s))."
        }
    }

    fun updateGroupMembers(groupId: String, selectedCodes: Set<String>) {
        viewModelScope.launch {
            repository.updateGroupMembers(groupId, selectedCodes)
            refreshSystemHooks()
            statusMessage.value = "Circuitos del grupo actualizados (${selectedCodes.size})."
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
        if (circuitCode.isBlank()) {
            statusMessage.value = "Selecciona un circuito para el horario fijo."
            return
        }
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

    fun saveManualException(
        id: String?,
        circuitCode: String,
        dateStr: String,
        endDateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hasService: Boolean,
        reason: String
    ) {
        val date = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull() ?: run {
            statusMessage.value = "Fecha inicial inválida (usa AAAA-MM-DD)."
            return
        }
        val parsedEnd = endDateStr.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull() ?: run {
                statusMessage.value = "Fecha final inválida (usa AAAA-MM-DD)."
                return
            }
        }
        if (parsedEnd != null && parsedEnd.isBefore(date)) {
            statusMessage.value = "La fecha Hasta no puede ser anterior a Desde."
            return
        }
        val startMin = parseTimeMinutes(startTimeStr)
        val endMin = parseTimeMinutes(endTimeStr)
        if (startMin == null || endMin == null) {
            statusMessage.value = "Hora inválida (usa HH:mm)."
            return
        }
        val storedEndEpoch = parsedEnd?.takeIf { it.isAfter(date) }?.toEpochDay()
        viewModelScope.launch {
            repository.upsertManualException(
                ManualException(
                    id = id ?: "ex_${UUID.randomUUID().toString().take(8)}",
                    circuitCode = circuitCode.trim().uppercase(),
                    dateEpochDay = date.toEpochDay(),
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    hasService = hasService,
                    reason = reason.trim(),
                    endDateEpochDay = storedEndEpoch
                )
            )
            refreshSystemHooks()
            statusMessage.value = if (storedEndEpoch != null) {
                "Excepción multi-día guardada para $circuitCode."
            } else {
                "Excepción guardada para $circuitCode."
            }
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
        type: RelationType,
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

    private fun parseDateTimeMillis(dateStr: String, timeStr: String, zoneId: ZoneId): Long? {
        val date = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull() ?: return null
        val mins = parseTimeMinutes(timeStr) ?: return null
        val safeMins = mins.coerceIn(0, 1439)
        return date.atTime(LocalTime.of(safeMins / 60, safeMins % 60))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private data class FilterControls(
        val primaryTab: CircuitsPrimaryTab,
        val programmingSubTab: ProgrammingSubTab,
        val searchQuery: String,
        val selectedGroupFilterId: String?,
        val fixedOnly: Boolean,
        val favoritesOnly: Boolean
    )

    companion object {
        fun formatMinute24h(minuteOfDay: Int): String {
            val clamped = minuteOfDay.coerceIn(0, 1440)
            if (clamped == 1440) return "24:00"
            return String.format("%02d:%02d", clamped / 60, clamped % 60)
        }

        fun formatEpochDateStr(epochMillis: Long, zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE): String =
            Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toString()

        fun formatEpochTime24Str(epochMillis: Long, zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE): String {
            val lt = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
            return String.format("%02d:%02d", lt.hour, lt.minute)
        }
    }

    class Factory(
        private val context: Context,
        private val repository: LuzIslaRepository,
        private val prefsRepo: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CircuitsViewModel(context.applicationContext, repository, prefsRepo) as T
        }
    }
}
