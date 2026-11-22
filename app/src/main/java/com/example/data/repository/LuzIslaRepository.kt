package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CircuitEntity
import com.example.data.local.CircuitRelationEntity
import com.example.data.local.ConfigBackupEntity
import com.example.data.local.ExplicitForecastEntity
import com.example.data.local.FixedScheduleRuleEntity
import com.example.data.local.ManualExceptionEntity
import com.example.data.local.ScheduleGroupEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
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

    val snapshotFlow: Flow<ScheduleSnapshot> = combine(
        circuitDao.observeAll().map { list -> list.map { it.toDomain() } },
        groupDao.observeAll().map { list -> list.map { it.toDomain() } },
        fixedDao.observeAll().map { list -> list.map { it.toDomain() } },
        forecastDao.observeAll().map { list -> list.map { it.toDomain() } },
        exceptionDao.observeAll().map { list -> list.map { it.toDomain() } },
        relationDao.observeAll().map { list -> list.map { it.toDomain() } }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ScheduleSnapshot(
            circuits = values[0] as List<Circuit>,
            groups = values[1] as List<ScheduleGroup>,
            fixedRules = values[2] as List<FixedScheduleRule>,
            explicitForecasts = values[3] as List<ExplicitForecast>,
            manualExceptions = values[4] as List<ManualException>,
            relations = values[5] as List<CircuitRelation>
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
            relations = relationDao.getAllOnce().map { it.toDomain() }
        )
    }

    suspend fun upsertCircuit(circuit: Circuit) {
        val now = System.currentTimeMillis()
        val existing = circuitDao.getByCode(circuit.code.trim().uppercase())
        val normalized = circuit.copy(
            code = circuit.code.trim().uppercase(),
            createdAt = existing?.createdAt?.takeIf { it > 0L } ?: circuit.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now
        )
        database.withTransaction {
            if (normalized.isPrimaryHome) {
                val all = circuitDao.getAllOnce().map {
                    if (it.code != normalized.code && it.isPrimaryHome) it.copy(isPrimaryHome = false) else it
                }
                circuitDao.updateAll(all)
            }
            circuitDao.upsert(CircuitEntity.fromDomain(normalized))
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
            val updated = circuitDao.getAllOnce().map { item ->
                if (item.code == targetCode) {
                    item.copy(
                        isPrimaryHome = !item.isPrimaryHome,
                        isFavorite = if (!item.isPrimaryHome) true else item.isFavorite,
                        updatedAt = System.currentTimeMillis()
                    )
                } else if (item.isPrimaryHome) {
                    item.copy(isPrimaryHome = false)
                } else {
                    item
                }
            }
            circuitDao.updateAll(updated)
        }
    }

    suspend fun toggleCircuitActive(code: String) {
        val existing = circuitDao.getByCode(code.trim().uppercase()) ?: return
        circuitDao.upsert(
            existing.copy(
                isActive = !existing.isActive,
                updatedAt = System.currentTimeMillis()
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
        database.withTransaction {
            circuitDao.deleteByCode(normalized)
            fixedDao.deleteByCircuitCode(normalized)
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertGroup(group: ScheduleGroup) {
        groupDao.upsert(ScheduleGroupEntity.fromDomain(group))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteGroup(groupId: String) {
        database.withTransaction {
            val updatedCircuits = circuitDao.getAllOnce().map {
                if (it.groupId == groupId) it.copy(groupId = null) else it
            }
            circuitDao.updateAll(updatedCircuits)
            groupDao.deleteById(groupId)
        }
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertFixedRule(rule: FixedScheduleRule) {
        fixedDao.upsert(FixedScheduleRuleEntity.fromDomain(rule))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteFixedRule(ruleId: String) {
        fixedDao.deleteById(ruleId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertExplicitForecast(forecast: ExplicitForecast) {
        forecastDao.upsert(ExplicitForecastEntity.fromDomain(forecast))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteExplicitForecast(forecastId: String) {
        forecastDao.deleteById(forecastId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertManualException(exception: ManualException) {
        exceptionDao.upsert(ManualExceptionEntity.fromDomain(exception))
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun deleteManualException(exceptionId: String) {
        exceptionDao.deleteById(exceptionId)
        prefsRepo.markForecastUpdatedNow()
    }

    suspend fun upsertRelation(relation: CircuitRelation) {
        relationDao.upsert(CircuitRelationEntity.fromDomain(relation))
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
        database.withTransaction {
            val existingCircuits = circuitDao.getAllOnce().associateBy { it.code.uppercase() }

            when (mode) {
                ImportMergeMode.MERGE_KEEP_PERSONAL -> {
                    groupDao.upsertAll(incoming.groups.map { ScheduleGroupEntity.fromDomain(it) })
                    val mergedCircuits = incoming.circuits.mapIndexed { idx, inc ->
                        val code = inc.code.trim().uppercase()
                        val local = existingCircuits[code]
                        if (local != null) {
                            CircuitEntity(
                                code = code,
                                customName = if (includePersonalFromIncoming && inc.customName.isNotBlank()) inc.customName else local.customName.ifBlank { inc.customName },
                                comment = if (includePersonalFromIncoming && inc.comment.isNotBlank()) inc.comment else local.comment.ifBlank { inc.comment },
                                groupId = inc.groupId,
                                scheduleType = inc.scheduleType.name,
                                isActive = inc.isActive,
                                isFavorite = if (includePersonalFromIncoming) inc.isFavorite else local.isFavorite,
                                isPrimaryHome = if (includePersonalFromIncoming) inc.isPrimaryHome else local.isPrimaryHome,
                                displayOrder = local.displayOrder,
                                createdAt = local.createdAt,
                                updatedAt = now
                            )
                        } else {
                            CircuitEntity.fromDomain(
                                inc.copy(
                                    code = code,
                                    isFavorite = if (includePersonalFromIncoming) inc.isFavorite else false,
                                    isPrimaryHome = if (includePersonalFromIncoming) inc.isPrimaryHome else false,
                                    displayOrder = existingCircuits.size + idx,
                                    createdAt = inc.createdAt.takeIf { it > 0L } ?: now,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                    circuitDao.upsertAll(mergedCircuits)
                    fixedDao.upsertAll(incoming.fixedRules.map { FixedScheduleRuleEntity.fromDomain(it) })
                    forecastDao.upsertAll(incoming.explicitForecasts.map { ExplicitForecastEntity.fromDomain(it) })
                    exceptionDao.upsertAll(incoming.manualExceptions.map { ManualExceptionEntity.fromDomain(it) })
                    relationDao.upsertAll(incoming.relations.map { CircuitRelationEntity.fromDomain(it) })
                }

                ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL,
                ImportMergeMode.REPLACE_ALL_INCLUDING_PERSONAL -> {
                    val keepPersonal = mode == ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL && !includePersonalFromIncoming
                    groupDao.deleteAll()
                    fixedDao.deleteAll()
                    forecastDao.deleteAll()
                    exceptionDao.deleteAll()
                    relationDao.deleteAll()
                    circuitDao.deleteAll()

                    groupDao.upsertAll(incoming.groups.map { ScheduleGroupEntity.fromDomain(it) })
                    val replacedCircuits = incoming.circuits.mapIndexed { idx, inc ->
                        val code = inc.code.trim().uppercase()
                        val local = existingCircuits[code]
                        CircuitEntity(
                            code = code,
                            customName = if (keepPersonal && local != null && local.customName.isNotBlank()) local.customName else inc.customName,
                            comment = if (keepPersonal && local != null && local.comment.isNotBlank()) local.comment else inc.comment,
                            groupId = inc.groupId,
                            scheduleType = inc.scheduleType.name,
                            isActive = inc.isActive,
                            isFavorite = if (keepPersonal) (local?.isFavorite ?: false) else inc.isFavorite,
                            isPrimaryHome = if (keepPersonal) (local?.isPrimaryHome ?: false) else inc.isPrimaryHome,
                            displayOrder = local?.displayOrder ?: idx,
                            createdAt = local?.createdAt ?: inc.createdAt.takeIf { it > 0L } ?: now,
                            updatedAt = now
                        )
                    }
                    circuitDao.upsertAll(replacedCircuits)
                    fixedDao.upsertAll(incoming.fixedRules.map { FixedScheduleRuleEntity.fromDomain(it) })
                    forecastDao.upsertAll(incoming.explicitForecasts.map { ExplicitForecastEntity.fromDomain(it) })
                    exceptionDao.upsertAll(incoming.manualExceptions.map { ManualExceptionEntity.fromDomain(it) })
                    relationDao.upsertAll(incoming.relations.map { CircuitRelationEntity.fromDomain(it) })
                }
            }
        }
        prefsRepo.markForecastUpdatedNow()
    }
}
