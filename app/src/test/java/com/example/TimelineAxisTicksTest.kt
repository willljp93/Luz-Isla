package com.example

import com.example.ui.components.buildTimelineAxisTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimelineAxisTicksTest {

    private val havana = ZoneId.of("America/Havana")

    @Test
    fun `centered window strips minutes and aligns labels to exact 4-hour marks in 12h and 24h`() {
        val today = LocalDate.of(2026, 11, 25)
        val nowMillis = today.atTime(LocalTime.of(1, 52)).atZone(havana).toInstant().toEpochMilli()
        val halfDay = 12L * 60L * 60L * 1000L
        val start = nowMillis - halfDay
        val end = nowMillis + halfDay

        val ticks12h = buildTimelineAxisTicks(
            windowStartMillis = start,
            windowEndMillis = end,
            primaryDate = today,
            isCustomWindow = true,
            use24h = false,
            zoneId = havana
        )

        assertEquals(24, ticks12h.size)
        val labels12h = ticks12h.mapNotNull { it.label }
        assertEquals(listOf("2pm", "6pm", "10pm", "2am", "6am", "10am"), labels12h)
        assertTrue(labels12h.none { it.contains(":") })

        val ticks24h = buildTimelineAxisTicks(
            windowStartMillis = start,
            windowEndMillis = end,
            primaryDate = today,
            isCustomWindow = true,
            use24h = true,
            zoneId = havana
        )
        val labels24h = ticks24h.mapNotNull { it.label }
        assertEquals(listOf("14h", "18h", "22h", "02h", "06h", "10h"), labels24h)
        assertTrue(labels24h.none { it.contains(":") })
    }

    @Test
    fun `centered window distinguishes yesterday and today across midnight`() {
        val today = LocalDate.of(2026, 11, 25)
        val nowMillis = today.atTime(LocalTime.of(1, 52)).atZone(havana).toInstant().toEpochMilli()
        val halfDay = 12L * 60L * 60L * 1000L

        val ticks = buildTimelineAxisTicks(
            windowStartMillis = nowMillis - halfDay,
            windowEndMillis = nowMillis + halfDay,
            primaryDate = today,
            isCustomWindow = true,
            use24h = false,
            zoneId = havana
        )

        val midnightTick = ticks.single { it.isMidnight }
        assertEquals(today, midnightTick.dayDate)
        assertTrue(midnightTick.isPrimaryDay)

        val yesterdayTicks = ticks.filter { it.dayDate == today.minusDays(1) }
        val todayTicks = ticks.filter { it.dayDate == today }
        assertTrue(yesterdayTicks.isNotEmpty())
        assertTrue(todayTicks.isNotEmpty())
        assertTrue(yesterdayTicks.all { !it.isPrimaryDay })
        assertTrue(todayTicks.all { it.isPrimaryDay })
    }

    @Test
    fun `standard 24h day view produces 25 hourly boundary ticks and 7 major labels`() {
        val date = LocalDate.of(2026, 11, 25)
        val dayStart = date.atStartOfDay(havana).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(havana).toInstant().toEpochMilli()

        val ticks12h = buildTimelineAxisTicks(
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            primaryDate = date,
            isCustomWindow = false,
            use24h = false,
            zoneId = havana
        )

        assertEquals(25, ticks12h.size)
        assertEquals(listOf("12am", "4am", "8am", "12pm", "4pm", "8pm", "12am"), ticks12h.mapNotNull { it.label })
        assertTrue(ticks12h.all { it.isPrimaryDay })
        assertFalse(ticks12h[1].isMajor)

        val ticks24h = buildTimelineAxisTicks(
            windowStartMillis = dayStart,
            windowEndMillis = dayEnd,
            primaryDate = date,
            isCustomWindow = false,
            use24h = true,
            zoneId = havana
        )
        assertEquals(listOf("00h", "04h", "08h", "12h", "16h", "20h", "24h"), ticks24h.mapNotNull { it.label })
    }
}
