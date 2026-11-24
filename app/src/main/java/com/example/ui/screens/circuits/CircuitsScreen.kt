package com.example.ui.screens.circuits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.components.CompactSearchBar
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
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

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
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
                modifier = Modifier.testTag("admin_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar registro")
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "admin_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Circuitos y Programación",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        TabRow(
                            selectedTabIndex = state.primaryTab.ordinal,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            CircuitsPrimaryTab.entries.forEach { tab ->
                                Tab(
                                    selected = state.primaryTab == tab,
                                    onClick = { viewModel.selectPrimaryTab(tab) },
                                    text = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (state.primaryTab == tab) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier.testTag("circuits_primary_tab_${tab.name.lowercase()}")
                                )
                            }
                        }

                        if (state.primaryTab == CircuitsPrimaryTab.PROGRAMMING) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProgrammingSubTab.entries.forEach { subTab ->
                                    FilterChip(
                                        selected = state.programmingSubTab == subTab,
                                        onClick = { viewModel.selectProgrammingSubTab(subTab) },
                                        label = { Text(subTab.label) },
                                        modifier = Modifier.testTag("admin_tab_${subTab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }

                when (state.primaryTab) {
                    CircuitsPrimaryTab.CIRCUIT_EDITOR -> {
                        item(key = "circuit_filters") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactSearchBar(
                                    query = state.searchQuery,
                                    onQueryChange = viewModel::updateSearch,
                                    placeholder = "Buscar código, nombre o comentario…",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("circuits_search_input")
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = state.favoritesOnly,
                                        onClick = viewModel::toggleFavoritesOnly,
                                        label = { Text("Favoritos") }
                                    )
                                    FilterChip(
                                        selected = state.showInactive,
                                        onClick = viewModel::toggleShowInactive,
                                        label = { Text("Mostrar inactivos") }
                                    )
                                    FilterChip(
                                        selected = state.selectedGroupFilterId == null && !state.fixedOnly,
                                        onClick = { viewModel.filterByGroup(null) },
                                        label = { Text("Todos los grupos") }
                                    )
                                    FilterChip(
                                        selected = state.fixedOnly,
                                        onClick = viewModel::toggleFixedOnly,
                                        label = { Text("Fijos") },
                                        modifier = Modifier.testTag("circuits_filter_fixed")
                                    )
                                    state.groups.forEach { grp ->
                                        FilterChip(
                                            selected = !state.fixedOnly && state.selectedGroupFilterId == grp.id,
                                            onClick = { viewModel.filterByGroup(grp.id) },
                                            label = { Text(grp.name) }
                                        )
                                    }
                                }
                            }
                        }

                        items(state.circuits, key = { it.code }) { circuit ->
                            val grpName = state.groups.firstOrNull { it.id == circuit.groupId }?.name
                            ResponsiveCircuitAdminCard(
                                circuit = circuit,
                                groupName = grpName,
                                favoriteColor = statusColors.favorite,
                                onMoveUp = { viewModel.moveOrder(circuit.code, true) },
                                onMoveDown = { viewModel.moveOrder(circuit.code, false) },
                                onToggleFavorite = { viewModel.toggleFavorite(circuit.code) },
                                onToggleActive = { viewModel.toggleActive(circuit.code) },
                                onEdit = { editingCircuit = false to circuit },
                                onDelete = { pendingDeleteCircuit = circuit }
                            )
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
                            items(state.groups, key = { it.id }) { grp ->
                                val assigned = state.allCircuits.filter { it.groupId == grp.id }
                                val parentCycles = state.cycles.filter { grp.id in it.orderedGroupIds }
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
                                            IconButton(onClick = { editingGroup = false to grp }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Editar grupo")
                                            }
                                            IconButton(onClick = { viewModel.deleteGroup(grp.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar grupo", tint = MaterialTheme.colorScheme.error)
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
                                                        shape = RoundedCornerShape(8.dp),
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
                                            modifier = Modifier.fillMaxWidth()
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

                        ProgrammingSubTab.FIXED -> {
                            items(state.fixedRules, key = { it.id }) { rule ->
                                FixedRuleCard(
                                    rule = rule,
                                    use24h = state.use24h,
                                    onEdit = { editingFixed = false to rule },
                                    onDelete = { viewModel.deleteFixedRule(rule.id) }
                                )
                            }
                        }

                        ProgrammingSubTab.EXCEPTIONS -> {
                            items(state.manualExceptions, key = { it.id }) { ex ->
                                val d = LocalDate.ofEpochDay(ex.dateEpochDay)
                                AdminItemCard(
                                    title = "${ex.circuitCode} • ${ScheduleEngine.formatShortDate(d)}",
                                    subtitle = "${ScheduleEngine.formatMinuteOfDay(ex.startMinuteOfDay, state.use24h)} – ${ScheduleEngine.formatMinuteOfDay(ex.endMinuteOfDay, state.use24h)} • ${if (ex.hasService) "Con servicio" else "Sin servicio"}",
                                    detail = ex.reason,
                                    onEdit = { editingException = false to ex },
                                    onDelete = { viewModel.deleteManualException(ex.id) }
                                )
                            }
                        }

                        ProgrammingSubTab.RELATIONS -> {
                            items(state.relations, key = { it.id }) { rel ->
                                AdminItemCard(
                                    title = "${rel.sourceCircuitCode} → ${rel.targetCircuitCode}",
                                    subtitle = "Tipo: ${rel.relationType.name.lowercase().replace('_', ' ')} • Sync: ${if (rel.syncRuleEnabled) "Activa" else "Solo informativa"}",
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
    favoriteColor: androidx.compose.ui.graphics.Color,
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
        shape = RoundedCornerShape(16.dp),
        color = if (circuit.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = circuit.displayTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (circuit.isPrimaryHome) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = "Mi vivienda",
                                tint = favoriteColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (!circuit.isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
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

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Favorito",
                        tint = if (circuit.isFavorite) favoriteColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir orden", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar orden", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleActive) {
                    Icon(
                        if (circuit.isActive) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (circuit.isActive) "Desactivar" else "Reactivar",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar circuito", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar circuito",
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                    text = "${rule.circuitCode} • ${ScheduleEngine.formatMinuteOfDay(rule.startMinuteOfDay, use24h)} – ${ScheduleEngine.formatMinuteOfDay(rule.endMinuteOfDay, use24h)}",
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
                                .size(24.dp)
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
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatMinutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
