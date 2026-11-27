package com.example.ui.screens.circuits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleType
import com.example.ui.components.CenteredScrollableChipRow
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.ScreenLoadingCard
import com.example.ui.components.StateNoticeCard
import com.example.ui.theme.LocalBottomOverlayPadding
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CircuitsScreen(
    viewModel: CircuitsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editingCircuit by remember { mutableStateOf<Pair<Boolean, Circuit?>?>(null) }
    var editingCycle by remember { mutableStateOf<Pair<Boolean, RotationCycle?>?>(null) }
    var editingGroup by remember { mutableStateOf<Pair<Boolean, ScheduleGroup?>?>(null) }
    var pickingMembersForGroup by remember { mutableStateOf<ScheduleGroup?>(null) }
    var editingFixed by remember { mutableStateOf<Pair<Boolean, FixedScheduleRule?>?>(null) }
    var editingException by remember { mutableStateOf<Pair<Boolean, ManualException?>?>(null) }
    var editingRelation by remember { mutableStateOf<Pair<Boolean, CircuitRelation?>?>(null) }
    var pendingDeleteCircuit by remember { mutableStateOf<Circuit?>(null) }

    val bottomOverlayPadding = LocalBottomOverlayPadding.current

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomOverlayPadding)
            )
        },
        floatingActionButton = {
            val fabLabel = when (state.primaryTab) {
                CircuitsPrimaryTab.CIRCUIT_EDITOR -> "Agregar circuito"
                CircuitsPrimaryTab.PROGRAMMING -> when (state.programmingSubTab) {
                    ProgrammingSubTab.CYCLES -> "Agregar ciclo rotativo"
                    ProgrammingSubTab.GROUPS -> "Agregar grupo"
                    ProgrammingSubTab.FIXED -> "Agregar horario fijo"
                    ProgrammingSubTab.EXCEPTIONS -> "Agregar excepción"
                    ProgrammingSubTab.RELATIONS -> "Agregar relación"
                }
            }
            FloatingActionButton(
                onClick = {
                    when (state.primaryTab) {
                        CircuitsPrimaryTab.CIRCUIT_EDITOR -> editingCircuit = true to null
                        CircuitsPrimaryTab.PROGRAMMING -> when (state.programmingSubTab) {
                            ProgrammingSubTab.CYCLES -> editingCycle = true to null
                            ProgrammingSubTab.GROUPS -> editingGroup = true to null
                            ProgrammingSubTab.FIXED -> editingFixed = true to null
                            ProgrammingSubTab.EXCEPTIONS -> editingException = true to null
                            ProgrammingSubTab.RELATIONS -> editingRelation = true to null
                        }
                    }
                },
                modifier = Modifier
                    .padding(bottom = bottomOverlayPadding)
                    .testTag("admin_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = fabLabel)
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp + bottomOverlayPadding
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "admin_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Circuitos y Programación",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Administra circuitos, ciclos rotativos, reglas fijas y excepciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PrimaryTabRow(
                                selectedTabIndex = state.primaryTab.ordinal,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                                divider = {},
                                indicator = {
                                    TabRowDefaults.PrimaryIndicator(
                                        modifier = Modifier
                                            .tabIndicatorOffset(state.primaryTab.ordinal, matchContentSize = false)
                                            .padding(horizontal = 12.dp),
                                        width = Dp.Unspecified,
                                        height = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircuitsPrimaryTab.entries.forEach { tab ->
                                    val isSelected = state.primaryTab == tab
                                    val tabCount = when (tab) {
                                        CircuitsPrimaryTab.CIRCUIT_EDITOR -> state.allCircuits.size
                                        CircuitsPrimaryTab.PROGRAMMING ->
                                            state.cycles.size + state.groups.size + state.fixedRules.size +
                                                state.manualExceptions.size + state.relations.size
                                    }
                                    val tabIcon = when (tab) {
                                        CircuitsPrimaryTab.CIRCUIT_EDITOR -> Icons.Filled.Tune
                                        CircuitsPrimaryTab.PROGRAMMING -> Icons.Filled.CalendarMonth
                                    }
                                    Tab(
                                        selected = isSelected,
                                        onClick = { viewModel.selectPrimaryTab(tab) },
                                        selectedContentColor = MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .semantics { selected = isSelected }
                                            .testTag("circuits_primary_tab_${tab.name.lowercase()}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = tabIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "${tab.label} ($tabCount)",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.primaryTab == CircuitsPrimaryTab.PROGRAMMING) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Categoría de programación",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    CenteredScrollableChipRow {
                                        ProgrammingSubTab.entries.forEach { subTab ->
                                            val subIcon = when (subTab) {
                                                ProgrammingSubTab.CYCLES -> Icons.Filled.Autorenew
                                                ProgrammingSubTab.GROUPS -> Icons.Filled.Groups
                                                ProgrammingSubTab.FIXED -> Icons.Filled.LockClock
                                                ProgrammingSubTab.EXCEPTIONS -> Icons.AutoMirrored.Filled.EventNote
                                                ProgrammingSubTab.RELATIONS -> Icons.Filled.Link
                                            }
                                            val subCount = when (subTab) {
                                                ProgrammingSubTab.CYCLES -> state.cycles.size
                                                ProgrammingSubTab.GROUPS -> state.groups.size
                                                ProgrammingSubTab.FIXED -> state.fixedRules.size
                                                ProgrammingSubTab.EXCEPTIONS -> state.manualExceptions.size
                                                ProgrammingSubTab.RELATIONS -> state.relations.size
                                            }
                                            FilterChip(
                                                selected = state.programmingSubTab == subTab,
                                                onClick = { viewModel.selectProgrammingSubTab(subTab) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = subIcon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = "${subTab.label} ($subCount)",
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                },
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .testTag("admin_tab_${subTab.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    item(key = "circuits_loading") {
                        ScreenLoadingCard(
                            title = "Cargando circuitos y reglas…",
                            subtitle = "Preparando programación y relaciones locales.",
                            modifier = Modifier.testTag("circuits_loading_card")
                        )
                    }
                } else when (state.primaryTab) {
                    CircuitsPrimaryTab.CIRCUIT_EDITOR -> {
                        item(key = "circuit_filters") {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactSearchBar(
                                        query = state.searchQuery,
                                        onQueryChange = viewModel::updateSearch,
                                        placeholder = "Buscar código, nombre o comentario…",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("circuits_search_input")
                                    )
                                    Text(
                                        text = "Filtrar listado (${state.circuits.size} de ${state.allCircuits.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    CenteredScrollableChipRow(spacing = 6.dp) {
                                        if (state.favoritesOnly || state.favoritesCount > 0) {
                                            FilterChip(
                                                selected = state.favoritesOnly,
                                                onClick = viewModel::toggleFavoritesOnly,
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text("Favoritos", maxLines = 1, softWrap = false) }
                                            )
                                        }
                                        if (state.inactiveCount > 0) {
                                            FilterChip(
                                                selected = state.showInactive,
                                                onClick = viewModel::toggleShowInactive,
                                                leadingIcon = {
                                                    Icon(
                                                        if (state.showInactive) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                label = { Text("Mostrar inactivos", maxLines = 1, softWrap = false) }
                                            )
                                        }
                                        FilterChip(
                                            selected = state.selectedGroupFilterId == null && !state.fixedOnly,
                                            onClick = { viewModel.filterByGroup(null) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            label = { Text("Todos", maxLines = 1, softWrap = false) }
                                        )
                                        if (state.fixedOnly || state.fixedCount > 0) {
                                            FilterChip(
                                                selected = state.fixedOnly,
                                                onClick = viewModel::toggleFixedOnly,
                                                leadingIcon = {
                                                    Icon(Icons.Filled.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                label = { Text("Fijos", maxLines = 1, softWrap = false) },
                                                modifier = Modifier.testTag("circuits_filter_fixed")
                                            )
                                        }
                                        state.groups.forEach { grp ->
                                            val memberCount = state.groupMemberCounts[grp.id] ?: 0
                                            val isSelected = !state.fixedOnly && state.selectedGroupFilterId == grp.id
                                            if (isSelected || memberCount > 0) {
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { viewModel.filterByGroup(grp.id) },
                                                    leadingIcon = {
                                                        Icon(Icons.Filled.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    },
                                                    label = { Text(grp.name, maxLines = 1, softWrap = false) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.circuits.isEmpty()) {
                            item(key = "circuits_empty") {
                                if (state.allCircuits.isEmpty()) {
                                    StateNoticeCard(
                                        icon = Icons.Filled.Bolt,
                                        title = "No hay circuitos registrados",
                                        subtitle = "Agrega tu primer circuito eléctrico o sincroniza una configuración desde Ajustes.",
                                        actionLabel = "Agregar circuito",
                                        onAction = { editingCircuit = true to null },
                                        modifier = Modifier.testTag("circuits_empty_catalog_card")
                                    )
                                } else {
                                    StateNoticeCard(
                                        icon = Icons.Filled.SearchOff,
                                        title = "Sin circuitos para mostrar",
                                        subtitle = "Ningún circuito coincide con la búsqueda o los filtros actuales.",
                                        actionLabel = "Restablecer filtros",
                                        onAction = {
                                            viewModel.updateSearch("")
                                            viewModel.filterByGroup(null)
                                            if (state.favoritesOnly) viewModel.toggleFavoritesOnly()
                                            if (state.showInactive) viewModel.toggleShowInactive()
                                        },
                                        modifier = Modifier.testTag("circuits_empty_filtered_card")
                                    )
                                }
                            }
                        } else {
                            items(state.circuits, key = { it.code }) { circuit ->
                                val grpName = state.groups.firstOrNull { it.id == circuit.groupId }?.name
                                ResponsiveCircuitAdminCard(
                                    circuit = circuit,
                                    groupName = grpName,
                                    favoriteColor = statusColors.favorite,
                                    onFavoriteColor = statusColors.onFavoriteContainer,
                                    onMoveUp = { viewModel.moveOrder(circuit.code, true) },
                                    onMoveDown = { viewModel.moveOrder(circuit.code, false) },
                                    onToggleFavorite = { viewModel.toggleFavorite(circuit.code) },
                                    onToggleActive = { viewModel.toggleActive(circuit.code) },
                                    onEdit = { editingCircuit = false to circuit },
                                    onDelete = { pendingDeleteCircuit = circuit }
                                )
                            }
                        }
                    }

                    CircuitsPrimaryTab.PROGRAMMING -> when (state.programmingSubTab) {
                        ProgrammingSubTab.CYCLES -> {
                            item(key = "cycles_intro") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Define los tiempos de servicio y afectación de cada ciclo, y ordena los grupos que rotan secuencialmente. La app calcula automáticamente los turnos y desfases sin solapamiento.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            if (state.cycles.isEmpty()) {
                                item(key = "cycles_empty") {
                                    StateNoticeCard(
                                        icon = Icons.Filled.Autorenew,
                                        title = "Sin ciclos rotativos",
                                        subtitle = "Crea un ciclo para definir la duración del servicio y el orden de rotación.",
                                        actionLabel = "Agregar ciclo",
                                        onAction = { editingCycle = true to null },
                                        modifier = Modifier.testTag("circuits_empty_cycles_card")
                                    )
                                }
                            } else {
                                items(state.cycles, key = { it.id }) { cycle ->
                                    val orderedNames = cycle.orderedGroupIds.mapIndexed { idx, gid ->
                                        val gName = state.groups.firstOrNull { it.id == gid }?.name ?: gid
                                        val offsetH = (idx * cycle.serviceDurationMinutes) / 60f
                                        val offsetStr = if (offsetH % 1f == 0f) "+${offsetH.toInt()}h" else "+${idx * cycle.serviceDurationMinutes}m"
                                        "${idx + 1}. $gName ($offsetStr)"
                                    }
                                    val svcStr = formatMinutesLabel(cycle.serviceDurationMinutes)
                                    val outStr = formatMinutesLabel(cycle.outageDurationMinutes)
                                    val totalStr = formatMinutesLabel(cycle.totalCycleMinutes)
                                    val capText = "Capacidad teórica: ${cycle.maxGroupsSupported} grupo(s) por ciclo ($totalStr total)" +
                                        if (cycle.remainderMinutes > 0) " • Sobra ${cycle.remainderMinutes}m" else ""

                                    AdminItemCard(
                                        title = cycle.name,
                                        subtitle = "Servicio: $svcStr • Sin servicio: $outStr • $capText",
                                        detail = buildString {
                                            append("Inicio ref.: ${CircuitsViewModel.formatEpochDateStr(cycle.referenceEpochMillis, state.zoneId)} ")
                                            append(ScheduleEngine.formatTime(cycle.referenceEpochMillis, state.use24h, state.zoneId))
                                            if (orderedNames.isNotEmpty()) {
                                                append("\nOrden de rotación: ${orderedNames.joinToString(" → ")}")
                                            } else {
                                                append("\nSin grupos asignados en este ciclo")
                                            }
                                        },
                                        onEdit = { editingCycle = false to cycle },
                                        onDelete = { viewModel.deleteCycle(cycle.id) }
                                    )
                                }
                            }
                        }

                        ProgrammingSubTab.GROUPS -> {
                            item(key = "groups_intro") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Agrupa aquí los circuitos que comparten bloque eléctrico. Luego ordénalos en la pestaña Ciclos para definir su rotación.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            if (state.groups.isEmpty()) {
                                item(key = "groups_empty") {
                                    StateNoticeCard(
                                        icon = Icons.Filled.Groups,
                                        title = "Sin grupos definidos",
                                        subtitle = "Agrupa circuitos que comparten el mismo bloque horario para vincularlos a un ciclo.",
                                        actionLabel = "Agregar grupo",
                                        onAction = { editingGroup = true to null },
                                        modifier = Modifier.testTag("circuits_empty_groups_card")
                                    )
                                }
                            } else {
                                items(state.groups, key = { it.id }) { grp ->
                                    val assigned = state.allCircuits.filter { it.groupId == grp.id }
                                    val parentCycles = state.cycles.filter { grp.id in it.orderedGroupIds }
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = grp.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (grp.description.isNotBlank()) {
                                                        Text(
                                                            text = grp.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { editingGroup = false to grp },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Editar grupo ${grp.name}")
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteGroup(grp.id) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Eliminar grupo ${grp.name}",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }

                                            if (parentCycles.isNotEmpty()) {
                                                Text(
                                                    text = "En ciclo: ${parentCycles.joinToString { it.name }} (fase +${grp.phaseOffsetMinutes / 60}h)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            if (assigned.isEmpty()) {
                                                Text(
                                                    text = "Sin circuitos agrupados todavía",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    assigned.forEach { c ->
                                                        Surface(
                                                            shape = MaterialTheme.shapes.small,
                                                            color = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                text = c.displayTitle(),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { pickingMembersForGroup = grp },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Circuitos del grupo (${assigned.size})",
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ProgrammingSubTab.FIXED -> {
                            if (state.fixedRules.isEmpty()) {
                                item(key = "fixed_empty") {
                                    StateNoticeCard(
                                        icon = Icons.Filled.LockClock,
                                        title = "Sin reglas fijas",
                                        subtitle = "Agrega reglas recurrentes por día de la semana para circuitos con horario fijo.",
                                        actionLabel = "Agregar horario fijo",
                                        onAction = { editingFixed = true to null },
                                        modifier = Modifier.testTag("circuits_empty_fixed_card")
                                    )
                                }
                            } else {
                                items(state.fixedRules, key = { it.id }) { rule ->
                                    FixedRuleCard(
                                        rule = rule,
                                        use24h = state.use24h,
                                        onEdit = { editingFixed = false to rule },
                                        onDelete = { viewModel.deleteFixedRule(rule.id) }
                                    )
                                }
                            }
                        }

                        ProgrammingSubTab.EXCEPTIONS -> {
                            if (state.manualExceptions.isEmpty()) {
                                item(key = "exceptions_empty") {
                                    StateNoticeCard(
                                        icon = Icons.AutoMirrored.Filled.EventNote,
                                        title = "Sin excepciones manuales",
                                        subtitle = "Registra cambios puntuales por fecha que tengan prioridad sobre el ciclo habitual.",
                                        actionLabel = "Agregar excepción",
                                        onAction = { editingException = true to null },
                                        modifier = Modifier.testTag("circuits_empty_exceptions_card")
                                    )
                                }
                            } else {
                                items(state.manualExceptions, key = { it.id }) { ex ->
                                    val d = LocalDate.ofEpochDay(ex.dateEpochDay)
                                    val dateRangeLabel = if (ex.isMultiDay()) {
                                        val endD = LocalDate.ofEpochDay(ex.effectiveEndEpochDay())
                                        "${ScheduleEngine.formatShortDate(d)} – ${ScheduleEngine.formatShortDate(endD)}"
                                    } else {
                                        ScheduleEngine.formatShortDate(d)
                                    }
                                    AdminItemCard(
                                        title = "${ex.circuitCode} • $dateRangeLabel",
                                        subtitle = "${ScheduleEngine.formatMinuteOfDay(ex.startMinuteOfDay, state.use24h)}\u2060–\u2060${ScheduleEngine.formatMinuteOfDay(ex.endMinuteOfDay, state.use24h)} • ${if (ex.hasService) "Con servicio" else "Sin servicio"}",
                                        detail = ex.reason,
                                        onEdit = { editingException = false to ex },
                                        onDelete = { viewModel.deleteManualException(ex.id) }
                                    )
                                }
                            }
                        }

                        ProgrammingSubTab.RELATIONS -> {
                            item(key = "relations_intro") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "Tipos de relación y cómo afectan el horario",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "• Mismo horario: copia exactamente el estado y las horas del circuito referencia.\n" +
                                                "• Horario inverso: invierte el estado (con servicio ↔ sin servicio) respecto al circuito referencia.\n" +
                                                "• Dependiente: el circuito dependiente sigue la disponibilidad eléctrica del referencia.\n" +
                                                "• Personalizada: replica el horario del circuito referencia permitiendo añadir notas o condiciones propias.\n" +
                                                "• Informativa: solo muestra el vínculo en el detalle sin alterar el horario propio del circuito.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Prioridad: Excepciones manuales > Horarios por fecha > Relación sincronizada > Grupo rotativo / Horario fijo.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (state.relations.isEmpty()) {
                                item(key = "relations_empty") {
                                    StateNoticeCard(
                                        icon = Icons.Filled.Link,
                                        title = "Sin relaciones configuradas",
                                        subtitle = "Vincula un circuito con otro para seguir su horario, invertirlo o documentar su dependencia.",
                                        actionLabel = "Agregar relación",
                                        onAction = { editingRelation = true to null },
                                        modifier = Modifier.testTag("circuits_empty_relations_card")
                                    )
                                }
                            } else {
                                items(state.relations, key = { it.id }) { rel ->
                                    val isSyncActive = rel.syncRuleEnabled &&
                                        rel.relationType != com.example.domain.model.RelationType.INFORMATIVA
                                    val typeLabel = when (rel.relationType) {
                                        com.example.domain.model.RelationType.INFORMATIVA -> "Informativa"
                                        com.example.domain.model.RelationType.MISMO_HORARIO -> "Mismo horario"
                                        com.example.domain.model.RelationType.HORARIO_INVERSO -> "Horario inverso"
                                        com.example.domain.model.RelationType.DEPENDIENTE -> "Dependiente"
                                        com.example.domain.model.RelationType.PERSONALIZADA -> "Personalizada"
                                    }
                                    AdminItemCard(
                                        title = "${rel.sourceCircuitCode} sigue a ${rel.targetCircuitCode}",
                                        subtitle = "$typeLabel • ${if (isSyncActive) "Activa (anula grupo/fijo)" else "Solo informativa"}",
                                        detail = rel.notes,
                                        onEdit = { editingRelation = false to rel },
                                        onDelete = { viewModel.deleteRelation(rel.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCircuit?.let { (_, existing) ->
        CircuitEditorDialog(
            existing = existing,
            groups = state.groups,
            onDismiss = { editingCircuit = null },
            onSave = viewModel::saveCircuit
        )
    }

    editingCycle?.let { (_, existing) ->
        CycleEditorDialog(
            existing = existing,
            groups = state.groups,
            zoneId = state.zoneId,
            use24h = state.use24h,
            onDismiss = { editingCycle = null },
            onSave = viewModel::saveCycle
        )
    }

    editingGroup?.let { (_, existing) ->
        val currentMembers = if (existing == null) emptySet()
        else state.allCircuits.filter { it.groupId == existing.id }.map { it.code }.toSet()
        GroupEditorDialog(
            existing = existing,
            allCircuits = state.allCircuits,
            initialMemberCodes = currentMembers,
            onDismiss = { editingGroup = null },
            onSave = viewModel::saveGroup
        )
    }

    pickingMembersForGroup?.let { grp ->
        val currentMembers = state.allCircuits.filter { it.groupId == grp.id }.map { it.code }.toSet()
        GroupMembersPickerDialog(
            groupName = grp.name,
            currentGroupId = grp.id,
            allCircuits = state.allCircuits,
            initialSelectedCodes = currentMembers,
            onDismiss = { pickingMembersForGroup = null },
            onConfirm = { selected ->
                viewModel.updateGroupMembers(grp.id, selected)
                pickingMembersForGroup = null
            }
        )
    }

    editingFixed?.let { (_, existing) ->
        FixedRuleEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
            use24h = state.use24h,
            onDismiss = { editingFixed = null },
            onSave = viewModel::saveFixedRule
        )
    }

    editingException?.let { (_, existing) ->
        ExceptionEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
            zoneId = state.zoneId,
            use24h = state.use24h,
            onDismiss = { editingException = null },
            onSave = viewModel::saveManualException
        )
    }

    editingRelation?.let { (_, existing) ->
        RelationEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
            onDismiss = { editingRelation = null },
            onSave = viewModel::saveRelation
        )
    }

    pendingDeleteCircuit?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCircuit = null },
            title = { Text("Eliminar ${target.code}") },
            text = { Text("¿Deseas eliminar permanentemente este circuito? También puedes desactivarlo temporalmente para conservar sus datos.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCircuit(target.code)
                    pendingDeleteCircuit = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCircuit = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ResponsiveCircuitAdminCard(
    circuit: Circuit,
    groupName: String?,
    favoriteColor: Color,
    onFavoriteColor: Color,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when (circuit.scheduleType) {
        ScheduleType.FIXED -> "Fijo"
        ScheduleType.ROTATING -> "Rotativo"
        ScheduleType.CUSTOM -> "Por fecha"
        ScheduleType.NONE -> "Sin prog."
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (circuit.isActive) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (circuit.isPrimaryHome) 1.5.dp else 1.dp,
            color = if (circuit.isPrimaryHome) {
                favoriteColor.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (circuit.isPrimaryHome) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = favoriteColor.copy(alpha = 0.16f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = null,
                                    tint = onFavoriteColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Mi vivienda",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onFavoriteColor
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = circuit.displayTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!circuit.isActive) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "Inactivo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (circuit.comment.isNotBlank()) {
                        Text(
                            text = circuit.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Modalidad: $typeLabel" + (groupName?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (circuit.isFavorite) "Quitar ${circuit.code} de favoritos" else "Marcar ${circuit.code} como favorito",
                        tint = if (circuit.isFavorite) favoriteColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir orden de ${circuit.code}", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar orden de ${circuit.code}", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleActive, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (circuit.isActive) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (circuit.isActive) "Desactivar ${circuit.code}" else "Reactivar ${circuit.code}",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar ${circuit.code}", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar ${circuit.code}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedRuleCard(
    rule: FixedScheduleRule,
    use24h: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dayInitials = listOf("L", "M", "X", "J", "V", "S", "D")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${rule.circuitCode} • ${ScheduleEngine.formatMinuteOfDay(rule.startMinuteOfDay, use24h)}\u2060–\u2060${ScheduleEngine.formatMinuteOfDay(rule.endMinuteOfDay, use24h)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = rule.label.ifBlank { "Horario fijo recurrente" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dayInitials.forEachIndexed { idx, letter ->
                        val active = (rule.daysOfWeekMask and (1 shl idx)) != 0
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar regla de ${rule.circuitCode}")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar regla de ${rule.circuitCode}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AdminItemCard(
    title: String,
    subtitle: String,
    detail: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (detail.isNotBlank()) {
                    Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar $title")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar $title", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatMinutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
