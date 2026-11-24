package com.example

import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.BottomBarBehavior
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetCardStyle
import com.example.widget.ResolvedWidgetSurface
import com.example.widget.WidgetSizeTier
import com.example.widget.WidgetUpdateHelper
import com.example.ui.components.computeHourlySolarPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SolarAndWidgetUiTest {

    @Test
    fun `hourly solar palette transitions gradually across the day and reflects sunrise sunset`() {
        val zone = ZoneId.of("America/Havana")
        val date = LocalDate.of(2026, 11, 24)
        val sunrise = 6 * 60 + 30
        val sunset = 18 * 60 + 30

        val palettes = (0..23).map { hour ->
            val millis = date.atTime(LocalTime.of(hour, 15)).atZone(zone).toInstant().toEpochMilli()
            computeHourlySolarPalette(
                nowMillis = millis,
                zoneId = zone,
                sunriseMinutes = sunrise,
                sunsetMinutes = sunset,
                isDarkTheme = false
            )
        }

        // Hour-by-hour progression produces distinct tones across solar transitions
        val midnight = palettes[0]
        val preDawn = palettes[5]
        val sunriseHour = palettes[6]
        val morning = palettes[9]
        val noon = palettes[12]
        val goldenHour = palettes[17]
        val sunsetHour = palettes[18]
        val night = palettes[21]

        assertNotEquals(midnight.startColor, preDawn.startColor)
        assertNotEquals(preDawn.startColor, sunriseHour.startColor)
        assertNotEquals(sunriseHour.startColor, morning.startColor)
        assertNotEquals(morning.startColor, noon.startColor)
        assertNotEquals(noon.startColor, goldenHour.startColor)
        assertNotEquals(goldenHour.startColor, sunsetHour.startColor)
        assertNotEquals(sunsetHour.startColor, night.startColor)

        assertEquals("Madrugada", midnight.phaseLabel)
        assertEquals("Amanecer", sunriseHour.phaseLabel)
        assertEquals("Mediodía", noon.phaseLabel)
        assertEquals("Atardecer", sunsetHour.phaseLabel)
        assertEquals("Noche", night.phaseLabel)

        assertTrue(sunriseHour.isTwilightOrGolden)
        assertTrue(sunsetHour.isTwilightOrGolden)
    }

    @Test
    fun `widget size tier selects small, compact, and large layouts cleanly`() {
        assertEquals(WidgetSizeTier.SMALL, WidgetUpdateHelper.resolveWidgetSizeTier(120, 70))
        assertEquals(WidgetSizeTier.SMALL, WidgetUpdateHelper.resolveWidgetSizeTier(140, 110))
        assertEquals(WidgetSizeTier.COMPACT, WidgetUpdateHelper.resolveWidgetSizeTier(180, 110))
        assertEquals(WidgetSizeTier.COMPACT, WidgetUpdateHelper.resolveWidgetSizeTier(220, 130))
        assertEquals(WidgetSizeTier.LARGE, WidgetUpdateHelper.resolveWidgetSizeTier(260, 140))
        assertEquals(WidgetSizeTier.LARGE, WidgetUpdateHelper.resolveWidgetSizeTier(200, 170))
    }

    @Test
    fun `widget surface style resolves light, dark, transparent, and system themes accurately`() {
        assertEquals(
            ResolvedWidgetSurface.TRANSPARENT,
            WidgetUpdateHelper.resolveSurfaceStyle(WidgetCardStyle.TRANSPARENT, ThemeMode.LIGHT, false)
        )
        assertEquals(
            ResolvedWidgetSurface.DARK,
            WidgetUpdateHelper.resolveSurfaceStyle(WidgetCardStyle.DARK, ThemeMode.LIGHT, false)
        )
        assertEquals(
            ResolvedWidgetSurface.LIGHT,
            WidgetUpdateHelper.resolveSurfaceStyle(WidgetCardStyle.LIGHT, ThemeMode.DARK, true)
        )
        assertEquals(
            ResolvedWidgetSurface.DARK,
            WidgetUpdateHelper.resolveSurfaceStyle(WidgetCardStyle.SYSTEM, ThemeMode.SYSTEM, true)
        )
        assertEquals(
            ResolvedWidgetSurface.LIGHT,
            WidgetUpdateHelper.resolveSurfaceStyle(WidgetCardStyle.SYSTEM, ThemeMode.SYSTEM, false)
        )
    }

    @Test
    fun `preferences default to app-open sync, auto bottom bar, and normalize legacy intervals`() {
        val defaults = UserPreferences()
        assertEquals(0, defaults.syncIntervalHours)
        assertEquals(BottomBarBehavior.AUTO, defaults.bottomBarBehavior)
        assertTrue(defaults.checkAllOnAppOpen)

        assertEquals(0, UserPreferencesRepository.normalizeSyncInterval(6))
        assertEquals(0, UserPreferencesRepository.normalizeSyncInterval(12))
        assertEquals(0, UserPreferencesRepository.normalizeSyncInterval(0))
        assertEquals(1, UserPreferencesRepository.normalizeSyncInterval(1))
        assertEquals(24, UserPreferencesRepository.normalizeSyncInterval(24))
    }
}
