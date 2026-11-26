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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduledState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

object ScheduleNotificationManager {

    const val CHANNEL_ALERTS = "luz_isla_schedule_alerts"
    const val CHANNEL_UPDATES = "luz_isla_forecast_updates"

    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_CIRCUIT_CODE = "extra_circuit_code"
    const val EXTRA_NOTIF_ID = "extra_notif_id"

    private const val MAX_SCHEDULED_ALARMS = 24

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

    suspend fun rescheduleUpcomingNotifications(context: Context) = withContext(Dispatchers.IO) {
        ensureChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelExistingAlarms(context, alarmManager)

        val db = AppDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val repo = LuzIslaRepository(db, prefsRepo)
        val prefs = prefsRepo.getSnapshot()

        if (!prefs.notifyServiceStart && !prefs.notifyServiceEnd && !prefs.notifyNextRestore) {
            return@withContext
        }

        val snapshot = repo.getCurrentSnapshot()
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val use24h = prefs.use24HourFormat
        val nowMillis = System.currentTimeMillis()
        val leadMinutes = prefs.notifyLeadMinutes.coerceIn(0, 60)
        val leadMillis = leadMinutes * 60_000L
        val leadPhrase = when (leadMinutes) {
            0 -> "ahora"
            60 -> "en 1 hora"
            else -> "en $leadMinutes min"
        }
        val horizonMillis = nowMillis + 24 * 60 * 60_000L
        val today = LocalDate.now(zoneId)

        val targetCircuits = snapshot.circuits.filter { c ->
            c.isActive && (!prefs.notifyFavoritesOnly || c.isFavorite || c.isPrimaryHome)
        }

        data class PlannedAlarm(
            val triggerAtMillis: Long,
            val circuitCode: String,
            val title: String,
            val body: String
        )

        val alarms = mutableListOf<PlannedAlarm>()
        for (circuit in targetCircuits) {
            val todaySegs = ScheduleEngine.getTimelineForDay(circuit.code, snapshot, today, zoneId)
            val tomorrowSegs = ScheduleEngine.getTimelineForDay(circuit.code, snapshot, today.plusDays(1), zoneId)
            val combined = todaySegs + tomorrowSegs

            for (i in 1 until combined.size) {
                val prev = combined[i - 1]
                val curr = combined[i]
                if (prev.state == curr.state) continue
                val transitionMillis = curr.startMillis
                val triggerAt = transitionMillis - leadMillis
                if (triggerAt <= nowMillis || triggerAt > horizonMillis) continue

                val timeStr = ScheduleEngine.formatTime(transitionMillis, use24h, zoneId)
                when (curr.state) {
                    ScheduledState.SERVICE_SCHEDULED -> {
                        if (prefs.notifyServiceStart || prefs.notifyNextRestore) {
                            val verb = if (prev.state == ScheduledState.OUTAGE_SCHEDULED) "Restablecimiento" else "Inicio de servicio"
                            alarms += PlannedAlarm(
                                triggerAtMillis = triggerAt,
                                circuitCode = circuit.code,
                                title = "${circuit.displayTitle()}: $verb $leadPhrase",
                                body = "Servicio programado a partir de las $timeStr (hasta ${ScheduleEngine.formatTime(curr.endMillis, use24h, zoneId)})."
                            )
                        }
                    }
                    ScheduledState.OUTAGE_SCHEDULED -> {
                        if (prefs.notifyServiceEnd) {
                            alarms += PlannedAlarm(
                                triggerAtMillis = triggerAt,
                                circuitCode = circuit.code,
                                title = "${circuit.displayTitle()}: Fin de servicio $leadPhrase",
                                body = "Interrupción programada a partir de las $timeStr."
                            )
                        }
                    }
                    ScheduledState.UNKNOWN -> Unit
                }
            }
        }

        alarms.sortedBy { it.triggerAtMillis }
            .take(MAX_SCHEDULED_ALARMS)
            .forEachIndexed { index, alarm ->
                val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
                    putExtra(EXTRA_TITLE, alarm.title)
                    putExtra(EXTRA_BODY, alarm.body)
                    putExtra(EXTRA_CIRCUIT_CODE, alarm.circuitCode)
                    putExtra(EXTRA_NOTIF_ID, 2000 + index)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    1000 + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pi)
            }
    }

    private fun cancelExistingAlarms(context: Context, alarmManager: AlarmManager) {
        for (index in 0 until MAX_SCHEDULED_ALARMS) {
            val intent = Intent(context, ScheduleNotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                1000 + index,
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
