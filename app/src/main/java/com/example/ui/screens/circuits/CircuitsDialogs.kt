package com.example.ui.screens.circuits

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleType
import java.time.LocalDate

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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduleType.entries.forEach { st ->
                        val label = when (st) {
                            ScheduleType.FIXED -> "Fijo"
                            ScheduleType.ROTATING -> "Rotativo"
                            ScheduleType.CUSTOM -> "Por fecha"
                            ScheduleType.NONE -> "Sin prog."
                        }
                        FilterChip(
                            selected = scheduleType == st,
                            onClick = { scheduleType = st },
                            label = { Text(label) }
                        )
                    }
                }

                if (groups.isNotEmpty()) {
                    Text("Grupo rotativo", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = groupId == null,
                            onClick = { groupId = null },
                            label = { Text("Ninguno") }
                        )
                        groups.forEach { grp ->
                            FilterChip(
                                selected = groupId == grp.id,
                                onClick = {
                                    groupId = grp.id
                                    scheduleType = ScheduleType.ROTATING
                                },
                                label = { Text(grp.name) }
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
        phaseOffsetMinutes: Int
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
            existing?.let { CircuitsViewModel.formatEpochDateStr(it.referenceEpochMillis) }
                ?: LocalDate.now(ScheduleEngine.HAVANA_ZONE).toString()
        )
    }
    var refTime by remember {
        mutableStateOf(
            existing?.let { CircuitsViewModel.formatEpochTime24Str(it.referenceEpochMillis) } ?: "00:00"
        )
    }
    var phaseOffset by remember { mutableStateOf((existing?.phaseOffsetMinutes ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo grupo rotativo" else "Editar grupo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = refDate,
                        onValueChange = { refDate = it },
                        label = { Text("Fecha ref. (AAAA-MM-DD)") },
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = refTime,
                        onValueChange = { refTime = it },
                        label = { Text("Hora (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = phaseOffset,
                    onValueChange = { phaseOffset = it },
                    label = { Text("Desplazamiento / fase (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    phaseOffset.toIntOrNull() ?: 0
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
fun FixedRuleEditorDialog(
    circuits: List<Circuit>,
    existing: FixedScheduleRule?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Int, String, String, String, String, String) -> Unit
) {
    var selectedCode by remember { mutableStateOf(existing?.circuitCode ?: circuits.firstOrNull()?.code.orEmpty()) }
    var daysMask by remember { mutableIntStateOf(existing?.daysOfWeekMask ?: 0b1111111) }
    var startStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.startMinuteOfDay) } ?: "09:00") }
    var endStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.endMinuteOfDay) } ?: "17:00") }
    var validFrom by remember { mutableStateOf(existing?.validFromEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var validTo by remember { mutableStateOf(existing?.validToEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }

    val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horario fijo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Circuito", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    circuits.forEach { c ->
                        FilterChip(
                            selected = selectedCode == c.code,
                            onClick = { selectedCode = c.code },
                            label = { Text(c.code) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startStr,
                        onValueChange = { startStr = it },
                        label = { Text("Inicio (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endStr,
                        onValueChange = { endStr = it },
                        label = { Text("Fin (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Días de la semana", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEachIndexed { idx, dLabel ->
                        val checked = (daysMask and (1 shl idx)) != 0
                        FilterChip(
                            selected = checked,
                            onClick = { daysMask = daysMask xor (1 shl idx) },
                            label = { Text(dLabel) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = validFrom,
                        onValueChange = { validFrom = it },
                        label = { Text("Desde (opc.)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = validTo,
                        onValueChange = { validTo = it },
                        label = { Text("Hasta (opc.)") },
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
                onSave(existing?.id, selectedCode, daysMask, startStr, endStr, validFrom, validTo, label)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ForecastEditorDialog(
    circuits: List<Circuit>,
    existing: ExplicitForecast?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, IntervalKind, String) -> Unit
) {
    var dateStr by remember {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.dateEpochDay).toString() }
                ?: LocalDate.now(ScheduleEngine.HAVANA_ZONE).toString()
        )
    }
    var startStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.startMinuteOfDay) } ?: "08:00") }
    var endStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.endMinuteOfDay) } ?: "12:00") }
    var codesCsv by remember { mutableStateOf(existing?.circuitCodes?.joinToString(",") ?: "") }
    var kind by remember { mutableStateOf(existing?.intervalKind ?: IntervalKind.SERVICE) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pronóstico por fecha") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Fecha (AAAA-MM-DD)*") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startStr,
                        onValueChange = { startStr = it },
                        label = { Text("Inicio (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endStr,
                        onValueChange = { endStr = it },
                        label = { Text("Fin (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = codesCsv,
                    onValueChange = { codesCsv = it.uppercase() },
                    label = { Text("Circuitos (ej. A210, A177)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    circuits.forEach { c ->
                        val selected = codesCsv.split(",").map { it.trim() }.contains(c.code)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val current = codesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                                if (c.code in current) current.remove(c.code) else current.add(c.code)
                                codesCsv = current.joinToString(",")
                            },
                            label = { Text(c.code) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == IntervalKind.SERVICE,
                        onClick = { kind = IntervalKind.SERVICE },
                        label = { Text("Con servicio") }
                    )
                    FilterChip(
                        selected = kind == IntervalKind.OUTAGE,
                        onClick = { kind = IntervalKind.OUTAGE },
                        label = { Text("Sin servicio") }
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(existing?.id, dateStr, startStr, endStr, codesCsv, kind, notes)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ExceptionEditorDialog(
    circuits: List<Circuit>,
    existing: ManualException?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, Boolean, String) -> Unit
) {
    var code by remember { mutableStateOf(existing?.circuitCode ?: circuits.firstOrNull()?.code.orEmpty()) }
    var dateStr by remember {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.dateEpochDay).toString() }
                ?: LocalDate.now(ScheduleEngine.HAVANA_ZONE).toString()
        )
    }
    var startStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.startMinuteOfDay) } ?: "00:00") }
    var endStr by remember { mutableStateOf(existing?.let { CircuitsViewModel.formatMinute24h(it.endMinuteOfDay) } ?: "24:00") }
    var hasService by remember { mutableStateOf(existing?.hasService ?: false) }
    var reason by remember { mutableStateOf(existing?.reason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excepción manual") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    circuits.forEach { c ->
                        FilterChip(
                            selected = code == c.code,
                            onClick = { code = c.code },
                            label = { Text(c.code) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startStr, onValueChange = { startStr = it }, label = { Text("Inicio") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endStr, onValueChange = { endStr = it }, label = { Text("Fin") }, modifier = Modifier.weight(1f))
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
                onSave(existing?.id, code, dateStr, startStr, endStr, hasService, reason)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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
    var type by remember { mutableStateOf(existing?.relationType ?: RelationType.INFORMATIVA) }
    var syncEnabled by remember { mutableStateOf(existing?.syncRuleEnabled ?: false) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relación entre circuitos") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Circuito origen", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    circuits.forEach { c ->
                        FilterChip(selected = src == c.code, onClick = { src = c.code }, label = { Text(c.code) })
                    }
                }
                Text("Circuito relacionado", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    circuits.forEach { c ->
                        FilterChip(selected = tgt == c.code, onClick = { tgt = c.code }, label = { Text(c.code) })
                    }
                }
                Text("Tipo de relación", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RelationType.entries.forEach { rt ->
                        FilterChip(
                            selected = type == rt,
                            onClick = { type = rt },
                            label = { Text(rt.name.lowercase().replace('_', ' ')) }
                        )
                    }
                }
                SwitchRow("Aplicar regla de sincronización automática", syncEnabled) { syncEnabled = it }
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
                onSave(existing?.id, src, tgt, type, syncEnabled, notes)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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
