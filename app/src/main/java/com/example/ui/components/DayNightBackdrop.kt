package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ThemeMode
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

data class SolarHeroPalette(
    val startColor: Color,
    val midColor: Color,
    val endColor: Color,
    val ambientTint: Color,
    val phaseLabel: String,
    val isTwilightOrGolden: Boolean
)

private data class SolarAnchor(
    val minuteOfDay: Float,
    val start: Color,
    val mid: Color,
    val end: Color,
    val ambientLight: Color,
    val ambientDark: Color
)

/**
 * Computes a gradual hour-by-hour solar palette anchored dynamically to [sunriseMinutes] and [sunsetMinutes].
 * Sampling at the midpoint of the current hour (`hour * 60 + 30`) produces 24 distinct hourly tones
 * with smooth gradual interpolation across midnight, dawn, sunrise, morning, noon, afternoon, sunset, and dusk.
 */
fun computeHourlySolarPalette(
    nowMillis: Long,
    zoneId: ZoneId,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    isDarkTheme: Boolean
): SolarHeroPalette {
    val sr = sunriseMinutes.coerceIn(180, 660).toFloat()
    val ss = sunsetMinutes.coerceIn((sr + 180f).toInt(), 1320).toFloat()
    val zdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val hourlyMinute = (zdt.hour * 60 + 30).toFloat()

    val dayLen = (ss - sr).coerceAtLeast(180f)
    val nightLen = (1440f - dayLen).coerceAtLeast(180f)
    val solarNoon = sr + dayLen * 0.5f
    val nightMid = (ss + nightLen * 0.5f) % 1440f

    val anchors = buildList {
        // 1. Pre-dawn deep slate
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(sr - 90f),
                start = Color(0xFF0E2340),
                mid = Color(0xFF1A3658),
                end = Color(0xFF174557),
                ambientLight = Color(0xFFD2E1F4),
                ambientDark = Color(0xFF0C1B30)
            )
        )
        // 2. Dawn / first light
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(sr - 35f),
                start = Color(0xFF282E59),
                mid = Color(0xFF4D426E),
                end = Color(0xFF6B4C59),
                ambientLight = Color(0xFFE7DAEE),
                ambientDark = Color(0xFF1B1C34)
            )
        )
        // 3. Sunrise
        add(
            SolarAnchor(
                minuteOfDay = sr,
                start = Color(0xFF934831),
                mid = Color(0xFFB56630),
                end = Color(0xFF2B6B60),
                ambientLight = Color(0xFFFDE4C8),
                ambientDark = Color(0xFF2F1D16)
            )
        )
        // 4. Early morning
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(sr + 75f),
                start = Color(0xFF1A6C5E),
                mid = Color(0xFF2B806C),
                end = Color(0xFF53824A),
                ambientLight = Color(0xFFE7F3D8),
                ambientDark = Color(0xFF162A22)
            )
        )
        // 5. Late morning
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(sr + dayLen * 0.30f),
                start = Color(0xFF0D715B),
                mid = Color(0xFF167F6C),
                end = Color(0xFF2B8058),
                ambientLight = Color(0xFFDCF2E7),
                ambientDark = Color(0xFF132D24)
            )
        )
        // 6. Solar noon
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(solarNoon),
                start = Color(0xFF096B52),
                mid = Color(0xFF0F7B6C),
                end = Color(0xFF1E7E4C),
                ambientLight = Color(0xFFD6F3EB),
                ambientDark = Color(0xFF112C25)
            )
        )
        // 7. Mid-afternoon
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(ss - dayLen * 0.28f),
                start = Color(0xFF126A56),
                mid = Color(0xFF237452),
                end = Color(0xFF4F7434),
                ambientLight = Color(0xFFEDF2D3),
                ambientDark = Color(0xFF1B2B1E)
            )
        )
        // 8. Golden hour before sunset
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(ss - 60f),
                start = Color(0xFF844D23),
                mid = Color(0xFF9E5D28),
                end = Color(0xFF2B6354),
                ambientLight = Color(0xFFFCE2C0),
                ambientDark = Color(0xFF2E2015)
            )
        )
        // 9. Sunset
        add(
            SolarAnchor(
                minuteOfDay = ss,
                start = Color(0xFF8A343E),
                mid = Color(0xFF6A315B),
                end = Color(0xFF223C5E),
                ambientLight = Color(0xFFF6D4D8),
                ambientDark = Color(0xFF2B1826)
            )
        )
        // 10. Evening twilight
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(ss + 50f),
                start = Color(0xFF282B54),
                mid = Color(0xFF1C355C),
                end = Color(0xFF134255),
                ambientLight = Color(0xFFD8DDF3),
                ambientDark = Color(0xFF151933)
            )
        )
        // 11. Night
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(ss + 130f),
                start = Color(0xFF0B2239),
                mid = Color(0xFF13334C),
                end = Color(0xFF103F43),
                ambientLight = Color(0xFFD2E4F5),
                ambientDark = Color(0xFF091627)
            )
        )
        // 12. Deep midnight
        add(
            SolarAnchor(
                minuteOfDay = normalizeMin(nightMid),
                start = Color(0xFF071528),
                mid = Color(0xFF0E2540),
                end = Color(0xFF0C343F),
                ambientLight = Color(0xFFCADBF0),
                ambientDark = Color(0xFF07111F)
            )
        )
    }.sortedBy { it.minuteOfDay }

    var prev = anchors.last()
    var next = anchors.first()
    var span = (next.minuteOfDay + 1440f) - prev.minuteOfDay
    var offset = if (hourlyMinute >= prev.minuteOfDay) {
        hourlyMinute - prev.minuteOfDay
    } else {
        hourlyMinute + 1440f - prev.minuteOfDay
    }

    for (i in 0 until anchors.lastIndex) {
        val a = anchors[i]
        val b = anchors[i + 1]
        if (hourlyMinute >= a.minuteOfDay && hourlyMinute < b.minuteOfDay) {
            prev = a
            next = b
            span = (b.minuteOfDay - a.minuteOfDay).coerceAtLeast(1f)
            offset = hourlyMinute - a.minuteOfDay
            break
        }
    }

    val rawFraction = (offset / span.coerceAtLeast(1f)).coerceIn(0f, 1f)
    // Gentle smoothstep keeps hourly jumps natural and prevents jarring mid-transitions
    val t = rawFraction * rawFraction * (3f - 2f * rawFraction)

    val baseStart = lerp(prev.start, next.start, t)
    val baseMid = lerp(prev.mid, next.mid, t)
    val baseEnd = lerp(prev.end, next.end, t)

    // In dark mode darken slightly for calm nighttime eye comfort while keeping distinct hue shifts
    val startColor = if (isDarkTheme) lerp(baseStart, Color(0xFF071018), 0.16f) else baseStart
    val midColor = if (isDarkTheme) lerp(baseMid, Color(0xFF071018), 0.14f) else baseMid
    val endColor = if (isDarkTheme) lerp(baseEnd, Color(0xFF071018), 0.14f) else baseEnd

    val ambientBase = if (isDarkTheme) {
        lerp(prev.ambientDark, next.ambientDark, t).copy(alpha = 0.56f)
    } else {
        lerp(prev.ambientLight, next.ambientLight, t).copy(alpha = 0.44f)
    }

    val phaseLabel = resolveSolarPhaseLabel(hourlyMinute, sr, ss, solarNoon)
    val isTwilight = abs(hourlyMinute - sr) <= 50f || abs(hourlyMinute - ss) <= 65f

    return SolarHeroPalette(
        startColor = startColor,
        midColor = midColor,
        endColor = endColor,
        ambientTint = ambientBase,
        phaseLabel = phaseLabel,
        isTwilightOrGolden = isTwilight
    )
}

