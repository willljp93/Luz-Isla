package com.example

import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.RotationCycle
import com.example.domain.model.RuleSource
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEngineTest {

    private fun havanaMillis(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute))
            .atZone(ScheduleEngine.HAVANA_ZONE)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `rotating 4h service and 12h outage alternates correctly across midnight and months`() {
        val refDate = LocalDate.of(2026, 11, 30)
        val group = ScheduleGroup(
            id = "g1",
            name = "Grupo 1",
            serviceDurationMinutes = 240,
            outageDurationMinutes = 720,
            referenceEpochMillis = havanaMillis(refDate, 22, 0),
            phaseOffsetMinutes = 0
        )
        val circuit = Circuit(code = "A210", groupId = "g1", scheduleType = ScheduleType.ROTATING)
        val snapshot = ScheduleSnapshot(listOf(circuit), listOf(group), emptyList(), emptyList(), emptyList(), emptyList())

        val evalNight = ScheduleEngine.evaluateCircuit(
            circuit,
            snapshot,
            java.time.Instant.ofEpochMilli(havanaMillis(refDate, 23, 0))
        )
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalNight.state)
        assertEquals(3 * 3600_000L, evalNight.remainingMillis)

        val dec1 = LocalDate.of(2026, 12, 1)
        val evalDec1Early = ScheduleEngine.evaluateCircuit(
            circuit,
            snapshot,
            java.time.Instant.ofEpochMilli(havanaMillis(dec1, 1, 0))
        )
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalDec1Early.state)
        assertEquals(havanaMillis(dec1, 14, 0), evalDec1Early.nextServiceStartMillis)
        assertEquals(havanaMillis(dec1, 18, 0), evalDec1Early.nextServiceEndMillis)
    }

    @Test
    fun `different cycle durations 3h-9h and 6h-18h and group offsets work accurately`() {
        val base = LocalDate.of(2026, 11, 19)
        val g39 = ScheduleGroup("g39", "3/9", serviceDurationMinutes = 180, outageDurationMinutes = 540, referenceEpochMillis = havanaMillis(base, 0, 0))
        val g618 = ScheduleGroup("g618", "6/18", serviceDurationMinutes = 360, outageDurationMinutes = 1080, referenceEpochMillis = havanaMillis(base, 0, 0), phaseOffsetMinutes = 360)

        val c1 = Circuit("C1", groupId = "g39", scheduleType = ScheduleType.ROTATING)
        val c2 = Circuit("C2", groupId = "g618", scheduleType = ScheduleType.ROTATING)
        val snapshot = ScheduleSnapshot(listOf(c1, c2), listOf(g39, g618), emptyList(), emptyList(), emptyList(), emptyList())

        val segs1 = ScheduleEngine.getTimelineForDay("C1", snapshot, base)
        assertEquals(4, segs1.size)
        assertEquals(ScheduledState.SERVICE_SCHEDULED, segs1[0].state)
        assertEquals(3 * 3600_000L, segs1[0].durationMillis)
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, segs1[1].state)
        assertEquals(9 * 3600_000L, segs1[1].durationMillis)

        val evalC2 = ScheduleEngine.evaluateCircuit(c2, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(base, 7, 30)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalC2.state)
        assertEquals(havanaMillis(base, 12, 0), evalC2.currentIntervalEndMillis)
    }

    @Test
    fun `priority order manual exception overrides explicit forecast which overrides fixed rule`() {
        val date = LocalDate.of(2026, 11, 21)
        val circuit = Circuit("A030", scheduleType = ScheduleType.FIXED)
        val fixed = FixedScheduleRule("f1", "A030", 0b1111111, 9 * 60, 17 * 60)
        val forecast = ExplicitForecast("fc1", date.toEpochDay(), 10 * 60, 14 * 60, listOf("A030"), IntervalKind.OUTAGE, "Mantenimiento")
        val exception = ManualException("ex1", "A030", date.toEpochDay(), 11 * 60, 12 * 60, hasService = true, reason = "Restablecimiento de emergencia")

        val snapshot = ScheduleSnapshot(listOf(circuit), emptyList(), listOf(fixed), listOf(forecast), listOf(exception), emptyList())

        val at0930 = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 9, 30)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, at0930.state)
        assertEquals(RuleSource.FIXED_RULE, at0930.ruleSource)

        val at1030 = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 10, 30)))
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, at1030.state)
        assertEquals(RuleSource.EXPLICIT_FORECAST, at1030.ruleSource)

        val at1130 = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 11, 30)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, at1130.state)
        assertEquals(RuleSource.MANUAL_EXCEPTION, at1130.ruleSource)
    }

    @Test
    fun `circuit without schedule returns UNKNOWN and does not invent hours`() {
        val date = LocalDate.of(2026, 11, 21)
        val circuit = Circuit("A999", scheduleType = ScheduleType.NONE)
        val snapshot = ScheduleSnapshot(listOf(circuit), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val eval = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 12, 0)))
        assertEquals(ScheduledState.UNKNOWN, eval.state)
        assertEquals(RuleSource.NONE, eval.ruleSource)
        assertNull(eval.nextServiceStartMillis)
        assertEquals(1, eval.todaySegments.size)
        assertEquals(ScheduledState.UNKNOWN, eval.todaySegments.first().state)
    }

    @Test
    fun `consecutive service forecasts merge cleanly and year boundary is preserved`() {
        val dec31 = LocalDate.of(2026, 12, 31)
        val jan1 = LocalDate.of(2027, 1, 1)
        val circuit = Circuit("A177", scheduleType = ScheduleType.CUSTOM)
        val fc1 = ExplicitForecast("fc1", dec31.toEpochDay(), 20 * 60, 22 * 60, listOf("A177"), IntervalKind.SERVICE, "Bloque A")
        val fc2 = ExplicitForecast("fc2", dec31.toEpochDay(), 22 * 60, 24 * 60, listOf("A177"), IntervalKind.SERVICE, "Bloque A")
        val fc3 = ExplicitForecast("fc3", jan1.toEpochDay(), 0, 2 * 60, listOf("A177"), IntervalKind.SERVICE, "Bloque A")

        val snapshot = ScheduleSnapshot(listOf(circuit), emptyList(), emptyList(), listOf(fc1, fc2, fc3), emptyList(), emptyList())
        val eval = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(dec31, 21, 0)))

        assertEquals(ScheduledState.SERVICE_SCHEDULED, eval.state)
        assertEquals(havanaMillis(dec31, 20, 0), eval.currentIntervalStartMillis)
        assertEquals(havanaMillis(jan1, 2, 0), eval.currentIntervalEndMillis)
        assertEquals(5 * 3600_000L, eval.remainingMillis)
    }

    @Test
    fun `synchronized inverse relation derives schedule while informative relation does not`() {
        val date = LocalDate.of(2026, 11, 21)
        val a060 = Circuit("A060", scheduleType = ScheduleType.FIXED)
        val a080Info = Circuit("A080", scheduleType = ScheduleType.NONE)
        val a081Sync = Circuit("A081", scheduleType = ScheduleType.NONE)
        val fixed = FixedScheduleRule("f60", "A060", 0b1111111, 8 * 60, 12 * 60)

        val relInfo = CircuitRelation("r1", "A080", "A060", RelationType.INFORMATIVA, syncRuleEnabled = false)
        val relSync = CircuitRelation("r2", "A081", "A060", RelationType.HORARIO_INVERSO, syncRuleEnabled = true)

        val snapshot = ScheduleSnapshot(
            listOf(a060, a080Info, a081Sync),
            emptyList(),
            listOf(fixed),
            emptyList(),
            emptyList(),
            listOf(relInfo, relSync)
        )

        val evalInfo = ScheduleEngine.evaluateCircuit(a080Info, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 9, 0)))
        assertEquals(ScheduledState.UNKNOWN, evalInfo.state)

        val evalSync = ScheduleEngine.evaluateCircuit(a081Sync, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 9, 0)))
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, evalSync.state)
        assertTrue(evalSync.todaySegments.any { it.state == ScheduledState.SERVICE_SCHEDULED })
    }

    @Test
    fun `rotation cycle math computes maxGroupsSupported and effectiveOutageMinutes accurately`() {
        val cycle4x12 = RotationCycle(
            id = "c1",
            name = "4h / 12h",
            serviceDurationMinutes = 240,
            outageDurationMinutes = 720,
            referenceEpochMillis = 0L,
            orderedGroupIds = listOf("g1", "g2", "g3", "g4")
        )
        assertEquals(960, cycle4x12.totalCycleMinutes)
        assertEquals(4, cycle4x12.maxGroupsSupported)
        assertEquals(0, cycle4x12.remainderMinutes)
        assertEquals(720, cycle4x12.effectiveOutageMinutes)

        // When 5 groups of 4h try to share a 4h/12h cycle, effectiveOutageMinutes stretches to 4 * 240 = 960m to prevent overlap
        val extendedCycle = cycle4x12.copy(orderedGroupIds = listOf("g1", "g2", "g3", "g4", "g5"))
        assertEquals(960, extendedCycle.effectiveOutageMinutes)
    }

    @Test
    fun `day night helper and 12h-24h formatting honor configured zoneId`() {
        // Historical standard-time date (Jan 15, 2026): Havana is UTC-5 and Madrid is UTC+1 across all tzdata versions
        val date = LocalDate.of(2026, 1, 15)
        val noonHavana = havanaMillis(date, 12, 30)
        val nightHavana = havanaMillis(date, 22, 15)

        assertTrue(ScheduleEngine.isDaytime(noonHavana, sunriseMinutes = 6 * 60 + 30, sunsetMinutes = 18 * 60 + 30))
        assertFalse(ScheduleEngine.isDaytime(nightHavana, sunriseMinutes = 6 * 60 + 30, sunsetMinutes = 18 * 60 + 30))

        assertEquals("12:30", ScheduleEngine.formatTime(noonHavana, use24h = true, zoneId = ScheduleEngine.HAVANA_ZONE))
        assertEquals("22:15", ScheduleEngine.formatTime(nightHavana, use24h = true, zoneId = ScheduleEngine.HAVANA_ZONE))

        // UTC-5 vs UTC+1 (+6h difference check)
        val madrid = ZoneId.of("Europe/Madrid")
        val noonMadrid = ScheduleEngine.formatTime(noonHavana, use24h = true, zoneId = madrid)
        assertEquals("18:30", noonMadrid)
    }

    @Test
    fun `numeric GMT offsets and legacy zone IDs parse and format cleanly`() {
        val gmtMinus5 = ScheduleEngine.parseManualZoneId("GMT-5")
        assertEquals("GMT-5", ScheduleEngine.formatGmtOffset(gmtMinus5))

        val plus4 = ScheduleEngine.parseManualZoneId("+4")
        assertEquals("GMT+4", ScheduleEngine.formatGmtOffset(plus4))

        val halfOffset = ScheduleEngine.parseManualZoneId("GMT+5:30")
        assertEquals("GMT+5:30", ScheduleEngine.formatGmtOffset(halfOffset))

        val legacyHavana = ScheduleEngine.parseManualZoneId("America/Havana")
        assertEquals(ScheduleEngine.HAVANA_ZONE, legacyHavana)

        val effectiveManual = ScheduleEngine.effectiveZone(useSystemTimezone = false, manualTimezoneId = "GMT-4")
        assertEquals("GMT-4", ScheduleEngine.formatGmtOffset(effectiveManual))

        val effectiveSystem = ScheduleEngine.effectiveZone(useSystemTimezone = true, manualTimezoneId = "GMT-4")
        assertEquals(ZoneId.systemDefault(), effectiveSystem)
        assertEquals(27, ScheduleEngine.gmtOffsetOptions.size)
        assertEquals("GMT-12", ScheduleEngine.gmtOffsetOptions.first())
        assertEquals("GMT-11", ScheduleEngine.gmtOffsetOptions[1])
        assertEquals("GMT-10", ScheduleEngine.gmtOffsetOptions[2])
        assertEquals("GMT+0", ScheduleEngine.gmtOffsetOptions[12])
        assertEquals("GMT+14", ScheduleEngine.gmtOffsetOptions.last())

        // Representative IANA resolution matches expected offset across full range (Jan 15, 2026 standard time)
        val winterInstant = java.time.Instant.parse("2026-01-15T12:00:00Z")
        for (h in -12..14) {
            val zoneIdStr = ScheduleEngine.representativeZoneIdForOffset(h, winterInstant)
            val resolvedZone = ScheduleEngine.parseManualZoneId(zoneIdStr)
            assertEquals(ScheduleEngine.formatHourOffsetId(h), ScheduleEngine.formatGmtOffset(resolvedZone, winterInstant))
        }
        assertEquals("America/Havana", ScheduleEngine.representativeZoneIdForOffset(-5, winterInstant))
    }

    @Test
    fun `formatZoneSummary hides technical Etc GMT identifiers and keepAmPmTogether avoids breaking spaces`() {
        val winterInstant = java.time.Instant.parse("2026-01-15T12:00:00Z")
        assertTrue(ScheduleEngine.isTechnicalOffsetZoneId("Etc/GMT+4"))
        assertFalse(ScheduleEngine.isTechnicalOffsetZoneId("America/Havana"))

        assertEquals("GMT-4", ScheduleEngine.formatZoneSummary("Etc/GMT+4", winterInstant))
        assertEquals("America/Havana (GMT-5)", ScheduleEngine.formatZoneSummary("America/Havana", winterInstant))

        val formattedNbsp = ScheduleEngine.keepAmPmTogether("08:34\u202Fa.\u00A0m.")
        assertFalse(formattedNbsp.contains(' '))
        assertTrue(formattedNbsp.contains('\u00A0'))

        val noonHavana12 = ScheduleEngine.formatTime12h(havanaMillis(LocalDate.of(2026, 1, 15), 12, 30))
        assertFalse(noonHavana12.contains(' '))
        assertTrue(noonHavana12.contains('\u00A0'))
    }

    @Test
    fun `multi-day manual exception single record overrides schedule across entire date range`() {
        val startDate = LocalDate.of(2026, 11, 24)
        val midDate = LocalDate.of(2026, 11, 25)
        val endDate = LocalDate.of(2026, 11, 26)
        val afterDate = LocalDate.of(2026, 11, 27)

        val circuit = Circuit("A030", scheduleType = ScheduleType.FIXED)
        val fixed = FixedScheduleRule("f1", "A030", 0b1111111, 9 * 60, 17 * 60)
        val rangeException = ManualException(
            id = "ex_range",
            circuitCode = "A030",
            dateEpochDay = startDate.toEpochDay(),
            endDateEpochDay = endDate.toEpochDay(),
            startMinuteOfDay = 10 * 60,
            endMinuteOfDay = 14 * 60,
            hasService = false,
            reason = "Mantenimiento programado 3 días"
        )
        val snapshot = ScheduleSnapshot(listOf(circuit), emptyList(), listOf(fixed), emptyList(), listOf(rangeException), emptyList())

        listOf(startDate, midDate, endDate).forEach { day ->
            val eval = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(day, 12, 0)))
            assertEquals(ScheduledState.OUTAGE_SCHEDULED, eval.state)
            assertEquals(RuleSource.MANUAL_EXCEPTION, eval.ruleSource)
        }

        val evalAfter = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(afterDate, 12, 0)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalAfter.state)
        assertEquals(RuleSource.FIXED_RULE, evalAfter.ruleSource)
    }

    @Test
    fun `synchronized relation overrides rotating group and fixed rules while manual exception wins`() {
        val date = LocalDate.of(2026, 11, 24)
        val group = ScheduleGroup(
            id = "g1",
            name = "G1",
            serviceDurationMinutes = 240,
            outageDurationMinutes = 720,
            referenceEpochMillis = havanaMillis(date, 0, 0),
            phaseOffsetMinutes = 0
        )
        val targetFixed = Circuit("A060", scheduleType = ScheduleType.FIXED)
        val fixedRule = FixedScheduleRule("f60", "A060", 0b1111111, 8 * 60, 12 * 60)

        // cRot belongs to G1 (00:00-04:00 service, 04:00-16:00 outage) AND has an active MISMO_HORARIO relation with A060
        val cRot = Circuit("A210", groupId = "g1", scheduleType = ScheduleType.ROTATING)
        val relSame = CircuitRelation("rel_same", "A210", "A060", RelationType.MISMO_HORARIO, syncRuleEnabled = true)
        val manualEx = ManualException("ex_top", "A210", date.toEpochDay(), startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60, hasService = false, reason = "Avería")

        val snapshot = ScheduleSnapshot(
            circuits = listOf(targetFixed, cRot),
            groups = listOf(group),
            fixedRules = listOf(fixedRule),
            explicitForecasts = emptyList(),
            manualExceptions = listOf(manualEx),
            relations = listOf(relSame)
        )

        // At 10:30, G1 would be OUTAGE, but synchronized relation copies A060's SERVICE (08:00-12:00)
        val at1030 = ScheduleEngine.evaluateCircuit(cRot, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 10, 30)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, at1030.state)
        assertEquals(RuleSource.SYNCHRONIZED_RELATION, at1030.ruleSource)

        // At 09:30, Manual Exception overrides the synchronized relation
        val at0930 = ScheduleEngine.evaluateCircuit(cRot, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 9, 30)))
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, at0930.state)
        assertEquals(RuleSource.MANUAL_EXCEPTION, at0930.ruleSource)
    }
}



