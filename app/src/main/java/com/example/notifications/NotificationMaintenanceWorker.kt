package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class NotificationMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = executeMaintenance(applicationContext)

    companion object {
        const val WORK_NAME = "luz_isla_notification_maintenance"
        const val MAINTENANCE_INTERVAL_HOURS = 12L

        internal suspend fun executeMaintenance(context: Context): Result {
            return try {
                ScheduleNotificationManager.rescheduleUpcomingNotifications(context)
                WidgetUpdateHelper.refreshAllWidgets(context)
                Result.success()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                Result.retry()
            }
        }

        fun schedule(context: Context, enabled: Boolean): Boolean {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return false
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return false
            }
            val request = PeriodicWorkRequestBuilder<NotificationMaintenanceWorker>(
                MAINTENANCE_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            return true
        }
    }
}
