package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CircuitDao {
    @Query("SELECT * FROM circuits ORDER BY isPrimaryHome DESC, isFavorite DESC, displayOrder ASC, code ASC")
    fun observeAll(): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits ORDER BY displayOrder ASC, code ASC")
    suspend fun getAllOnce(): List<CircuitEntity>

    @Query("SELECT * FROM circuits WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): CircuitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(circuit: CircuitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(circuits: List<CircuitEntity>)

    @Update
    suspend fun updateAll(circuits: List<CircuitEntity>)

    @Query("DELETE FROM circuits WHERE code = :code")
    suspend fun deleteByCode(code: String)

    @Query("DELETE FROM circuits")
    suspend fun deleteAll()
}

@Dao
interface ScheduleGroupDao {
    @Query("SELECT * FROM schedule_groups ORDER BY name ASC")
    fun observeAll(): Flow<List<ScheduleGroupEntity>>

    @Query("SELECT * FROM schedule_groups ORDER BY name ASC")
    suspend fun getAllOnce(): List<ScheduleGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: ScheduleGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<ScheduleGroupEntity>)

    @Query("DELETE FROM schedule_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM schedule_groups")
    suspend fun deleteAll()
}

@Dao
interface FixedScheduleRuleDao {
    @Query("SELECT * FROM fixed_schedule_rules ORDER BY circuitCode ASC, startMinuteOfDay ASC")
    fun observeAll(): Flow<List<FixedScheduleRuleEntity>>

    @Query("SELECT * FROM fixed_schedule_rules")
    suspend fun getAllOnce(): List<FixedScheduleRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FixedScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<FixedScheduleRuleEntity>)

    @Query("DELETE FROM fixed_schedule_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM fixed_schedule_rules WHERE circuitCode = :code")
    suspend fun deleteByCircuitCode(code: String)

    @Query("DELETE FROM fixed_schedule_rules")
    suspend fun deleteAll()
}

@Dao
interface ExplicitForecastDao {
    @Query("SELECT * FROM explicit_forecasts ORDER BY dateEpochDay ASC, startMinuteOfDay ASC")
    fun observeAll(): Flow<List<ExplicitForecastEntity>>

    @Query("SELECT * FROM explicit_forecasts")
    suspend fun getAllOnce(): List<ExplicitForecastEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forecast: ExplicitForecastEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(forecasts: List<ExplicitForecastEntity>)

    @Query("DELETE FROM explicit_forecasts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM explicit_forecasts")
    suspend fun deleteAll()
}

@Dao
interface ManualExceptionDao {
    @Query("SELECT * FROM manual_exceptions ORDER BY dateEpochDay ASC, startMinuteOfDay ASC")
    fun observeAll(): Flow<List<ManualExceptionEntity>>

    @Query("SELECT * FROM manual_exceptions")
    suspend fun getAllOnce(): List<ManualExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exception: ManualExceptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exceptions: List<ManualExceptionEntity>)

    @Query("DELETE FROM manual_exceptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM manual_exceptions")
    suspend fun deleteAll()
}

@Dao
interface CircuitRelationDao {
    @Query("SELECT * FROM circuit_relations ORDER BY sourceCircuitCode ASC")
    fun observeAll(): Flow<List<CircuitRelationEntity>>

    @Query("SELECT * FROM circuit_relations")
    suspend fun getAllOnce(): List<CircuitRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relation: CircuitRelationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(relations: List<CircuitRelationEntity>)

    @Query("DELETE FROM circuit_relations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM circuit_relations")
    suspend fun deleteAll()
}

@Dao
interface ConfigBackupDao {
    @Query("SELECT * FROM config_backups ORDER BY createdAtMillis DESC LIMIT 10")
    fun observeRecent(): Flow<List<ConfigBackupEntity>>

    @Query("SELECT * FROM config_backups ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getLatest(): ConfigBackupEntity?

    @Insert
    suspend fun insert(backup: ConfigBackupEntity)

    @Query("DELETE FROM config_backups WHERE id NOT IN (SELECT id FROM config_backups ORDER BY createdAtMillis DESC LIMIT 10)")
    suspend fun pruneOldBackups()
}

@Dao
interface RotationCycleDao {
    @Query("SELECT * FROM rotation_cycles ORDER BY name ASC")
    fun observeAll(): Flow<List<RotationCycleEntity>>

    @Query("SELECT * FROM rotation_cycles ORDER BY name ASC")
    suspend fun getAllOnce(): List<RotationCycleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cycle: RotationCycleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cycles: List<RotationCycleEntity>)

    @Query("DELETE FROM rotation_cycles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM rotation_cycles")
    suspend fun deleteAll()
}
