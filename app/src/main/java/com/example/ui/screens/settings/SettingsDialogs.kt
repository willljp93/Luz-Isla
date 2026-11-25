package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.ImportMergeMode
import com.example.domain.engine.ScheduleEngine
import com.example.sync.SunriseSunsetService
import com.example.transfer.ConfigCodec
import com.example.ui.components.TimeSelectorField
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
        title = { Text("Exportación generada (v${bundle.configVersionName})") },
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onShareCode(bundle.alphanumericCode) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = { onSaveJson("luz_isla_config_${bundle.configVersionName}.json") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar archivo JSON", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
                Text("Origen: ${s.sourceName} (v${s.displayVersion})", fontWeight = FontWeight.Bold)
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
                Text("• Horarios por fecha: ${s.forecastCount}")
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
    isAutoMode: Boolean,
    isSyncing: Boolean,
    statusMessage: String,
    cachedDate: String,
    initialSunriseMinutes: Int,
    initialSunsetMinutes: Int,
    use24h: Boolean,
    onSyncNow: () -> Unit,
    onSelectAuto: () -> Unit,
    onSaveManual: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var autoSelected by remember(isAutoMode) { mutableStateOf(isAutoMode) }
    var sunrise by remember(initialSunriseMinutes) { mutableIntStateOf(initialSunriseMinutes) }
    var sunset by remember(initialSunsetMinutes) { mutableIntStateOf(initialSunsetMinutes) }

    LaunchedEffect(initialSunriseMinutes, initialSunsetMinutes, isAutoMode) {
        if (isAutoMode) {
            sunrise = initialSunriseMinutes
            sunset = initialSunsetMinutes
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horario de día y noche") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configura cómo se determinan el amanecer y el atardecer para los degradados día/noche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = autoSelected,
                        onClick = { autoSelected = true },
                        leadingIcon = {
                            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Automático", maxLines = 1, softWrap = false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("solar_mode_auto_chip")
                    )
                    FilterChip(
                        selected = !autoSelected,
                        onClick = { autoSelected = false },
                        leadingIcon = {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Manual", maxLines = 1, softWrap = false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("solar_mode_manual_chip")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (autoSelected) {
                                "Sincronización automática · ${SunriseSunsetService.DEFAULT_LOCATION_LABEL}"
                            } else {
                                "Modo manual activo: la sincronización automática no sobrescribirá tus horas."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = buildString {
                                append(statusMessage)
                                if (cachedDate.isNotBlank()) {
                                    append(" • Caché: $cachedDate")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                autoSelected = true
                                onSyncNow()
                            },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("solar_dialog_sync_now_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSyncing) "Sincronizando…" else "Sincronizar ahora (Nueva Gerona)")
                        }
                    }
                }

                TimeSelectorField(
                    label = "Amanecer (Inicio de día)",
                    minuteOfDay = sunrise,
                    use24h = use24h,
                    onTimeSelected = { picked ->
                        sunrise = picked.coerceIn(0, 1439)
                        autoSelected = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TimeSelectorField(
                    label = "Atardecer (Inicio de noche)",
                    minuteOfDay = sunset,
                    use24h = use24h,
                    onTimeSelected = { picked ->
                        sunset = picked.coerceIn(0, 1439)
                        autoSelected = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching { uriHandler.openUri(SunriseSunsetService.ATTRIBUTION_URL) }
                        }
                        .padding(vertical = 4.dp)
                        .testTag("solar_attribution_dialog_link")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir sunrisesunset.io",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Datos astronómicos cortesía de sunrisesunset.io",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (autoSelected) {
                        onSelectAuto()
                    } else {
                        onSaveManual(sunrise, sunset)
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.testTag("solar_dialog_confirm_button")
            ) {
                Text(
                    text = if (autoSelected) "Usar Automático" else "Guardar Manual",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", maxLines = 1, softWrap = false) }
        }
    )
}
