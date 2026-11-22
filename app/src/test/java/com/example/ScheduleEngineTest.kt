package com.example

import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.RuleSource
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

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
            referenceEpochMillis = havanaMillis(refDate, 22, 0), // 22:00 Nov 30 -> 02:00 Dec 1
            phaseOffsetMinutes = 0
        )
        val circuit = Circuit(code = "A210", groupId = "g1", scheduleType = ScheduleType.ROTATING)
        val snapshot = ScheduleSnapshot(listOf(circuit), listOf(group), emptyList(), emptyList(), emptyList(), emptyList())

        // At 23:00 Nov 30 -> still in first 4h service window [22:00 Nov 30, 02:00 Dec 1)
        val evalNight = ScheduleEngine.evaluateCircuit(
            circuit,
            snapshot,
            java.time.Instant.ofEpochMilli(havanaMillis(refDate, 23, 0))
        )
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalNight.state)
        assertEquals(3 * 3600_000L, evalNight.remainingMillis)

        // At 01:00 Dec 1 -> still in service until 02:00 Dec 1
        val dec1 = LocalDate.of(2026, 12, 1)
        val evalDec1Early = ScheduleEngine.evaluateCircuit(
            circuit,
            snapshot,
            java.time.Instant.ofEpochMilli(havanaMillis(dec1, 1, 0))
        )
        assertEquals(ScheduledState.SERVICE_SCHEDULED, evalDec1Early.state)

        // Next service after 02:00 Dec 1 + 12h outage = 14:00 Dec 1 .. 18:00 Dec 1
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

        // C1 has service [00:00-03:00), [12:00-15:00)
        val segs1 = ScheduleEngine.getTimelineForDay("C1", snapshot, base)
        assertEquals(4, segs1.size)
        assertEquals(ScheduledState.SERVICE_SCHEDULED, segs1[0].state)
        assertEquals(3 * 3600_000L, segs1[0].durationMillis)
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, segs1[1].state)
        assertEquals(9 * 3600_000L, segs1[1].durationMillis)

        // C2 offset +6h -> outage [00:00-06:00), service [06:00-12:00), outage [12:00-24:00)
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

        // 09:30 -> Fixed service
        val at0930 = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 9, 30)))
        assertEquals(ScheduledState.SERVICE_SCHEDULED, at0930.state)
        assertEquals(RuleSource.FIXED_RULE, at0930.ruleSource)

        // 10:30 -> Explicit forecast outage overrides fixed service
        val at1030 = ScheduleEngine.evaluateCircuit(circuit, snapshot, java.time.Instant.ofEpochMilli(havanaMillis(date, 10, 30)))
        assertEquals(ScheduledState.OUTAGE_SCHEDULED, at1030.state)
        assertEquals(RuleSource.EXPLICIT_FORECAST, at1030.ruleSource)

        // 11:30 -> Manual exception service overrides explicit forecast outage
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
}
