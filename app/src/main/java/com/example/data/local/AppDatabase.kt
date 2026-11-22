package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        ConfigBackupEntity::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
            if (database.circuitDao().getAllOnce().isNotEmpty()) return
            val now = System.currentTimeMillis()
            database.scheduleGroupDao().upsertAll(SeedDataProvider.initialGroups())
            database.circuitDao().upsertAll(SeedDataProvider.initialCircuits(now))
            database.fixedScheduleRuleDao().upsertAll(SeedDataProvider.initialFixedRules())
            database.circuitRelationDao().upsertAll(SeedDataProvider.initialRelations())
            database.explicitForecastDao().upsertAll(SeedDataProvider.initialHistoricalTestForecasts())
        }
    }
}
