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
import androidx.compose.ui.graphics.lerp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

private data class TimelineSegSpan(
    val seg: TimelineSegment,
    val startFrac: Float,
    val endFrac: Float,
    val color: Color
)

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

    val highlightColor = MaterialTheme.colorScheme.primary

    val hasServiceSegments = remember(segments) {
        segments.any { it.state == ScheduledState.SERVICE_SCHEDULED }
    }
    val electricPhase = if (enableElectricAnimations && hasServiceSegments) {
        val transition = rememberInfiniteTransition(label = "timeline_electric_flow")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "electric_phase"
        )
        phase
    } else {
        0f
    }

    // Simplified night sky <-> day sky border gradient (fewer colors, atmospheric blue progression)
    val dayNightBrush = remember(sunriseMinutes, sunsetMinutes) {
        val sr = (sunriseMinutes / 1440f).coerceIn(0.14f, 0.44f)
        val ss = (sunsetMinutes / 1440f).coerceIn(0.56f, 0.88f)
        val nightSky = Color(0xFF0B162C)
        val transitionSky = Color(0xFF1D4ED8)
        val daySky = Color(0xFF38BDF8)
        val middaySky = Color(0xFF7DD3FC)

        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to nightSky,
                (sr - 0.04f).coerceAtLeast(0.02f) to nightSky,
                (sr + 0.03f).coerceAtMost(0.46f) to transitionSky,
                (sr + 0.11f).coerceAtMost(0.49f) to daySky,
                0.50f to middaySky,
                (ss - 0.11f).coerceAtLeast(0.51f) to daySky,
                (ss - 0.03f).coerceAtLeast(0.54f) to transitionSky,
                (ss + 0.04f).coerceAtMost(0.98f) to nightSky,
                1.00f to nightSky
            )
        )
    }

    val spans = remember(segments, dayStart, totalDayMillis, statusColors) {
        segments.mapNotNull { seg ->
            val s = ((seg.startMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
            val e = ((seg.endMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
            if (e <= s) null
            else {
                val c = when (seg.state) {
                    ScheduledState.SERVICE_SCHEDULED -> statusColors.service
                    ScheduledState.OUTAGE_SCHEDULED -> statusColors.outage
                    ScheduledState.UNKNOWN -> statusColors.unknown
                }
                TimelineSegSpan(seg, s, e, c)
            }
        }
    }

    val blendedBaseBrush = remember(spans, statusColors.unknown) {
        if (spans.isEmpty()) {
            Brush.horizontalGradient(listOf(statusColors.unknown, statusColors.unknown))
        } else if (spans.size == 1) {
            Brush.horizontalGradient(listOf(spans.first().color, spans.first().color))
        } else {
            val stops = mutableListOf<Pair<Float, Color>>()
            stops += 0f to spans.first().color
            for (i in 0 until spans.lastIndex) {
                val curr = spans[i]
                val next = spans[i + 1]
                if (curr.stateColorEquals(next)) continue
                val boundary = next.startFrac.coerceIn(0f, 1f)
                val currLen = (curr.endFrac - curr.startFrac).coerceAtLeast(0.002f)
                val nextLen = (next.endFrac - next.startFrac).coerceAtLeast(0.002f)
                val halfBlend = minOf(0.020f, currLen * 0.36f, nextLen * 0.36f)
                val leftStop = (boundary - halfBlend).coerceAtLeast(stops.last().first)
                val rightStop = (boundary + halfBlend).coerceAtMost(1f)
                if (leftStop > stops.last().first) {
                    stops += leftStop to curr.color
                }
                stops += boundary.coerceIn(leftStop, rightStop) to lerp(curr.color, next.color, 0.5f)
                if (rightStop > boundary) {
                    stops += rightStop to next.color
                }
            }
            if (stops.last().first < 1f) {
                stops += 1f to spans.last().color
            } else {
                stops[stops.lastIndex] = 1f to spans.last().color
            }
            Brush.horizontalGradient(colorStops = stops.toTypedArray())
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(width = 2.6.dp, brush = dayNightBrush, shape = RoundedCornerShape(12.dp))
                .padding(2.6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(9.5.dp))
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

                if (spans.isEmpty()) {
                    drawRoundRect(
                        color = statusColors.unknown,
                        topLeft = Offset.Zero,
                        size = Size(w, h),
                        cornerRadius = CornerRadius(9.5f, 9.5f)
                    )
                } else {
                    // 1. Continuous blended base gradient (zero hard vertical cuts or dark lines)
                    drawRect(
                        brush = blendedBaseBrush,
                        topLeft = Offset.Zero,
                        size = Size(w, h)
                    )

                    val twoPi = (2.0 * PI).toFloat()

                    // 2. Render inert/powered-off textures on OUTAGE_SCHEDULED segments
                    spans.forEachIndexed { idx, span ->
                        if (span.seg.state != ScheduledState.OUTAGE_SCHEDULED) return@forEachIndexed
                        val left = span.startFrac * w
                        val right = span.endFrac * w
                        val segW = right - left
                        if (segW <= 3f) return@forEachIndexed

                        val fadeLeft = if (idx > 0 && spans[idx - 1].seg.state != ScheduledState.OUTAGE_SCHEDULED) {
                            minOf(14.dp.toPx(), segW * 0.35f)
                        } else 0f
                        val fadeRight = if (idx < spans.lastIndex && spans[idx + 1].seg.state != ScheduledState.OUTAGE_SCHEDULED) {
                            minOf(14.dp.toPx(), segW * 0.35f)
                        } else 0f

                        clipRect(left = left, top = 0f, right = right, bottom = h) {
                            // Subtle "powered-down" dark-coral overlay that softens near green boundaries
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to if (fadeLeft > 0f) Color.Transparent else Color(0xFF450A0A).copy(alpha = 0.22f),
                                        (fadeLeft / segW).coerceIn(0f, 0.45f) to Color(0xFF450A0A).copy(alpha = 0.22f),
                                        (1f - (fadeRight / segW)).coerceIn(0.55f, 1f) to Color(0xFF450A0A).copy(alpha = 0.22f),
                                        1f to if (fadeRight > 0f) Color.Transparent else Color(0xFF450A0A).copy(alpha = 0.22f)
                                    ),
                                    startX = left,
                                    endX = right
                                ),
                                topLeft = Offset(left, 0f),
                                size = Size(segW, h)
                            )

                            // Cold, sagging low-energy horizontal filament indicating inactive line
                            val stepPx = 6.dp.toPx().coerceAtLeast(5f)
                            val steps = ((segW / stepPx).toInt() + 1).coerceAtLeast(2)
                            val flatColor = Color(0xFFFEE2E2).copy(alpha = 0.22f)
                            for (s in 0 until steps) {
                                val px0 = (left + s * stepPx).coerceAtMost(right)
                                val px1 = (left + (s + 1) * stepPx).coerceAtMost(right)
                                if (px1 <= px0) continue
                                val mid = (px0 + px1) * 0.5f
                                val env = edgeEnvelope(mid, left, right, fadeLeft, fadeRight)
                                val y0 = h * 0.64f + sin(px0 * 0.045f) * (h * 0.04f) * env
                                val y1 = h * 0.64f + sin(px1 * 0.045f) * (h * 0.04f) * env
                                drawLine(
                                    color = flatColor.copy(alpha = 0.24f * env),
                                    start = Offset(px0, y0),
                                    end = Offset(px1, y1),
                                    strokeWidth = 1.1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Faint dormant specks sitting nearly stationary in the unpowered zone
                            val speckCount = (segW / 32.dp.toPx()).toInt().coerceIn(1, 7)
                            for (k in 0 until speckCount) {
                                val fx = (k + 0.5f) / speckCount
                                val sx = left + fx * segW
                                val env = edgeEnvelope(sx, left, right, fadeLeft, fadeRight)
                                if (env > 0.15f) {
                                    val sy = h * (0.30f + 0.42f * ((k * 37 % 10) / 9f))
                                    drawCircle(
                                        color = Color(0xFFFCA5A5).copy(alpha = 0.22f * env),
                                        radius = 1.1.dp.toPx(),
                                        center = Offset(sx, sy)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Render sinuous current waves & glowing flowing "electron" light particles on SERVICE_SCHEDULED
                    spans.forEachIndexed { idx, span ->
                        if (span.seg.state != ScheduledState.SERVICE_SCHEDULED) return@forEachIndexed
                        val left = span.startFrac * w
                        val right = span.endFrac * w
                        val segW = right - left
                        if (segW <= 3f) return@forEachIndexed

                        val hasLeftTransition = idx > 0 && spans[idx - 1].seg.state != ScheduledState.SERVICE_SCHEDULED
                        val hasRightTransition = idx < spans.lastIndex && spans[idx + 1].seg.state != ScheduledState.SERVICE_SCHEDULED
                        val bleedPx = 6.dp.toPx()
                        val rampPx = minOf(18.dp.toPx(), segW * 0.42f)

                        val drawLeft = (left - if (hasLeftTransition) bleedPx else 0f).coerceAtLeast(0f)
                        val drawRight = (right + if (hasRightTransition) bleedPx else 0f).coerceAtMost(w)
                        val drawWidth = (drawRight - drawLeft).coerceAtLeast(1f)

                        clipRect(left = drawLeft, top = 0f, right = drawRight, bottom = h) {
                            // Moving emerald-mint luminous surge along the energized span
                            if (enableElectricAnimations) {
                                val bandHalf = (w * 0.16f).coerceAtLeast(30f)
                                val surgeCenter = -bandHalf + electricPhase * (w + bandHalf * 2f)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFFA7F3D0).copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.24f),
                                            Color(0xFFA7F3D0).copy(alpha = 0.26f),
                                            Color.Transparent
                                        ),
                                        startX = surgeCenter - bandHalf,
                                        endX = surgeCenter + bandHalf
                                    ),
                                    topLeft = Offset(drawLeft, 0f),
                                    size = Size(drawWidth, h)
                                )
                            }

                            // Two sinuous green-mint energy waves whose amplitude & opacity ramp smoothly across boundaries
                            val stepPx = 4.dp.toPx().coerceAtLeast(4f)
                            val steps = ((drawWidth / stepPx).toInt() + 1).coerceAtLeast(2)

                            fun waveUpperY(px: Float, env: Float): Float {
                                val n1 = sin((px * 0.085f) - electricPhase * twoPi * 3f)
                                val n2 = sin((px * 0.19f) + electricPhase * twoPi * 2f) * 0.35f
                                return h * 0.38f + (n1 * 0.72f + n2) * (h * 0.17f) * env
                            }

                            fun waveLowerY(px: Float, env: Float): Float {
                                val n1 = sin((px * 0.105f) + electricPhase * twoPi * 2f + 1.4f)
                                val n2 = sin((px * 0.22f) - electricPhase * twoPi * 4f) * 0.30f
                                return h * 0.65f + (n1 * 0.75f - n2) * (h * 0.16f) * env
                            }

                            fun envAt(px: Float): Float {
                                var e = 1f
                                if (hasLeftTransition) {
                                    val t = ((px - drawLeft) / (bleedPx + rampPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    e = minOf(e, smoothStep(t))
                                }
                                if (hasRightTransition) {
                                    val t = ((drawRight - px) / (bleedPx + rampPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    e = minOf(e, smoothStep(t))
                                }
                                return e
                            }

                            for (s in 0 until steps) {
                                val x0 = (drawLeft + s * stepPx).coerceAtMost(drawRight)
                                val x1 = (drawLeft + (s + 1) * stepPx).coerceAtMost(drawRight)
                                if (x1 <= x0) continue
                                val mid = (x0 + x1) * 0.5f
                                val env = envAt(mid)
                                if (env <= 0.02f) continue

                                val u0 = waveUpperY(x0, env)
                                val u1 = waveUpperY(x1, env)
                                val l0 = waveLowerY(x0, env)
                                val l1 = waveLowerY(x1, env)

                                drawLine(
                                    color = Color(0xFFD1FAE5).copy(alpha = 0.60f * env),
                                    start = Offset(x0, u0),
                                    end = Offset(x1, u1),
                                    strokeWidth = 1.55.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.38f * env),
                                    start = Offset(x0, l0),
                                    end = Offset(x1, l1),
                                    strokeWidth = 1.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Energetic flowing "electron" light particles vibrating and riding along the sinuous waves
                            if (enableElectricAnimations) {
                                val upperElectronCount = (drawWidth / 22.dp.toPx()).toInt().coerceIn(2, 10)
                                for (k in 0 until upperElectronCount) {
                                    val frac = ((k.toFloat() / upperElectronCount) + electricPhase * 1.0f) % 1f
                                    val ex = drawLeft + frac * drawWidth
                                    val env = envAt(ex)
                                    if (env <= 0.04f) continue
                                    val vib = 0.85f + 0.30f * sin((electricPhase * twoPi * 4f) + k * 1.9f)
                                    val ey = waveUpperY(ex, env)
                                    // Outer luminous halo
                                    drawCircle(
                                        color = Color(0xFF6EE7B7).copy(alpha = 0.42f * env),
                                        radius = 3.2.dp.toPx() * vib,
                                        center = Offset(ex, ey)
                                    )
                                    // Bright electron core
                                    drawCircle(
                                        color = Color(0xFFECFDF5).copy(alpha = 0.94f * env),
                                        radius = 1.45.dp.toPx() * vib,
                                        center = Offset(ex, ey)
                                    )
                                }

                                val lowerElectronCount = (drawWidth / 28.dp.toPx()).toInt().coerceIn(1, 8)
                                for (k in 0 until lowerElectronCount) {
                                    // Counter-flowing / secondary speed stream for rich dynamic depth
                                    val frac = ((k.toFloat() / lowerElectronCount) + electricPhase * 2.0f) % 1f
                                    val ex = drawLeft + frac * drawWidth
                                    val env = envAt(ex)
                                    if (env <= 0.04f) continue
                                    val vib = 0.85f + 0.28f * sin((electricPhase * twoPi * 5f) + k * 2.7f)
                                    val ey = waveLowerY(ex, env)
                                    drawCircle(
                                        color = Color(0xFFA7F3D0).copy(alpha = 0.36f * env),
                                        radius = 2.7.dp.toPx() * vib,
                                        center = Offset(ex, ey)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.88f * env),
                                        radius = 1.25.dp.toPx() * vib,
                                        center = Offset(ex, ey)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Optional selected segment highlight (without any black divider lines)
                    if (selectedSegment != null) {
                        val hit = spans.firstOrNull {
                            it.seg.startMillis == selectedSegment.startMillis &&
                                it.seg.endMillis == selectedSegment.endMillis
                        }
                        if (hit != null) {
                            val left = hit.startFrac * w
                            val segW = (hit.endFrac - hit.startFrac) * w
                            drawRect(
                                color = Color.White.copy(alpha = 0.22f),
                                topLeft = Offset(left, 0f),
                                size = Size(segW, h)
                            )
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(left, 0f),
                                size = Size(segW, 3.dp.toPx())
                            )
                        }
                    }
                }

                // 5. Crisp current-time marker needle with top pointer and luminous halo
                if (currentTimeMillis != null && currentTimeMillis in dayStart..dayEnd) {
                    val nowFrac = ((currentTimeMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
                    val x = (nowFrac * w).coerceIn(3f, (w - 3f).coerceAtLeast(3f))
                    val auraHalf = 8.dp.toPx()

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

                    drawLine(
                        color = Color.Black.copy(alpha = 0.32f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 4.dp.toPx()
                    )

                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.4.dp.toPx()
                    )

                    val arrowHalf = 4.5.dp.toPx()
                    val arrowDepth = 5.5.dp.toPx().coerceAtMost(h * 0.35f)
                    val arrowPath = Path().apply {
                        moveTo(x - arrowHalf, 0f)
                        lineTo(x + arrowHalf, 0f)
                        lineTo(x, arrowDepth)
                        close()
                    }
                    drawPath(path = arrowPath, color = Color.White)

                    drawCircle(
                        color = Color.White,
                        radius = 2.4.dp.toPx(),
                        center = Offset(x, (h - 2.4.dp.toPx()).coerceAtLeast(0f))
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

private fun TimelineSegSpan.stateColorEquals(other: TimelineSegSpan): Boolean =
    seg.state == other.seg.state && color == other.color

private fun smoothStep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun edgeEnvelope(
    px: Float,
    left: Float,
    right: Float,
    fadeLeft: Float,
    fadeRight: Float
): Float {
    var env = 1f
    if (fadeLeft > 0f) {
        env = minOf(env, smoothStep((px - left) / fadeLeft))
    }
    if (fadeRight > 0f) {
        env = minOf(env, smoothStep((right - px) / fadeRight))
    }
    return env
}

