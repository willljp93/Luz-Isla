package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduledState
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetCardStyle
import com.example.notifications.ScheduleNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

enum class WidgetSizeTier {
    SMALL,
    COMPACT,
    LARGE
}

enum class ResolvedWidgetSurface {
    LIGHT,
    DARK,
    TRANSPARENT
}

object WidgetUpdateHelper {

    const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"

    fun resolveWidgetSizeTier(minWidthDp: Int, minHeightDp: Int): WidgetSizeTier = when {
        minHeightDp > 0 && minHeightDp < 96 -> WidgetSizeTier.SMALL
        minWidthDp > 0 && minWidthDp < 152 -> WidgetSizeTier.SMALL
        minHeightDp >= 158 || (minWidthDp >= 250 && minHeightDp >= 120) -> WidgetSizeTier.LARGE
        else -> WidgetSizeTier.COMPACT
    }

    fun resolveSurfaceStyle(
        requestedStyle: WidgetCardStyle,
        appThemeMode: ThemeMode,
        isSystemNightMode: Boolean
    ): ResolvedWidgetSurface = when (requestedStyle) {
        WidgetCardStyle.LIGHT -> ResolvedWidgetSurface.LIGHT
        WidgetCardStyle.DARK -> ResolvedWidgetSurface.DARK
        WidgetCardStyle.TRANSPARENT -> ResolvedWidgetSurface.TRANSPARENT
        WidgetCardStyle.SYSTEM -> {
            val darkActive = when (appThemeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemNightMode
            }
            if (darkActive) ResolvedWidgetSurface.DARK else ResolvedWidgetSurface.LIGHT
        }
    }

