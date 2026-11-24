package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = false
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
                db.execSQL("DELETE FROM `schedule_groups` WHERE `id` LIKE 'grp_ejemplo_%'")
                db.execSQL("DELETE FROM `rotation_cycles` WHERE `id` = 'cycle_ejemplo_4_12'")
                db.execSQL("DELETE FROM `explicit_forecasts` WHERE `isHistoricalTest` = 1")
                db.execSQL("DELETE FROM `circuit_relations` WHERE `id` = 'rel_a080_a060'")
                db.execSQL("DELETE FROM `circuits` WHERE `code` IN ('A010', 'A330', 'A340')")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
            val now = System.currentTimeMillis()
            val existingCircuits = database.circuitDao().getAllOnce()
            if (existingCircuits.isEmpty()) {
                database.scheduleGroupDao().upsertAll(SeedDataProvider.initialGroups())
                database.rotationCycleDao().upsertAll(SeedDataProvider.initialCycles())
                database.circuitDao().upsertAll(SeedDataProvider.initialCircuits(now))
                database.fixedScheduleRuleDao().upsertAll(SeedDataProvider.initialFixedRules())
                return
            }

            val hasCurrentGroups = database.scheduleGroupDao().getAllOnce().any { it.id == "grp_g1" }
            val hasCurrentCycle = database.rotationCycleDao().getAllOnce().any { it.id == "cycle_12x4" }
            if (!hasCurrentGroups && !hasCurrentCycle) {
                val existingByCode = existingCircuits.associateBy { it.code.uppercase() }
                val mergedCircuits = SeedDataProvider.initialCircuits(now).map { seed ->
                    val local = existingByCode[seed.code.uppercase()]
                    if (local != null) {
                        seed.copy(
                            customName = local.customName.ifBlank { seed.customName },
                            comment = local.comment.ifBlank { seed.comment },
                            isActive = local.isActive,
                            isFavorite = local.isFavorite,
                            isPrimaryHome = local.isPrimaryHome,
                            createdAt = local.createdAt.takeIf { it > 0L } ?: now,
                            updatedAt = now
                        )
                    } else {
                        seed
                    }
                }
                database.scheduleGroupDao().upsertAll(SeedDataProvider.initialGroups())
                database.rotationCycleDao().upsertAll(SeedDataProvider.initialCycles())
                database.circuitDao().upsertAll(mergedCircuits)
                database.fixedScheduleRuleDao().upsertAll(SeedDataProvider.initialFixedRules())
            }
        }
    }
}
