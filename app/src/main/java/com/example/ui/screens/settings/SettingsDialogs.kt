package com.example.ui.screens.settings

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ImportMergeMode
import com.example.domain.engine.ScheduleEngine
import com.example.transfer.ConfigCodec
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.time.ZoneId

@Composable
fun ExportConfigDialog(
    bundle: ExportBundle,
    use24h: Boolean,
    zoneId: ZoneId,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onSaveJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportación generada (v${bundle.configVersion})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Generado: ${ScheduleEngine.formatDateTimeReadable(bundle.generatedAtMillis, use24h, zoneId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Código alfanumérico (${bundle.alphanumericCode.length} caracteres):",
                    style = MaterialTheme.typography.labelMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bundle.alphanumericCode.take(220) + if (bundle.alphanumericCode.length > 220) "…" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopyCode(bundle.alphanumericCode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar")
                    }
                    OutlinedButton(
                        onClick = { onShareCode(bundle.alphanumericCode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir")
                    }
                }
                Button(
                    onClick = { onSaveJson("luz_isla_config_v${bundle.configVersion}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar como archivo JSON")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun ImportReviewDialog(
    review: PendingImportReview,
    use24h: Boolean,
    zoneId: ZoneId,
    onConfirm: (ImportMergeMode, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val s = review.summary
    var includePersonal by remember { mutableStateOf(false) }
    val generatedReadable = remember(s.generatedAtIso, use24h, zoneId) {
        ConfigCodec.parseGeneratedAtMillis(s.generatedAtIso)?.let {
            ScheduleEngine.formatDateTimeReadable(it, use24h, zoneId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resumen de importación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Origen: ${s.sourceName} (v${s.configVersion})", fontWeight = FontWeight.Bold)
                if (generatedReadable != null) {
                    Text(
                        text = "Fecha de exportación: $generatedReadable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("• Circuitos: ${s.circuitCount}")
                Text("• Ciclos: ${s.cycleCount} • Grupos: ${s.groupCount}")
                Text("• Horarios fijos: ${s.fixedRuleCount}")
                Text("• Pronósticos por fecha: ${s.forecastCount}")
                Text("• Excepciones: ${s.exceptionCount} • Relaciones: ${s.relationCount}")
                Text(
                    text = if (s.sha256Verified) "Integridad SHA-256: Verificada correctamente" else "Integridad SHA-256: No incluida en JSON externo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (s.includesPersonalData) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Importar también favoritos y nombres personales",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = includePersonal, onCheckedChange = { includePersonal = it })
                    }
                }
                Text(
                    text = "Se guardará un respaldo automático de tu configuración actual antes de aplicar los cambios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { onConfirm(ImportMergeMode.MERGE_KEEP_PERSONAL, includePersonal) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Combinar (conservar mis datos)")
                }
                OutlinedButton(
                    onClick = { onConfirm(ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL, includePersonal) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reemplazar programación")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@Composable
fun DayNightTimeDialog(
    initialSunriseMinutes: Int,
    initialSunsetMinutes: Int,
    use24h: Boolean,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sunrise by remember(initialSunriseMinutes) { mutableIntStateOf(initialSunriseMinutes) }
    var sunset by remember(initialSunsetMinutes) { mutableIntStateOf(initialSunsetMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horario de día y noche") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Define a qué hora inicia el tono visual diurno y a qué hora cambia a modo nocturno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MinuteStepperRow(
                    label = "Amanecer (Inicio de día)",
                    minutes = sunrise,
                    use24h = use24h,
                    onMinus = { sunrise = (sunrise - 15).coerceAtLeast(4 * 60) },
                    onPlus = { sunrise = (sunrise + 15).coerceAtMost(10 * 60) }
                )
                MinuteStepperRow(
                    label = "Atardecer (Inicio de noche)",
                    minutes = sunset,
                    use24h = use24h,
                    onMinus = { sunset = (sunset - 15).coerceAtLeast(16 * 60) },
                    onPlus = { sunset = (sunset + 15).coerceAtMost(22 * 60) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(sunrise, sunset) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun MinuteStepperRow(
    label: String,
    minutes: Int,
    use24h: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = onMinus) { Text("-15m") }
            Text(
                text = ScheduleEngine.formatMinuteOfDay(minutes, use24h),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onPlus) { Text("+15m") }
        }
    }
}
