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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object ScheduleEngine {

    val HAVANA_ZONE: ZoneId = ZoneId.of("America/Havana")
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_DAY = 1440
    private const val LOOKAHEAD_DAYS = 14L

    private val GMT_OFFSET_REGEX = Regex("^(?:GMT|UTC)?\\s*([+-]?\\d{1,2})(?::(\\d{2}))?$", RegexOption.IGNORE_CASE)

    data class TimezonePreset(
        val zoneId: String,
        val displayName: String,
        val regionLabel: String,
        val standardOffsetHours: Int
    )

    val gmtOffsetOptions: List<String> = (-12..14).map { formatHourOffsetId(it) }

    val realTimezonePresets: List<TimezonePreset> = listOf(
        TimezonePreset("GMT-12", "Línea Int. Oeste (Baker)", "Pacífico", -12),
        TimezonePreset("Pacific/Pago_Pago", "Pago Pago (Samoa Amer.)", "Oceanía", -11),
        TimezonePreset("Pacific/Honolulu", "Honolulu (Hawái)", "Oceanía", -10),
        TimezonePreset("America/Anchorage", "Anchorage (Alaska)", "Norteamérica", -9),
        TimezonePreset("America/Los_Angeles", "Los Ángeles / Tijuana", "Norteamérica", -8),
        TimezonePreset("America/Denver", "Denver / Chihuahua", "Norteamérica", -7),
        TimezonePreset("America/Mexico_City", "Ciudad de México / Centroamérica", "América", -6),
        TimezonePreset("America/Havana", "La Habana / Nueva Gerona (Cuba)", "Caribe", -5),
        TimezonePreset("America/New_York", "Nueva York / Miami", "Norteamérica", -5),
        TimezonePreset("America/Bogota", "Bogotá / Lima / Panamá", "Sudamérica", -5),
        TimezonePreset("America/Santo_Domingo", "Santo Domingo / San Juan", "Caribe", -4),
        TimezonePreset("America/Caracas", "Caracas (Venezuela)", "Sudamérica", -4),
        TimezonePreset("America/Santiago", "Santiago (Chile)", "Sudamérica", -4),
        TimezonePreset("America/Argentina/Buenos_Aires", "Buenos Aires / Montevideo", "Sudamérica", -3),
        TimezonePreset("America/Sao_Paulo", "São Paulo / Brasilia", "Sudamérica", -3),
        TimezonePreset("Atlantic/South_Georgia", "Atlántico Sur (Georgias)", "Atlántico", -2),
        TimezonePreset("Atlantic/Azores", "Azores / Cabo Verde", "Atlántico", -1),
        TimezonePreset("Europe/London", "Londres / Lisboa", "Europa", 0),
        TimezonePreset("UTC", "Tiempo Universal (UTC)", "Estándar", 0),
        TimezonePreset("Europe/Madrid", "Madrid / París / Roma / Berlín", "Europa", 1),
        TimezonePreset("Europe/Athens", "Atenas / El Cairo / Johannesburgo", "Europa/África", 2),
        TimezonePreset("Europe/Moscow", "Moscú / Estambul / Riad", "Europa/Asia", 3),
        TimezonePreset("Asia/Dubai", "Dubái / Bakú", "Asia", 4),
        TimezonePreset("Asia/Karachi", "Karachi / Taskent", "Asia", 5),
        TimezonePreset("Asia/Dhaka", "Daca / Almaty", "Asia", 6),
        TimezonePreset("Asia/Bangkok", "Bangkok / Yakarta", "Asia", 7),
        TimezonePreset("Asia/Shanghai", "Pekín / Singapur / Manila", "Asia", 8),
        TimezonePreset("Asia/Tokyo", "Tokio / Seúl", "Asia", 9),
        TimezonePreset("Australia/Sydney", "Sídney / Guam", "Oceanía", 10),
        TimezonePreset("Pacific/Noumea", "Numea / Islas Salomón", "Oceanía", 11),
        TimezonePreset("Pacific/Auckland", "Auckland / Fiyi", "Oceanía", 12),
        TimezonePreset("Pacific/Tongatapu", "Nukualofa (Tonga)", "Oceanía", 13),
        TimezonePreset("Pacific/Kiritimati", "Kiritimati (Islas de la Línea)", "Oceanía", 14)
    )

    fun formatHourOffsetId(hour: Int): String = if (hour >= 0) "GMT+$hour" else "GMT$hour"

    fun parseGmtOffsetHours(raw: String): Int? {
        val match = GMT_OFFSET_REGEX.matchEntire(raw.trim()) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        return h.takeIf { it in -12..14 }
    }

    fun representativePresetForOffset(
        offsetHours: Int,
        atInstant: Instant = Instant.now()
    ): TimezonePreset {
        val targetSeconds = offsetHours * 3600
        // 1. Prefer America/Havana when its current offset matches
        val havana = realTimezonePresets.first { it.zoneId == HAVANA_ZONE.id }
        val havanaOffset = runCatching { HAVANA_ZONE.rules.getOffset(atInstant).totalSeconds }.getOrNull()
        if (havanaOffset == targetSeconds) return havana

        // 2. Match a preset whose live offset at `atInstant` equals targetSeconds
        val liveMatch = realTimezonePresets.firstOrNull { preset ->
            val z = runCatching { ZoneId.of(preset.zoneId) }.getOrNull() ?: return@firstOrNull false
            runCatching { z.rules.getOffset(atInstant).totalSeconds }.getOrNull() == targetSeconds
        }
        if (liveMatch != null) return liveMatch

        // 3. Fallback to standard offset catalog or fixed GMT
        return realTimezonePresets.firstOrNull { it.standardOffsetHours == offsetHours }
            ?: TimezonePreset(
                zoneId = formatHourOffsetId(offsetHours),
                displayName = "Huso fijo ${formatHourOffsetId(offsetHours)}",
                regionLabel = "Estándar",
                standardOffsetHours = offsetHours
            )
    }

    fun representativeZoneIdForOffset(
        offsetHours: Int,
        atInstant: Instant = Instant.now()
    ): String {
        val targetSeconds = offsetHours * 3600
        val preset = representativePresetForOffset(offsetHours, atInstant)
        val liveSeconds = runCatching {
            ZoneId.of(preset.zoneId).rules.getOffset(atInstant).totalSeconds
        }.getOrNull()
        return if (liveSeconds == targetSeconds) {
            preset.zoneId
        } else {
            formatHourOffsetId(offsetHours)
        }
    }

    fun isTechnicalOffsetZoneId(rawId: String): Boolean {
        val id = rawId.trim()
        return id.startsWith("Etc/", ignoreCase = true) ||
            id.startsWith("SystemV/", ignoreCase = true) ||
            GMT_OFFSET_REGEX.matches(id) ||
            id.equals("Z", ignoreCase = true)
    }

    fun presetLabelForZone(zoneId: ZoneId): String? {
        val id = zoneId.id
        return realTimezonePresets.firstOrNull { it.zoneId.equals(id, ignoreCase = true) }?.displayName
            ?: id.substringAfter('/').replace('_', ' ').takeIf { id.contains('/') && !isTechnicalOffsetZoneId(id) }
    }

    fun formatZoneSummary(
        rawZoneId: String,
        atInstant: Instant = Instant.now()
    ): String {
        val zone = parseManualZoneId(rawZoneId)
        return formatZoneSummary(zone, atInstant)
    }

    fun formatZoneSummary(
        zoneId: ZoneId,
        atInstant: Instant = Instant.now()
    ): String {
        val gmt = formatGmtOffset(zoneId, atInstant)
        val id = zoneId.id
        return if ((id.contains('/') && !isTechnicalOffsetZoneId(id)) || id.equals("UTC", ignoreCase = true)) {
            "$id ($gmt)"
        } else {
            gmt
        }
    }

    fun isSameInstantOffset(
        first: ZoneId,
        second: ZoneId,
        atInstant: Instant = Instant.now()
    ): Boolean {
        val a = runCatching { first.rules.getOffset(atInstant).totalSeconds }.getOrNull()
        val b = runCatching { second.rules.getOffset(atInstant).totalSeconds }.getOrNull()
        return a != null && a == b
    }

    fun parseManualZoneId(raw: String): ZoneId {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return HAVANA_ZONE
        val match = GMT_OFFSET_REGEX.matchEntire(trimmed)
        if (match != null) {
            val hours = match.groupValues[1].toIntOrNull()
            val mins = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (hours != null && hours in -18..18 && mins in 0..59) {
                val signedMins = if (hours < 0) -mins else mins
                return runCatching {
                    ZoneId.ofOffset("GMT", ZoneOffset.ofHoursMinutes(hours, signedMins))
                }.getOrDefault(HAVANA_ZONE)
            }
        }
        return runCatching { ZoneId.of(trimmed) }.getOrDefault(HAVANA_ZONE)
    }

    fun formatGmtOffset(zoneId: ZoneId, atInstant: Instant = Instant.now()): String {
        val totalSeconds = runCatching { zoneId.rules.getOffset(atInstant).totalSeconds }.getOrDefault(0)
        if (totalSeconds == 0) return "GMT+0"
        val sign = if (totalSeconds >= 0) "+" else "-"
        val absSeconds = abs(totalSeconds)
        val hours = absSeconds / 3600
        val mins = (absSeconds % 3600) / 60
        return if (mins == 0) {
            "GMT$sign$hours"
        } else {
            String.format(Locale.US, "GMT%s%d:%02d", sign, hours, mins)
        }
    }

    fun effectiveZone(useSystemTimezone: Boolean, manualTimezoneId: String): ZoneId {
        if (useSystemTimezone) return ZoneId.systemDefault()
        return parseManualZoneId(manualTimezoneId)
    }

    fun isDaytime(
        nowMillis: Long,
        sunriseMinutes: Int,
        sunsetMinutes: Int,
        zoneId: ZoneId = HAVANA_ZONE
    ): Boolean {
        val lt = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalTime()
        val nowMin = lt.hour * 60 + lt.minute
        val sr = sunriseMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        val ss = sunsetMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        return if (sr < ss) {
            nowMin in sr until ss
        } else {
            nowMin >= sr || nowMin < ss
        }
    }

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
        atInstant: Instant = Instant.now(),
        zoneId: ZoneId = HAVANA_ZONE
    ): CircuitStatusEvaluation {
        val nowMillis = atInstant.toEpochMilli()
        val zdt = atInstant.atZone(zoneId)
        val today = zdt.toLocalDate()
        val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val lookaheadEnd = today.plusDays(LOOKAHEAD_DAYS).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val fullTimeline = buildTimelineForRange(
            circuitCode = circuit.code,
            snapshot = snapshot,
            rangeStartMillis = todayStart,
            rangeEndMillis = lookaheadEnd,
            visitedCircuits = emptySet(),
            zoneId = zoneId
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
        date: LocalDate,
        zoneId: ZoneId = HAVANA_ZONE
    ): List<TimelineSegment> {
        val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return buildTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = startMillis,
            rangeEndMillis = endMillis,
            visitedCircuits = emptySet(),
            zoneId = zoneId
        )
    }

    fun getTimelineForRange(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zoneId: ZoneId = HAVANA_ZONE
    ): List<TimelineSegment> {
        return buildTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            visitedCircuits = emptySet(),
            zoneId = zoneId
        )
    }

    fun getTimelineForNextDays(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        startDate: LocalDate,
        days: Int = 7,
        zoneId: ZoneId = HAVANA_ZONE
    ): Map<LocalDate, List<TimelineSegment>> {
        val safeDays = days.coerceIn(1, 31)
        val rangeStart = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rangeEnd = startDate.plusDays(safeDays.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val merged = buildTimelineForRange(
            circuitCode = circuitCode,
            snapshot = snapshot,
            rangeStartMillis = rangeStart,
            rangeEndMillis = rangeEnd,
            visitedCircuits = emptySet(),
            zoneId = zoneId
        )
        return (0 until safeDays).associate { offset ->
            val day = startDate.plusDays(offset.toLong())
            val dStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            day to clipSegments(merged, dStart, dEnd)
        }
    }

    fun getGroupTimelineForDay(
        group: ScheduleGroup,
        date: LocalDate,
        zoneId: ZoneId = HAVANA_ZONE
    ): List<TimelineSegment> {
        val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return getGroupTimelineForRange(group, startMillis, endMillis)
    }

    fun getGroupTimelineForRange(
        group: ScheduleGroup,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): List<TimelineSegment> {
        if (rangeEndMillis <= rangeStartMillis) return emptyList()
        val candidates = generateRotatingCandidates(group, rangeStartMillis, rangeEndMillis)
        return resolveCandidatesToSegments(candidates, rangeStartMillis, rangeEndMillis)
    }

    fun buildTimelineForRange(
        circuitCode: String,
        snapshot: ScheduleSnapshot,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        visitedCircuits: Set<String> = emptySet(),
        zoneId: ZoneId = HAVANA_ZONE
    ): List<TimelineSegment> {
        if (rangeEndMillis <= rangeStartMillis) return emptyList()
        val normalizedCode = circuitCode.trim().uppercase()
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

        val startDate = Instant.ofEpochMilli(rangeStartMillis).atZone(zoneId).toLocalDate().minusDays(1)
        val endDate = Instant.ofEpochMilli(rangeEndMillis).atZone(zoneId).toLocalDate().plusDays(1)

        // Priority 1: Manual Exceptions (single day or date range)
        snapshot.manualExceptions
            .filter { it.circuitCode.equals(circuit.code, ignoreCase = true) }
            .forEach { ex ->
                val exStart = LocalDate.ofEpochDay(ex.dateEpochDay)
                val exEnd = LocalDate.ofEpochDay(ex.effectiveEndEpochDay())
                var cursor = if (exStart.isBefore(startDate)) startDate else exStart
                val limit = if (exEnd.isAfter(endDate)) endDate else exEnd
                while (!cursor.isAfter(limit)) {
                    val (s, e) = resolveDateMinuteSpan(cursor, ex.startMinuteOfDay, ex.endMinuteOfDay, zoneId)
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
                    cursor = cursor.plusDays(1)
                }
            }

        // Priority 2: Explicit Forecasts
        snapshot.explicitForecasts
            .filter { forecast -> forecast.circuitCodes.any { it.equals(circuit.code, ignoreCase = true) } }
            .forEach { fc ->
                val fcDate = LocalDate.ofEpochDay(fc.dateEpochDay)
                if (!fcDate.isBefore(startDate) && !fcDate.isAfter(endDate)) {
                    val (s, e) = resolveDateMinuteSpan(fcDate, fc.startMinuteOfDay, fc.endMinuteOfDay, zoneId)
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

        // Priority 3: Synchronized Relation overrides rotating group / fixed rule when enabled
        val syncRelation = snapshot.relations.firstOrNull {
            it.sourceCircuitCode.equals(circuit.code, ignoreCase = true) &&
                it.syncRuleEnabled &&
                it.relationType != RelationType.INFORMATIVA
        }
        if (syncRelation != null) {
            val targetSegments = buildTimelineForRange(
                circuitCode = syncRelation.targetCircuitCode,
                snapshot = snapshot,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                visitedCircuits = nextVisited,
                zoneId = zoneId
            )
            targetSegments.forEach { seg ->
                val mappedState = mapRelationState(seg.state, syncRelation.relationType)
                if (mappedState != ScheduledState.UNKNOWN) {
                    val relationLabel = when (syncRelation.relationType) {
                        RelationType.HORARIO_INVERSO -> "Horario inverso de ${syncRelation.targetCircuitCode}"
                        RelationType.DEPENDIENTE -> "Dependiente de ${syncRelation.targetCircuitCode}"
                        RelationType.MISMO_HORARIO, RelationType.PERSONALIZADA, RelationType.INFORMATIVA ->
                            "Sincronizado con ${syncRelation.targetCircuitCode}"
                    }
                    candidates += CandidateInterval(
                        startMillis = seg.startMillis,
                        endMillis = seg.endMillis,
                        state = mappedState,
                        source = RuleSource.SYNCHRONIZED_RELATION,
                        label = relationLabel,
                        notes = syncRelation.notes.ifBlank { relationLabel },
                        isHistoricalTest = seg.isHistoricalTest,
                        priority = 3
                    )
                }
            }
        } else {
            // Priority 4: Fixed or Rotating recurring schedules
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
                                val dayStart = cursor.atStartOfDay(zoneId).toInstant().toEpochMilli()
                                val dayEnd = cursor.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                                if (dayEnd > rangeStartMillis && dayStart < rangeEndMillis) {
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
                                    val (s, e) = resolveDateMinuteSpan(cursor, rule.startMinuteOfDay, rule.endMinuteOfDay, zoneId)
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

                ScheduleType.CUSTOM, ScheduleType.NONE -> Unit
            }
        }

        return resolveCandidatesToSegments(candidates, rangeStartMillis, rangeEndMillis)
    }

    private fun mapRelationState(targetState: ScheduledState, type: RelationType): ScheduledState {
        return when (type) {
            RelationType.MISMO_HORARIO, RelationType.DEPENDIENTE, RelationType.PERSONALIZADA -> targetState
            RelationType.HORARIO_INVERSO -> when (targetState) {
                ScheduledState.SERVICE_SCHEDULED -> ScheduledState.OUTAGE_SCHEDULED
                ScheduledState.OUTAGE_SCHEDULED -> ScheduledState.SERVICE_SCHEDULED
                ScheduledState.UNKNOWN -> ScheduledState.UNKNOWN
            }
            RelationType.INFORMATIVA -> ScheduledState.UNKNOWN
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

    fun resolveDateMinuteSpan(
        date: LocalDate,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        zoneId: ZoneId = HAVANA_ZONE
    ): Pair<Long, Long> {
        val clampedStart = startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val startZdt = date.atTime(LocalTime.of(clampedStart / 60, clampedStart % 60)).atZone(zoneId)

        val endZdt: ZonedDateTime = when {
            endMinuteOfDay > MINUTES_PER_DAY -> {
                val extraDays = endMinuteOfDay / MINUTES_PER_DAY
                val remMin = endMinuteOfDay % MINUTES_PER_DAY
                date.plusDays(extraDays.toLong())
                    .atTime(LocalTime.of(remMin / 60, remMin % 60))
                    .atZone(zoneId)
            }
            endMinuteOfDay == MINUTES_PER_DAY -> {
                date.plusDays(1).atStartOfDay(zoneId)
            }
            endMinuteOfDay <= clampedStart -> {
                val safeEnd = endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
                date.plusDays(1)
                    .atTime(LocalTime.of(safeEnd / 60, safeEnd % 60))
                    .atZone(zoneId)
            }
            else -> {
                date.atTime(LocalTime.of(endMinuteOfDay / 60, endMinuteOfDay % 60)).atZone(zoneId)
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

    private val unicodeSpaceRegex = Regex("[\\s\\u00A0\\u202F]+")
    private val amPmSpacingRegex = Regex(
        "[\\s\\u00A0\\u202F]+(a\\.?[\\s\\u00A0\\u202F]*m\\.?|p\\.?[\\s\\u00A0\\u202F]*m\\.?)",
        RegexOption.IGNORE_CASE
    )
    private val esCuLocale: Locale = Locale.forLanguageTag("es-CU")
    private val time12Formatter = DateTimeFormatter.ofPattern("hh:mm a", esCuLocale)
    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d 'de' MMM, yyyy", esCuLocale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
    private val dateTime12Formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", esCuLocale)
    private val dateTime24Formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US)

    fun keepAmPmTogether(formatted: String): String =
        amPmSpacingRegex.replace(formatted) { match ->
            "\u00A0" + match.groupValues[1].replace(unicodeSpaceRegex, "\u00A0")
        }

    fun formatTime(epochMillis: Long, use24h: Boolean, zoneId: ZoneId = HAVANA_ZONE): String =
        if (use24h) formatTime24h(epochMillis, zoneId) else formatTime12h(epochMillis, zoneId)

    fun formatTime12h(epochMillis: Long, zoneId: ZoneId = HAVANA_ZONE): String =
        keepAmPmTogether(Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(time12Formatter))

    fun formatTime24h(epochMillis: Long, zoneId: ZoneId = HAVANA_ZONE): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(time24Formatter)

    fun formatDateTimeReadable(epochMillis: Long, use24h: Boolean, zoneId: ZoneId = HAVANA_ZONE): String {
        val raw = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            .format(if (use24h) dateTime24Formatter else dateTime12Formatter)
        return if (use24h) raw else keepAmPmTogether(raw)
    }

    fun formatDateEs(date: LocalDate): String =
        date.format(dateFormatter).replaceFirstChar { it.uppercase() }

    fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

    fun formatMinuteOfDay(minuteOfDay: Int, use24h: Boolean): String =
        if (use24h) formatMinuteOfDay24h(minuteOfDay) else formatMinuteOfDay12h(minuteOfDay)

    fun formatMinuteOfDay12h(minuteOfDay: Int): String {
        val norm = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val t = LocalTime.of(norm / 60, norm % 60)
        return keepAmPmTogether(t.format(time12Formatter))
    }

    fun formatMinuteOfDay24h(minuteOfDay: Int): String {
        val norm = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return String.format(Locale.US, "%02d:%02d", norm / 60, norm % 60)
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
