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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
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
import com.example.ui.theme.LocalElectricStatusColors
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

internal data class TimelineAxisTick(
    val epochMillis: Long,
    val fraction: Float,
    val label: String?,
    val isMajor: Boolean,
    val isMidnight: Boolean,
    val dayDate: LocalDate,
    val isPrimaryDay: Boolean
)

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
    windowStartMillis: Long? = null,
    windowEndMillis: Long? = null,
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
    val dayStart = windowStartMillis ?: date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val dayEnd = windowEndMillis ?: date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val totalDayMillis = (dayEnd - dayStart).coerceAtLeast(1L).toFloat()
    val isCustomWindow = windowStartMillis != null && windowEndMillis != null

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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f

    // Night-sky to day-sky gradient anchored to sunrise and sunset within the visible window
    val dayNightBrush = remember(dayStart, dayEnd, sunriseMinutes, sunsetMinutes, isDarkTheme, zoneId) {
        buildWindowDayNightBorderBrush(
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            zoneId = zoneId,
            sunriseMinutes = sunriseMinutes,
            sunsetMinutes = sunsetMinutes,
            isDarkTheme = isDarkTheme
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
                .border(width = 2.dp, brush = dayNightBrush, shape = RoundedCornerShape(12.dp))
                .padding(2.dp)
        ) {
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

                    // 2. Render subtle inert shading & dormant specks on OUTAGE_SCHEDULED segments
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
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to if (fadeLeft > 0f) Color.Transparent else Color(0xFF450A0A).copy(alpha = 0.20f),
                                        (fadeLeft / segW).coerceIn(0f, 0.45f) to Color(0xFF450A0A).copy(alpha = 0.20f),
                                        (1f - (fadeRight / segW)).coerceIn(0.55f, 1f) to Color(0xFF450A0A).copy(alpha = 0.20f),
                                        1f to if (fadeRight > 0f) Color.Transparent else Color(0xFF450A0A).copy(alpha = 0.20f)
                                    ),
                                    startX = left,
                                    endX = right
                                ),
                                topLeft = Offset(left, 0f),
                                size = Size(segW, h)
                            )

                            val speckCount = (segW / 34.dp.toPx()).toInt().coerceIn(1, 7)
                            for (k in 0 until speckCount) {
                                val fx = (k + 0.5f) / speckCount
                                val sx = left + fx * segW
                                val env = edgeEnvelope(sx, left, right, fadeLeft, fadeRight)
                                if (env > 0.15f) {
                                    val sy = h * (0.28f + 0.44f * ((k * 37 % 10) / 9f))
                                    drawCircle(
                                        color = Color(0xFFFCA5A5).copy(alpha = 0.20f * env),
                                        radius = 1.05.dp.toPx(),
                                        center = Offset(sx, sy)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Moving emerald-mint luminous surge & flowing electron particles in SERVICE_SCHEDULED segments
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
                        }
                    }

                    // 4. TWO CONTINUOUS END-TO-END WAVES across the entire 24h bar
                    //    In green (active service): vibrant electric amplitude, brightness, and particles
                    //    In red (no service): same 2 lines flow seamlessly at low resting amplitude/opacity
                    val stepPx = 3.5.dp.toPx().coerceAtLeast(3f)
                    val totalSteps = ((w / stepPx).toInt() + 1).coerceAtLeast(4)

                    fun upperWaveY(px: Float, energy: Float): Float {
                        val activeAmp = h * 0.17f
                        val restAmp = h * 0.032f
                        val amp = restAmp + (activeAmp - restAmp) * energy
                        val baseCenter = h * 0.37f + (h * 0.04f) * (1f - energy)
                        val n1 = sin((px * 0.085f) - electricPhase * twoPi * 3f * (0.25f + 0.75f * energy))
                        val n2 = sin((px * 0.19f) + electricPhase * twoPi * 2f * energy) * 0.35f * energy
                        return baseCenter + (n1 * 0.75f + n2) * amp
                    }

                    fun lowerWaveY(px: Float, energy: Float): Float {
                        val activeAmp = h * 0.16f
                        val restAmp = h * 0.028f
                        val amp = restAmp + (activeAmp - restAmp) * energy
                        val baseCenter = h * 0.65f - (h * 0.03f) * (1f - energy)
                        val n1 = sin((px * 0.105f) + electricPhase * twoPi * 2f * (0.25f + 0.75f * energy) + 1.4f)
                        val n2 = sin((px * 0.22f) - electricPhase * twoPi * 4f * energy) * 0.30f * energy
                        return baseCenter + (n1 * 0.78f - n2) * amp
                    }

                    val upperRestColor = Color(0xFFFEE2E2).copy(alpha = 0.26f)
                    val upperActiveColor = Color(0xFFD1FAE5).copy(alpha = 0.78f)
                    val lowerRestColor = Color(0xFFFCA5A5).copy(alpha = 0.20f)
                    val lowerActiveColor = Color.White.copy(alpha = 0.56f)

                    var prevX = 0f
                    var prevEnergy = stateIntensityAt(0f, spans)
                    var prevUpperY = upperWaveY(0f, prevEnergy)
                    var prevLowerY = lowerWaveY(0f, prevEnergy)

                    for (s in 1..totalSteps) {
                        val currX = (s * stepPx).coerceAtMost(w)
                        val midFrac = (((prevX + currX) * 0.5f) / w.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val energy = stateIntensityAt(midFrac, spans)
                        val currUpperY = upperWaveY(currX, stateIntensityAt((currX / w.coerceAtLeast(1f)).coerceIn(0f, 1f), spans))
                        val currLowerY = lowerWaveY(currX, stateIntensityAt((currX / w.coerceAtLeast(1f)).coerceIn(0f, 1f), spans))

                        val upperStroke = (0.95f + 0.65f * energy).dp.toPx()
                        val lowerStroke = (0.85f + 0.45f * energy).dp.toPx()

                        drawLine(
                            color = lerp(upperRestColor, upperActiveColor, energy),
                            start = Offset(prevX, prevUpperY),
                            end = Offset(currX, currUpperY),
                            strokeWidth = upperStroke,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = lerp(lowerRestColor, lowerActiveColor, energy),
                            start = Offset(prevX, prevLowerY),
                            end = Offset(currX, currLowerY),
                            strokeWidth = lowerStroke,
                            cap = StrokeCap.Round
                        )

                        prevX = currX
                        prevUpperY = currUpperY
                        prevLowerY = currLowerY
                    }

                    // 5. Energetic glowing "electron" nodes riding along both continuous waves inside energized spans
                    if (enableElectricAnimations && hasServiceSegments) {
                        val upperNodes = (w / 24.dp.toPx()).toInt().coerceIn(4, 16)
                        for (k in 0 until upperNodes) {
                            val frac = ((k.toFloat() / upperNodes) + electricPhase * 0.45f) % 1f
                            val energy = stateIntensityAt(frac, spans)
                            if (energy <= 0.12f) continue
                            val ex = frac * w
                            val vib = 0.85f + 0.28f * sin((electricPhase * twoPi * 4f) + k * 1.9f)
                            val ey = upperWaveY(ex, energy)
                            drawCircle(
                                color = Color(0xFF6EE7B7).copy(alpha = 0.45f * energy),
                                radius = 3.0.dp.toPx() * vib,
                                center = Offset(ex, ey)
                            )
                            drawCircle(
                                color = Color(0xFFECFDF5).copy(alpha = 0.95f * energy),
                                radius = 1.4.dp.toPx() * vib,
                                center = Offset(ex, ey)
                            )
                        }

                        val lowerNodes = (w / 30.dp.toPx()).toInt().coerceIn(3, 12)
                        for (k in 0 until lowerNodes) {
                            val frac = ((k.toFloat() / lowerNodes) + electricPhase * 0.72f) % 1f
                            val energy = stateIntensityAt(frac, spans)
                            if (energy <= 0.12f) continue
                            val ex = frac * w
                            val vib = 0.85f + 0.25f * sin((electricPhase * twoPi * 5f) + k * 2.7f)
                            val ey = lowerWaveY(ex, energy)
                            drawCircle(
                                color = Color(0xFFA7F3D0).copy(alpha = 0.38f * energy),
                                radius = 2.6.dp.toPx() * vib,
                                center = Offset(ex, ey)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.90f * energy),
                                radius = 1.2.dp.toPx() * vib,
                                center = Offset(ex, ey)
                            )
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

                // 6. Classic crisp white current-time marker needle with shadow casing and soft white aura
                if (currentTimeMillis != null && currentTimeMillis in dayStart..dayEnd) {
                    val nowFrac = ((currentTimeMillis - dayStart).toFloat() / totalDayMillis).coerceIn(0f, 1f)
                    val x = (nowFrac * w).coerceIn(3f, (w - 3f).coerceAtLeast(3f))
                    val auraHalf = 9.dp.toPx()

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
                        color = Color.Black.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 3.8.dp.toPx()
                    )

                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.0.dp.toPx()
                    )

                    val arrowHalf = 4.6.dp.toPx()
                    val arrowDepth = 5.6.dp.toPx().coerceAtMost(h * 0.36f)
                    val arrowPath = Path().apply {
                        moveTo(x - arrowHalf, 0f)
                        lineTo(x + arrowHalf, 0f)
                        lineTo(x, arrowDepth)
                        close()
                    }
                    drawPath(
                        path = arrowPath,
                        color = Color.Black.copy(alpha = 0.45f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    drawPath(path = arrowPath, color = Color.White)

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.45f),
                        radius = 3.2.dp.toPx(),
                        center = Offset(x, (h - 3.0.dp.toPx()).coerceAtLeast(0f))
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.0.dp.toPx(),
                        center = Offset(x, (h - 3.0.dp.toPx()).coerceAtLeast(0f))
                    )
                }
            }
        }

        if (showHourAxis) {
            Spacer(modifier = Modifier.height(2.dp))
            val primaryDate = remember(isCustomWindow, date, currentTimeMillis, dayStart, dayEnd, zoneId) {
                if (isCustomWindow) {
                    val refMillis = currentTimeMillis ?: (dayStart + (dayEnd - dayStart) / 2L)
                    Instant.ofEpochMilli(refMillis).atZone(zoneId).toLocalDate()
                } else {
                    date
                }
            }
            val axisTicks = remember(dayStart, dayEnd, primaryDate, isCustomWindow, use24h, zoneId) {
                buildTimelineAxisTicks(
                    windowStartMillis = dayStart,
                    windowEndMillis = dayEnd,
                    primaryDate = primaryDate,
                    isCustomWindow = isCustomWindow,
                    use24h = use24h,
                    zoneId = zoneId
                )
            }
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val axisHeight = remember(density.fontScale) {
                10.dp + (18.dp * density.fontScale.coerceAtLeast(1f))
            }
            val baseLabelStyle = MaterialTheme.typography.labelSmall
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            val primaryColor = MaterialTheme.colorScheme.primary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(axisHeight)
            ) {
                val w = size.width
                if (w <= 0f) return@Canvas

                val minorTickHeight = 3.5.dp.toPx()
                val majorTickHeight = 6.5.dp.toPx()
                val midnightTickHeight = 8.dp.toPx()
                val labelTopY = 8.dp.toPx()

                axisTicks.forEach { tick ->
                    val x = (tick.fraction * w).coerceIn(0.5.dp.toPx(), (w - 0.5.dp.toPx()).coerceAtLeast(0.5.dp.toPx()))
                    val highlightMidnight = isCustomWindow && tick.isMidnight

                    val tickHeight = when {
                        highlightMidnight -> midnightTickHeight
                        tick.isMajor -> majorTickHeight
                        else -> minorTickHeight
                    }
                    val tickStroke = when {
                        highlightMidnight -> 2.dp.toPx()
                        tick.isMajor -> 1.4.dp.toPx()
                        else -> 1.dp.toPx()
                    }
                    val tickColor = when {
                        highlightMidnight -> primaryColor.copy(alpha = 0.92f)
                        tick.isMajor && tick.isPrimaryDay -> onSurfaceColor.copy(alpha = 0.72f)
                        tick.isMajor -> onSurfaceVariantColor.copy(alpha = 0.42f)
                        tick.isPrimaryDay -> onSurfaceColor.copy(alpha = 0.34f)
                        else -> onSurfaceVariantColor.copy(alpha = 0.20f)
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(x, 0f),
                        end = Offset(x, tickHeight),
                        strokeWidth = tickStroke,
                        cap = StrokeCap.Round
                    )

                    if (highlightMidnight && !tick.isMajor) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.92f),
                            radius = 1.6.dp.toPx(),
                            center = Offset(x, midnightTickHeight + 2.2.dp.toPx())
                        )
                    }

                    val labelText = tick.label
                    if (labelText != null) {
                        val labelColor = when {
                            highlightMidnight -> primaryColor
                            tick.isPrimaryDay -> onSurfaceColor.copy(alpha = 0.94f)
                            else -> onSurfaceVariantColor.copy(alpha = 0.54f)
                        }
                        val labelWeight = when {
                            highlightMidnight -> FontWeight.Bold
                            tick.isPrimaryDay -> FontWeight.SemiBold
                            else -> FontWeight.Normal
                        }
                        val measured = textMeasurer.measure(
                            text = labelText,
                            style = baseLabelStyle.copy(
                                color = labelColor,
                                fontWeight = labelWeight
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        val textW = measured.size.width.toFloat()
                        val textX = (x - textW / 2f).coerceIn(0f, (w - textW).coerceAtLeast(0f))
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(textX, labelTopY)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineSegmentLegend(modifier: Modifier = Modifier) {
    val statusColors = LocalElectricStatusColors.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendChip(
            color = statusColors.service,
            icon = Icons.Filled.Bolt,
            label = "Con servicio"
        )
        LegendChip(
            color = statusColors.outage,
            icon = Icons.Filled.PowerOff,
            label = "Sin servicio"
        )
        LegendChip(
            color = statusColors.unknown,
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            label = "Sin info"
        )
    }
}

@Composable
private fun LegendChip(
    color: Color,
    icon: ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
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
            Icons.AutoMirrored.Filled.HelpOutline,
            "Sin información disponible"
        )
    }

    val sourceLabel = when (segment.source) {
        RuleSource.MANUAL_EXCEPTION -> "Excepción manual"
        RuleSource.EXPLICIT_FORECAST -> if (segment.isHistoricalTest) "Horario histórico de prueba" else "Horario por fecha"
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
                    text = "${ScheduleEngine.formatTime(segment.startMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(segment.endMillis, use24h, zoneId)} (${ScheduleEngine.formatDurationHuman(segment.durationMillis)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
            val (accent, onContainer, icon, label) = when (seg.state) {
                ScheduledState.SERVICE_SCHEDULED -> Quad(
                    statusColors.service,
                    statusColors.onServiceContainer,
                    Icons.Filled.Bolt,
                    "Con servicio"
                )
                ScheduledState.OUTAGE_SCHEDULED -> Quad(
                    statusColors.outage,
                    statusColors.onOutageContainer,
                    Icons.Filled.PowerOff,
                    "Sin servicio"
                )
                ScheduledState.UNKNOWN -> Quad(
                    statusColors.unknown,
                    statusColors.onUnknownContainer,
                    Icons.AutoMirrored.Filled.HelpOutline,
                    "Sin información"
                )
            }
            val isSelected = selectedSegment?.startMillis == seg.startMillis && selectedSegment.endMillis == seg.endMillis
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) accent.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(seg) }
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${ScheduleEngine.formatTime(seg.startMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(seg.endMillis, use24h, zoneId)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$label (${ScheduleEngine.formatDurationHuman(seg.durationMillis)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
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

internal fun buildTimelineAxisTicks(
    windowStartMillis: Long,
    windowEndMillis: Long,
    primaryDate: LocalDate,
    isCustomWindow: Boolean,
    use24h: Boolean,
    zoneId: ZoneId
): List<TimelineAxisTick> {
    val span = (windowEndMillis - windowStartMillis).coerceAtLeast(1L).toFloat()
    var cursor = Instant.ofEpochMilli(windowStartMillis).atZone(zoneId).truncatedTo(ChronoUnit.HOURS)
    if (cursor.toInstant().toEpochMilli() < windowStartMillis) {
        cursor = cursor.plusHours(1)
    }

    val hourInstants = mutableListOf<java.time.ZonedDateTime>()
    while (cursor.toInstant().toEpochMilli() <= windowEndMillis) {
        hourInstants += cursor
        cursor = cursor.plusHours(1)
    }

    return hourInstants.mapIndexed { idx, zdt ->
        val millis = zdt.toInstant().toEpochMilli()
        val frac = ((millis - windowStartMillis).toFloat() / span).coerceIn(0f, 1f)
        val isMajor = idx % 4 == 0
        val isMidnight = zdt.hour == 0
        val isDayEndMidnight = !isCustomWindow && idx == hourInstants.lastIndex && zdt.hour == 0
        val label = if (isMajor) {
            formatAxisHourLabel(
                hour = zdt.hour,
                use24h = use24h,
                isDayEndMidnight = isDayEndMidnight
            )
        } else {
            null
        }
        val dayDate = zdt.toLocalDate()
        val isPrimaryDay = !isCustomWindow || dayDate == primaryDate
        TimelineAxisTick(
            epochMillis = millis,
            fraction = frac,
            label = label,
            isMajor = isMajor,
            isMidnight = isMidnight,
            dayDate = dayDate,
            isPrimaryDay = isPrimaryDay
        )
    }
}

internal fun formatAxisHourLabel(
    hour: Int,
    use24h: Boolean,
    isDayEndMidnight: Boolean = false
): String {
    return if (use24h) {
        if (isDayEndMidnight) "24h" else String.format(Locale.US, "%02dh", hour)
    } else {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (hour < 12) "am" else "pm"
        "$h12$suffix"
    }
}

private fun skyColorAtMinute(
    minuteOfDay: Float,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    isDarkTheme: Boolean
): Color {
    val sr = sunriseMinutes.coerceIn(120, 660).toFloat()
    val ss = sunsetMinutes.coerceIn(780, 1320).toFloat()
    val noon = (sr + ss) * 0.5f
    val halfTransition = 55f

    val nightDeep = if (isDarkTheme) Color(0xFF4338CA) else Color(0xFF1E1B4B)
    val twilightBlue = if (isDarkTheme) Color(0xFF6366F1) else Color(0xFF2563EB)
    val skyDay = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0EA5E9)
    val skyNoon = if (isDarkTheme) Color(0xFF7DD3FC) else Color(0xFF38BDF8)

    val dawnStart = sr - halfTransition
    val dawnEnd = sr + halfTransition
    val duskStart = ss - halfTransition
    val duskEnd = ss + halfTransition

    return when {
        minuteOfDay <= dawnStart || minuteOfDay >= duskEnd -> nightDeep
        minuteOfDay < sr -> {
            val t = smoothStep((minuteOfDay - dawnStart) / halfTransition)
            lerp(nightDeep, twilightBlue, t)
        }
        minuteOfDay < dawnEnd -> {
            val t = smoothStep((minuteOfDay - sr) / halfTransition)
            lerp(twilightBlue, skyDay, t)
        }
        minuteOfDay < noon -> {
            val t = smoothStep((minuteOfDay - dawnEnd) / (noon - dawnEnd).coerceAtLeast(1f))
            lerp(skyDay, skyNoon, t)
        }
        minuteOfDay < duskStart -> {
            val t = smoothStep((minuteOfDay - noon) / (duskStart - noon).coerceAtLeast(1f))
            lerp(skyNoon, skyDay, t)
        }
        minuteOfDay < ss -> {
            val t = smoothStep((minuteOfDay - duskStart) / halfTransition)
            lerp(skyDay, twilightBlue, t)
        }
        else -> {
            val t = smoothStep((minuteOfDay - ss) / halfTransition)
            lerp(twilightBlue, nightDeep, t)
        }
    }
}

private fun buildWindowDayNightBorderBrush(
    windowStartMillis: Long,
    windowEndMillis: Long,
    zoneId: ZoneId,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    isDarkTheme: Boolean
): Brush {
    val span = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
    val sampleCount = 24
    val stops = Array(sampleCount + 1) { idx ->
        val frac = idx.toFloat() / sampleCount.toFloat()
        val instantMillis = windowStartMillis + (span * frac).roundToLong()
        val zdt = Instant.ofEpochMilli(instantMillis).atZone(zoneId)
        val minuteOfDay = zdt.hour * 60f + zdt.minute + zdt.second / 60f
        frac to skyColorAtMinute(minuteOfDay, sunriseMinutes, sunsetMinutes, isDarkTheme)
    }
    return Brush.horizontalGradient(colorStops = stops)
}

private fun stateIntensityAt(frac: Float, spans: List<TimelineSegSpan>): Float {
    if (spans.isEmpty()) return 0f
    val idx = spans.indexOfFirst { frac >= it.startFrac && frac <= it.endFrac }
        .let { if (it >= 0) it else if (frac < spans.first().startFrac) 0 else spans.lastIndex }
    val curr = spans[idx]
    val target = if (curr.seg.state == ScheduledState.SERVICE_SCHEDULED) 1f else 0f

    if (idx > 0) {
        val prev = spans[idx - 1]
        val prevTarget = if (prev.seg.state == ScheduledState.SERVICE_SCHEDULED) 1f else 0f
        if (prevTarget != target) {
            val prevLen = (prev.endFrac - prev.startFrac).coerceAtLeast(0.002f)
            val currLen = (curr.endFrac - curr.startFrac).coerceAtLeast(0.002f)
            val halfBlend = minOf(0.020f, prevLen * 0.36f, currLen * 0.36f)
            val dist = frac - curr.startFrac
            if (dist < halfBlend) {
                val t = smoothStep((dist + halfBlend) / (2f * halfBlend).coerceAtLeast(0.001f))
                return prevTarget + (target - prevTarget) * t
            }
        }
    }

    if (idx < spans.lastIndex) {
        val next = spans[idx + 1]
        val nextTarget = if (next.seg.state == ScheduledState.SERVICE_SCHEDULED) 1f else 0f
        if (nextTarget != target) {
            val currLen = (curr.endFrac - curr.startFrac).coerceAtLeast(0.002f)
            val nextLen = (next.endFrac - next.startFrac).coerceAtLeast(0.002f)
            val halfBlend = minOf(0.020f, currLen * 0.36f, nextLen * 0.36f)
            val dist = curr.endFrac - frac
            if (dist < halfBlend) {
                val t = smoothStep((dist + halfBlend) / (2f * halfBlend).coerceAtLeast(0.001f))
                return nextTarget + (target - nextTarget) * t
            }
        }
    }

    return target
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