private fun resolveSolarPhaseLabel(
    minuteOfDay: Float,
    sr: Float,
    ss: Float,
    solarNoon: Float
): String = when {
    minuteOfDay < sr - 60f -> "Madrugada"
    minuteOfDay < sr - 10f -> "Alba"
    minuteOfDay <= sr + 55f -> "Amanecer"
    minuteOfDay < solarNoon - 60f -> "Mañana"
    minuteOfDay <= solarNoon + 75f -> "Mediodía"
    minuteOfDay < ss - 65f -> "Tarde"
    minuteOfDay <= ss + 15f -> "Atardecer"
    minuteOfDay <= ss + 75f -> "Crepúsculo"
    else -> "Noche"
}

private fun normalizeMin(value: Float): Float {
    val mod = value % 1440f
    return if (mod < 0f) mod + 1440f else mod
}

@Composable
fun rememberHeroDayNightBrush(
    nowMillis: Long,
    zoneId: ZoneId,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    themeMode: ThemeMode
): Brush {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val hourBucket = remember(nowMillis / 60_000L, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
    }
    val palette = remember(hourBucket, zoneId, sunriseMinutes, sunsetMinutes, isDark) {
        computeHourlySolarPalette(
            nowMillis = nowMillis,
            zoneId = zoneId,
            sunriseMinutes = sunriseMinutes,
            sunsetMinutes = sunsetMinutes,
            isDarkTheme = isDark
        )
    }

    val startAnim by animateColorAsState(
        targetValue = palette.startColor,
        animationSpec = tween(durationMillis = 850),
        label = "solar_hero_start"
    )
    val midAnim by animateColorAsState(
        targetValue = palette.midColor,
        animationSpec = tween(durationMillis = 850),
        label = "solar_hero_mid"
    )
    val endAnim by animateColorAsState(
        targetValue = palette.endColor,
        animationSpec = tween(durationMillis = 850),
        label = "solar_hero_end"
    )

    return Brush.linearGradient(colors = listOf(startAnim, midAnim, endAnim))
}

