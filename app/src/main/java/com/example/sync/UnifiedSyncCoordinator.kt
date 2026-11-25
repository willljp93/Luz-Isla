package com.example.sync

import android.content.Context
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.notifications.ScheduleNotificationManager
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class UnifiedSyncReport(
    val jsonStatus: String,
    val solarStatus: String,
    val updateStatus: String,
    val forecastUpdated: Boolean
)

class UnifiedSyncCoordinator(
    private val appContext: Context,
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) {
    suspend fun runChecks(
        zoneId: ZoneId,
        isManualTrigger: Boolean
    ): UnifiedSyncReport = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getSnapshot()
        var forecastUpdated = false

        val shouldRunUrlSync = prefs.syncUrl.isNotBlank() &&
            (isManualTrigger || (prefs.autoSyncEnabled && prefs.syncIntervalHours == 0))

        val jsonStatus = when {
            prefs.syncUrl.isBlank() -> "Sin URL configurada"
            !shouldRunUrlSync -> "Auto (${prefs.syncIntervalHours}h)"
            else -> {
                when (val outcome = HttpsSyncManager(repository, prefsRepo).syncFromUrl(prefs.syncUrl, forceApply = false)) {
                    is SyncOutcome.Updated -> {
                        forecastUpdated = true
                        ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
                        WidgetUpdateHelper.refreshAllWidgets(appContext)
                        val label = outcome.versionName.ifBlank { outcome.version.toString() }
                        "Actualizado (v$label)"
                    }
                    is SyncOutcome.UpToDate -> {
                        val label = outcome.versionName.ifBlank { "v${outcome.version}" }
                        "Al día ($label)"
                    }
                    is SyncOutcome.Failure -> "Sin conexión"
                }
            }
        }

        val solarStatus = when (
            val solar = SunriseSunsetService(prefsRepo).syncIfNeeded(zoneId = zoneId, force = isManualTrigger)
        ) {
            is SolarSyncOutcome.SyncedFromNetwork -> "Sincronizado"
            is SolarSyncOutcome.UsedTodayCache -> "Caché de hoy"
            is SolarSyncOutcome.SkippedManualMode -> "Modo manual"
            is SolarSyncOutcome.OfflineFallback -> if (solar.usedStaleCache) "Caché local" else "Predeterminado"
        }

        val updateStatus = when (
            val update = AppUpdateService(prefsRepo).checkForUpdates(force = isManualTrigger)
        ) {
            is UpdateCheckResult.UpdateAvailable -> "Disponible ${update.latestTag}"
            is UpdateCheckResult.UpToDate -> "Al día (${update.latestTag})"
            is UpdateCheckResult.NoReleasesYet -> "Sin releases"
            is UpdateCheckResult.CheckFailed -> "Sin conexión"
        }

        prefsRepo.recordUnifiedCheckSummary(
            timestampMillis = System.currentTimeMillis(),
            jsonStatus = jsonStatus,
            solarStatus = solarStatus,
            updateStatus = updateStatus
        )

        UnifiedSyncReport(
            jsonStatus = jsonStatus,
            solarStatus = solarStatus,
            updateStatus = updateStatus,
            forecastUpdated = forecastUpdated
        )
    }
}
