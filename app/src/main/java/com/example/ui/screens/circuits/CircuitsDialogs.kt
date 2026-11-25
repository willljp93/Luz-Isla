package com.example.ui.screens.circuits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleType
import com.example.ui.components.CenteredScrollableChipRow
import com.example.ui.components.DateSelectorField
import com.example.ui.components.TimeSelectorField
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CircuitEditorDialog(
    existing: Circuit?,
    groups: List<ScheduleGroup>,
    onDismiss: () -> Unit,
    onSave: (
        code: String,
        customName: String,
        comment: String,
        groupId: String?,
        scheduleType: ScheduleType,
        isActive: Boolean,
        isFavorite: Boolean,
        isPrimaryHome: Boolean,
        isNew: Boolean
    ) -> Unit
) {
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var name by remember { mutableStateOf(existing?.customName ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var groupId by remember { mutableStateOf(existing?.groupId) }
    var scheduleType by remember { mutableStateOf(existing?.scheduleType ?: ScheduleType.CUSTOM) }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var isFavorite by remember { mutableStateOf(existing?.isFavorite ?: false) }
    var isPrimaryHome by remember { mutableStateOf(existing?.isPrimaryHome ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo circuito" else "Editar ${existing.code}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Código oficial (ej. A210)*") },
                    enabled = existing == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre personalizado (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comentario / Reparto / Localidad") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Modalidad de programación", style = MaterialTheme.typography.labelLarge)
                CenteredScrollableChipRow(spacing = 6.dp) {
                    ScheduleType.entries.forEach { st ->
                        val (label, icon) = when (st) {
                            ScheduleType.FIXED -> "Fijo" to Icons.Filled.LockClock
                            ScheduleType.ROTATING -> "Rotativo" to Icons.Filled.Autorenew
                            ScheduleType.CUSTOM -> "Por fecha" to Icons.Filled.DateRange
                            ScheduleType.NONE -> "Sin prog." to Icons.AutoMirrored.Filled.HelpOutline
                        }
                        FilterChip(
                            selected = scheduleType == st,
                            onClick = { scheduleType = st },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }

                if (groups.isNotEmpty()) {
                    Text("Grupo", style = MaterialTheme.typography.labelLarge)
                    CenteredScrollableChipRow(spacing = 6.dp) {
                        FilterChip(
                            selected = groupId == null,
                            onClick = { groupId = null },
                            leadingIcon = {
                                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Ninguno", maxLines = 1, softWrap = false) }
                        )
                        groups.forEach { grp ->
                            FilterChip(
                                selected = groupId == grp.id,
                                onClick = {
                                    groupId = grp.id
                                    scheduleType = ScheduleType.ROTATING
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(grp.name, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }

                SwitchRow("Circuito activo", isActive) { isActive = it }
                SwitchRow("Favorito", isFavorite) { isFavorite = it }
                SwitchRow("Mi vivienda principal", isPrimaryHome) {
                    isPrimaryHome = it
                    if (it) isFavorite = true
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(code, name, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, existing == null)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun GroupEditorDialog(
    existing: ScheduleGroup?,
    allCircuits: List<Circuit>,
    initialMemberCodes: Set<String>,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        name: String,
        description: String,
        selectedCircuitCodes: Set<String>
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var selectedCodes by remember { mutableStateOf(initialMemberCodes) }
    var showMemberPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo grupo de circuitos" else "Editar grupo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo (ej. Grupo 1)*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showMemberPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Elegir circuitos (${selectedCodes.size})")
                }
                if (selectedCodes.isNotEmpty()) {
                    Text(
                        text = "Seleccionados: ${selectedCodes.sorted().joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(existing?.id, name, description, selectedCodes)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    if (showMemberPicker) {
        GroupMembersPickerDialog(
            groupName = name.ifBlank { "Grupo" },
            currentGroupId = existing?.id,
            allCircuits = allCircuits,
            initialSelectedCodes = selectedCodes,
            onDismiss = { showMemberPicker = false },
            onConfirm = { picked ->
                selectedCodes = picked
                showMemberPicker = false
            }
        )
    }
}

@Composable
fun FixedRuleEditorDialog(
    circuits: List<Circuit>,
    existing: FixedScheduleRule?,
    use24h: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String?, String, Int, String, String, String, String, String) -> Unit
) {
    var selectedCode by remember { mutableStateOf(existing?.circuitCode ?: circuits.firstOrNull()?.code.orEmpty()) }
    var daysMask by remember { mutableIntStateOf(existing?.daysOfWeekMask ?: 0b1111111) }
    var startMinute by remember { mutableIntStateOf(existing?.startMinuteOfDay ?: 9 * 60) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinuteOfDay ?: 17 * 60) }
    var validFromDate by remember { mutableStateOf(existing?.validFromEpochDay?.let { LocalDate.ofEpochDay(it) }) }
    var validToDate by remember { mutableStateOf(existing?.validToEpochDay?.let { LocalDate.ofEpochDay(it) }) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var showCircuitPicker by remember { mutableStateOf(false) }

    val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
    val selectedCircuitObj = circuits.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horario fijo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Circuito", style = MaterialTheme.typography.labelLarge)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCircuitPicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedCircuitObj?.displayTitle() ?: selectedCode.ifBlank { "Seleccionar circuito…" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir lista de circuitos")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeSelectorField(
                        label = "Inicio",
                        minuteOfDay = startMinute,
                        use24h = use24h,
                        onTimeSelected = { startMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimeSelectorField(
                        label = "Fin",
                        minuteOfDay = endMinute,
                        use24h = use24h,
                        onTimeSelected = { endMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Días de la semana", style = MaterialTheme.typography.labelLarge)
                CenteredScrollableChipRow(spacing = 4.dp) {
                    dayLabels.forEachIndexed { idx, dLabel ->
                        val checked = (daysMask and (1 shl idx)) != 0
                        FilterChip(
                            selected = checked,
                            onClick = { daysMask = daysMask xor (1 shl idx) },
                            label = { Text(dLabel, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateSelectorField(
                        label = "Desde (opc.)",
                        date = validFromDate,
                        placeholder = "Sin inicio",
                        onDateSelected = { validFromDate = it },
                        onClear = { validFromDate = null },
                        modifier = Modifier.weight(1f)
                    )
                    DateSelectorField(
                        label = "Hasta (opc.)",
                        date = validToDate,
                        placeholder = "Sin fin",
                        onDateSelected = { validToDate = it },
                        onClear = { validToDate = null },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Descripción / etiqueta") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    existing?.id,
                    selectedCode,
                    daysMask,
                    CircuitsViewModel.formatMinute24h(startMinute),
                    CircuitsViewModel.formatMinute24h(endMinute),
                    validFromDate?.toString().orEmpty(),
                    validToDate?.toString().orEmpty(),
                    label
                )
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showCircuitPicker) {
        CircuitSelectorDialog(
            title = "Elegir circuito",
            circuits = circuits,
            selectedCode = selectedCode,
            onSelect = {
                selectedCode = it
                showCircuitPicker = false
            },
            onDismiss = { showCircuitPicker = false }
        )
    }
}

@Composable
fun ExceptionEditorDialog(
    circuits: List<Circuit>,
    existing: ManualException?,
    zoneId: ZoneId,
    use24h: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String, Boolean, String) -> Unit
) {
    var code by remember { mutableStateOf(existing?.circuitCode ?: circuits.firstOrNull()?.code.orEmpty()) }
    var selectedDate by remember {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.dateEpochDay) }
                ?: LocalDate.now(zoneId)
        )
    }
    var endDate by remember {
        mutableStateOf(
            existing?.endDateEpochDay
                ?.takeIf { it > existing.dateEpochDay }
                ?.let { LocalDate.ofEpochDay(it) }
        )
    }
    var startMinute by remember { mutableIntStateOf(existing?.startMinuteOfDay ?: 0) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinuteOfDay ?: 1440) }
    var hasService by remember { mutableStateOf(existing?.hasService ?: false) }
    var reason by remember { mutableStateOf(existing?.reason ?: "") }
    var showCircuitPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excepción manual") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCircuitPicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Circuito: ${circuits.firstOrNull { it.code == code }?.displayTitle() ?: code}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Seleccionar circuito")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateSelectorField(
                        label = "Desde",
                        date = selectedDate,
                        onDateSelected = { newStart ->
                            selectedDate = newStart
                            val currentEnd = endDate
                            if (currentEnd != null && currentEnd.isBefore(newStart)) {
                                endDate = newStart
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exception_start_date_field")
                    )
                    DateSelectorField(
                        label = "Hasta (opc.)",
                        date = endDate,
                        placeholder = "Solo un día",
                        onDateSelected = { newEnd ->
                            endDate = if (newEnd.isBefore(selectedDate)) selectedDate else newEnd
                        },
                        onClear = { endDate = null },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exception_end_date_field")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeSelectorField(
                        label = "Inicio",
                        minuteOfDay = startMinute,
                        use24h = use24h,
                        onTimeSelected = { startMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimeSelectorField(
                        label = "Fin",
                        minuteOfDay = endMinute,
                        use24h = use24h,
                        onTimeSelected = { endMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                SwitchRow("Tiene servicio en esta excepción", hasService) { hasService = it }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (avería, mantenimiento, etc.)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    existing?.id,
                    code,
                    selectedDate.toString(),
                    endDate?.toString().orEmpty(),
                    CircuitsViewModel.formatMinute24h(startMinute),
                    CircuitsViewModel.formatMinute24h(endMinute),
                    hasService,
                    reason
                )
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showCircuitPicker) {
        CircuitSelectorDialog(
            title = "Circuito de excepción",
            circuits = circuits,
            selectedCode = code,
            onSelect = {
                code = it
                showCircuitPicker = false
            },
            onDismiss = { showCircuitPicker = false }
        )
    }
}

@Composable
fun RelationEditorDialog(
    circuits: List<Circuit>,
    existing: CircuitRelation?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, RelationType, Boolean, String) -> Unit
) {
    var src by remember { mutableStateOf(existing?.sourceCircuitCode ?: circuits.firstOrNull()?.code.orEmpty()) }
    var tgt by remember { mutableStateOf(existing?.targetCircuitCode ?: circuits.getOrNull(1)?.code.orEmpty()) }
    var type by remember { mutableStateOf(existing?.relationType ?: RelationType.MISMO_HORARIO) }
    var syncEnabled by remember {
        mutableStateOf(
            existing?.syncRuleEnabled ?: true
        )
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var pickTarget by remember { mutableStateOf<String?>(null) } // "SRC" or "TGT"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relación entre circuitos") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Circuito dependiente (recibe horario)", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickTarget = "SRC" }, modifier = Modifier.fillMaxWidth()) {
                    Text(circuits.firstOrNull { it.code == src }?.displayTitle() ?: src)
                }
                Text("Circuito referencia (aporta horario)", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickTarget = "TGT" }, modifier = Modifier.fillMaxWidth()) {
                    Text(circuits.firstOrNull { it.code == tgt }?.displayTitle() ?: tgt)
                }
                Text("Tipo de relación", style = MaterialTheme.typography.labelLarge)
                CenteredScrollableChipRow(spacing = 6.dp) {
                    RelationType.entries.forEach { rt ->
                        val (rtLabel, rtIcon) = when (rt) {
                            RelationType.INFORMATIVA -> "Informativa" to Icons.Filled.Info
                            RelationType.MISMO_HORARIO -> "Mismo horario" to Icons.Filled.Sync
                            RelationType.HORARIO_INVERSO -> "Horario inverso" to Icons.Filled.SwapHoriz
                            RelationType.DEPENDIENTE -> "Dependiente" to Icons.Filled.Link
                            RelationType.PERSONALIZADA -> "Personalizada" to Icons.Filled.Tune
                        }
                        FilterChip(
                            selected = type == rt,
                            onClick = {
                                type = rt
                                syncEnabled = rt != RelationType.INFORMATIVA
                            },
                            leadingIcon = {
                                Icon(rtIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(rtLabel, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                val typeExplanation = when (type) {
                    RelationType.MISMO_HORARIO ->
                        "Mismo horario: copia exactamente los bloques con y sin servicio del circuito referencia."
                    RelationType.HORARIO_INVERSO ->
                        "Horario inverso: cuando el circuito referencia tiene servicio, el dependiente está sin servicio, y viceversa."
                    RelationType.DEPENDIENTE ->
                        "Dependiente: este circuito solo tiene servicio cuando el circuito referencia está con servicio."
                    RelationType.PERSONALIZADA ->
                        "Personalizada: sigue los cambios de estado del circuito referencia con tus propias notas descriptivas."
                    RelationType.INFORMATIVA ->
                        "Informativa: muestra la relación en el detalle del circuito sin cambiar su horario actual."
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = typeExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (type != RelationType.INFORMATIVA) {
                    SwitchRow("Aplicar sincronización automática", syncEnabled) { syncEnabled = it }
                    Text(
                        text = "Si está activa, anula el grupo rotativo o regla fija del circuito dependiente (debajo de excepciones manuales y horarios por fecha).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    existing?.id,
                    src,
                    tgt,
                    type,
                    type != RelationType.INFORMATIVA && syncEnabled,
                    notes
                )
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    pickTarget?.let { which ->
        CircuitSelectorDialog(
            title = if (which == "SRC") "Circuito dependiente" else "Circuito referencia",
            circuits = circuits,
            selectedCode = if (which == "SRC") src else tgt,
            onSelect = {
                if (which == "SRC") src = it else tgt = it
                pickTarget = null
            },
            onDismiss = { pickTarget = null }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
