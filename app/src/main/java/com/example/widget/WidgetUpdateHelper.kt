package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduledState
import com.example.notifications.ScheduleNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

object WidgetUpdateHelper {

    const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"

    suspend fun refreshAllWidgets(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val repo = LuzIslaRepository(db, prefsRepo)
        val prefs = prefsRepo.getSnapshot()
        val snapshot = repo.getCurrentSnapshot()
        val now = Instant.now()
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val use24h = prefs.use24HourFormat

        val activeCircuits = snapshot.circuits.filter { it.isActive }
        val primaryCircuit = activeCircuits.firstOrNull { it.isPrimaryHome }
            ?: activeCircuits.firstOrNull { it.isFavorite }
            ?: activeCircuits.firstOrNull()

        val primaryHomeCircuit = activeCircuits.firstOrNull { it.isPrimaryHome }
        val primaryHomeState = primaryHomeCircuit?.let {
            ScheduleEngine.evaluateCircuit(it, snapshot, now, zoneId).state
        }
        LauncherIconManager.syncLauncherIcon(
            context = context,
            dynamicEnabled = prefs.dynamicLauncherIconEnabled,
            primaryHomeState = primaryHomeState
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val primaryIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, PrimaryFavoriteWidgetProvider::class.java)
        )
        val configurableIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ConfigurableCircuitWidgetProvider::class.java)
        )
        if (primaryIds.isEmpty() && configurableIds.isEmpty()) return@withContext

        primaryIds.forEach { widgetId ->
            val views = buildRemoteViews(
                context = context,
                appWidgetId = widgetId,
                circuitCode = primaryCircuit?.code,
                snapshot = snapshot,
                now = now,
                zoneId = zoneId,
                use24h = use24h,
                providerClass = PrimaryFavoriteWidgetProvider::class.java
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        configurableIds.forEach { widgetId ->
            val selectedCode = prefs.widgetCircuitMap[widgetId] ?: primaryCircuit?.code
            val views = buildRemoteViews(
                context = context,
                appWidgetId = widgetId,
                circuitCode = selectedCode,
                snapshot = snapshot,
                now = now,
                zoneId = zoneId,
                use24h = use24h,
                providerClass = ConfigurableCircuitWidgetProvider::class.java
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        circuitCode: String?,
        snapshot: com.example.domain.model.ScheduleSnapshot,
        now: Instant,
        zoneId: ZoneId,
        use24h: Boolean,
        providerClass: Class<*>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_circuit_compact)
        val circuit = circuitCode?.let { code ->
            snapshot.circuits.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }

        if (circuit == null) {
            views.setTextViewText(R.id.widget_circuit_title, "Sin circuitos")
            views.setTextViewText(R.id.widget_status_badge, "SIN CONFIGURAR")
            views.setInt(R.id.widget_status_badge, "setBackgroundColor", Color.parseColor("#606D76"))
            views.setTextViewText(R.id.widget_next_change, "Toca para abrir Luz Isla")
            views.setTextViewText(R.id.widget_remaining, "--:--:--")
            views.setTextViewText(R.id.widget_updated_at, "Sin conexión requerida")
        } else {
            val eval = ScheduleEngine.evaluateCircuit(circuit, snapshot, now, zoneId)
            views.setTextViewText(R.id.widget_circuit_title, circuit.displayTitle())

            val (badgeText, badgeColor, remainingText) = when (eval.state) {
                ScheduledState.SERVICE_SCHEDULED -> Triple(
                    "CON SERVICIO",
                    Color.parseColor("#0B6E4F"),
                    eval.remainingMillis?.let { "Faltan ${ScheduleEngine.formatDurationHms(it)}" } ?: "En servicio"
                )
                ScheduledState.OUTAGE_SCHEDULED -> Triple(
                    "SIN SERVICIO",
                    Color.parseColor("#C63D2F"),
                    eval.remainingMillis?.let { "Restablece en ${ScheduleEngine.formatDurationHms(it)}" } ?: "Afectado"
                )
                ScheduledState.UNKNOWN -> Triple(
                    "SIN INFORMACIÓN",
                    Color.parseColor("#606D76"),
                    "Sin pronóstico"
                )
            }

            views.setTextViewText(R.id.widget_status_badge, badgeText)
            views.setInt(R.id.widget_status_badge, "setBackgroundColor", badgeColor)
            views.setTextViewText(R.id.widget_remaining, remainingText)

            val nextLine = when {
                eval.state == ScheduledState.SERVICE_SCHEDULED && eval.currentIntervalEndMillis != null ->
                    "Próxima interrupción: ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                eval.nextServiceStartMillis != null ->
                    "Próximo servicio: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
                else -> "Próximo cambio: Sin datos"
            }
            views.setTextViewText(R.id.widget_next_change, nextLine)
            views.setTextViewText(
                R.id.widget_updated_at,
                "Programado • Act. ${ScheduleEngine.formatTime(now.toEpochMilli(), use24h, zoneId)}"
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (circuit != null) {
                putExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE, circuit.code)
            }
        }
        val openPi = PendingIntent.getActivity(
            context,
            3000 + appWidgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPi)

        val refreshIntent = Intent(context, providerClass).apply {
            action = ACTION_WIDGET_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPi = PendingIntent.getBroadcast(
            context,
            4000 + appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPi)

        return views
    }
}
