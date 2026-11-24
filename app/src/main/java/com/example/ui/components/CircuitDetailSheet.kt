package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.CircuitStatusEvaluation
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import com.example.domain.model.TimelineSegment
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalElectricStatusColors
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitDetailBottomSheet(
    evaluation: CircuitStatusEvaluation,
    snapshot: ScheduleSnapshot,
    nowMillis: Long,
    use24h: Boolean = false,
    zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    sunriseMinutes: Int = 6 * 60 + 30,
    sunsetMinutes: Int = 18 * 60 + 30,
    enableElectricAnimations: Boolean = true,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetPrimaryHome: (String) -> Unit,
    onShareText: (CircuitStatusEvaluation) -> Unit,
    onOpenInCalendar: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statusColors = LocalElectricStatusColors.current
    val circuit = evaluation.circuit
    val today = remember(nowMillis, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    }
    var selectedSegment by remember(evaluation.circuit.code) {
        mutableStateOf<TimelineSegment?>(
            evaluation.todaySegments.firstOrNull { nowMillis >= it.startMillis && nowMillis < it.endMillis }
        )
    }
    val next7Days = remember(circuit.code, snapshot, today, zoneId) {
        ScheduleEngine.getTimelineForNextDays(circuit.code, snapshot, today, 7, zoneId)
    }

    val (accent, container, statusIcon, statusLabel) = when (evaluation.state) {
        ScheduledState.SERVICE_SCHEDULED -> DetailVisual(
            statusColors.service,
            statusColors.serviceContainer,
            Icons.Filled.Bolt,
            "Con servicio programado"
        )
        ScheduledState.OUTAGE_SCHEDULED -> DetailVisual(
            statusColors.outage,
            statusColors.outageContainer,
            Icons.Filled.PowerOff,
            "Sin servicio programado"
        )
        ScheduledState.UNKNOWN -> DetailVisual(
            statusColors.unknown,
            statusColors.unknownContainer,
            Icons.Filled.HelpOutline,
            "Sin información disponible"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("circuit_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = circuit.code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (circuit.isPrimaryHome) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = statusColors.favoriteContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Home,
                                        contentDescription = "Mi vivienda",
                                        tint = statusColors.favorite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Mi vivienda",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColors.favorite
                                    )
                                }
                            }
                        }
                    }
                    if (circuit.customName.isNotBlank()) {
                        Text(
                            text = circuit.customName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = { onSetPrimaryHome(circuit.code) },
                    modifier = Modifier.testTag("detail_primary_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Marcar como mi vivienda",
                        tint = if (circuit.isPrimaryHome) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onToggleFavorite(circuit.code) },
                    modifier = Modifier.testTag("detail_favorite_button")
                ) {
                    Icon(
                        imageVector = if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (circuit.isFavorite) "Quitar de favoritos" else "Marcar favorito",
                        tint = if (circuit.isFavorite) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (circuit.comment.isNotBlank()) {
                Text(
                    text = "Comentario: ${circuit.comment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val groupText = evaluation.group?.name ?: when (circuit.scheduleType) {
                ScheduleType.FIXED -> "Horario fijo"
                ScheduleType.CUSTOM -> "Pronóstico personalizado"
                ScheduleType.ROTATING -> "Grupo rotativo sin asignar"
                ScheduleType.NONE -> "Sin grupo asignado"
            }
            Text(
                text = "Grupo / Modalidad: $groupText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = container,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent)
                        ) {
                            Icon(statusIcon, contentDescription = statusLabel, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Estado: $statusLabel",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Estado calculado según programación (no es medición física en vivo)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = accent.copy(alpha = 0.2f))

                    if (evaluation.currentIntervalStartMillis != null && evaluation.currentIntervalEndMillis != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Inicio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    ScheduleEngine.formatTime(evaluation.currentIntervalStartMillis, use24h, zoneId),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Finalización", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    ScheduleEngine.formatTime(evaluation.currentIntervalEndMillis, use24h, zoneId),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        }
                    }

                    if (evaluation.remainingMillis != null) {
                        Text(
                            text = "Tiempo restante: ${ScheduleEngine.formatDurationHms(evaluation.remainingMillis)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val nextServiceText = if (evaluation.nextServiceStartMillis != null && evaluation.nextServiceEndMillis != null) {
                        val nextDay = Instant.ofEpochMilli(evaluation.nextServiceStartMillis)
                            .atZone(zoneId).toLocalDate()
                        val dayTag = if (nextDay == today) "Hoy" else ScheduleEngine.formatShortDate(nextDay)
                        "$dayTag ${ScheduleEngine.formatTime(evaluation.nextServiceStartMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(evaluation.nextServiceEndMillis, use24h, zoneId)}"
                    } else {
                        "Según programación / Sin datos registrados"
                    }
                    Text(
                        text = "Próximo servicio: $nextServiceText",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (evaluation.isHistoricalTest) {
                Surface(
                    color = statusColors.favoriteContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nota: El período mostrado proviene de datos históricos de prueba (19–20 nov 2026) y no constituye programación vigente.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (evaluation.relations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Circuitos relacionados",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    evaluation.relations.forEach { rel ->
                        val other = if (rel.sourceCircuitCode.equals(circuit.code, true)) rel.targetCircuitCode else rel.sourceCircuitCode
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${rel.sourceCircuitCode} → ${rel.targetCircuitCode} (${rel.relationType.name.lowercase().replace('_', ' ')}): ${rel.notes.ifBlank { "Relación con $other" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Continuous endless 3D wheel timeline
            Continuous3DTickerTimeline(
                circuitCode = circuit.code,
                snapshot = snapshot,
                nowMillis = nowMillis,
                use24h = use24h,
                zoneId = zoneId,
                onSegmentFocused = { selectedSegment = it }
            )

            // Today's 24h bar timeline
            Text(
                text = "Resumen de hoy (24h)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Timeline24hBar(
                date = today,
                segments = evaluation.todaySegments,
                currentTimeMillis = nowMillis,
                selectedSegment = selectedSegment,
                use24h = use24h,
                zoneId = zoneId,
                sunriseMinutes = sunriseMinutes,
                sunsetMinutes = sunsetMinutes,
                enableElectricAnimations = enableElectricAnimations,
                onSegmentTap = { selectedSegment = it }
            )
            TimelineSegmentLegend()
            TimelineIntervalsSummaryList(
                segments = evaluation.todaySegments,
                selectedSegment = selectedSegment,
                use24h = use24h,
                zoneId = zoneId,
                onSelect = { selectedSegment = it }
            )

            // Upcoming 7 days quick summary
            Text(
                text = "Próximos 7 días",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                next7Days.forEach { (date, segs) ->
                    val serviceWindows = segs.filter { it.state == ScheduledState.SERVICE_SCHEDULED }
                    val summary = if (segs.all { it.state == ScheduledState.UNKNOWN }) {
                        "Sin pronóstico"
                    } else if (serviceWindows.isEmpty()) {
                        "Sin períodos de servicio"
                    } else {
                        serviceWindows.joinToString(", ") {
                            "${ScheduleEngine.formatTime(it.startMillis, use24h, zoneId)}–${ScheduleEngine.formatTime(it.endMillis, use24h, zoneId)}"
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ScheduleEngine.formatDateEs(date),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.45f)
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.55f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenInCalendar(circuit.code) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_calendar_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Calendario", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { onShareText(evaluation) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_share_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Compartir", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class DetailVisual(
    val accent: Color,
    val container: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)
