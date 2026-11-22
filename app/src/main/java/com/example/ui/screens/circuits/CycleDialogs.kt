package com.example.ui.screens.circuits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Circuit
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CycleEditorDialog(
    existing: RotationCycle?,
    groups: List<ScheduleGroup>,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var svcH by remember { mutableStateOf(((existing?.serviceDurationMinutes ?: 240) / 60).toString()) }
    var svcM by remember { mutableStateOf(((existing?.serviceDurationMinutes ?: 240) % 60).toString()) }
    var outH by remember { mutableStateOf(((existing?.outageDurationMinutes ?: 720) / 60).toString()) }
    var outM by remember { mutableStateOf(((existing?.outageDurationMinutes ?: 720) % 60).toString()) }
    var refDate by remember {
        mutableStateOf(
            existing?.let { CircuitsViewModel.formatEpochDateStr(it.referenceEpochMillis, zoneId) }
                ?: LocalDate.now(zoneId).toString()
        )
    }
    var refTime by remember {
        mutableStateOf(
            existing?.let { CircuitsViewModel.formatEpochTime24Str(it.referenceEpochMillis, zoneId) } ?: "00:00"
        )
    }
    var orderedGroupIds by remember { mutableStateOf(existing?.orderedGroupIds ?: emptyList()) }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }

    val svcTotal = ((svcH.toIntOrNull() ?: 0) * 60 + (svcM.toIntOrNull() ?: 0)).coerceAtLeast(15)
    val outTotal = ((outH.toIntOrNull() ?: 0) * 60 + (outM.toIntOrNull() ?: 0)).coerceAtLeast(15)
    val totalCycle = svcTotal + outTotal
    val maxGroups = (totalCycle / svcTotal).coerceAtLeast(1)
    val availableGroups = groups.filter { it.id !in orderedGroupIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo ciclo rotativo" else "Editar ciclo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del ciclo (ej. 4h / 12h)*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = svcH,
                        onValueChange = { svcH = it },
                        label = { Text("Serv. (h)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = svcM,
                        onValueChange = { svcM = it },
                        label = { Text("Serv. (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = outH,
                        onValueChange = { outH = it },
                        label = { Text("Sin serv. (h)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = outM,
                        onValueChange = { outM = it },
                        label = { Text("Sin serv. (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live mathematical calculation banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Capacidad calculada: admite hasta $maxGroups grupo(s) por rotación",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (orderedGroupIds.size > maxGroups) {
                                "Seleccionaste ${orderedGroupIds.size} grupos: la afectación se ajustará automáticamente a ${((orderedGroupIds.size - 1) * svcTotal) / 60}h para respetar el orden sin solapamientos."
                            } else {
                                "Cada grupo entra inmediatamente cuando finaliza el anterior (+${svcTotal / 60}h ${svcTotal % 60}m entre turnos)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = refDate,
                        onValueChange = { refDate = it },
                        label = { Text("Fecha inicio (AAAA-MM-DD)") },
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = refTime,
                        onValueChange = { refTime = it },
                        label = { Text("Hora (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Orden de rotación de grupos", style = MaterialTheme.typography.labelLarge)
                if (orderedGroupIds.isEmpty()) {
                    Text(
                        text = "Agrega grupos abajo para definir quién inicia primero y el orden sucesivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    orderedGroupIds.forEachIndexed { idx, gid ->
                        val grp = groups.firstOrNull { it.id == gid }
                        val offsetMin = idx * svcTotal
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Turno ${idx + 1}: ${grp?.name ?: gid}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Desfase: +${offsetMin / 60}h ${offsetMin % 60}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val list = orderedGroupIds.toMutableList()
                                            val item = list.removeAt(idx)
                                            list.add(idx - 1, item)
                                            orderedGroupIds = list
                                        }
                                    },
                                    enabled = idx > 0
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir turno", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < orderedGroupIds.lastIndex) {
                                            val list = orderedGroupIds.toMutableList()
                                            val item = list.removeAt(idx)
                                            list.add(idx + 1, item)
                                            orderedGroupIds = list
                                        }
                                    },
                                    enabled = idx < orderedGroupIds.lastIndex
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar turno", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    orderedGroupIds = orderedGroupIds.filterNot { it == gid }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Quitar grupo", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                if (availableGroups.isNotEmpty()) {
                    Text("Añadir grupo al ciclo", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableGroups.forEach { grp ->
                            FilterChip(
                                selected = false,
                                onClick = { orderedGroupIds = orderedGroupIds + grp.id },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(grp.name) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ciclo activo", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    existing?.id,
                    name,
                    description,
                    svcH.toIntOrNull() ?: 4,
                    svcM.toIntOrNull() ?: 0,
                    outH.toIntOrNull() ?: 12,
                    outM.toIntOrNull() ?: 0,
                    refDate,
                    refTime,
                    orderedGroupIds,
                    isActive
                )
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun GroupMembersPickerDialog(
    groupName: String,
    allCircuits: List<Circuit>,
    initialSelectedCodes: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSelectedCodes) }
    val filtered = remember(search, allCircuits) {
        if (search.isBlank()) allCircuits
        else allCircuits.filter {
            it.code.contains(search.trim(), ignoreCase = true) ||
                it.customName.contains(search.trim(), ignoreCase = true) ||
                it.comment.contains(search.trim(), ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Circuitos de $groupName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar circuito…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(filtered, key = { it.code }) { circuit ->
                        val isChecked = circuit.code in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) selected - circuit.code else selected + circuit.code
                                }
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    selected = if (it) selected + circuit.code else selected - circuit.code
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = circuit.displayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (circuit.comment.isNotBlank()) {
                                    Text(
                                        text = circuit.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Aplicar (${selected.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun CircuitSelectorDialog(
    title: String,
    circuits: List<Circuit>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, circuits) {
        if (search.isBlank()) circuits
        else circuits.filter {
            it.code.contains(search.trim(), ignoreCase = true) ||
                it.customName.contains(search.trim(), ignoreCase = true) ||
                it.comment.contains(search.trim(), ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar por código o nombre…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(filtered, key = { it.code }) { circuit ->
                        val isSelected = circuit.code.equals(selectedCode, ignoreCase = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(circuit.code) }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = { onSelect(circuit.code) })
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = circuit.displayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (circuit.comment.isNotBlank()) {
                                    Text(
                                        text = circuit.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
