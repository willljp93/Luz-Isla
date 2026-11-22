package com.example.ui.screens.circuits

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.ScheduleGroup
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@Composable
fun CircuitsScreen(
    viewModel: CircuitsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editingCircuit by remember { mutableStateOf<Pair<Boolean, Circuit?>?>(null) }
    var editingGroup by remember { mutableStateOf<Pair<Boolean, ScheduleGroup?>?>(null) }
    var editingFixed by remember { mutableStateOf<Pair<Boolean, FixedScheduleRule?>?>(null) }
    var editingForecast by remember { mutableStateOf<Pair<Boolean, ExplicitForecast?>?>(null) }
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
                    when (state.activeTab) {
                        AdminTab.CIRCUITS -> editingCircuit = true to null
                        AdminTab.GROUPS -> editingGroup = true to null
                        AdminTab.FIXED -> editingFixed = true to null
                        AdminTab.FORECASTS -> editingForecast = true to null
                        AdminTab.EXCEPTIONS -> editingException = true to null
                        AdminTab.RELATIONS -> editingRelation = true to null
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Catálogo y Programación",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = state.activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                when (state.activeTab) {
                    AdminTab.CIRCUITS -> {
                        item(key = "circuit_filters") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = viewModel::updateSearch,
                                    placeholder = { Text("Buscar por código, nombre o comentario…") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (state.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
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
                                        selected = state.selectedGroupFilterId == null,
                                        onClick = { viewModel.filterByGroup(null) },
                                        label = { Text("Todos los grupos") }
                                    )
                                    state.groups.forEach { grp ->
                                        FilterChip(
                                            selected = state.selectedGroupFilterId == grp.id,
                                            onClick = { viewModel.filterByGroup(grp.id) },
                                            label = { Text(grp.name) }
                                        )
                                    }
                                }
                            }
                        }

                        items(state.circuits, key = { it.code }) { circuit ->
                            val grpName = state.groups.firstOrNull { it.id == circuit.groupId }?.name
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (circuit.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = circuit.displayTitle() + if (!circuit.isActive) " (Inactivo)" else "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (circuit.comment.isNotBlank()) {
                                            Text(
                                                text = circuit.comment,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Tipo: ${circuit.scheduleType.name}" + (grpName?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.moveOrder(circuit.code, true) }) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir orden", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { viewModel.moveOrder(circuit.code, false) }) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar orden", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { viewModel.toggleFavorite(circuit.code) }) {
                                        Icon(
                                            if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = "Favorito",
                                            tint = if (circuit.isFavorite) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.toggleActive(circuit.code) }) {
                                        Icon(
                                            if (circuit.isActive) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (circuit.isActive) "Desactivar" else "Reactivar"
                                        )
                                    }
                                    IconButton(onClick = { editingCircuit = false to circuit }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar circuito")
                                    }
                                    IconButton(onClick = { pendingDeleteCircuit = circuit }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar circuito", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.GROUPS -> {
                        items(state.groups, key = { it.id }) { grp ->
                            val assigned = state.allCircuits.filter { it.groupId == grp.id }.map { it.code }
                            AdminItemCard(
                                title = grp.name,
                                subtitle = "Servicio: ${grp.serviceDurationMinutes / 60}h ${grp.serviceDurationMinutes % 60}m • Sin servicio: ${grp.outageDurationMinutes / 60}h ${grp.outageDurationMinutes % 60}m • Fase: +${grp.phaseOffsetMinutes}m",
                                detail = "Ref: ${CircuitsViewModel.formatEpochDateStr(grp.referenceEpochMillis)} ${CircuitsViewModel.formatEpochTime24Str(grp.referenceEpochMillis)} • Circuitos: ${assigned.ifEmpty { listOf("Ninguno") }.joinToString(", ")}",
                                onEdit = { editingGroup = false to grp },
                                onDelete = { viewModel.deleteGroup(grp.id) }
                            )
                        }
                    }

                    AdminTab.FIXED -> {
                        items(state.fixedRules, key = { it.id }) { rule ->
                            AdminItemCard(
                                title = "${rule.circuitCode} • ${ScheduleEngine.formatMinuteOfDay12h(rule.startMinuteOfDay)} – ${ScheduleEngine.formatMinuteOfDay12h(rule.endMinuteOfDay)}",
                                subtitle = rule.label.ifBlank { "Horario fijo recurrente" },
                                detail = "Máscara días: ${Integer.toBinaryString(rule.daysOfWeekMask).padStart(7, '0')}",
                                onEdit = { editingFixed = false to rule },
                                onDelete = { viewModel.deleteFixedRule(rule.id) }
                            )
                        }
                    }

                    AdminTab.FORECASTS -> {
                        items(state.explicitForecasts, key = { it.id }) { fc ->
                            val d = LocalDate.ofEpochDay(fc.dateEpochDay)
                            val kindLabel = if (fc.intervalKind == IntervalKind.SERVICE) "Con servicio" else "Sin servicio"
                            AdminItemCard(
                                title = "${ScheduleEngine.formatShortDate(d)} • ${ScheduleEngine.formatMinuteOfDay12h(fc.startMinuteOfDay)} – ${ScheduleEngine.formatMinuteOfDay12h(fc.endMinuteOfDay)}",
                                subtitle = "$kindLabel: ${fc.circuitCodes.joinToString(", ")}",
                                detail = fc.notes + if (fc.isHistoricalTest) " [Prueba histórica]" else "",
                                onEdit = { editingForecast = false to fc },
                                onDelete = { viewModel.deleteExplicitForecast(fc.id) }
                            )
                        }
                    }

                    AdminTab.EXCEPTIONS -> {
                        items(state.manualExceptions, key = { it.id }) { ex ->
                            val d = LocalDate.ofEpochDay(ex.dateEpochDay)
                            AdminItemCard(
                                title = "${ex.circuitCode} • ${ScheduleEngine.formatShortDate(d)}",
                                subtitle = "${ScheduleEngine.formatMinuteOfDay12h(ex.startMinuteOfDay)} – ${ScheduleEngine.formatMinuteOfDay12h(ex.endMinuteOfDay)} • ${if (ex.hasService) "Con servicio" else "Sin servicio"}",
                                detail = ex.reason,
                                onEdit = { editingException = false to ex },
                                onDelete = { viewModel.deleteManualException(ex.id) }
                            )
                        }
                    }

                    AdminTab.RELATIONS -> {
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

    editingCircuit?.let { (_, existing) ->
        CircuitEditorDialog(
            existing = existing,
            groups = state.groups,
            onDismiss = { editingCircuit = null },
            onSave = viewModel::saveCircuit
        )
    }

    editingGroup?.let { (_, existing) ->
        GroupEditorDialog(
            existing = existing,
            onDismiss = { editingGroup = null },
            onSave = viewModel::saveGroup
        )
    }

    editingFixed?.let { (_, existing) ->
        FixedRuleEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
            onDismiss = { editingFixed = null },
            onSave = viewModel::saveFixedRule
        )
    }

    editingForecast?.let { (_, existing) ->
        ForecastEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
            onDismiss = { editingForecast = null },
            onSave = viewModel::saveExplicitForecast
        )
    }

    editingException?.let { (_, existing) ->
        ExceptionEditorDialog(
            circuits = state.allCircuits,
            existing = existing,
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
