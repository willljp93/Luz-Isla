package com.example.domain.engine

import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.CircuitStatusEvaluation
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
import com.example.domain.model.TimelineSegment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleEngine {

    val HAVANA_ZONE: ZoneId = ZoneId.of("America/Havana")
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_DAY = 1440
    private const val LOOKAHEAD_DAYS = 14L

    private data class CandidateInterval(
        val startMillis: Long,
        val endMillis: Long,
        val state: ScheduledState,
        val source: RuleSource,
        val label: String,
        val notes: String,
        val isHistoricalTest: Boolean,
        val priority: Int
    )

    fun evaluateCircuit(
        circuit: Circuit,
        snapshot: ScheduleSnapshot,
        atInstant: Instant = Instant.now()
    ): CircuitStatusEvaluation {
        val nowMillis = atInstant.toEpochMilli()
        val zdt = atInstant.atZone(HAVANA_ZONE)
        val today = zdt.toLocalDate()
        val todayStart = today.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()

        val lookaheadEnd = today.plusDays(LOOKAHEAD_DAYS).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val fullTimeline = buildTimelineForRange(
            circuitCode = circuit.code,
            snapshot = snapshot,
            rangeStartMillis = todayStart,
            rangeEndMillis = lookaheadEnd,
            visitedCircuits = emptySet()
        )

        val todaySegments = clipSegments(fullTimeline, todayStart, todayEnd)
        val currentSeg = fullTimeline.firstOrNull { nowMillis >= it.startMillis && nowMillis < it.endMillis }

        val currentState = currentSeg?.state ?: ScheduledState.UNKNOWN
        val currentSource = currentSeg?.source ?: RuleSource.NONE
        val currentStart = currentSeg?.startMillis
        val currentEnd = currentSeg?.endMillis
        val remaining = currentEnd?.let { (it - nowMillis).coerceAtLeast(0L) }

        val nextServiceSeg = when (currentState) {
            ScheduledState.SERVICE_SCHEDULED -> {
                fullTimeline.firstOrNull {
                    it.state == ScheduledState.SERVICE_SCHEDULED && it.startMillis > (currentEnd ?: nowMillis)
                }
            }
            else -> {
                fullTimeline.firstOrNull {
                    it.state == ScheduledState.SERVICE_SCHEDULED && it.startMillis >= nowMillis
                }
            }
        }

        val nextChange = currentSeg?.let { cur ->
            val following = fullTimeline.firstOrNull { it.startMillis >= cur.endMillis && it.state != cur.state }
            following?.startMillis ?: cur.endMillis.takeIf { it < lookaheadEnd }
        }

        val group = circuit.groupId?.let { gid -> snapshot.groups.firstOrNull { it.id == gid } }
        val relations = snapshot.relations.filter {
            it.sourceCircuitCode.equals(circuit.code, ignoreCase = true) ||
                it.targetCircuitCode.equals(circuit.code, ignoreCase = true)
        }

        return CircuitStatusEvaluation(
            circuit = circuit,
            group = group,
            state = currentState,
            ruleSource = currentSource,
            currentIntervalStartMillis = currentStart,
            currentIntervalEndMillis = currentEnd,
            remainingMillis = remaining,
            nextServiceStartMillis = nextServiceSeg?.startMillis,
            nextServiceEndMillis = nextServiceSeg?.endMillis,
            nextChangeMillis = nextChange,
            activeNotes = currentSeg?.notes.orEmpty(),
            isHistoricalTest = currentSeg?.isHistoricalTest ?: false,
            todaySegments = todaySegments,
            relations = relations
        )
    }

    fun getTimelineForDay(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        date: LocalDate
    ): List<TimelineSegment> {
        val startMillis = date.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        return buildTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = startMillis,
            rangeEndMillis = endMillis,
            visitedCircuits = emptySet()
        )
    }

    fun getTimelineForNextDays(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        startDate: LocalDate,
        days: Int = 7
    ): Map<LocalDate, List<TimelineSegment>> {
        val safeDays = days.coerceIn(1, 31)
        val rangeStart = startDate.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val rangeEnd = startDate.plusDays(safeDays.toLong()).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val merged = buildTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = rangeStart,
            rangeEndMillis = rangeEnd,
            visitedCircuits = emptySet()
        )
        return (0 until safeDays).associate { offset ->
            val day = startDate.plusDays(offset.toLong())
            val dStart = day.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
            val dEnd = day.plusDays(1).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
            day to clipSegments(merged, dStart, dEnd)
        }
    }

    fun getGroupTimelineForDay(
        group: ScheduleGroup,
        date: LocalDate
    ): List<TimelineSegment> {
        val startMillis = date.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
        val candidates = generateRotatingCandidates(group, startMillis, endMillis)
        return resolveCandidatesToSegments(candidates, startMillis, endMillis)
    }

    private fun buildTimelineForRange(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        visitedCircuits: Set<String>
    ): List<TimelineSegment> {
        if (rangeEndMillis <= rangeStartMillis) return emptyList()
        val normalizedCode = circuitCode.trim()
        if (normalizedCode in visitedCircuits) return emptyList()
        val nextVisited = visitedCircuits + normalizedCode

        val circuit = snapshot.circuits.firstOrNull {
            it.code.equals(normalizedCode, ignoreCase = true)
        } ?: return listOf(
            TimelineSegment(
                startMillis = rangeStartMillis,
                endMillis = rangeEndMillis,
                state = ScheduledState.UNKNOWN,
                source = RuleSource.NONE
            )
        )

        val candidates = mutableListOf<CandidateInterval>()

        val startDate = Instant.ofEpochMilli(rangeStartMillis).atZone(HAVANA_ZONE).toLocalDate().minusDays(1)
        val endDate = Instant.ofEpochMilli(rangeEndMillis).atZone(HAVANA_ZONE).toLocalDate().plusDays(1)

        // Priority 1: Manual Exceptions
        snapshot.manualExceptions
            .filter { it.circuitCode.equals(circuit.code, ignoreCase = true) }
            .forEach { ex ->
                val exDate = LocalDate.ofEpochDay(ex.dateEpochDay)
                if (!exDate.isBefore(startDate) && !exDate.isAfter(endDate)) {
                    val (s, e) = resolveDateMinuteSpan(exDate, ex.startMinuteOfDay, ex.endMinuteOfDay)
                    if (e > rangeStartMillis && s < rangeEndMillis) {
                        candidates += CandidateInterval(
                            startMillis = maxOf(s, rangeStartMillis),
                            endMillis = minOf(e, rangeEndMillis),
                            state = if (ex.hasService) ScheduledState.SERVICE_SCHEDULED else ScheduledState.OUTAGE_SCHEDULED,
                            source = RuleSource.MANUAL_EXCEPTION,
                            label = if (ex.hasService) "Excepción: Con servicio" else "Excepción: Sin servicio",
                            notes = ex.reason,
                            isHistoricalTest = false,
                            priority = 1
                        )
                    }
                }
            }

        // Priority 2: Explicit Forecasts
        snapshot.explicitForecasts
            .filter { forecast -> forecast.circuitCodes.any { it.equals(circuit.code, ignoreCase = true) } }
            .forEach { fc ->
                val fcDate = LocalDate.ofEpochDay(fc.dateEpochDay)
                if (!fcDate.isBefore(startDate) && !fcDate.isAfter(endDate)) {
                    val (s, e) = resolveDateMinuteSpan(fcDate, fc.startMinuteOfDay, fc.endMinuteOfDay)
                    if (e > rangeStartMillis && s < rangeEndMillis) {
                        val isService = fc.intervalKind == IntervalKind.SERVICE
                        candidates += CandidateInterval(
                            startMillis = maxOf(s, rangeStartMillis),
                            endMillis = minOf(e, rangeEndMillis),
                            state = if (isService) ScheduledState.SERVICE_SCHEDULED else ScheduledState.OUTAGE_SCHEDULED,
                            source = RuleSource.EXPLICIT_FORECAST,
                            label = if (isService) "Pronóstico: Con servicio" else "Pronóstico: Sin servicio",
                            notes = fc.notes,
                            isHistoricalTest = fc.isHistoricalTest,
                            priority = 2
                        )
                    }
                }
            }

        // Priority 3: Recurring schedules (Fixed, Rotating, or Synchronized Relation)
        when (circuit.scheduleType) {
            ScheduleType.FIXED -> {
                val rules = snapshot.fixedRules.filter { it.circuitCode.equals(circuit.code, ignoreCase = true) }
                if (rules.isNotEmpty()) {
                    var cursor = startDate
                    while (!cursor.isAfter(endDate)) {
                        val dayIndex = cursor.dayOfWeek.toZeroBasedMondayIndex()
                        val epochDay = cursor.toEpochDay()
                        val matchingRules = rules.filter { rule ->
                            val matchesDay = (rule.daysOfWeekMask and (1 shl dayIndex)) != 0
                            val afterStart = rule.validFromEpochDay == null || epochDay >= rule.validFromEpochDay
                            val beforeEnd = rule.validToEpochDay == null || epochDay <= rule.validToEpochDay
                            matchesDay && afterStart && beforeEnd
                        }
                        if (matchingRules.isNotEmpty()) {
                            val dayStart = cursor.atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
                            val dayEnd = cursor.plusDays(1).atStartOfDay(HAVANA_ZONE).toInstant().toEpochMilli()
                            if (dayEnd > rangeStartMillis && dayStart < rangeEndMillis) {
                                // Default to outage on active days outside fixed service intervals unless overridden
                                candidates += CandidateInterval(
                                    startMillis = maxOf(dayStart, rangeStartMillis),
                                    endMillis = minOf(dayEnd, rangeEndMillis),
                                    state = ScheduledState.OUTAGE_SCHEDULED,
                                    source = RuleSource.FIXED_RULE,
                                    label = "Horario fijo: Fuera de servicio",
                                    notes = "",
                                    isHistoricalTest = false,
                                    priority = 4
                                )
                            }
                            matchingRules.forEach { rule ->
                                val (s, e) = resolveDateMinuteSpan(cursor, rule.startMinuteOfDay, rule.endMinuteOfDay)
                                if (e > rangeStartMillis && s < rangeEndMillis) {
                                    candidates += CandidateInterval(
                                        startMillis = maxOf(s, rangeStartMillis),
                                        endMillis = minOf(e, rangeEndMillis),
                                        state = ScheduledState.SERVICE_SCHEDULED,
                                        source = RuleSource.FIXED_RULE,
                                        label = rule.label.ifBlank { "Horario fijo" },
                                        notes = rule.label,
                                        isHistoricalTest = false,
                                        priority = 3
                                    )
                                }
                            }
                        }
                        cursor = cursor.plusDays(1)
                    }
                }
            }

            ScheduleType.ROTATING -> {
                val group = circuit.groupId?.let { gid ->
                    snapshot.groups.firstOrNull { it.id == gid && it.isActive }
                }
                if (group != null) {
                    candidates += generateRotatingCandidates(group, rangeStartMillis, rangeEndMillis)
                }
            }

            ScheduleType.CUSTOM, ScheduleType.NONE -> {
                // Check synchronized relation if explicitly enabled
                val syncRelation = snapshot.relations.firstOrNull {
                    it.sourceCircuitCode.equals(circuit.code, ignoreCase = true) && it.syncRuleEnabled
                }
                if (syncRelation != null) {
                    val targetSegments = buildTimelineForRange(
                        circuitCode = syncRelation.targetCircuitCode,
                        snapshot = snapshot,
                        rangeStartMillis = rangeStartMillis,
                        rangeEndMillis = rangeEndMillis,
                        visitedCircuits = nextVisited
                    )
                    targetSegments.forEach { seg ->
                        val mappedState = mapRelationState(seg.state, syncRelation.relationType)
                        if (mappedState != ScheduledState.UNKNOWN) {
                            candidates += CandidateInterval(
                                startMillis = seg.startMillis,
                                endMillis = seg.endMillis,
                                state = mappedState,
                                source = RuleSource.SYNCHRONIZED_RELATION,
                                label = "Sincronizado con ${syncRelation.targetCircuitCode}",
                                notes = syncRelation.notes,
                                isHistoricalTest = seg.isHistoricalTest,
                                priority = 3
                            )
                        }
                    }
                }
            }
        }

        return resolveCandidatesToSegments(candidates, rangeStartMillis, rangeEndMillis)
    }

    private fun mapRelationState(targetState: ScheduledState, type: RelationType): ScheduledState {
        return when (type) {
            RelationType.MISMO_HORARIO -> targetState
            RelationType.HORARIO_INVERSO -> when (targetState) {
                ScheduledState.SERVICE_SCHEDULED -> ScheduledState.OUTAGE_SCHEDULED
                ScheduledState.OUTAGE_SCHEDULED -> ScheduledState.SERVICE_SCHEDULED
                ScheduledState.UNKNOWN -> ScheduledState.UNKNOWN
            }
            RelationType.DEPENDIENTE -> when (targetState) {
                ScheduledState.SERVICE_SCHEDULED -> ScheduledState.SERVICE_SCHEDULED
                ScheduledState.OUTAGE_SCHEDULED -> ScheduledState.OUTAGE_SCHEDULED
                ScheduledState.UNKNOWN -> ScheduledState.UNKNOWN
            }
            RelationType.INFORMATIVA, RelationType.PERSONALIZADA -> ScheduledState.UNKNOWN
        }
    }

    private fun generateRotatingCandidates(
        group: ScheduleGroup,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): List<CandidateInterval> {
        val serviceMin = group.serviceDurationMinutes.coerceAtLeast(1)
        val outageMin = group.outageDurationMinutes.coerceAtLeast(1)
        val cycleMin = (serviceMin + outageMin).toLong()
        val cycleMillis = cycleMin * MILLIS_PER_MINUTE
        val serviceMillis = serviceMin.toLong() * MILLIS_PER_MINUTE

        val effectiveRefMillis = group.referenceEpochMillis + group.phaseOffsetMinutes * MILLIS_PER_MINUTE
        val startOffsetFromRef = rangeStartMillis - effectiveRefMillis
        val firstCycleIndex = startOffsetFromRef.floorDiv(cycleMillis) - 1L
        val endOffsetFromRef = rangeEndMillis - effectiveRefMillis
        val lastCycleIndex = endOffsetFromRef.floorDiv(cycleMillis) + 1L

        val result = mutableListOf<CandidateInterval>()
        var idx = firstCycleIndex
        while (idx <= lastCycleIndex) {
            val cycleStart = effectiveRefMillis + idx * cycleMillis
            val serviceEnd = cycleStart + serviceMillis
            val cycleEnd = cycleStart + cycleMillis

            if (serviceEnd > rangeStartMillis && cycleStart < rangeEndMillis) {
                result += CandidateInterval(
                    startMillis = maxOf(cycleStart, rangeStartMillis),
                    endMillis = minOf(serviceEnd, rangeEndMillis),
                    state = ScheduledState.SERVICE_SCHEDULED,
                    source = RuleSource.ROTATING_GROUP,
                    label = "${group.name} (Con servicio)",
                    notes = group.description,
                    isHistoricalTest = false,
                    priority = 3
                )
            }
            if (cycleEnd > rangeStartMillis && serviceEnd < rangeEndMillis) {
                result += CandidateInterval(
                    startMillis = maxOf(serviceEnd, rangeStartMillis),
                    endMillis = minOf(cycleEnd, rangeEndMillis),
                    state = ScheduledState.OUTAGE_SCHEDULED,
                    source = RuleSource.ROTATING_GROUP,
                    label = "${group.name} (Sin servicio)",
                    notes = group.description,
                    isHistoricalTest = false,
                    priority = 3
                )
            }
            idx++
        }
        return result
    }

    private fun resolveCandidatesToSegments(
        candidates: List<CandidateInterval>,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): List<TimelineSegment> {
        val points = sortedSetOf(rangeStartMillis, rangeEndMillis)
        candidates.forEach {
            if (it.startMillis in rangeStartMillis..rangeEndMillis) points += it.startMillis
            if (it.endMillis in rangeStartMillis..rangeEndMillis) points += it.endMillis
        }

        val sortedPoints = points.toList()
        val rawSegments = mutableListOf<TimelineSegment>()
        for (i in 0 until sortedPoints.size - 1) {
            val segStart = sortedPoints[i]
            val segEnd = sortedPoints[i + 1]
            if (segEnd <= segStart) continue

            val active = candidates
                .filter { segStart >= it.startMillis && segEnd <= it.endMillis }
                .minWithOrNull(compareBy<CandidateInterval> { it.priority }.thenBy { it.state.ordinal })

            val segment = if (active != null) {
                TimelineSegment(
                    startMillis = segStart,
                    endMillis = segEnd,
                    state = active.state,
                    source = active.source,
                    label = active.label,
                    notes = active.notes,
                    isHistoricalTest = active.isHistoricalTest
                )
            } else {
                TimelineSegment(
                    startMillis = segStart,
                    endMillis = segEnd,
                    state = ScheduledState.UNKNOWN,
                    source = RuleSource.NONE
                )
            }
            rawSegments += segment
        }

        return mergeConsecutiveSegments(rawSegments)
    }

    private fun mergeConsecutiveSegments(segments: List<TimelineSegment>): List<TimelineSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<TimelineSegment>()
        var current = segments.first()
        for (i in 1 until segments.size) {
            val next = segments[i]
            val sameGroup = current.endMillis == next.startMillis &&
                current.state == next.state &&
                current.source == next.source &&
                current.label == next.label &&
                current.notes == next.notes &&
                current.isHistoricalTest == next.isHistoricalTest
            if (sameGroup) {
                current = current.copy(endMillis = next.endMillis)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun clipSegments(
        segments: List<TimelineSegment>,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): List<TimelineSegment> {
        val clipped = segments.mapNotNull { seg ->
            val s = maxOf(seg.startMillis, rangeStartMillis)
            val e = minOf(seg.endMillis, rangeEndMillis)
            if (e > s) seg.copy(startMillis = s, endMillis = e) else null
        }
        return mergeConsecutiveSegments(clipped)
    }

    /**
     * Resolves [startMinuteOfDay, endMinuteOfDay) anchored on [date] in America/Havana.
     * Supports midnight crossing via endMinuteOfDay <= startMinuteOfDay or endMinuteOfDay > 1440.
     */
    fun resolveDateMinuteSpan(
        date: LocalDate,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int
    ): Pair<Long, Long> {
        val clampedStart = startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val startZdt = date.atTime(LocalTime.of(clampedStart / 60, clampedStart % 60)).atZone(HAVANA_ZONE)

        val endZdt: ZonedDateTime = when {
            endMinuteOfDay > MINUTES_PER_DAY -> {
                val extraDays = endMinuteOfDay / MINUTES_PER_DAY
                val remMin = endMinuteOfDay % MINUTES_PER_DAY
                date.plusDays(extraDays.toLong())
                    .atTime(LocalTime.of(remMin / 60, remMin % 60))
                    .atZone(HAVANA_ZONE)
            }
            endMinuteOfDay == MINUTES_PER_DAY -> {
                date.plusDays(1).atStartOfDay(HAVANA_ZONE)
            }
            endMinuteOfDay <= clampedStart -> {
                // Crosses midnight into next day
                val safeEnd = endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
                date.plusDays(1)
                    .atTime(LocalTime.of(safeEnd / 60, safeEnd % 60))
                    .atZone(HAVANA_ZONE)
            }
            else -> {
                date.atTime(LocalTime.of(endMinuteOfDay / 60, endMinuteOfDay % 60)).atZone(HAVANA_ZONE)
            }
        }
        return startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
    }

    private fun DayOfWeek.toZeroBasedMondayIndex(): Int = when (this) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }

    // Formatting utilities in America/Havana
    private val time12Formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "CU"))
    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d 'de' MMM, yyyy", Locale("es", "CU"))
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    fun formatTime12h(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(HAVANA_ZONE).format(time12Formatter)

    fun formatTime24h(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(HAVANA_ZONE).format(time24Formatter)

    fun formatDateEs(date: LocalDate): String =
        date.format(dateFormatter).replaceFirstChar { it.uppercase() }

    fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

    fun formatMinuteOfDay12h(minuteOfDay: Int): String {
        val norm = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val t = LocalTime.of(norm / 60, norm % 60)
        return t.format(time12Formatter)
    }

    fun formatDurationHms(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationHuman(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
