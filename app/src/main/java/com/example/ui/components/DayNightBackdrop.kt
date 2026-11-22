package com.example.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ThemeMode

@Composable
fun rememberHeroDayNightBrush(
    isDaytime: Boolean,
    themeMode: ThemeMode
): Brush {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    return if (isDaytime) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0E6E54),
                    Color(0xFF1F6B5D),
                    Color(0xFF3E6C34)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    Color(0xFF287D56)
                )
            )
        }
    } else {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0B2239),
                    Color(0xFF13334C),
                    Color(0xFF103F43)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF102B48),
                    Color(0xFF19455B),
                    Color(0xFF0E5A4C)
                )
            )
        }
    }
}

@Composable
fun DayNightAmbientScreenBrush(
    isDaytime: Boolean,
    themeMode: ThemeMode
): Brush {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val topTint = when {
        isDaytime && !isDark -> Color(0xFFFFF4D2).copy(alpha = 0.45f)
        isDaytime && isDark -> Color(0xFF1C3528).copy(alpha = 0.35f)
        !isDaytime && !isDark -> Color(0xFFD7E6F5).copy(alpha = 0.42f)
        else -> Color(0xFF091526).copy(alpha = 0.55f)
    }
    return Brush.verticalGradient(
        colors = listOf(
            topTint,
            MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun DayNightChip(
    isDaytime: Boolean,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    use24h: Boolean,
    modifier: Modifier = Modifier
) {
    val icon = if (isDaytime) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    val label = if (isDaytime) "Día" else "Noche"
    val nextBoundary = if (isDaytime) {
        "Ocaso ${ScheduleEngine.formatMinuteOfDay(sunsetMinutes, use24h)}"
    } else {
        "Alba ${ScheduleEngine.formatMinuteOfDay(sunriseMinutes, use24h)}"
    }
    val tint = if (isDaytime) Color(0xFFFDE047) else Color(0xFFBAE6FD)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$label • $nextBoundary",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
