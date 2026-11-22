package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleType

@Entity(
    tableName = "circuits",
    indices = [Index(value = ["groupId"]), Index(value = ["displayOrder"])]
)
data class CircuitEntity(
    @PrimaryKey val code: String,
    val customName: String = "",
    val comment: String = "",
    val groupId: String? = null,
    val scheduleType: String = ScheduleType.NONE.name,
    val isActive: Boolean = true,
    val isFavorite: Boolean = false,
    val isPrimaryHome: Boolean = false,
    val displayOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toDomain(): Circuit = Circuit(
        code = code,
        customName = customName,
        comment = comment,
        groupId = groupId,
        scheduleType = runCatching { ScheduleType.valueOf(scheduleType) }.getOrDefault(ScheduleType.NONE),
        isActive = isActive,
        isFavorite = isFavorite,
        isPrimaryHome = isPrimaryHome,
        displayOrder = displayOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(model: Circuit): CircuitEntity = CircuitEntity(
            code = model.code.trim().uppercase(),
            customName = model.customName,
            comment = model.comment,
            groupId = model.groupId,
            scheduleType = model.scheduleType.name,
            isActive = model.isActive,
            isFavorite = model.isFavorite,
            isPrimaryHome = model.isPrimaryHome,
            displayOrder = model.displayOrder,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }
}

@Entity(tableName = "schedule_groups")
data class ScheduleGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val serviceDurationMinutes: Int = 240,
    val outageDurationMinutes: Int = 720,
    val referenceEpochMillis: Long,
    val phaseOffsetMinutes: Int = 0,
    val isActive: Boolean = true
) {
    fun toDomain(): ScheduleGroup = ScheduleGroup(
        id = id,
        name = name,
        description = description,
        serviceDurationMinutes = serviceDurationMinutes,
        outageDurationMinutes = outageDurationMinutes,
        referenceEpochMillis = referenceEpochMillis,
        phaseOffsetMinutes = phaseOffsetMinutes,
        isActive = isActive
    )

    companion object {
        fun fromDomain(model: ScheduleGroup): ScheduleGroupEntity = ScheduleGroupEntity(
            id = model.id,
            name = model.name,
            description = model.description,
            serviceDurationMinutes = model.serviceDurationMinutes,
            outageDurationMinutes = model.outageDurationMinutes,
            referenceEpochMillis = model.referenceEpochMillis,
            phaseOffsetMinutes = model.phaseOffsetMinutes,
            isActive = model.isActive
        )
    }
}

@Entity(
    tableName = "fixed_schedule_rules",
    indices = [Index(value = ["circuitCode"])]
)
data class FixedScheduleRuleEntity(
    @PrimaryKey val id: String,
    val circuitCode: String,
    val daysOfWeekMask: Int = 0b1111111,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val validFromEpochDay: Long? = null,
    val validToEpochDay: Long? = null,
    val label: String = ""
) {
    fun toDomain(): FixedScheduleRule = FixedScheduleRule(
        id = id,
        circuitCode = circuitCode,
        daysOfWeekMask = daysOfWeekMask,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        validFromEpochDay = validFromEpochDay,
        validToEpochDay = validToEpochDay,
        label = label
    )

    companion object {
        fun fromDomain(model: FixedScheduleRule): FixedScheduleRuleEntity = FixedScheduleRuleEntity(
            id = model.id,
            circuitCode = model.circuitCode.trim().uppercase(),
            daysOfWeekMask = model.daysOfWeekMask,
            startMinuteOfDay = model.startMinuteOfDay,
            endMinuteOfDay = model.endMinuteOfDay,
            validFromEpochDay = model.validFromEpochDay,
            validToEpochDay = model.validToEpochDay,
            label = model.label
        )
    }
}

