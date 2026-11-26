package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduledState
import com.example.domain.model.TimelineSegment
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

private const val MILLIS_PER_HOUR = 3_600_000L
private const val HALF_RING_HOURS = 18.0 // Front hemisphere covers -18h .. +18h (36h visible)

@Composable
fun Continuous3DTickerTimeline(
    circuitCode: String,
    snapshot: ScheduleSnapshot,
    nowMillis: Long,
    use24h: Boolean,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    initialFocusMillis: Long = nowMillis,
    targetDate: LocalDate? = null,
    onFocusedDateChanged: ((LocalDate) -> Unit)? = null,
    onSegmentFocused: ((TimelineSegment) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Offset in hours from nowMillis stored in Animatable for silky endless scroll + fling
    val offsetHoursAnim = remember(circuitCode) {
        Animatable(((initialFocusMillis - nowMillis).toDouble() / MILLIS_PER_HOUR).toFloat())
    }
    var anchorMillis by remember(circuitCode) { mutableLongStateOf(nowMillis) }

    val centerMillis = remember(anchorMillis, offsetHoursAnim.value) {
        anchorMillis + (offsetHoursAnim.value.toDouble() * MILLIS_PER_HOUR).roundToLong()
    }

    val centerZdt = remember(centerMillis, zoneId) {
        Instant.ofEpochMilli(centerMillis).atZone(zoneId)
    }
    val todayDate = remember(nowMillis, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    }
    val centerDate = centerZdt.toLocalDate()
    var lastReportedDate by remember(circuitCode) { mutableStateOf(centerDate) }

    LaunchedEffect(centerDate) {
        if (lastReportedDate != centerDate) {
            lastReportedDate = centerDate
            onFocusedDateChanged?.invoke(centerDate)
        }
    }

    LaunchedEffect(targetDate, circuitCode) {
        if (targetDate != null && targetDate != lastReportedDate && targetDate != centerDate) {
            lastReportedDate = targetDate
            val desiredMillis = if (targetDate == todayDate) {
                nowMillis
            } else {
                targetDate.atTime(centerZdt.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
            }
            val desiredOffsetHours = ((desiredMillis - anchorMillis).toDouble() / MILLIS_PER_HOUR).toFloat()
            offsetHoursAnim.animateTo(
                targetValue = desiredOffsetHours,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // Fetch a 96h window around centerMillis so both front and back ring arcs render seamlessly
    val centerBucketHour = centerMillis / (4 * MILLIS_PER_HOUR)
    val segments = remember(circuitCode, snapshot, centerBucketHour, zoneId) {
        val spanMillis = 48L * MILLIS_PER_HOUR
        ScheduleEngine.getTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = centerMillis - spanMillis,
            rangeEndMillis = centerMillis + spanMillis,
            zoneId = zoneId
        )
    }

    val focusedSegment = remember(segments, centerMillis) {
        segments.firstOrNull { centerMillis >= it.startMillis && centerMillis < it.endMillis }
    }

    LaunchedEffect(focusedSegment) {
        if (focusedSegment != null) {
            onSegmentFocused?.invoke(focusedSegment)
        }
    }

    val isNearNow = abs(centerMillis - nowMillis) < 90_000L
    val pillLabel = remember(centerMillis, isNearNow, use24h, zoneId, todayDate) {
        val timeText = ScheduleEngine.formatTime(centerMillis, use24h, zoneId)
        if (isNearNow) {
            "AHORA • $timeText"
        } else {
            val d = centerZdt.toLocalDate()
            val dayPrefix = when (d) {
                todayDate -> "Hoy"
                todayDate.minusDays(1) -> "Ayer"
                todayDate.plusDays(1) -> "Mañana"
                else -> d.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es-CU")))
                    .replaceFirstChar { it.uppercase() }
            }
            "$dayPrefix • $timeText"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Línea de tiempo 3D dinámica",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Desliza horizontalmente para navegar sin fin por fechas y horas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0C1715),
            border = BorderStroke(1.dp, Color(0xFF28413B)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(242.dp)
                .testTag("timeline_3d_wheel")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF172C27),
                                Color(0xFF0A1412)
                            )
                        )
                    )
            ) {
                // Interactive 3D Ring Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(circuitCode) {
                            val velocityTracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                    scope.launch { offsetHoursAnim.stop() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val hoursPerPx = 28f / size.width.coerceAtLeast(1)
                                    val deltaHours = -dragAmount * hoursPerPx
                                    scope.launch {
                                        offsetHoursAnim.snapTo(offsetHoursAnim.value + deltaHours)
                                    }
                                },
                                onDragEnd = {
                                    val vx = velocityTracker.calculateVelocity().x
                                    val hoursPerPx = 28f / size.width.coerceAtLeast(1)
                                    val velocityHoursPerSec = -vx * hoursPerPx
                                    scope.launch {
                                        offsetHoursAnim.animateDecay(
                                            initialVelocity = velocityHoursPerSec,
                                            animationSpec = exponentialDecay(frictionMultiplier = 1.9f)
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    draw3DCylindricalRing(
                        centerMillis = centerMillis,
                        nowMillis = nowMillis,
                        segments = segments,
                        todayDate = todayDate,
                        use24h = use24h,
                        zoneId = zoneId
                    )
                }

                // Top centered floating timestamp pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1A2F2A).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFF6CE5B8).copy(alpha = 0.45f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = pillLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFEAFBF4),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }

                // Left & Right quick step buttons
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF182925).copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                offsetHoursAnim.animateTo(
                                    offsetHoursAnim.value - 4f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                        .testTag("timeline_3d_prev")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Retroceder 4 horas",
                            tint = Color(0xFFE0ECE7)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF182925).copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                offsetHoursAnim.animateTo(
                                    offsetHoursAnim.value + 4f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                        .testTag("timeline_3d_next")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Avanzar 4 horas",
                            tint = Color(0xFFE0ECE7)
                        )
                    }
                }

                // Bottom-left "Ir a Ahora" button
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isNearNow) {
                        Color(0xFF172D28).copy(alpha = 0.78f)
                    } else {
                        Color(0xFF1F5445).copy(alpha = 0.95f)
                    },
                    border = BorderStroke(1.dp, Color(0xFF6CE5B8).copy(alpha = 0.45f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            anchorMillis = System.currentTimeMillis()
                            scope.launch {
                                offsetHoursAnim.animateTo(
                                    0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                        .testTag("timeline_3d_now")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Ir a Ahora",
                            tint = Color(0xFFBDF7DF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ir a Ahora",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFEAFBF4),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Focused interval card under the 3D drum
        focusedSegment?.let { seg ->
            TimelineSegmentDetailCard(
                segment = seg,
                use24h = use24h,
                zoneId = zoneId
            )
        }
    }
}

private fun DrawScope.draw3DCylindricalRing(
    centerMillis: Long,
    nowMillis: Long,
    segments: List<TimelineSegment>,
    todayDate: LocalDate,
    use24h: Boolean,
    zoneId: ZoneId
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.50f
    val rx = w * 0.43f
    val ry = h * 0.145f
    val bandHeight = h * 0.21f
    val tickBandHeight = h * 0.10f

    // Helper mapping timeMillis -> ring angle in radians (0 = dead-center front)
    fun millisToAngle(tMillis: Long): Double {
        val diffHours = (tMillis - centerMillis).toDouble() / MILLIS_PER_HOUR
        return (diffHours / HALF_RING_HOURS) * (PI / 2.0)
    }

    fun ringPoint(theta: Double, yOffset: Float = 0f): Offset {
        val x = cx + (rx * sin(theta)).toFloat()
        val y = cy + (ry * cos(theta)).toFloat() + yOffset
        return Offset(x, y)
    }

    // Back base band shadow (subtle dark 3D ring)
    drawOvalRingRim(cx, cy, rx, ry, bandHeight)

    // Draw segments in two passes: back hemisphere first (cos < 0), then front hemisphere (cos >= 0)
    val serviceBase = Color(0xFF34D399)
    val outageBase = Color(0xFFF87171)
    val unknownBase = Color(0xFF52626B)

    for (passIsFront in listOf(false, true)) {
        segments.forEach { seg ->
            val startTheta = millisToAngle(seg.startMillis)
            val endTheta = millisToAngle(seg.endMillis)
            // Keep within [-PI, PI] visible ring
            val clampedStart = maxOf(startTheta, -PI)
            val clampedEnd = minOf(endTheta, PI)
            if (clampedEnd <= clampedStart) return@forEach

            val baseColor = when (seg.state) {
                ScheduledState.SERVICE_SCHEDULED -> serviceBase
                ScheduledState.OUTAGE_SCHEDULED -> outageBase
                ScheduledState.UNKNOWN -> unknownBase
            }

            // Step into fine angular slices (~3.5 degrees)
            val totalSpan = clampedEnd - clampedStart
            val steps = (totalSpan / 0.06).toInt().coerceIn(1, 72)
            val stepSize = totalSpan / steps

            for (i in 0 until steps) {
                val a0 = clampedStart + i * stepSize
                val a1 = a0 + stepSize
                val midA = (a0 + a1) * 0.5
                val depth = cos(midA) // +1 front, -1 back
                val isFront = depth >= 0.0
                if (isFront != passIsFront) continue

                val shadeFactor = if (isFront) {
                    (0.58 + 0.42 * depth).toFloat()
                } else {
                    (0.18 + 0.14 * (depth + 1.0)).toFloat()
                }
                val sliceColor = shadeColor(baseColor, shadeFactor, alpha = if (isFront) 0.96f else 0.34f)

                val p0Top = ringPoint(a0, -bandHeight * 0.5f)
                val p1Top = ringPoint(a1, -bandHeight * 0.5f)
                val p1Bot = ringPoint(a1, bandHeight * 0.5f)
                val p0Bot = ringPoint(a0, bandHeight * 0.5f)

                val quad = Path().apply {
                    moveTo(p0Top.x, p0Top.y)
                    lineTo(p1Top.x, p1Top.y)
                    lineTo(p1Bot.x, p1Bot.y)
                    lineTo(p0Bot.x, p0Bot.y)
                    close()
                }
                drawPath(quad, color = sliceColor)

                // Lower translucent hour axis skirt on front arc
                if (isFront) {
                    val skirtColor = Color(0xFF0D1716).copy(alpha = (0.72f * (0.4f + 0.6f * depth.toFloat())).coerceIn(0f, 0.85f))
                    val s0Bot = ringPoint(a0, bandHeight * 0.5f + tickBandHeight)
                    val s1Bot = ringPoint(a1, bandHeight * 0.5f + tickBandHeight)
                    val skirt = Path().apply {
                        moveTo(p0Bot.x, p0Bot.y)
                        lineTo(p1Bot.x, p1Bot.y)
                        lineTo(s1Bot.x, s1Bot.y)
                        lineTo(s0Bot.x, s0Bot.y)
                        close()
                    }
                    drawPath(skirt, color = skirtColor)
                }
            }

            // Crisp segment boundary divider on front
            val midBoundaryCos = cos(clampedStart)
            if (passIsFront && midBoundaryCos > 0.05 && clampedStart > -PI * 0.95) {
                val bTop = ringPoint(clampedStart, -bandHeight * 0.5f)
                val bBot = ringPoint(clampedStart, bandHeight * 0.5f)
                drawLine(
                    color = Color(0xFF0A1412).copy(alpha = 0.85f),
                    start = bTop,
                    end = bBot,
                    strokeWidth = 2.5f
                )
            }
        }
    }

    // Hour tick lines & labels along front arc (every 4 hours)
    val startZdt = Instant.ofEpochMilli(centerMillis - (HALF_RING_HOURS * 1.35 * MILLIS_PER_HOUR).toLong())
        .atZone(zoneId)
    val firstTickDay = startZdt.toLocalDate()
    val tickHours = listOf(0, 4, 8, 12, 16, 20)

    val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val datePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    for (dayOffset in 0..3) {
        val day = firstTickDay.plusDays(dayOffset.toLong())

        // Draw Day label above noon (12:00) of each day if visible
        val dayNoonMillis = day.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val dayTheta = millisToAngle(dayNoonMillis)
        if (abs(dayTheta) <= PI * 0.85) {
            val cosD = cos(dayTheta)
            val labelPos = ringPoint(dayTheta, -bandHeight * 0.74f)
            val dayText = when (day) {
                todayDate -> "Hoy (${day.dayOfMonth}/${day.monthValue})"
                todayDate.minusDays(1) -> "Ayer (${day.dayOfMonth}/${day.monthValue})"
                todayDate.plusDays(1) -> "Mañana (${day.dayOfMonth}/${day.monthValue})"
                else -> {
                    val dow = day.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es-CU")))
                        .replaceFirstChar { it.uppercase() }
                    dow
                }
            }
            val alpha = ((cosD + 0.55) / 1.55).toFloat().coerceIn(0.28f, 0.96f)
            datePaint.color = android.graphics.Color.argb((alpha * 255).toInt(), 224, 244, 236)
            datePaint.textSize = (11.5f + 2.5f * maxOf(0.0, cosD).toFloat()) * density
            drawContext.canvas.nativeCanvas.drawText(dayText, labelPos.x, labelPos.y, datePaint)
        }

        // Hour ticks every 4h
        tickHours.forEach { hr ->
            val tMillis = day.atTime(hr, 0).atZone(zoneId).toInstant().toEpochMilli()
            val theta = millisToAngle(tMillis)
            val cosT = cos(theta)
            if (cosT > 0.08 && abs(theta) < PI * 0.48) {
                val tickTop = ringPoint(theta, bandHeight * 0.5f)
                val tickBot = ringPoint(theta, bandHeight * 0.5f + 6.dp.toPx())
                drawLine(
                    color = Color.White.copy(alpha = (0.35f * cosT.toFloat()).coerceIn(0.1f, 0.5f)),
                    start = tickTop,
                    end = tickBot,
                    strokeWidth = 1.5f
                )
                val labelPos = ringPoint(theta, bandHeight * 0.5f + tickBandHeight * 0.82f)
                val labelStr = if (use24h) {
                    String.format(Locale.US, "%02d", hr)
                } else {
                    when (hr) {
                        0 -> "12am"
                        4 -> "4am"
                        8 -> "8am"
                        12 -> "12pm"
                        16 -> "4pm"
                        else -> "8pm"
                    }
                }
                val alpha = (cosT * 0.92).toFloat().coerceIn(0.15f, 0.95f)
                textPaint.color = android.graphics.Color.argb((alpha * 255).toInt(), 210, 228, 222)
                textPaint.textSize = (10f + 2f * cosT.toFloat()) * density
                drawContext.canvas.nativeCanvas.drawText(labelStr, labelPos.x, labelPos.y, textPaint)
            }
        }
    }

    // "Now" real-time marker bar if visible on front hemisphere
    val nowTheta = millisToAngle(nowMillis)
    if (cos(nowTheta) > 0.05 && abs(nowTheta) < PI * 0.49) {
        val nTop = ringPoint(nowTheta, -bandHeight * 0.52f)
        val nBot = ringPoint(nowTheta, bandHeight * 0.52f)
        drawLine(
            color = Color.White.copy(alpha = 0.95f),
            start = nTop,
            end = nBot,
            strokeWidth = 4.dp.toPx()
        )
    }

    // Center focus needle + glowing ring reticle at theta = 0
    val centerTop = ringPoint(0.0, -bandHeight * 0.95f)
    val centerBot = ringPoint(0.0, bandHeight * 1.15f)
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF9DF7D6),
                Color(0xFF9DF7D6),
                Color.Transparent
            ),
            startY = centerTop.y,
            endY = centerBot.y
        ),
        start = centerTop,
        end = centerBot,
        strokeWidth = 3.dp.toPx()
    )
    val reticleCenter = ringPoint(0.0, bandHeight * 0.5f)
    drawCircle(
        color = Color(0xFF142A24),
        radius = 8.dp.toPx(),
        center = reticleCenter
    )
    drawCircle(
        color = Color(0xFFA7F9DA),
        radius = 8.dp.toPx(),
        center = reticleCenter,
        style = Stroke(width = 2.5.dp.toPx())
    )
}

private fun DrawScope.drawOvalRingRim(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    bandHeight: Float
) {
    drawOval(
        color = Color(0xFF1B302A).copy(alpha = 0.55f),
        topLeft = Offset(cx - rx, cy - ry - bandHeight * 0.5f),
        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
        style = Stroke(width = 2.dp.toPx())
    )
    drawOval(
        color = Color(0xFF0E1B18).copy(alpha = 0.65f),
        topLeft = Offset(cx - rx, cy - ry + bandHeight * 0.5f),
        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun shadeColor(color: Color, factor: Float, alpha: Float): Color {
    val f = factor.coerceIn(0f, 1.15f)
    return Color(
        red = (color.red * f).coerceIn(0f, 1f),
        green = (color.green * f).coerceIn(0f, 1f),
        blue = (color.blue * f).coerceIn(0f, 1f),
        alpha = alpha
    )
}
