package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CircuitEntity
import com.example.data.local.CircuitRelationEntity
import com.example.data.local.ConfigBackupEntity
import com.example.data.local.ExplicitForecastEntity
import com.example.data.local.FixedScheduleRuleEntity
import com.example.data.local.ManualExceptionEntity
import com.example.data.local.RecordOrigin
import com.example.data.local.RotationCycleEntity
import com.example.data.local.ScheduleGroupEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

enum class ImportMergeMode {
    MERGE_KEEP_PERSONAL,
    REPLACE_SCHEDULE_KEEP_PERSONAL,
    REPLACE_ALL_INCLUDING_PERSONAL
}

class LuzIslaRepository(
    private val database: AppDatabase,
    private val prefsRepo: UserPreferencesRepository
) {
    private val circuitDao = database.circuitDao()
    private val groupDao = database.scheduleGroupDao()
    private val fixedDao = database.fixedScheduleRuleDao()
    private val forecastDao = database.explicitForecastDao()
    private val exceptionDao = database.manualExceptionDao()
    private val relationDao = database.circuitRelationDao()
    private val backupDao = database.configBackupDao()
    private val cycleDao = database.rotationCycleDao()

    private val baseEntitiesFlow = combine(
        circuitDao.observeAll().map { list -> list.map { it.toDomain() } },
        groupDao.observeAll().map { list -> list.map { it.toDomain() } },
        fixedDao.observeAll().map { list -> list.map { it.toDomain() } },
        forecastDao.observeAll().map { list -> list.map { it.toDomain() } },
        exceptionDao.observeAll().map { list -> list.map { it.toDomain() } }
    ) { circuits, groups, fixed, forecasts, exceptions ->
        ScheduleSnapshot(
            circuits = circuits,
            groups = groups,
            fixedRules = fixed,
            explicitForecasts = forecasts,
            manualExceptions = exceptions,
            relations = emptyList(),
            cycles = emptyList()
        )
    }

    val snapshotFlow: Flow<ScheduleSnapshot> = combine(
        baseEntitiesFlow,
        relationDao.observeAll().map { list -> list.map { it.toDomain() } },
        cycleDao.observeAll().map { list -> list.map { it.toDomain() } }
    ) { base, relations, cycles ->
        base.copy(
            relations = relations,
            cycles = cycles
        )
    }

    val backupsFlow: Flow<List<ConfigBackupEntity>> = backupDao.observeRecent()

    suspend fun ensureSeeded() {
        AppDatabase.seedIfEmpty(database)
    }

    suspend fun getCurrentSnapshot(): ScheduleSnapshot {
        ensureSeeded()
        return ScheduleSnapshot(
            circuits = circuitDao.getAllOnce().map { it.toDomain() },
            groups = groupDao.getAllOnce().map { it.toDomain() },
            fixedRules = fixedDao.getAllOnce().map { it.toDomain() },
            explicitForecasts = forecastDao.getAllOnce().map { it.toDomain() },
            manualExceptions = exceptionDao.getAllOnce().map { it.toDomain() },
            relations = relationDao.getAllOnce().map { it.toDomain() },
            cycles = cycleDao.getAllOnce().map { it.toDomain() }
        )
    }

    suspend fun upsertCircuit(circuit: Circuit) {
        val now = System.currentTimeMillis()
        val normalizedCode = circuit.code.trim().uppercase()
        database.withTransaction {
            val existing = circuitDao.getByCode(normalizedCode)
            val scheduleChanged = existing != null && (
                existing.groupId != circuit.groupId ||
                    existing.scheduleType != circuit.scheduleType.name ||
                    existing.isActive != circuit.isActive
                )
            val resolvedOrigin = when {
                existing == null -> RecordOrigin.LOCAL
                scheduleChanged -> RecordOrigin.LOCAL
                else -> RecordOrigin.fromStored(existing.dataOrigin)
            }
            val normalized = circuit.copy(
                code = normalizedCode,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: circuit.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = now
            )
            if (normalized.isPrimaryHome) {
                val demoted = circuitDao.getAllOnce().filter {
                    it.code != normalized.code && it.isPrimaryHome
                }.map { it.copy(isPrimaryHome = false) }
                if (demoted.isNotEmpty()) {
                    circuitDao.updateAll(demoted)
                }
            }
            circuitDao.upsert(CircuitEntity.fromDomain(normalized, resolvedOrigin))
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun toggleFavorite(code: String) {
        val existing = circuitDao.getByCode(code.trim().uppercase()) ?: return
        circuitDao.upsert(
            existing.copy(
                isFavorite = !existing.isFavorite,
                isPrimaryHome = if (existing.isFavorite) false else existing.isPrimaryHome,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setPrimaryHome(code: String) {
        val targetCode = code.trim().uppercase()
        database.withTransaction {
            val updated = circuitDao.getAllOnce().mapNotNull { item ->
                if (item.code == targetCode) {
                    item.copy(
                        isPrimaryHome = !item.isPrimaryHome,
                        isFavorite = if (!item.isPrimaryHome) true else item.isFavorite,
                        updatedAt = System.currentTimeMillis()
                    )
                } else if (item.isPrimaryHome) {
                    item.copy(isPrimaryHome = false)
                } else {
                    null
                }
            }
            if (updated.isNotEmpty()) {
                circuitDao.updateAll(updated)
            }
        }
    }

    suspend fun toggleCircuitActive(code: String) {
        val existing = circuitDao.getByCode(code.trim().uppercase()) ?: return
        circuitDao.upsert(
            existing.copy(
                isActive = !existing.isActive,
                updatedAt = System.currentTimeMillis(),
                dataOrigin = RecordOrigin.LOCAL.name
            )
        )
    }

    suspend fun moveCircuitOrder(code: String, moveUp: Boolean) {
        val all = circuitDao.getAllOnce().sortedBy { it.displayOrder }
        val index = all.indexOfFirst { it.code.equals(code, ignoreCase = true) }
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in all.indices) return

        val reordered = all.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(targetIndex, moved)
        val normalized = reordered.mapIndexed { idx, entity -> entity.copy(displayOrder = idx) }
        circuitDao.updateAll(normalized)
    }

    suspend fun deleteCircuit(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        database.withTransaction {
            circuitDao.deleteByCode(normalized)
            fixedDao.deleteByCircuitCode(normalized)
            exceptionDao.deleteByCircuitCode(normalized)
            relationDao.deleteByCircuitCode(normalized)

            forecastDao.getAllOnce().forEach { forecast ->
                val tokens = forecast.circuitCodesCsv
                    .split(",")
                    .map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }
                if (normalized in tokens) {
                    val remaining = tokens.filterNot { it == normalized }
                    if (remaining.isEmpty()) {
                        forecastDao.deleteById(forecast.id)
                    } else {
                        forecastDao.upsert(
                            forecast.copy(circuitCodesCsv = remaining.joinToString(","))
                        )
                    }
                }
            }
        }
        prefsRepo.removeWidgetsForCircuit(normalized)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertGroup(group: ScheduleGroup) {
        groupDao.upsert(ScheduleGroupEntity.fromDomain(group, RecordOrigin.LOCAL))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun updateGroupMembers(groupId: String, selectedCircuitCodes: Set<String>) {
        val normalizedSelected = selectedCircuitCodes.map { it.trim().uppercase() }.toSet()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val all = circuitDao.getAllOnce()
            val updated = all.mapNotNull { entity ->
                val code = entity.code.uppercase()
                when {
                    code in normalizedSelected -> {
                        if (entity.groupId == groupId && entity.scheduleType == ScheduleType.ROTATING.name) {
                            null
                        } else {
                            entity.copy(
                                groupId = groupId,
                                scheduleType = ScheduleType.ROTATING.name,
                                updatedAt = now,
                                dataOrigin = RecordOrigin.LOCAL.name
                            )
                        }
                    }
                    entity.groupId == groupId -> {
                        val nextType = if (entity.scheduleType == ScheduleType.ROTATING.name) {
                            ScheduleType.CUSTOM.name
                        } else {
                            entity.scheduleType
                        }
                        entity.copy(
                            groupId = null,
                            scheduleType = nextType,
                            updatedAt = now,
                            dataOrigin = RecordOrigin.LOCAL.name
                        )
                    }
                    else -> null
                }
            }
            if (updated.isNotEmpty()) {
                circuitDao.updateAll(updated)
            }
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteGroup(groupId: String) {
        val targetGroupId = groupId.trim()
        if (targetGroupId.isEmpty()) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            val affectedCircuits = circuitDao.getByGroupId(targetGroupId).map { entity ->
                val nextType = if (entity.scheduleType == ScheduleType.ROTATING.name) {
                    ScheduleType.CUSTOM.name
                } else {
                    entity.scheduleType
                }
                entity.copy(
                    groupId = null,
                    scheduleType = nextType,
                    updatedAt = now
                )
            }
            if (affectedCircuits.isNotEmpty()) {
                circuitDao.updateAll(affectedCircuits)
            }

            val updatedCycles = cycleDao.getAllOnce().mapNotNull { cycle ->
                val rawIds = cycle.orderedGroupIdsCsv
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (targetGroupId in rawIds) {
                    val remainingIds = rawIds.filterNot { it == targetGroupId }
                    cycle.copy(orderedGroupIdsCsv = remainingIds.joinToString(","))
                } else {
                    null
                }
            }
            if (updatedCycles.isNotEmpty()) {
                cycleDao.upsertAll(updatedCycles)
            }

            groupDao.deleteById(targetGroupId)
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertCycle(cycle: RotationCycle) {
        val cleanedIds = cycle.orderedGroupIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val normalizedCycle = cycle.copy(orderedGroupIds = cleanedIds)
        val effectiveOutage = normalizedCycle.effectiveOutageMinutes

        database.withTransaction {
            cycleDao.upsert(RotationCycleEntity.fromDomain(normalizedCycle, RecordOrigin.LOCAL))
            if (cleanedIds.isNotEmpty()) {
                val existingGroups = groupDao.getAllOnce().associateBy { it.id }
                val syncedGroups = cleanedIds.mapIndexedNotNull { index, gid ->
                    existingGroups[gid]?.let { existing ->
                        val nextPhase = index * normalizedCycle.serviceDurationMinutes
                        if (
                            existing.serviceDurationMinutes == normalizedCycle.serviceDurationMinutes &&
                            existing.outageDurationMinutes == effectiveOutage &&
                            existing.referenceEpochMillis == normalizedCycle.referenceEpochMillis &&
                            existing.phaseOffsetMinutes == nextPhase &&
                            existing.isActive == normalizedCycle.isActive
                        ) {
                            null
                        } else {
                            existing.copy(
                                serviceDurationMinutes = normalizedCycle.serviceDurationMinutes,
                                outageDurationMinutes = effectiveOutage,
                                referenceEpochMillis = normalizedCycle.referenceEpochMillis,
                                phaseOffsetMinutes = nextPhase,
                                isActive = normalizedCycle.isActive,
                                dataOrigin = RecordOrigin.LOCAL.name
                            )
                        }
                    }
                }
                if (syncedGroups.isNotEmpty()) {
                    groupDao.upsertAll(syncedGroups)
                }
            }
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteCycle(cycleId: String) {
        cycleDao.deleteById(cycleId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertFixedRule(rule: FixedScheduleRule) {
        fixedDao.upsert(FixedScheduleRuleEntity.fromDomain(rule, RecordOrigin.LOCAL))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteFixedRule(ruleId: String) {
        fixedDao.deleteById(ruleId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertExplicitForecast(forecast: ExplicitForecast) {
        forecastDao.upsert(ExplicitForecastEntity.fromDomain(forecast, RecordOrigin.LOCAL))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteExplicitForecast(forecastId: String) {
        forecastDao.deleteById(forecastId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertManualException(exception: ManualException) {
        exceptionDao.upsert(ManualExceptionEntity.fromDomain(exception, RecordOrigin.LOCAL))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteManualException(exceptionId: String) {
        exceptionDao.deleteById(exceptionId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertRelation(relation: CircuitRelation) {
        relationDao.upsert(CircuitRelationEntity.fromDomain(relation, RecordOrigin.LOCAL))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteRelation(relationId: String) {
        relationDao.deleteById(relationId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun saveBackup(reason: String, payloadJson: String) {
        backupDao.insert(
            ConfigBackupEntity(
                createdAtMillis = System.currentTimeMillis(),
                reason = reason,
                payloadJson = payloadJson
            )
        )
        backupDao.pruneOldBackups()
    }

    suspend fun getLatestBackup(): ConfigBackupEntity? = backupDao.getLatest()

    suspend fun applyImportedSnapshot(
        incoming: ScheduleSnapshot,
        mode: ImportMergeMode,
        includePersonalFromIncoming: Boolean
    ) {
        val now = System.currentTimeMillis()
        val removedWidgetCodes = mutableListOf<String>()
        var anyDataMutated = false

        database.withTransaction {
            val existingCircuits = circuitDao.getAllOnce()
            val existingCircuitsByCode = existingCircuits.associateBy { it.code.trim().uppercase() }

            when (mode) {
                ImportMergeMode.MERGE_KEEP_PERSONAL -> {
                    anyDataMutated = reconcileGroups(incoming.groups) || anyDataMutated
                    anyDataMutated = reconcileCycles(incoming.cycles) || anyDataMutated
                    val circuitOutcome = reconcileCircuits(
                        incomingCircuits = incoming.circuits,
                        existingByCode = existingCircuitsByCode,
                        includePersonalFromIncoming = includePersonalFromIncoming,
                        nowMillis = now
                    )
                    anyDataMutated = circuitOutcome.changed || anyDataMutated
                    removedWidgetCodes.addAll(circuitOutcome.deletedCodes)

                    anyDataMutated = reconcileFixedRules(incoming.fixedRules) || anyDataMutated
                    anyDataMutated = reconcileExplicitForecasts(incoming.explicitForecasts) || anyDataMutated
                    anyDataMutated = reconcileManualExceptions(incoming.manualExceptions) || anyDataMutated
                    anyDataMutated = reconcileRelations(incoming.relations) || anyDataMutated
                    anyDataMutated = enforceRelationalIntegrity(nowMillis = now) || anyDataMutated
                }

                ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL,
                ImportMergeMode.REPLACE_ALL_INCLUDING_PERSONAL -> {
                    val keepPersonal = mode == ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL && !includePersonalFromIncoming
                    val replacedOrigin = if (mode == ImportMergeMode.REPLACE_ALL_INCLUDING_PERSONAL) {
                        RecordOrigin.LOCAL
                    } else {
                        RecordOrigin.REMOTE
                    }
                    groupDao.deleteAll()
                    cycleDao.deleteAll()
                    fixedDao.deleteAll()
                    forecastDao.deleteAll()
                    exceptionDao.deleteAll()
                    relationDao.deleteAll()
                    circuitDao.deleteAll()

                    val dedupGroups = incoming.groups.associateBy { it.id }.values.map {
                        ScheduleGroupEntity.fromDomain(it, replacedOrigin)
                    }
                    val dedupCycles = incoming.cycles.associateBy { it.id }.values.map {
                        RotationCycleEntity.fromDomain(it, replacedOrigin)
                    }
                    val dedupCircuits = incoming.circuits.associateBy { it.code.trim().uppercase() }
                    var primaryAssigned = false
                    val replacedCircuits = dedupCircuits.values.mapIndexed { idx, inc ->
                        val code = inc.code.trim().uppercase()
                        val local = existingCircuitsByCode[code]
                        val desiredPrimary = if (keepPersonal) (local?.isPrimaryHome ?: false) else inc.isPrimaryHome
                        val resolvedPrimary = desiredPrimary && !primaryAssigned
                        if (resolvedPrimary) primaryAssigned = true
                        val resolvedFav = if (keepPersonal) (local?.isFavorite ?: false) else inc.isFavorite
                        CircuitEntity(
                            code = code,
                            customName = if (keepPersonal && local != null && local.customName.isNotBlank()) local.customName else inc.customName,
                            comment = if (keepPersonal && local != null && local.comment.isNotBlank()) local.comment else inc.comment,
                            groupId = inc.groupId,
                            scheduleType = inc.scheduleType.name,
                            isActive = inc.isActive,
                            isFavorite = resolvedFav || resolvedPrimary,
                            isPrimaryHome = resolvedPrimary,
                            displayOrder = local?.displayOrder ?: idx,
                            createdAt = local?.createdAt ?: inc.createdAt.takeIf { it > 0L } ?: now,
                            updatedAt = now,
                            dataOrigin = replacedOrigin.name
                        )
                    }

                    groupDao.upsertAll(dedupGroups)
                    cycleDao.upsertAll(dedupCycles)
                    circuitDao.upsertAll(replacedCircuits)
                    fixedDao.upsertAll(incoming.fixedRules.associateBy { it.id }.values.map {
                        FixedScheduleRuleEntity.fromDomain(it, replacedOrigin)
                    })
                    forecastDao.upsertAll(incoming.explicitForecasts.associateBy { it.id }.values.map {
                        ExplicitForecastEntity.fromDomain(it, replacedOrigin)
                    })
                    exceptionDao.upsertAll(incoming.manualExceptions.associateBy { it.id }.values.map {
                        ManualExceptionEntity.fromDomain(it, replacedOrigin)
                    })
                    relationDao.upsertAll(incoming.relations.associateBy { it.id }.values.map {
                        CircuitRelationEntity.fromDomain(it, replacedOrigin)
                    })

                    enforceRelationalIntegrity(nowMillis = now)
                    val remainingCodes = circuitDao.getAllOnce().map { it.code.trim().uppercase() }.toSet()
                    existingCircuitsByCode.keys.filterNot { it in remainingCodes }.forEach {
                        removedWidgetCodes.add(it)
                    }
                    anyDataMutated = true
                }
            }
        }

        removedWidgetCodes.distinct().forEach { code ->
            prefsRepo.removeWidgetsForCircuit(code)
        }
        if (anyDataMutated) {
            prefsRepo.markForecastUpdatedNow()
        }
    }

    private suspend fun reconcileGroups(incoming: List<ScheduleGroup>): Boolean {
        val existingList = groupDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            groupDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = ScheduleGroupEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            groupDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private suspend fun reconcileCycles(incoming: List<RotationCycle>): Boolean {
        val existingList = cycleDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            cycleDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = RotationCycleEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            cycleDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private data class CircuitReconcileOutcome(
        val changed: Boolean,
        val deletedCodes: List<String>
    )

    private suspend fun reconcileCircuits(
        incomingCircuits: List<Circuit>,
        existingByCode: Map<String, CircuitEntity>,
        includePersonalFromIncoming: Boolean,
        nowMillis: Long
    ): CircuitReconcileOutcome {
        val incomingByCode = incomingCircuits
            .map { it.copy(code = it.code.trim().uppercase()) }
            .filter { it.code.isNotEmpty() }
            .associateBy { it.code }

        var nextDisplayOrder = (existingByCode.values.maxOfOrNull { it.displayOrder } ?: -1) + 1
        var changed = false
        val deletedCodes = mutableListOf<String>()
        val toUpsert = mutableListOf<CircuitEntity>()

        // 1. Reconciliar circuitos remotos que ya no existen en la configuración remota
        existingByCode.values.forEach { existing ->
            val code = existing.code.trim().uppercase()
            if (code !in incomingByCode && RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.REMOTE) {
                val hasPersonalData = existing.isFavorite ||
                    existing.isPrimaryHome ||
                    existing.customName.isNotBlank() ||
                    existing.comment.isNotBlank()
                if (hasPersonalData) {
                    toUpsert += existing.copy(
                        groupId = null,
                        scheduleType = ScheduleType.NONE.name,
                        dataOrigin = RecordOrigin.LOCAL.name,
                        updatedAt = nowMillis
                    )
                    changed = true
                } else {
                    circuitDao.deleteByCode(code)
                    fixedDao.deleteByCircuitCode(code)
                    exceptionDao.deleteByCircuitCode(code)
                    relationDao.deleteByCircuitCode(code)
                    deletedCodes += code
                    changed = true
                }
            }
        }

        // 2. Insertar o actualizar circuitos entrantes conservando datos personales y programación LOCAL
        incomingByCode.values.forEach { inc ->
            val code = inc.code
            val local = existingByCode[code]
            if (local == null) {
                val hasExistingPrimary = existingByCode.values.any { it.isPrimaryHome } ||
                    toUpsert.any { it.isPrimaryHome }
                val allowIncomingPrimary = includePersonalFromIncoming && inc.isPrimaryHome && !hasExistingPrimary
                val candidate = CircuitEntity.fromDomain(
                    model = inc.copy(
                        code = code,
                        isFavorite = if (includePersonalFromIncoming) (inc.isFavorite || allowIncomingPrimary) else false,
                        isPrimaryHome = allowIncomingPrimary,
                        displayOrder = nextDisplayOrder++,
                        createdAt = inc.createdAt.takeIf { it > 0L } ?: nowMillis,
                        updatedAt = nowMillis
                    ),
                    origin = RecordOrigin.REMOTE
                )
                toUpsert += candidate
                changed = true
            } else {
                val localOrigin = RecordOrigin.fromStored(local.dataOrigin)
                val resolvedCustomName = if (includePersonalFromIncoming && inc.customName.isNotBlank()) {
                    inc.customName
                } else {
                    local.customName.ifBlank { inc.customName }
                }
                val resolvedComment = if (includePersonalFromIncoming && inc.comment.isNotBlank()) {
                    inc.comment
                } else {
                    local.comment.ifBlank { inc.comment }
                }
                val resolvedFavorite = if (includePersonalFromIncoming) inc.isFavorite else local.isFavorite
                val resolvedPrimaryHome = if (includePersonalFromIncoming) inc.isPrimaryHome else local.isPrimaryHome

                val candidate = if (localOrigin == RecordOrigin.LOCAL) {
                    // Programación modificada/creada localmente: conservar reglas y estado local
                    local.copy(
                        customName = resolvedCustomName,
                        comment = resolvedComment,
                        isFavorite = resolvedFavorite,
                        isPrimaryHome = resolvedPrimaryHome
                    )
                } else {
                    local.copy(
                        customName = resolvedCustomName,
                        comment = resolvedComment,
                        groupId = inc.groupId,
                        scheduleType = inc.scheduleType.name,
                        isActive = inc.isActive,
                        isFavorite = resolvedFavorite,
                        isPrimaryHome = resolvedPrimaryHome,
                        dataOrigin = RecordOrigin.REMOTE.name
                    )
                }

                if (candidate.copy(updatedAt = local.updatedAt) != local) {
                    toUpsert += candidate.copy(updatedAt = nowMillis)
                    changed = true
                }
            }
        }

        if (toUpsert.isNotEmpty()) {
            circuitDao.upsertAll(toUpsert)
        }
        return CircuitReconcileOutcome(changed = changed, deletedCodes = deletedCodes)
    }

    private suspend fun reconcileFixedRules(incoming: List<FixedScheduleRule>): Boolean {
        val existingList = fixedDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            fixedDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = FixedScheduleRuleEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            fixedDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private suspend fun reconcileExplicitForecasts(incoming: List<ExplicitForecast>): Boolean {
        val existingList = forecastDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            forecastDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = ExplicitForecastEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            forecastDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private suspend fun reconcileManualExceptions(incoming: List<ManualException>): Boolean {
        val existingList = exceptionDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            exceptionDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = ManualExceptionEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            exceptionDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private suspend fun reconcileRelations(incoming: List<CircuitRelation>): Boolean {
        val existingList = relationDao.getAllOnce()
        val existingById = existingList.associateBy { it.id }
        val incomingById = incoming.associateBy { it.id }
        var changed = false

        existingList.filter {
            RecordOrigin.fromStored(it.dataOrigin) == RecordOrigin.REMOTE && it.id !in incomingById
        }.forEach { obsolete ->
            relationDao.deleteById(obsolete.id)
            changed = true
        }

        val toUpsert = incomingById.values.mapNotNull { model ->
            val candidate = CircuitRelationEntity.fromDomain(model, RecordOrigin.REMOTE)
            val existing = existingById[candidate.id]
            when {
                existing == null -> candidate
                RecordOrigin.fromStored(existing.dataOrigin) == RecordOrigin.LOCAL -> null
                existing != candidate -> candidate
                else -> null
            }
        }
        if (toUpsert.isNotEmpty()) {
            relationDao.upsertAll(toUpsert)
            changed = true
        }
        return changed
    }

    private suspend fun enforceRelationalIntegrity(nowMillis: Long): Boolean {
        var changed = false
        val validGroupIds = groupDao.getAllOnce().map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
        val currentCircuits = circuitDao.getAllOnce()
        val validCircuitCodes = currentCircuits.map { it.code.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

        // 1. Reparar circuitos cuyo groupId desapareció
        val repairedCircuits = currentCircuits.mapNotNull { entity ->
            val gid = entity.groupId?.trim()
            val invalidGroup = gid != null && gid.isNotEmpty() && gid !in validGroupIds
            val emptyGroupRotating = entity.scheduleType == ScheduleType.ROTATING.name && (gid == null || gid.isEmpty())
            if (invalidGroup || emptyGroupRotating) {
                val nextType = if (entity.scheduleType == ScheduleType.ROTATING.name) {
                    ScheduleType.CUSTOM.name
                } else {
                    entity.scheduleType
                }
                entity.copy(
                    groupId = null,
                    scheduleType = nextType,
                    updatedAt = nowMillis
                )
            } else {
                null
            }
        }
        if (repairedCircuits.isNotEmpty()) {
            circuitDao.updateAll(repairedCircuits)
            changed = true
        }

        // 2. Limpiar referencias a grupos eliminados en ciclos
        val repairedCycles = cycleDao.getAllOnce().mapNotNull { cycle ->
            val rawTokens = cycle.orderedGroupIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val keptTokens = rawTokens.filter { it in validGroupIds }
            val updatedCsv = keptTokens.joinToString(",")
            if (keptTokens.size != rawTokens.size || updatedCsv != cycle.orderedGroupIdsCsv) {
                cycle.copy(orderedGroupIdsCsv = updatedCsv)
            } else {
                null
            }
        }
        if (repairedCycles.isNotEmpty()) {
            cycleDao.upsertAll(repairedCycles)
            changed = true
        }

        // 3. Limpiar reglas fijas, excepciones y relaciones huérfanas de circuitos inexistentes
        fixedDao.getAllOnce().filter {
            it.circuitCode.trim().uppercase() !in validCircuitCodes
        }.forEach {
            fixedDao.deleteById(it.id)
            changed = true
        }

        exceptionDao.getAllOnce().filter {
            it.circuitCode.trim().uppercase() !in validCircuitCodes
        }.forEach {
            exceptionDao.deleteById(it.id)
            changed = true
        }

        relationDao.getAllOnce().filter {
            it.sourceCircuitCode.trim().uppercase() !in validCircuitCodes ||
                it.targetCircuitCode.trim().uppercase() !in validCircuitCodes
        }.forEach {
            relationDao.deleteById(it.id)
            changed = true
        }

        // 4. Limpiar códigos inexistentes en explicit_forecasts
        forecastDao.getAllOnce().forEach { forecast ->
            val tokens = forecast.circuitCodesCsv.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            val kept = tokens.filter { it in validCircuitCodes }
            if (kept.isEmpty()) {
                forecastDao.deleteById(forecast.id)
                changed = true
            } else {
                val updatedCsv = kept.joinToString(",")
                if (kept.size != tokens.size || updatedCsv != forecast.circuitCodesCsv) {
                    forecastDao.upsert(forecast.copy(circuitCodesCsv = updatedCsv))
                    changed = true
                }
            }
        }

        return changed
    }
}
