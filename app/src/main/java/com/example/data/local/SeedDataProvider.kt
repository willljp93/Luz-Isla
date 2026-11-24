package com.example.data.local

import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduleType
import java.time.LocalDate

object SeedDataProvider {

    private data class SeedCircuitSpec(
        val code: String,
        val customName: String = "",
        val comment: String = "",
        val groupId: String? = null,
        val scheduleType: ScheduleType
    )

    private fun referenceEpochMillis(): Long =
        LocalDate.of(2026, 11, 22)
            .atTime(12, 0)
            .atZone(ScheduleEngine.HAVANA_ZONE)
            .toInstant()
            .toEpochMilli()

    fun initialCircuits(nowMillis: Long): List<CircuitEntity> {
        val specs = listOf(
            // G1 (0h): A-888, A-220, A-480, A-177
            SeedCircuitSpec("A888", comment = "Anteriormente A147", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A220", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A480", comment = "Referenciado en G1 y G3", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A177", customName = "Chacón", comment = "Reparto Chacón", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),

            // G2 (+4h): A-060, A-090, A-040
            SeedCircuitSpec("A060", groupId = "grp_g2", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A090", groupId = "grp_g2", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A040", groupId = "grp_g2", scheduleType = ScheduleType.ROTATING),

            // G3 (+8h): A-070, A-080, A-133, A-480 (ya asignado en G1), A-386
            SeedCircuitSpec("A070", groupId = "grp_g3", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A080", groupId = "grp_g3", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A133", groupId = "grp_g3", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A386", customName = "Sierra Caballo", comment = "Zona del Polígono", groupId = "grp_g3", scheduleType = ScheduleType.ROTATING),

            // G4 (+12h): A-050, A-210, A-310, 3040, 3042
            SeedCircuitSpec("A050", groupId = "grp_g4", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A210", groupId = "grp_g4", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("A310", groupId = "grp_g4", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("3040", groupId = "grp_g4", scheduleType = ScheduleType.ROTATING),
            SeedCircuitSpec("3042", groupId = "grp_g4", scheduleType = ScheduleType.ROTATING),

            // Horario fijo
            SeedCircuitSpec("A030", comment = "Fijo 1 · Diario 09:00–17:00", groupId = null, scheduleType = ScheduleType.FIXED),
            SeedCircuitSpec("A230", comment = "Fijo 1 · Diario 09:00–17:00", groupId = null, scheduleType = ScheduleType.FIXED),
            SeedCircuitSpec("A020", comment = "Fijo 2 · Diario 08:00–17:00", groupId = null, scheduleType = ScheduleType.FIXED)
        )

        return specs.mapIndexed { index, spec ->
            CircuitEntity(
                code = spec.code,
                customName = spec.customName,
                comment = spec.comment,
                groupId = spec.groupId,
                scheduleType = spec.scheduleType.name,
                isActive = true,
                isFavorite = spec.code == "A210" || spec.code == "A386",
                isPrimaryHome = spec.code == "A210",
                displayOrder = index,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
        }
    }

    fun initialFixedRules(): List<FixedScheduleRuleEntity> = listOf(
        FixedScheduleRuleEntity(
            id = "fixed_a030",
            circuitCode = "A030",
            daysOfWeekMask = 0b1111111,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            label = "Fijo 1 · Diario 09:00–17:00"
        ),
        FixedScheduleRuleEntity(
            id = "fixed_a230",
            circuitCode = "A230",
            daysOfWeekMask = 0b1111111,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            label = "Fijo 1 · Diario 09:00–17:00"
        ),
        FixedScheduleRuleEntity(
            id = "fixed_a020",
            circuitCode = "A020",
            daysOfWeekMask = 0b1111111,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 17 * 60,
            label = "Fijo 2 · Diario 08:00–17:00"
        )
    )

    fun initialGroups(): List<ScheduleGroupEntity> {
        val baseRef = referenceEpochMillis()
        return listOf(
            ScheduleGroupEntity(
                id = "grp_g1",
                name = "G1",
                description = "Ciclo 12×4 · Ref 22/11/2026: sin servicio 00:00–12:00, servicio 12:00 (desfase 0 h).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 0,
                isActive = true
            ),
            ScheduleGroupEntity(
                id = "grp_g2",
                name = "G2",
                description = "Ciclo 12×4 · Ref 22/11/2026: sin servicio 04:00–16:00, servicio 16:00 (desfase +4 h).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 240,
                isActive = true
            ),
            ScheduleGroupEntity(
                id = "grp_g3",
                name = "G3",
                description = "Ciclo 12×4 · Ref 22/11/2026: sin servicio 08:00–20:00, servicio 20:00 (desfase +8 h).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 480,
                isActive = true
            ),
            ScheduleGroupEntity(
                id = "grp_g4",
                name = "G4",
                description = "Ciclo 12×4 · Ref 22/11/2026: sin servicio 12:00–00:00, servicio 00:00 día siguiente (desfase +12 h).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 720,
                isActive = true
            )
        )
    }

    fun initialCycles(): List<RotationCycleEntity> {
        val baseRef = referenceEpochMillis()
        return listOf(
            RotationCycleEntity(
                id = "cycle_12x4",
                name = "Ciclo 12 × 4",
                description = "4 grupos rotativos (12h sin servicio / 4h con servicio, desfase +4h desde G1, ref. 22 nov 2026).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                orderedGroupIdsCsv = "grp_g1,grp_g2,grp_g3,grp_g4",
                isActive = true
            )
        )
    }

    fun initialRelations(): List<CircuitRelationEntity> = emptyList()

    fun initialHistoricalTestForecasts(): List<ExplicitForecastEntity> = emptyList()
}
