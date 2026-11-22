package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.RuleSource
import com.example.domain.model.ScheduledState
import com.example.domain.model.TimelineSegment
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalElectricStatusColors
import java.time.LocalDate

@Composable
fun Timeline24hBar(
    date: LocalDate,
    segments: List<TimelineSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 32.dp,
    currentTimeMillis: Long? = null,
    selectedSegment: TimelineSegment? = null,
    showHourAxis: Boolean = true,
    onSegmentTap: (TimelineSegment) -> Unit = {}
) {
    val statusColors = LocalElectricStatusColors.current
    val dayStart = date.atStartOfDay(ScheduleEngine.HAVANA_ZONE).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(ScheduleEngine.HAVANA_ZONE).toInstant().toEpochMilli()
    val totalDayMillis = (dayEnd - dayStart).coerceAtLeast(1L).toFloat()

    val dividerColor = MaterialTheme.colorScheme.surface
    val needleColor = MaterialTheme.colorScheme.onSurface
    val highlightColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(segments, dayStart, totalDayMillis) {
                    detectTapGestures { tapOffset ->
                        val frac = (tapOffset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val tappedMillis = dayStart + (frac * totalDayMillis).toLong()
                        val hit = segments.firstOrNull {
                            tappedMillis >= it.startMillis && tappedMillis < it.endMillis
                        } ?: segments.lastOrNull()
                        if (hit != null) onSegmentTap(hit)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            if (segments.isEmpty()) {
                drawRoundRect(
                    color = statusColors.unknown,
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            } else {
                segments.forEachIndexed { idx, seg ->
                    val startFrac = ((seg.startMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
                    val endFrac = ((seg.endMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
                    val left = startFrac * w
                    val segWidth = ((endFrac - startFrac) * w).coerceAtLeast(2f)

                    val segColor = when (seg.state) {
                        ScheduledState.SERVICE_SCHEDULED -> statusColors.service
                        ScheduledState.OUTAGE_SCHEDULED -> statusColors.outage
                        ScheduledState.UNKNOWN -> statusColors.unknown
                    }
                    drawRect(
                        color = segColor,
                        topLeft = Offset(left, 0f),
                        size = Size(segWidth, h)
                    )

                    if (selectedSegment != null &&
                        selectedSegment.startMillis == seg.startMillis &&
                        selectedSegment.endMillis == seg.endMillis
                    ) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(left, 0f),
                            size = Size(segWidth, h)
                        )
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(left, 0f),
                            size = Size(segWidth, 4.dp.toPx())
                        )
                    }

                    if (idx > 0) {
                        drawLine(
                            color = dividerColor,
                            start = Offset(left, 0f),
                            end = Offset(left, h),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            if (currentTimeMillis != null && currentTimeMillis in dayStart..dayEnd) {
                val nowFrac = ((currentTimeMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
                val x = nowFrac * w
                drawLine(
                    color = needleColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        if (showHourAxis) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00", "04", "08", "12", "16", "20", "24").forEach { hour ->
                    Text(
                        text = hour,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineSegmentLegend(modifier: Modifier = Modifier) {
    val statusColors = LocalElectricStatusColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendChip(color = statusColors.service, label = "Con servicio")
        LegendChip(color = statusColors.outage, label = "Sin servicio")
        LegendChip(color = statusColors.unknown, label = "Sin info")
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TimelineSegmentDetailCard(
    segment: TimelineSegment,
    modifier: Modifier = Modifier
) {
    val statusColors = LocalElectricStatusColors.current
    val (accent, container, icon, stateText) = when (segment.state) {
        ScheduledState.SERVICE_SCHEDULED -> Quad(
            statusColors.service,
            statusColors.serviceContainer,
            Icons.Filled.Bolt,
            "Con servicio programado"
        )
        ScheduledState.OUTAGE_SCHEDULED -> Quad(
            statusColors.outage,
            statusColors.outageContainer,
            Icons.Filled.PowerOff,
            "Interrupción programada"
        )
        ScheduledState.UNKNOWN -> Quad(
            statusColors.unknown,
            statusColors.unknownContainer,
            Icons.Filled.HelpOutline,
            "Sin información disponible"
        )
    }

    val sourceLabel = when (segment.source) {
        RuleSource.MANUAL_EXCEPTION -> "Excepción manual"
        RuleSource.EXPLICIT_FORECAST -> if (segment.isHistoricalTest) "Pronóstico histórico de prueba" else "Pronóstico explícito"
        RuleSource.FIXED_RULE -> "Horario fijo"
        RuleSource.ROTATING_GROUP -> "Ciclo rotativo"
        RuleSource.SYNCHRONIZED_RELATION -> "Relación sincronizada"
        RuleSource.NONE -> "Sin programación"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent)
            ) {
                Icon(icon, contentDescription = stateText, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ScheduleEngine.formatTime12h(segment.startMillis)} – ${ScheduleEngine.formatTime12h(segment.endMillis)} (${ScheduleEngine.formatDurationHuman(segment.durationMillis)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Origen: $sourceLabel" + if (segment.notes.isNotBlank()) " • ${segment.notes}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TimelineIntervalsSummaryList(
    segments: List<TimelineSegment>,
    selectedSegment: TimelineSegment?,
    onSelect: (TimelineSegment) -> Unit
) {
    val statusColors = LocalElectricStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            val accent = when (seg.state) {
                ScheduledState.SERVICE_SCHEDULED -> statusColors.service
                ScheduledState.OUTAGE_SCHEDULED -> statusColors.outage
                ScheduledState.UNKNOWN -> statusColors.unknown
            }
            val label = when (seg.state) {
                ScheduledState.SERVICE_SCHEDULED -> "Con servicio"
                ScheduledState.OUTAGE_SCHEDULED -> "Sin servicio"
                ScheduledState.UNKNOWN -> "Sin información"
            }
            val isSelected = selectedSegment?.startMillis == seg.startMillis && selectedSegment.endMillis == seg.endMillis
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(seg) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${ScheduleEngine.formatTime12h(seg.startMillis)} – ${ScheduleEngine.formatTime12h(seg.endMillis)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$label (${ScheduleEngine.formatDurationHuman(seg.durationMillis)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
