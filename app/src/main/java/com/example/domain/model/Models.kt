package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ScheduleType {
    FIXED,
    ROTATING,
    CUSTOM,
    NONE
}

enum class ScheduledState {
    SERVICE_SCHEDULED,
    OUTAGE_SCHEDULED,
    UNKNOWN
}

enum class RuleSource {
    MANUAL_EXCEPTION,
    EXPLICIT_FORECAST,
    FIXED_RULE,
    ROTATING_GROUP,
    SYNCHRONIZED_RELATION,
    NONE
}

enum class RelationType {
    INFORMATIVA,
    MISMO_HORARIO,
    HORARIO_INVERSO,
    DEPENDIENTE,
    PERSONALIZADA
}

enum class IntervalKind {
    SERVICE,
    OUTAGE
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Serializable
data class Circuit(
    val code: String,
    val customName: String = "",
    val comment: String = "",
    val groupId: String? = null,
    val scheduleType: ScheduleType = ScheduleType.NONE,
    val isActive: Boolean = true,
    val isFavorite: Boolean = false,
    val isPrimaryHome: Boolean = false,
    val displayOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun displayTitle(): String = if (customName.isNotBlank()) "$code – $customName" else code
}

@Serializable
data class ScheduleGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val serviceDurationMinutes: Int = 240,
    val outageDurationMinutes: Int = 720,
    val referenceEpochMillis: Long,
    val phaseOffsetMinutes: Int = 0,
    val isActive: Boolean = true
) {
    val cycleDurationMinutes: Int
        get() = (serviceDurationMinutes + outageDurationMinutes).coerceAtLeast(1)
}

@Serializable
data class FixedScheduleRule(
    val id: String,
    val circuitCode: String,
    val daysOfWeekMask: Int = 0b1111111, // bit 0 = Mon .. bit 6 = Sun
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int, // Supports >1440 or <= startMinuteOfDay for midnight crossing
    val validFromEpochDay: Long? = null,
    val validToEpochDay: Long? = null,
    val label: String = ""
)

@Serializable
data class ExplicitForecast(
    val id: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val circuitCodes: List<String>,
    val intervalKind: IntervalKind = IntervalKind.SERVICE,
    val notes: String = "",
    val isHistoricalTest: Boolean = false
)

@Serializable
data class ManualException(
    val id: String,
    val circuitCode: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val hasService: Boolean,
    val reason: String = ""
)

@Serializable
data class CircuitRelation(
    val id: String,
    val sourceCircuitCode: String,
    val targetCircuitCode: String,
    val relationType: RelationType = RelationType.INFORMATIVA,
    val syncRuleEnabled: Boolean = false,
    val notes: String = ""
)

data class ScheduleSnapshot(
    val circuits: List<Circuit>,
    val groups: List<ScheduleGroup>,
    val fixedRules: List<FixedScheduleRule>,
    val explicitForecasts: List<ExplicitForecast>,
    val manualExceptions: List<ManualException>,
    val relations: List<CircuitRelation>
)

data class TimelineSegment(
    val startMillis: Long,
    val endMillis: Long,
    val state: ScheduledState,
    val source: RuleSource,
    val label: String = "",
    val notes: String = "",
    val isHistoricalTest: Boolean = false
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}

data class CircuitStatusEvaluation(
    val circuit: Circuit,
    val group: ScheduleGroup?,
    val state: ScheduledState,
    val ruleSource: RuleSource,
    val currentIntervalStartMillis: Long?,
    val currentIntervalEndMillis: Long?,
    val remainingMillis: Long?,
    val nextServiceStartMillis: Long?,
    val nextServiceEndMillis: Long?,
    val nextChangeMillis: Long?,
    val activeNotes: String,
    val isHistoricalTest: Boolean,
    val todaySegments: List<TimelineSegment>,
    val relations: List<CircuitRelation>
)
