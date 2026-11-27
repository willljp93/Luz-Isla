package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CircuitEntity::class,
        ScheduleGroupEntity::class,
        FixedScheduleRuleEntity::class,
        ExplicitForecastEntity::class,
        ManualExceptionEntity::class,
        CircuitRelationEntity::class,
        ConfigBackupEntity::class,
        RotationCycleEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao
    abstract fun scheduleGroupDao(): ScheduleGroupDao
    abstract fun fixedScheduleRuleDao(): FixedScheduleRuleDao
    abstract fun explicitForecastDao(): ExplicitForecastDao
    abstract fun manualExceptionDao(): ManualExceptionDao
    abstract fun circuitRelationDao(): CircuitRelationDao
    abstract fun configBackupDao(): ConfigBackupDao
    abstract fun rotationCycleDao(): RotationCycleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rotation_cycles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `serviceDurationMinutes` INTEGER NOT NULL,
                        `outageDurationMinutes` INTEGER NOT NULL,
                        `referenceEpochMillis` INTEGER NOT NULL,
                        `orderedGroupIdsCsv` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Intencionalmente sin cambios destructivos:
                // preservar todos los circuitos, grupos, ciclos, relaciones y pronósticos
                // existentes del usuario sin eliminar registros por código o identificador.
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `manual_exceptions` ADD COLUMN `endDateEpochDay` INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Eliminar reglas fijas, excepciones y relaciones huérfanas de circuitos inexistentes
                db.execSQL(
                    """
                    DELETE FROM `fixed_schedule_rules`
                    WHERE UPPER(TRIM(`circuitCode`)) NOT IN (
                        SELECT UPPER(TRIM(`code`)) FROM `circuits`
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    DELETE FROM `manual_exceptions`
                    WHERE UPPER(TRIM(`circuitCode`)) NOT IN (
                        SELECT UPPER(TRIM(`code`)) FROM `circuits`
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    DELETE FROM `circuit_relations`
                    WHERE UPPER(TRIM(`sourceCircuitCode`)) NOT IN (
                        SELECT UPPER(TRIM(`code`)) FROM `circuits`
                    )
                    OR UPPER(TRIM(`targetCircuitCode`)) NOT IN (
                        SELECT UPPER(TRIM(`code`)) FROM `circuits`
                    )
                    """.trimIndent()
                )

                // 2. Reparar circuitos cuyo grupo ya no existe o están en ROTATING sin groupId válido
                val customType = ScheduleType.CUSTOM.name
                val rotatingType = ScheduleType.ROTATING.name
                db.execSQL(
                    """
                    UPDATE `circuits`
                    SET `groupId` = NULL,
                        `scheduleType` = ?
                    WHERE `scheduleType` = ?
                      AND (
                          `groupId` IS NULL
                          OR TRIM(`groupId`) = ''
                          OR TRIM(`groupId`) NOT IN (SELECT TRIM(`id`) FROM `schedule_groups`)
                      )
                    """.trimIndent(),
                    arrayOf<Any?>(customType, rotatingType)
                )
                db.execSQL(
                    """
                    UPDATE `circuits`
                    SET `groupId` = NULL
                    WHERE `scheduleType` != ?
                      AND `groupId` IS NOT NULL
                      AND (
                          TRIM(`groupId`) = ''
                          OR TRIM(`groupId`) NOT IN (SELECT TRIM(`id`) FROM `schedule_groups`)
                      )
                    """.trimIndent(),
                    arrayOf<Any?>(rotatingType)
                )

                // 3. Limpiar códigos de circuitos inexistentes en explicit_forecasts
                val validCircuitCodes = mutableSetOf<String>()
                db.query("SELECT UPPER(TRIM(`code`)) FROM `circuits`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val code = cursor.getString(0)
                        if (!code.isNullOrBlank()) {
                            validCircuitCodes.add(code)
                        }
                    }
                }
                val forecastsToDelete = mutableListOf<String>()
                val forecastsToUpdate = mutableListOf<Pair<String, String>>()
                db.query("SELECT `id`, `circuitCodesCsv` FROM `explicit_forecasts`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val rawCsv = cursor.getString(1).orEmpty()
                        val rawTokens = rawCsv.split(",")
                            .map { it.trim().uppercase() }
                            .filter { it.isNotEmpty() }
                        val keptTokens = rawTokens.filter { it in validCircuitCodes }
                        if (keptTokens.isEmpty()) {
                            forecastsToDelete.add(id)
                        } else {
                            val updatedCsv = keptTokens.joinToString(",")
                            if (keptTokens.size != rawTokens.size || updatedCsv != rawCsv) {
                                forecastsToUpdate.add(id to updatedCsv)
                            }
                        }
                    }
                }
                forecastsToDelete.forEach { id ->
                    db.execSQL("DELETE FROM `explicit_forecasts` WHERE `id` = ?", arrayOf<Any?>(id))
                }
                forecastsToUpdate.forEach { (id, updatedCsv) ->
                    db.execSQL(
                        "UPDATE `explicit_forecasts` SET `circuitCodesCsv` = ? WHERE `id` = ?",
                        arrayOf<Any?>(updatedCsv, id)
                    )
                }

                // 4. Limpiar grupos inexistentes en rotation_cycles
                val validGroupIds = mutableSetOf<String>()
                db.query("SELECT TRIM(`id`) FROM `schedule_groups`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        if (!id.isNullOrBlank()) {
                            validGroupIds.add(id)
                        }
                    }
                }
                val cyclesToUpdate = mutableListOf<Pair<String, String>>()
                db.query("SELECT `id`, `orderedGroupIdsCsv` FROM `rotation_cycles`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val rawCsv = cursor.getString(1).orEmpty()
                        val rawTokens = rawCsv.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val keptTokens = rawTokens.filter { it in validGroupIds }
                        val updatedCsv = keptTokens.joinToString(",")
                        if (keptTokens.size != rawTokens.size || updatedCsv != rawCsv) {
                            cyclesToUpdate.add(id to updatedCsv)
                        }
                    }
                }
                cyclesToUpdate.forEach { (id, updatedCsv) ->
                    db.execSQL(
                        "UPDATE `rotation_cycles` SET `orderedGroupIdsCsv` = ? WHERE `id` = ?",
                        arrayOf<Any?>(updatedCsv, id)
                    )
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val legacy = RecordOrigin.LEGACY.name
                db.execSQL("ALTER TABLE `circuits` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `schedule_groups` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `fixed_schedule_rules` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `explicit_forecasts` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `manual_exceptions` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `circuit_relations` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
                db.execSQL("ALTER TABLE `rotation_cycles` ADD COLUMN `dataOrigin` TEXT NOT NULL DEFAULT '$legacy'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "luz_isla.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedIfEmpty(getInstance(appContext))
                        }
                    }
                })
                .build()
        }

        suspend fun seedIfEmpty(database: AppDatabase) {
            database.withTransaction {
                val existingCircuits = database.circuitDao().getAllOnce()
                val existingGroups = database.scheduleGroupDao().getAllOnce()
                val existingCycles = database.rotationCycleDao().getAllOnce()
                val existingFixedRules = database.fixedScheduleRuleDao().getAllOnce()
                if (
                    existingCircuits.isEmpty() &&
                    existingGroups.isEmpty() &&
                    existingCycles.isEmpty() &&
                    existingFixedRules.isEmpty()
                ) {
                    val now = System.currentTimeMillis()
                    database.scheduleGroupDao().upsertAll(SeedDataProvider.initialGroups())
                    database.rotationCycleDao().upsertAll(SeedDataProvider.initialCycles())
                    database.circuitDao().upsertAll(SeedDataProvider.initialCircuits(now))
                    database.fixedScheduleRuleDao().upsertAll(SeedDataProvider.initialFixedRules())
                }
            }
        }
    }
}
