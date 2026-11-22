package com.example.data.local

import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.IntervalKind
import com.example.domain.model.RelationType
import com.example.domain.model.ScheduleType
import java.time.LocalDate

object SeedDataProvider {

    fun initialCircuits(nowMillis: Long): List<CircuitEntity> {
        val fixedCodes = setOf("A030", "A230", "A020")
        val raw = listOf(
            Triple("A386", "Sierra Caballo", "Zona del Polígono"),
            Triple("A070", "", ""),
            Triple("A133", "", ""),
            Triple("A090", "", ""),
            Triple("A220", "", ""),
            Triple("A177", "Chacón", "Reparto Chacón"),
            Triple("A888", "", "Anteriormente A147"),
            Triple("A210", "", ""),
            Triple("A050", "", ""),
            Triple("A010", "", ""),
            Triple("A310", "", ""),
            Triple("A330", "", ""),
            Triple("A340", "", ""),
            Triple("A060", "", ""),
            Triple("A040", "", ""),
            Triple("A080", "", ""),
            Triple("A030", "", "Horario fijo diario 09:00 AM - 05:00 PM"),
            Triple("A230", "", "Horario fijo diario 09:00 AM - 05:00 PM"),
            Triple("A020", "", "Horario fijo diario 08:00 AM - 05:00 PM")
        )
        return raw.mapIndexed { index, (code, name, comment) ->
            CircuitEntity(
                code = code,
                customName = name,
                comment = comment,
                groupId = null,
                scheduleType = if (code in fixedCodes) ScheduleType.FIXED.name else ScheduleType.CUSTOM.name,
                isActive = true,
                isFavorite = code == "A210" || code == "A386",
                isPrimaryHome = code == "A210",
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
            label = "Horario fijo diario 09:00 AM - 05:00 PM"
        ),
        FixedScheduleRuleEntity(
            id = "fixed_a230",
            circuitCode = "A230",
            daysOfWeekMask = 0b1111111,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            label = "Horario fijo diario 09:00 AM - 05:00 PM"
        ),
        FixedScheduleRuleEntity(
            id = "fixed_a020",
            circuitCode = "A020",
            daysOfWeekMask = 0b1111111,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 17 * 60,
            label = "Horario fijo diario 08:00 AM - 05:00 PM"
        )
    )

    fun initialGroups(): List<ScheduleGroupEntity> {
        val baseRef = LocalDate.of(2026, 11, 19)
            .atStartOfDay(ScheduleEngine.HAVANA_ZONE)
            .toInstant()
            .toEpochMilli()
        return listOf(
            ScheduleGroupEntity(
                id = "grp_ejemplo_1",
                name = "Grupo 1 (Plantilla 4h/12h)",
                description = "Plantilla editable de ejemplo: 4h con servicio y 12h sin servicio (base 00:00).",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 0,
                isActive = true
            ),
            ScheduleGroupEntity(
                id = "grp_ejemplo_2",
                name = "Grupo 2 (Plantilla 4h/12h)",
                description = "Plantilla editable de ejemplo: desplazado +4h respecto al Grupo 1.",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 240,
                isActive = true
            ),
            ScheduleGroupEntity(
                id = "grp_ejemplo_3",
                name = "Grupo 3 (Plantilla 4h/12h)",
                description = "Plantilla editable de ejemplo: desplazado +8h respecto al Grupo 1.",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = baseRef,
                phaseOffsetMinutes = 480,
                isActive = true
            )
        )
    }

    fun initialRelations(): List<CircuitRelationEntity> = listOf(
        CircuitRelationEntity(
            id = "rel_a080_a060",
            sourceCircuitCode = "A080",
            targetCircuitCode = "A060",
            relationType = RelationType.INFORMATIVA.name,
            syncRuleEnabled = false,
            notes = "El circuito A080 se afecta cuando el A060 tiene servicio."
        )
    )

    /**
     * Datos históricos de prueba (19 y 20 de noviembre de 2026) para validar
     * la introducción manual de pronósticos por fecha. No representan una
     * programación vigente ni ciclos rotativos permanentes.
     */
    fun initialHistoricalTestForecasts(): List<ExplicitForecastEntity> {
        val nov19 = LocalDate.of(2026, 11, 19).toEpochDay()
        val nov20 = LocalDate.of(2026, 11, 20).toEpochDay()
        val note = "Dato histórico de prueba (19-20 nov 2026). Solo referencia de validación manual."
        return listOf(
            ExplicitForecastEntity(
                id = "hist_20261119_b1",
                dateEpochDay = nov19,
                startMinuteOfDay = 0,
                endMinuteOfDay = 4 * 60,
                circuitCodesCsv = "A386,A070,A133,A090,A220",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261119_b2",
                dateEpochDay = nov19,
                startMinuteOfDay = 4 * 60,
                endMinuteOfDay = 8 * 60,
                circuitCodesCsv = "A177,A888,A210,A050,A010",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261119_b3",
                dateEpochDay = nov19,
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
                circuitCodesCsv = "A310,A330,A340,A060,A040",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261119_b4",
                dateEpochDay = nov19,
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 16 * 60,
                circuitCodesCsv = "A386,A070,A133,A090,A220",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261119_b5",
                dateEpochDay = nov19,
                startMinuteOfDay = 16 * 60,
                endMinuteOfDay = 20 * 60,
                circuitCodesCsv = "A177,A888,A210,A050,A010",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261119_b6",
                dateEpochDay = nov19,
                startMinuteOfDay = 20 * 60,
                endMinuteOfDay = 24 * 60,
                circuitCodesCsv = "A310,A330,A340,A060,A040",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b1",
                dateEpochDay = nov20,
                startMinuteOfDay = 0,
                endMinuteOfDay = 4 * 60,
                circuitCodesCsv = "A386,A070,A133,A090,A220",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b2",
                dateEpochDay = nov20,
                startMinuteOfDay = 4 * 60,
                endMinuteOfDay = 8 * 60,
                circuitCodesCsv = "A177,A888,A210,A050,A010",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b3",
                dateEpochDay = nov20,
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
                circuitCodesCsv = "A310,A330,A340,A060,A040",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b4",
                dateEpochDay = nov20,
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 16 * 60,
                circuitCodesCsv = "A386,A070,A133,A090,A220",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b5",
                dateEpochDay = nov20,
                startMinuteOfDay = 16 * 60,
                endMinuteOfDay = 20 * 60,
                circuitCodesCsv = "A177,A888,A210,A050,A010",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            ),
            ExplicitForecastEntity(
                id = "hist_20261120_b6",
                dateEpochDay = nov20,
                startMinuteOfDay = 20 * 60,
                endMinuteOfDay = 24 * 60,
                circuitCodesCsv = "A310,A330,A340,A060,A040",
                intervalKind = IntervalKind.SERVICE.name,
                notes = note,
                isHistoricalTest = true
            )
        )
    }
}
