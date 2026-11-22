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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
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
            if (database.circuitDao().getAllOnce().isNotEmpty()) {
                if (database.rotationCycleDao().getAllOnce().isEmpty() &&
                    database.scheduleGroupDao().getAllOnce().any { it.id.startsWith("grp_ejemplo_") }
                ) {
                    database.rotationCycleDao().upsertAll(SeedDataProvider.initialCycles())
                }
                return
            }
            val now = System.currentTimeMillis()
            database.scheduleGroupDao().upsertAll(SeedDataProvider.initialGroups())
            database.rotationCycleDao().upsertAll(SeedDataProvider.initialCycles())
            database.circuitDao().upsertAll(SeedDataProvider.initialCircuits(now))
            database.fixedScheduleRuleDao().upsertAll(SeedDataProvider.initialFixedRules())
            database.circuitRelationDao().upsertAll(SeedDataProvider.initialRelations())
            database.explicitForecastDao().upsertAll(SeedDataProvider.initialHistoricalTestForecasts())
        }
    }
}
