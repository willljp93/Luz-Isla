package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.notifications.ScheduleNotificationManager
import com.example.widget.WidgetUpdateHelper
import java.util.concurrent.TimeUnit

class ForecastSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val repository = LuzIslaRepository(db, prefsRepo)
        val prefs = prefsRepo.getSnapshot()

        if (!prefs.autoSyncEnabled || prefs.syncUrl.isBlank()) {
            return Result.success()
        }

        val syncManager = HttpsSyncManager(repository, prefsRepo)
        return when (val outcome = syncManager.syncFromUrl(prefs.syncUrl, forceApply = false)) {
            is SyncOutcome.Updated -> {
                if (prefs.notifyNewForecast) {
                    ScheduleNotificationManager.showNewForecastNotification(
                        context = applicationContext,
                        version = outcome.version,
                        circuitCount = outcome.circuitCount
                    )
                }
                ScheduleNotificationManager.rescheduleUpcomingNotifications(applicationContext)
                WidgetUpdateHelper.refreshAllWidgets(applicationContext)
                Result.success()
            }
            is SyncOutcome.UpToDate -> Result.success()
            is SyncOutcome.Failure -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "luz_isla_periodic_forecast_sync"

        fun schedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ForecastSyncWorker>(
                intervalHours.coerceIn(1, 24).toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