@Entity(
    tableName = "explicit_forecasts",
    indices = [Index(value = ["dateEpochDay"])]
)
data class ExplicitForecastEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val circuitCodesCsv: String,
    val intervalKind: String = IntervalKind.SERVICE.name,
    val notes: String = "",
    val isHistoricalTest: Boolean = false
) {
    fun toDomain(): ExplicitForecast = ExplicitForecast(
        id = id,
        dateEpochDay = dateEpochDay,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        circuitCodes = circuitCodesCsv.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() },
        intervalKind = runCatching { IntervalKind.valueOf(intervalKind) }.getOrDefault(IntervalKind.SERVICE),
        notes = notes,
        isHistoricalTest = isHistoricalTest
    )

    companion object {
        fun fromDomain(model: ExplicitForecast): ExplicitForecastEntity = ExplicitForecastEntity(
            id = model.id,
            dateEpochDay = model.dateEpochDay,
            startMinuteOfDay = model.startMinuteOfDay,
            endMinuteOfDay = model.endMinuteOfDay,
            circuitCodesCsv = model.circuitCodes.joinToString(",") { it.trim().uppercase() },
            intervalKind = model.intervalKind.name,
            notes = model.notes,
            isHistoricalTest = model.isHistoricalTest
        )
    }
}

@Entity(
    tableName = "manual_exceptions",
    indices = [Index(value = ["circuitCode"]), Index(value = ["dateEpochDay"])]
)
data class ManualExceptionEntity(
    @PrimaryKey val id: String,
    val circuitCode: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val hasService: Boolean,
    val reason: String = ""
) {
    fun toDomain(): ManualException = ManualException(
        id = id,
        circuitCode = circuitCode,
        dateEpochDay = dateEpochDay,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        hasService = hasService,
        reason = reason
    )

    companion object {
        fun fromDomain(model: ManualException): ManualExceptionEntity = ManualExceptionEntity(
            id = model.id,
            circuitCode = model.circuitCode.trim().uppercase(),
            dateEpochDay = model.dateEpochDay,
            startMinuteOfDay = model.startMinuteOfDay,
            endMinuteOfDay = model.endMinuteOfDay,
            hasService = model.hasService,
            reason = model.reason
        )
    }
}

@Entity(
    tableName = "circuit_relations",
    indices = [Index(value = ["sourceCircuitCode"]), Index(value = ["targetCircuitCode"])]
)
data class CircuitRelationEntity(
    @PrimaryKey val id: String,
    val sourceCircuitCode: String,
    val targetCircuitCode: String,
    val relationType: String = RelationType.INFORMATIVA.name,
    val syncRuleEnabled: Boolean = false,
    val notes: String = ""
) {
    fun toDomain(): CircuitRelation = CircuitRelation(
        id = id,
        sourceCircuitCode = sourceCircuitCode,
        targetCircuitCode = targetCircuitCode,
        relationType = runCatching { RelationType.valueOf(relationType) }.getOrDefault(RelationType.INFORMATIVA),
        syncRuleEnabled = syncRuleEnabled,
        notes = notes
    )

    companion object {
        fun fromDomain(model: CircuitRelation): CircuitRelationEntity = CircuitRelationEntity(
            id = model.id,
            sourceCircuitCode = model.sourceCircuitCode.trim().uppercase(),
            targetCircuitCode = model.targetCircuitCode.trim().uppercase(),
            relationType = model.relationType.name,
            syncRuleEnabled = model.syncRuleEnabled,
            notes = model.notes
        )
    }
}

@Entity(tableName = "config_backups")
data class ConfigBackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val reason: String,
    val payloadJson: String
)

@Entity(tableName = "rotation_cycles")
data class RotationCycleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val serviceDurationMinutes: Int = 240,
    val outageDurationMinutes: Int = 720,
    val referenceEpochMillis: Long,
    val orderedGroupIdsCsv: String = "",
    val isActive: Boolean = true
) {
    fun toDomain(): com.example.domain.model.RotationCycle =
        com.example.domain.model.RotationCycle(
            id = id,
            name = name,
            description = description,
            serviceDurationMinutes = serviceDurationMinutes,
            outageDurationMinutes = outageDurationMinutes,
            referenceEpochMillis = referenceEpochMillis,
            orderedGroupIds = orderedGroupIdsCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            isActive = isActive
        )

    companion object {
        fun fromDomain(model: com.example.domain.model.RotationCycle): RotationCycleEntity =
            RotationCycleEntity(
                id = model.id,
                name = model.name,
                description = model.description,
                serviceDurationMinutes = model.serviceDurationMinutes,
                outageDurationMinutes = model.outageDurationMinutes,
                referenceEpochMillis = model.referenceEpochMillis,
                orderedGroupIdsCsv = model.orderedGroupIds.joinToString(","),
                isActive = model.isActive
            )
    }
}
