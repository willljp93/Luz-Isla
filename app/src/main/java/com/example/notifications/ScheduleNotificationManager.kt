package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduledState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class NotificationMonitoringSummary(
    val anyAlertEnabled: Boolean = true,
    val favoritesOnly: Boolean = true,
    val activeCircuitCount: Int = 0,
    val monitoredCircuitCount: Int = 0,
    val excludedNonFavoriteCount: Int = 0,
    val hasPrimaryHome: Boolean = false,
    val favoriteCount: Int = 0
)

data class PlannedAlarm(
    val triggerAtMillis: Long,
    val transitionAtMillis: Long,
    val circuitCode: String,
    val targetState: ScheduledState,
    val priorityTier: Int,
    val notificationId: Int,
    val title: String,
    val body: String
)

data class NotificationScheduleResult(
    val scheduledAlarms: List<PlannedAlarm>,
    val totalCandidatesCount: Int,
    val droppedByCapCount: Int,
    val maintenanceWorkerActive: Boolean,
    val summary: NotificationMonitoringSummary
)

object ScheduleNotificationManager {

    const val CHANNEL_ALERTS = "luz_isla_schedule_alerts"
    const val CHANNEL_UPDATES = "luz_isla_forecast_updates"

    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_CIRCUIT_CODE = "extra_circuit_code"
    const val EXTRA_NOTIF_ID = "extra_notif_id"

    const val MAX_SCHEDULED_ALARMS = 24
    const val PLANNING_HORIZON_DAYS = 7L
    const val NEAR_TERM_WINDOW_HOURS = 48L

    private const val ALARM_REQUEST_CODE_BASE = 1000
    private const val NOTIF_ID_BASE = 2000
    private const val NOTIF_ID_SPAN = 100_000

    private val rescheduleMutex = Mutex()

    @Volatile
    private var testHooksOverride: Pair<LuzIslaRepository, UserPreferencesRepository>? = null

    @Volatile
    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    fun setTestHooks(repository: LuzIslaRepository, prefsRepo: UserPreferencesRepository) {
        testHooksOverride = repository to prefsRepo
    }