    suspend fun refreshAllWidgets(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val repo = LuzIslaRepository(db, prefsRepo)
        val prefs = prefsRepo.getSnapshot()
        val snapshot = repo.getCurrentSnapshot()
        val now = Instant.now()
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val use24h = prefs.use24HourFormat
        val isSystemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

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
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val surface = resolveSurfaceStyle(
                requestedStyle = prefs.widgetStyleMap[widgetId] ?: prefs.widgetCardStyle,
                appThemeMode = prefs.themeMode,
                isSystemNightMode = isSystemNight
            )
            val views = buildRemoteViews(
                context = context,
                appWidgetId = widgetId,
                options = options,
                surface = surface,
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
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val surface = resolveSurfaceStyle(
                requestedStyle = prefs.widgetStyleMap[widgetId] ?: prefs.widgetCardStyle,
                appThemeMode = prefs.themeMode,
                isSystemNightMode = isSystemNight
            )
            val views = buildRemoteViews(
                context = context,
                appWidgetId = widgetId,
                options = options,
                surface = surface,
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
        options: Bundle,
        surface: ResolvedWidgetSurface,
        circuitCode: String?,
        snapshot: ScheduleSnapshot,
        now: Instant,
        zoneId: ZoneId,
        use24h: Boolean,
        providerClass: Class<*>
    ): RemoteViews {
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
        val tier = resolveWidgetSizeTier(minW, minH)

        val layoutId = when (tier) {
            WidgetSizeTier.SMALL -> R.layout.widget_circuit_small
            WidgetSizeTier.COMPACT -> R.layout.widget_circuit_compact
            WidgetSizeTier.LARGE -> R.layout.widget_circuit_large
        }
        val views = RemoteViews(context.packageName, layoutId)

        // Apply Light / Dark / Transparent card surface and contrasting typography palette
        val palette = surfacePalette(surface)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.cardBgRes)
        views.setTextColor(R.id.widget_circuit_title, palette.titleColor)
        views.setTextColor(R.id.widget_next_change, palette.secondaryColor)
        views.setTextColor(R.id.widget_updated_at, palette.mutedColor)
        views.setInt(R.id.widget_refresh_button, "setColorFilter", palette.iconTintColor)
        if (tier == WidgetSizeTier.LARGE) {
            views.setTextColor(R.id.widget_circuit_subtitle, palette.mutedColor)
            views.setTextColor(R.id.widget_period_line, palette.secondaryColor)
        }

        // Responsive text scaling to utilize available card space smoothly without dead voids
        applyResponsiveSizes(views, tier, minW, maxOf(minH, maxH))

        val circuit = circuitCode?.let { code ->
            snapshot.circuits.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }

        if (circuit == null) {
            views.setTextViewText(R.id.widget_circuit_title, "Sin circuitos")
            views.setTextViewText(R.id.widget_status_badge, "SIN CONFIGURAR")
            views.setInt(R.id.widget_status_badge, "setBackgroundResource", palette.unknownPillRes)
            views.setTextColor(R.id.widget_remaining, palette.unknownAccent)
            views.setTextViewText(R.id.widget_remaining, "--:--:--")
            views.setTextViewText(R.id.widget_next_change, "Toca para abrir Luz Isla")
            views.setTextViewText(R.id.widget_updated_at, "Sin conexión requerida")
            if (tier == WidgetSizeTier.LARGE) {
                views.setViewVisibility(R.id.widget_circuit_subtitle, View.GONE)
                views.setViewVisibility(R.id.widget_period_line, View.GONE)
            }
        } else {
            val eval = ScheduleEngine.evaluateCircuit(circuit, snapshot, now, zoneId)
            views.setTextViewText(R.id.widget_circuit_title, circuit.displayTitle())

            val (badgeText, pillRes, accentColor, remainingText) = when (eval.state) {
                ScheduledState.SERVICE_SCHEDULED -> QuadValue(
                    "CON SERVICIO",
                    palette.servicePillRes,
                    palette.serviceAccent,
                    eval.remainingMillis?.let { "Quedan ${ScheduleEngine.formatDurationHms(it)}" } ?: "En servicio"
                )
                ScheduledState.OUTAGE_SCHEDULED -> QuadValue(
                    "SIN SERVICIO",
                    palette.outagePillRes,
                    palette.outageAccent,
                    eval.remainingMillis?.let { "Faltan ${ScheduleEngine.formatDurationHms(it)}" } ?: "Afectado"
                )
                ScheduledState.UNKNOWN -> QuadValue(
                    "SIN INFO",
                    palette.unknownPillRes,
                    palette.unknownAccent,
                    "Sin horario"
                )
            }

            views.setTextViewText(R.id.widget_status_badge, badgeText)
            views.setInt(R.id.widget_status_badge, "setBackgroundResource", pillRes)
            views.setTextColor(R.id.widget_remaining, accentColor)
            views.setTextViewText(R.id.widget_remaining, remainingText)

            val nextLine = when {
                eval.state == ScheduledState.SERVICE_SCHEDULED && eval.currentIntervalEndMillis != null ->
                    if (tier == WidgetSizeTier.SMALL) {
                        "Corte: ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                    } else {
                        "Próxima interrupción: ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                    }
                eval.nextServiceStartMillis != null ->
                    if (tier == WidgetSizeTier.SMALL) {
                        "Próx: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
                    } else {
                        "Próximo servicio: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
                    }
                else -> "Próximo cambio: Sin datos"
            }
            views.setTextViewText(R.id.widget_next_change, nextLine)

            if (tier == WidgetSizeTier.SMALL && minH in 1..78) {
                views.setViewVisibility(R.id.widget_updated_at, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_updated_at, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_updated_at,
                    "Programado • Act. ${ScheduleEngine.formatTime(now.toEpochMilli(), use24h, zoneId)}"
                )
            }

            if (tier == WidgetSizeTier.LARGE) {
                if (circuit.comment.isNotBlank()) {
                    views.setViewVisibility(R.id.widget_circuit_subtitle, View.VISIBLE)
                    views.setTextViewText(R.id.widget_circuit_subtitle, circuit.comment)
                } else {
                    views.setViewVisibility(R.id.widget_circuit_subtitle, View.GONE)
                }

                if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                    views.setViewVisibility(R.id.widget_period_line, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_period_line,
                        "Período: ${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                    )
                } else {
                    views.setViewVisibility(R.id.widget_period_line, View.GONE)
                }
            }
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

    private fun applyResponsiveSizes(
        views: RemoteViews,
        tier: WidgetSizeTier,
        widthDp: Int,
        heightDp: Int
    ) {
        when (tier) {
            WidgetSizeTier.SMALL -> {
                val tight = heightDp < 80 || widthDp < 135
                views.setTextViewTextSize(R.id.widget_circuit_title, TypedValue.COMPLEX_UNIT_SP, if (tight) 12.5f else 13.5f)
                views.setTextViewTextSize(R.id.widget_status_badge, TypedValue.COMPLEX_UNIT_SP, if (tight) 9.5f else 10.5f)
                views.setTextViewTextSize(R.id.widget_remaining, TypedValue.COMPLEX_UNIT_SP, if (tight) 13f else 14.5f)
                views.setTextViewTextSize(R.id.widget_next_change, TypedValue.COMPLEX_UNIT_SP, if (tight) 10.5f else 11.5f)
                views.setTextViewTextSize(R.id.widget_updated_at, TypedValue.COMPLEX_UNIT_SP, 9.5f)
            }
            WidgetSizeTier.COMPACT -> {
                val wideBoost = if (widthDp >= 240) 1f else 0f
                val tallBoost = if (heightDp >= 135) 1.5f else 0f
                views.setTextViewTextSize(R.id.widget_circuit_title, TypedValue.COMPLEX_UNIT_SP, 15.5f + wideBoost)
                views.setTextViewTextSize(R.id.widget_status_badge, TypedValue.COMPLEX_UNIT_SP, 11f + wideBoost * 0.5f)
                views.setTextViewTextSize(R.id.widget_remaining, TypedValue.COMPLEX_UNIT_SP, 16.5f + wideBoost + tallBoost)
                views.setTextViewTextSize(R.id.widget_next_change, TypedValue.COMPLEX_UNIT_SP, 12f + wideBoost * 0.5f)
                views.setTextViewTextSize(R.id.widget_updated_at, TypedValue.COMPLEX_UNIT_SP, 10f + wideBoost * 0.5f)
            }
            WidgetSizeTier.LARGE -> {
                val extraScale = ((widthDp - 240) / 80f).coerceIn(0f, 2f)
                views.setTextViewTextSize(R.id.widget_circuit_title, TypedValue.COMPLEX_UNIT_SP, 18f + extraScale)
                views.setTextViewTextSize(R.id.widget_circuit_subtitle, TypedValue.COMPLEX_UNIT_SP, 12f + extraScale * 0.5f)
                views.setTextViewTextSize(R.id.widget_status_badge, TypedValue.COMPLEX_UNIT_SP, 12f + extraScale * 0.5f)
                views.setTextViewTextSize(R.id.widget_remaining, TypedValue.COMPLEX_UNIT_SP, 21f + extraScale * 1.5f)
                views.setTextViewTextSize(R.id.widget_period_line, TypedValue.COMPLEX_UNIT_SP, 13f + extraScale * 0.5f)
                views.setTextViewTextSize(R.id.widget_next_change, TypedValue.COMPLEX_UNIT_SP, 13f + extraScale * 0.5f)
                views.setTextViewTextSize(R.id.widget_updated_at, TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        }
    }

    private fun surfacePalette(surface: ResolvedWidgetSurface): WidgetSurfacePalette = when (surface) {
        ResolvedWidgetSurface.LIGHT -> WidgetSurfacePalette(
            cardBgRes = R.drawable.widget_card_bg_light,
            servicePillRes = R.drawable.widget_pill_service,
            outagePillRes = R.drawable.widget_pill_outage,
            unknownPillRes = R.drawable.widget_pill_unknown,
            titleColor = Color.parseColor("#0F1F1A"),
            secondaryColor = Color.parseColor("#31443D"),
            mutedColor = Color.parseColor("#60726B"),
            serviceAccent = Color.parseColor("#0B6E4F"),
            outageAccent = Color.parseColor("#C63D2F"),
            unknownAccent = Color.parseColor("#606D76"),
            iconTintColor = Color.parseColor("#0B6E4F")
        )
        ResolvedWidgetSurface.DARK -> WidgetSurfacePalette(
            cardBgRes = R.drawable.widget_card_bg_dark,
            servicePillRes = R.drawable.widget_pill_service,
            outagePillRes = R.drawable.widget_pill_outage,
            unknownPillRes = R.drawable.widget_pill_unknown,
            titleColor = Color.parseColor("#F0F7F4"),
            secondaryColor = Color.parseColor("#C6D8D1"),
            mutedColor = Color.parseColor("#93ACA2"),
            serviceAccent = Color.parseColor("#5AE0A8"),
            outageAccent = Color.parseColor("#FF8F83"),
            unknownAccent = Color.parseColor("#B0C1BA"),
            iconTintColor = Color.parseColor("#5AE0A8")
        )
        ResolvedWidgetSurface.TRANSPARENT -> WidgetSurfacePalette(
            cardBgRes = R.drawable.widget_card_bg_transparent,
            servicePillRes = R.drawable.widget_pill_service_translucent,
            outagePillRes = R.drawable.widget_pill_outage_translucent,
            unknownPillRes = R.drawable.widget_pill_unknown_translucent,
            titleColor = Color.parseColor("#FFFFFF"),
            secondaryColor = Color.parseColor("#F1F5F9"),
            mutedColor = Color.parseColor("#CBD5E1"),
            serviceAccent = Color.parseColor("#6EE7B7"),
            outageAccent = Color.parseColor("#FCA5A5"),
            unknownAccent = Color.parseColor("#E2E8F0"),
            iconTintColor = Color.parseColor("#FFFFFF")
        )
    }

    private data class WidgetSurfacePalette(
        val cardBgRes: Int,
        val servicePillRes: Int,
        val outagePillRes: Int,
        val unknownPillRes: Int,
        val titleColor: Int,
        val secondaryColor: Int,
        val mutedColor: Int,
        val serviceAccent: Int,
        val outageAccent: Int,
        val unknownAccent: Int,
        val iconTintColor: Int
    )

    private data class QuadValue<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
