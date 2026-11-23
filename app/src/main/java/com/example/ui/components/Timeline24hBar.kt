package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun Timeline24hBar(
    date: LocalDate,
    segments: List<TimelineSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 32.dp,
    currentTimeMillis: Long? = null,
    selectedSegment: TimelineSegment? = null,
    showHourAxis: Boolean = true,
    use24h: Boolean = false,
    zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
    sunriseMinutes: Int = 6 * 60 + 30,
    sunsetMinutes: Int = 18 * 60 + 30,
    enableElectricAnimations: Boolean = true,
    onSegmentTap: (TimelineSegment) -> Unit = {}
) {
    val statusColors = LocalElectricStatusColors.current
    val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val totalDayMillis = (dayEnd - dayStart).coerceAtLeast(1L).toFloat()

    val dividerColor = MaterialTheme.colorScheme.surface
    val highlightColor = MaterialTheme.colorScheme.primary

    val hasServiceSegments = remember(segments) {
        segments.any { it.state == ScheduledState.SERVICE_SCHEDULED }
    }
    val electricPhase = if (enableElectricAnimations && hasServiceSegments) {
        val transition = rememberInfiniteTransition(label = "timeline_electric_static")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "electric_phase"
        )
        phase
    } else {
        0f
    }

    val dayNightBrush = remember(sunriseMinutes, sunsetMinutes) {
        val sr = (sunriseMinutes / 1440f).coerceIn(0.12f, 0.45f)
        val ss = (sunsetMinutes / 1440f).coerceIn(0.55f, 0.90f)
        val nightDeep = Color(0xFF141E3C)
        val twilight = Color(0xFF2B3F75)
        val dawnGold = Color(0xFFFFB74D)
        val skyBlue = Color(0xFF38BDF8)
        val noonSun = Color(0xFFFDE047)
        val duskCoral = Color(0xFFFF7043)

        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to nightDeep,
                (sr - 0.05f).coerceAtLeast(0.02f) to twilight,
                sr to dawnGold,
                (sr + 0.08f).coerceAtMost(0.48f) to skyBlue,
                0.50f to noonSun,
                (ss - 0.08f).coerceAtLeast(0.52f) to skyBlue,
                ss to duskCoral,
                (ss + 0.05f).coerceAtMost(0.98f) to twilight,
                1.00f to nightDeep
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(width = 3.dp, brush = dayNightBrush, shape = RoundedCornerShape(12.dp))
                .padding(3.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(9.dp))
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
                        cornerRadius = CornerRadius(9f, 9f)
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

                        // Animated electric current + static crackling arcs inside green service blocks
                        if (enableElectricAnimations && seg.state == ScheduledState.SERVICE_SCHEDULED && segWidth > 4f) {
                            clipRect(
                                left = left,
                                top = 0f,
                                right = (left + segWidth).coerceAtMost(w),
                                bottom = h
                            ) {
                                // Flowing electric charge wave
                                val bandHalf = (w * 0.14f).coerceAtLeast(28f)
                                val waveCenter = -bandHalf + electricPhase * (w + bandHalf * 2f)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFFA7F3D0).copy(alpha = 0.38f),
                                            Color.White.copy(alpha = 0.28f),
                                            Color(0xFFA7F3D0).copy(alpha = 0.38f),
                                            Color.Transparent
                                        ),
                                        startX = waveCenter - bandHalf,
                                        endX = waveCenter + bandHalf
                                    ),
                                    topLeft = Offset(left, 0f),
                                    size = Size(segWidth, h)
                                )

                                // Two live static/current filaments crackling across the block
                                val stepPx = 7.dp.toPx().coerceAtLeast(6f)
                                val steps = ((segWidth / stepPx).toInt() + 1).coerceAtLeast(2)
                                val twoPi = (2.0 * PI).toFloat()

                                val upperPath = Path()
                                val lowerPath = Path()
                                for (s in 0..steps) {
                                    val px = (left + s * stepPx).coerceAtMost(left + segWidth)
                                    val n1 = sin((px * 0.09f) - electricPhase * twoPi * 3f)
                                    val n2 = sin((px * 0.23f) + electricPhase * twoPi * 5f)
                                    val yTop = h * 0.34f + (n1 * 0.6f + n2 * 0.4f) * (h * 0.16f)
                                    val yBot = h * 0.68f + (sin((px * 0.13f) + electricPhase * twoPi * 4f) * 0.6f - n2 * 0.4f) * (h * 0.15f)
                                    if (s == 0) {
                                        upperPath.moveTo(px, yTop)
                                        lowerPath.moveTo(px, yBot)
                                    } else {
                                        upperPath.lineTo(px, yTop)
                                        lowerPath.lineTo(px, yBot)
                                    }
                                }
                                drawPath(
                                    path = upperPath,
                                    color = Color(0xFFD1FAE5).copy(alpha = 0.55f),
                                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawPath(
                                    path = lowerPath,
                                    color = Color.White.copy(alpha = 0.36f),
                                    style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Tiny drifting static spark nodes inside the block
                                val sparkCount = (segWidth / 28f).toInt().coerceIn(1, 6)
                                for (k in 0 until sparkCount) {
                                    val localFrac = ((k.toFloat() / sparkCount) + electricPhase * 1.35f) % 1f
                                    val sx = left + localFrac * segWidth
                                    val sy = h * (0.25f + 0.5f * ((sin((k * 2.1 + electricPhase * 12.0)) + 1.0).toFloat() * 0.5f))
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.72f),
                                        radius = 1.4.dp.toPx(),
                                        center = Offset(sx, sy)
                                    )
                                }
                            }
                        }

                        if (selectedSegment != null &&
                            selectedSegment.startMillis == seg.startMillis &&
                            selectedSegment.endMillis == seg.endMillis
                        ) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.24f),
                                topLeft = Offset(left, 0f),
                                size = Size(segWidth, h)
                            )
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(left, 0f),
                                size = Size(segWidth, 3.dp.toPx())
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
                    val x = (nowFrac * w).coerceIn(3f, (w - 3f).coerceAtLeast(3f))
                    val auraHalf = 8.dp.toPx()

                    // Soft translucent white halo gradient behind the needle
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            startX = x - auraHalf,
                            endX = x + auraHalf
                        ),
                        topLeft = Offset(x - auraHalf, 0f),
                        size = Size(auraHalf * 2f, h)
                    )

                    // Subtle dark contrast backing so white needle pops on every color
                    drawLine(
                        color = Color.Black.copy(alpha = 0.34f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 4.2.dp.toPx()
                    )

                    // Crisp white vertical line
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.4.dp.toPx()
                    )

                    // Top downward white arrow / pointer ("flechita")
                    val arrowHalf = 4.5.dp.toPx()
                    val arrowDepth = 5.5.dp.toPx().coerceAtMost(h * 0.35f)
                    val arrowPath = Path().apply {
                        moveTo(x - arrowHalf, 0f)
                        lineTo(x + arrowHalf, 0f)
                        lineTo(x, arrowDepth)
                        close()
                    }
                    drawPath(path = arrowPath, color = Color.White)

                    // Bottom bright anchor cap
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, (h - 2.5.dp.toPx()).coerceAtLeast(0f))
                    )
                }
            }
        }

        if (showHourAxis) {
            Spacer(modifier = Modifier.height(4.dp))
            val axisLabels = if (use24h) {
                listOf("00", "04", "08", "12", "16", "20", "24")
            } else {
                listOf("12a", "4a", "8a", "12p", "4p", "8p", "12a")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                axisLabels.forEach { hour ->
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
    use24h: Boolean = false,
    zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
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
                    text = "${ScheduleEngine.formatTime(segment.startMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(segment.endMillis, use24h, zoneId)} (${ScheduleEngine.formatDurationHuman(segment.durationMillis)})",
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
    use24h: Boolean = false,
    zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE,
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
                    text = "${ScheduleEngine.formatTime(seg.startMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(seg.endMillis, use24h, zoneId)}",
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