    @VisibleForTesting
    fun resetTestHooks() {
        testHooksOverride = null
        ioDispatcher = Dispatchers.IO
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Avisos de horarios eléctricos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones anticipadas de inicio, fin y restablecimiento programado."
            }
            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "Actualizaciones de configuración",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos cuando se descarga una nueva configuración de circuitos."
            }
            nm.createNotificationChannel(alertsChannel)
            nm.createNotificationChannel(updatesChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun summarizeMonitoringScope(
        snapshot: ScheduleSnapshot,
        prefs: UserPreferences
    ): NotificationMonitoringSummary {
        val activeCircuits = snapshot.circuits.filter { it.isActive }
        val hasPrimaryHome = activeCircuits.any { it.isPrimaryHome }
        val favoriteCount = activeCircuits.count { it.isFavorite || it.isPrimaryHome }
        val monitored = activeCircuits.filter { c ->
            !prefs.notifyFavoritesOnly || c.isFavorite || c.isPrimaryHome
        }
        val anyAlertEnabled = prefs.notifyServiceStart || prefs.notifyServiceEnd || prefs.notifyNextRestore
        return NotificationMonitoringSummary(
            anyAlertEnabled = anyAlertEnabled,
            favoritesOnly = prefs.notifyFavoritesOnly,
            activeCircuitCount = activeCircuits.size,
            monitoredCircuitCount = monitored.size,
            excludedNonFavoriteCount = (activeCircuits.size - monitored.size).coerceAtLeast(0),
            hasPrimaryHome = hasPrimaryHome,
            favoriteCount = favoriteCount
        )
    }

    suspend fun rescheduleUpcomingNotifications(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        repositoryOverride: LuzIslaRepository? = null,
        prefsRepoOverride: UserPreferencesRepository? = null
    ): NotificationScheduleResult = withContext(ioDispatcher) {
        rescheduleMutex.withLock {
            ensureChannels(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val hooks = testHooksOverride
            val prefsRepo = prefsRepoOverride ?: hooks?.second ?: UserPreferencesRepository(context)
            val repo = repositoryOverride ?: hooks?.first ?: LuzIslaRepository(
                AppDatabase.getInstance(context),
                prefsRepo
            )

            val prefs = prefsRepo.getSnapshot()
            val snapshot = repo.getCurrentSnapshot()
            val summary = summarizeMonitoringScope(snapshot, prefs)

            cancelExistingAlarms(context, alarmManager)

            if (!summary.anyAlertEnabled) {
                NotificationMaintenanceWorker.schedule(context, enabled = false)
                return@withLock NotificationScheduleResult(
                    scheduledAlarms = emptyList(),
                    totalCandidatesCount = 0,
                    droppedByCapCount = 0,
                    maintenanceWorkerActive = false,
                    summary = summary
                )
            }

            val maintenanceActive = NotificationMaintenanceWorker.schedule(context, enabled = true)
            val plan = buildNotificationPlan(snapshot, prefs, nowMillis)

            plan.selectedAlarms.forEachIndexed { index, alarm ->
                val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
                    putExtra(EXTRA_TITLE, alarm.title)
                    putExtra(EXTRA_BODY, alarm.body)
                    putExtra(EXTRA_CIRCUIT_CODE, alarm.circuitCode)
                    putExtra(EXTRA_NOTIF_ID, alarm.notificationId)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE_BASE + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Battery-friendly inexact wakeup alarm: avoids SCHEDULE_EXACT_ALARM special permissions.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pi)
            }

            NotificationScheduleResult(
                scheduledAlarms = plan.selectedAlarms,
                totalCandidatesCount = plan.totalCandidatesCount,
                droppedByCapCount = (plan.totalCandidatesCount - plan.selectedAlarms.size).coerceAtLeast(0),
                maintenanceWorkerActive = maintenanceActive,
                summary = summary
            )
        }
    }

    internal data class NotificationPlan(
        val selectedAlarms: List<PlannedAlarm>,
        val totalCandidatesCount: Int
    )

    internal fun buildNotificationPlan(
        snapshot: ScheduleSnapshot,
        prefs: UserPreferences,
        nowMillis: Long
    ): NotificationPlan {
        if (!prefs.notifyServiceStart && !prefs.notifyServiceEnd && !prefs.notifyNextRestore) {
            return NotificationPlan(emptyList(), 0)
        }

        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val use24h = prefs.use24HourFormat
        val leadMinutes = prefs.notifyLeadMinutes.coerceIn(0, 60)
        val leadMillis = leadMinutes * 60_000L
        val leadPhrase = when (leadMinutes) {
            0 -> "ahora"
            60 -> "en 1 hora"
            else -> "en $leadMinutes min"
        }

        val horizonMillis = nowMillis + TimeUnit.DAYS.toMillis(PLANNING_HORIZON_DAYS)
        val nearTermCutoffMillis = nowMillis + TimeUnit.HOURS.toMillis(NEAR_TERM_WINDOW_HOURS)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val rangeStartMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rangeEndMillis = today.plusDays(PLANNING_HORIZON_DAYS + 2L)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val targetCircuits = snapshot.circuits.filter { c ->
            c.isActive && (!prefs.notifyFavoritesOnly || c.isFavorite || c.isPrimaryHome)
        }

        data class TransitionKey(
            val circuitCode: String,
            val transitionAtMillis: Long,
            val targetState: ScheduledState
        )

        val seenKeys = HashSet<TransitionKey>()
        val usedNotifIds = HashSet<Int>()
        val candidates = mutableListOf<PlannedAlarm>()

        for (circuit in targetCircuits) {
            val normalizedCode = circuit.code.trim().uppercase()
            val priorityTier = when {
                circuit.isPrimaryHome -> 0
                circuit.isFavorite -> 1
                else -> 2
            }

            val timeline = ScheduleEngine.getTimelineForRange(
                circuitCode = circuit.code,
                snapshot = snapshot,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                zoneId = zoneId
            )

            for (i in 1 until timeline.size) {
                val prev = timeline[i - 1]
                val curr = timeline[i]
                if (prev.state == curr.state) continue

                val transitionMillis = curr.startMillis
                val triggerAt = transitionMillis - leadMillis
                if (triggerAt <= nowMillis || triggerAt > horizonMillis) continue

                val key = TransitionKey(normalizedCode, transitionMillis, curr.state)
                if (!seenKeys.add(key)) continue

                val timeStr = ScheduleEngine.formatTime(transitionMillis, use24h, zoneId)
                val endTimeStr = ScheduleEngine.formatTime(curr.endMillis, use24h, zoneId)

                val content: Pair<String, String>? = when (curr.state) {
                    ScheduledState.SERVICE_SCHEDULED -> {
                        if (prefs.notifyServiceStart || prefs.notifyNextRestore) {
                            val verb = if (prev.state == ScheduledState.OUTAGE_SCHEDULED) {
                                "Restablecimiento"
                            } else {
                                "Inicio de servicio"
                            }
                            "${circuit.displayTitle()}: $verb $leadPhrase" to
                                "Servicio programado a partir de las $timeStr (hasta $endTimeStr)."
                        } else {
                            null
                        }
                    }
                    ScheduledState.OUTAGE_SCHEDULED -> {
                        if (prefs.notifyServiceEnd) {
                            "${circuit.displayTitle()}: Fin de servicio $leadPhrase" to
                                "Interrupción programada a partir de las $timeStr."
                        } else {
                            null
                        }
                    }
                    ScheduledState.UNKNOWN -> null
                }

                if (content != null) {
                    val notifId = allocateDeterministicNotifId(
                        circuitCode = normalizedCode,
                        transitionMillis = transitionMillis,
                        targetState = curr.state,
                        usedNotifIds = usedNotifIds
                    )
                    candidates += PlannedAlarm(
                        triggerAtMillis = triggerAt,
                        transitionAtMillis = transitionMillis,
                        circuitCode = circuit.code,
                        targetState = curr.state,
                        priorityTier = priorityTier,
                        notificationId = notifId,
                        title = content.first,
                        body = content.second
                    )
                }
            }
        }

        val prioritizedSelection = selectPrioritizedAlarms(
            candidates = candidates,
            nearTermCutoffMillis = nearTermCutoffMillis,
            limit = MAX_SCHEDULED_ALARMS
        )

        return NotificationPlan(
            selectedAlarms = prioritizedSelection,
            totalCandidatesCount = candidates.size
        )
    }

    private fun allocateDeterministicNotifId(
        circuitCode: String,
        transitionMillis: Long,
        targetState: ScheduledState,
        usedNotifIds: MutableSet<Int>
    ): Int {
        val rawHash = ((circuitCode.hashCode() * 31) + (transitionMillis / 60_000L).toInt()) * 31 + targetState.ordinal
        var offset = abs(rawHash) % NOTIF_ID_SPAN
        var candidate = NOTIF_ID_BASE + offset
        while (!usedNotifIds.add(candidate)) {
            offset = (offset + 1) % NOTIF_ID_SPAN
            candidate = NOTIF_ID_BASE + offset
        }
        return candidate
    }

    private fun selectPrioritizedAlarms(
        candidates: List<PlannedAlarm>,
        nearTermCutoffMillis: Long,
        limit: Int
    ): List<PlannedAlarm> {
        if (candidates.size <= limit) {
            return candidates.sortedWith(
                compareBy<PlannedAlarm> { it.triggerAtMillis }
                    .thenBy { it.priorityTier }
                    .thenBy { it.circuitCode }
            )
        }

        val priorityComparator = compareBy<PlannedAlarm> { it.priorityTier }
            .thenBy { it.triggerAtMillis }
            .thenBy { it.circuitCode }

        val nearTerm = candidates
            .filter { it.triggerAtMillis <= nearTermCutoffMillis }
            .sortedWith(priorityComparator)
            .take(limit)

        val selected = if (nearTerm.size < limit) {
            val extended = candidates
                .filter { it.triggerAtMillis > nearTermCutoffMillis }
                .sortedWith(priorityComparator)
                .take(limit - nearTerm.size)
            nearTerm + extended
        } else {
            nearTerm
        }

        return selected.sortedWith(
            compareBy<PlannedAlarm> { it.triggerAtMillis }
                .thenBy { it.priorityTier }
                .thenBy { it.circuitCode }
        )
    }

    private fun cancelExistingAlarms(context: Context, alarmManager: AlarmManager) {
        for (index in 0 until MAX_SCHEDULED_ALARMS) {
            val intent = Intent(context, ScheduleNotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE_BASE + index,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    fun postNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        circuitCode: String? = null
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (circuitCode != null) putExtra(EXTRA_CIRCUIT_CODE, circuitCode)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_bolt)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (channelId == CHANNEL_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showNewForecastNotification(context: Context, version: Int, circuitCount: Int) {
        postNotification(
            context = context,
            notificationId = 9001,
            channelId = CHANNEL_UPDATES,
            title = "Nueva configuración disponible (v$version)",
            body = "Se actualizó la programación para $circuitCount circuitos conservando tus favoritos y nombres."
        )
    }
}