@Composable
fun DayNightAmbientScreenBrush(
    nowMillis: Long,
    zoneId: ZoneId,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    themeMode: ThemeMode
): Brush {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val hourBucket = remember(nowMillis / 60_000L, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
    }
    val palette = remember(hourBucket, zoneId, sunriseMinutes, sunsetMinutes, isDark) {
        computeHourlySolarPalette(
            nowMillis = nowMillis,
            zoneId = zoneId,
            sunriseMinutes = sunriseMinutes,
            sunsetMinutes = sunsetMinutes,
            isDarkTheme = isDark
        )
    }
    val topTint by animateColorAsState(
        targetValue = palette.ambientTint,
        animationSpec = tween(durationMillis = 850),
        label = "solar_ambient_top"
    )
    return Brush.verticalGradient(
        colors = listOf(
            topTint,
            MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun DayNightChip(
    nowMillis: Long,
    zoneId: ZoneId,
    isDaytime: Boolean,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    use24h: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    modifier: Modifier = Modifier
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val hourBucket = remember(nowMillis / 60_000L, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
    }
    val palette = remember(hourBucket, zoneId, sunriseMinutes, sunsetMinutes, isDark) {
        computeHourlySolarPalette(
            nowMillis = nowMillis,
            zoneId = zoneId,
            sunriseMinutes = sunriseMinutes,
            sunsetMinutes = sunsetMinutes,
            isDarkTheme = isDark
        )
    }

    val icon = when {
        palette.isTwilightOrGolden -> Icons.Filled.WbTwilight
        isDaytime -> Icons.Filled.WbSunny
        else -> Icons.Filled.NightsStay
    }
    val nextBoundary = if (isDaytime) {
        "Ocaso ${ScheduleEngine.formatMinuteOfDay(sunsetMinutes, use24h)}"
    } else {
        "Alba ${ScheduleEngine.formatMinuteOfDay(sunriseMinutes, use24h)}"
    }
    val tint = when {
        palette.isTwilightOrGolden -> Color(0xFFFDBA74)
        isDaytime -> Color(0xFFFDE047)
        else -> Color(0xFFBAE6FD)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = palette.phaseLabel,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${palette.phaseLabel} • $nextBoundary",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
