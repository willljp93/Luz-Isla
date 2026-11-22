package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "luz_isla_prefs")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val compactHomeCards: Boolean = false,
    val useSystemTimezone: Boolean = true,
    val manualTimezoneId: String = "America/Havana",
    val use24HourFormat: Boolean = false,
    val sunriseMinutes: Int = 6 * 60 + 30,
    val sunsetMinutes: Int = 18 * 60 + 30,
    val enableElectricAnimations: Boolean = true,
    val dynamicLauncherIconEnabled: Boolean = false,
    val activeConfigVersion: Int = 1,
    val activeConfigTimestampMillis: Long = 0L,
    val activeConfigSource: String = "Configuración local inicial",
    val syncUrl: String = "",
    val autoSyncEnabled: Boolean = false,
    val syncIntervalHours: Int = 6,
    val lastSyncEpochMillis: Long = 0L,
    val lastForecastUpdateEpochMillis: Long = 0L,
    val lastRemoteConfigVersion: Int = 0,
    val lastSyncStatus: String = "Sin sincronización remota configurada",
    val notifyServiceStart: Boolean = true,
    val notifyServiceEnd: Boolean = true,
    val notifyNextRestore: Boolean = false,
    val notifyNewForecast: Boolean = true,
    val notifyLeadMinutes: Int = 15,
    val notifyFavoritesOnly: Boolean = true,
    val widgetCircuitMap: Map<Int, String> = emptyMap()
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val COMPACT_HOME_CARDS = booleanPreferencesKey("compact_home_cards")
        val USE_SYSTEM_TIMEZONE = booleanPreferencesKey("use_system_timezone")
        val MANUAL_TIMEZONE_ID = stringPreferencesKey("manual_timezone_id")
        val USE_24_HOUR_FORMAT = booleanPreferencesKey("use_24_hour_format")
        val SUNRISE_MINUTES = intPreferencesKey("sunrise_minutes")
        val SUNSET_MINUTES = intPreferencesKey("sunset_minutes")
        val ENABLE_ELECTRIC_ANIMATIONS = booleanPreferencesKey("enable_electric_animations")
        val DYNAMIC_LAUNCHER_ICON = booleanPreferencesKey("dynamic_launcher_icon")
        val ACTIVE_CONFIG_VERSION = intPreferencesKey("active_config_version")
        val ACTIVE_CONFIG_TIMESTAMP = longPreferencesKey("active_config_timestamp")
        val ACTIVE_CONFIG_SOURCE = stringPreferencesKey("active_config_source")
        val SYNC_URL = stringPreferencesKey("sync_url")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val LAST_FORECAST_UPDATE_MILLIS = longPreferencesKey("last_forecast_update_millis")
        val LAST_REMOTE_VERSION = intPreferencesKey("last_remote_version")
        val LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")
        val NOTIFY_SERVICE_START = booleanPreferencesKey("notify_service_start")
        val NOTIFY_SERVICE_END = booleanPreferencesKey("notify_service_end")
        val NOTIFY_NEXT_RESTORE = booleanPreferencesKey("notify_next_restore")
        val NOTIFY_NEW_FORECAST = booleanPreferencesKey("notify_new_forecast")
        val NOTIFY_LEAD_MINUTES = intPreferencesKey("notify_lead_minutes")
        val NOTIFY_FAVORITES_ONLY = booleanPreferencesKey("notify_favorites_only")
        val WIDGET_CIRCUIT_MAP = stringPreferencesKey("widget_circuit_map")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val widgetMapRaw = prefs[Keys.WIDGET_CIRCUIT_MAP].orEmpty()
        val parsedWidgetMap = if (widgetMapRaw.isNotBlank()) {
            runCatching { json.decodeFromString<Map<Int, String>>(widgetMapRaw) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            compactHomeCards = prefs[Keys.COMPACT_HOME_CARDS] ?: false,
            useSystemTimezone = prefs[Keys.USE_SYSTEM_TIMEZONE] ?: true,
            manualTimezoneId = prefs[Keys.MANUAL_TIMEZONE_ID] ?: "America/Havana",
            use24HourFormat = prefs[Keys.USE_24_HOUR_FORMAT] ?: false,
            sunriseMinutes = prefs[Keys.SUNRISE_MINUTES] ?: (6 * 60 + 30),
            sunsetMinutes = prefs[Keys.SUNSET_MINUTES] ?: (18 * 60 + 30),
            enableElectricAnimations = prefs[Keys.ENABLE_ELECTRIC_ANIMATIONS] ?: true,
            dynamicLauncherIconEnabled = prefs[Keys.DYNAMIC_LAUNCHER_ICON] ?: false,
            activeConfigVersion = prefs[Keys.ACTIVE_CONFIG_VERSION] ?: 1,
            activeConfigTimestampMillis = prefs[Keys.ACTIVE_CONFIG_TIMESTAMP] ?: 0L,
            activeConfigSource = prefs[Keys.ACTIVE_CONFIG_SOURCE] ?: "Configuración local inicial",
            syncUrl = prefs[Keys.SYNC_URL].orEmpty(),
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: false,
            syncIntervalHours = prefs[Keys.SYNC_INTERVAL_HOURS] ?: 6,
            lastSyncEpochMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L,
            lastForecastUpdateEpochMillis = prefs[Keys.LAST_FORECAST_UPDATE_MILLIS] ?: 0L,
            lastRemoteConfigVersion = prefs[Keys.LAST_REMOTE_VERSION] ?: 0,
            lastSyncStatus = prefs[Keys.LAST_SYNC_STATUS] ?: "Sin sincronización remota configurada",
            notifyServiceStart = prefs[Keys.NOTIFY_SERVICE_START] ?: true,
            notifyServiceEnd = prefs[Keys.NOTIFY_SERVICE_END] ?: true,
            notifyNextRestore = prefs[Keys.NOTIFY_NEXT_RESTORE] ?: false,
            notifyNewForecast = prefs[Keys.NOTIFY_NEW_FORECAST] ?: true,
            notifyLeadMinutes = prefs[Keys.NOTIFY_LEAD_MINUTES] ?: 15,
            notifyFavoritesOnly = prefs[Keys.NOTIFY_FAVORITES_ONLY] ?: true,
            widgetCircuitMap = parsedWidgetMap
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCompactHomeCards(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_HOME_CARDS] = compact }
    }

    suspend fun setTimezonePreference(useSystem: Boolean, manualTimezoneId: String) {
        context.dataStore.edit {
            it[Keys.USE_SYSTEM_TIMEZONE] = useSystem
            it[Keys.MANUAL_TIMEZONE_ID] = manualTimezoneId.trim().ifEmpty { "America/Havana" }
        }
    }

    suspend fun setUse24HourFormat(use24h: Boolean) {
        context.dataStore.edit { it[Keys.USE_24_HOUR_FORMAT] = use24h }
    }

    suspend fun setDayNightTimes(sunriseMinutes: Int, sunsetMinutes: Int) {
        context.dataStore.edit {
            it[Keys.SUNRISE_MINUTES] = sunriseMinutes.coerceIn(0, 1439)
            it[Keys.SUNSET_MINUTES] = sunsetMinutes.coerceIn(0, 1439)
        }
    }

    suspend fun setEnableElectricAnimations(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_ELECTRIC_ANIMATIONS] = enabled }
    }

    suspend fun setDynamicLauncherIconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_LAUNCHER_ICON] = enabled }
    }

    suspend fun recordActiveConfigMetadata(version: Int, timestampMillis: Long, source: String) {
        context.dataStore.edit {
            it[Keys.ACTIVE_CONFIG_VERSION] = version.coerceAtLeast(1)
            it[Keys.ACTIVE_CONFIG_TIMESTAMP] = timestampMillis
            it[Keys.ACTIVE_CONFIG_SOURCE] = source.trim().ifEmpty { "Configuración cargada" }
        }
    }

    suspend fun setSyncConfig(url: String, autoSync: Boolean, intervalHours: Int) {
        context.dataStore.edit {
            it[Keys.SYNC_URL] = url.trim()
            it[Keys.AUTO_SYNC_ENABLED] = autoSync
            it[Keys.SYNC_INTERVAL_HOURS] = intervalHours.coerceIn(1, 24)
        }
    }

    suspend fun recordSyncSuccess(version: Int, forecastUpdateMillis: Long, statusMessage: String) {
        context.dataStore.edit {
            it[Keys.LAST_SYNC_MILLIS] = System.currentTimeMillis()
            it[Keys.LAST_FORECAST_UPDATE_MILLIS] = forecastUpdateMillis
            it[Keys.LAST_REMOTE_VERSION] = version
            it[Keys.LAST_SYNC_STATUS] = statusMessage
        }
    }

    suspend fun recordSyncStatus(statusMessage: String) {
        context.dataStore.edit {
            it[Keys.LAST_SYNC_STATUS] = statusMessage
        }
    }

    suspend fun markForecastUpdatedNow() {
        val now = System.currentTimeMillis()
        context.dataStore.edit {
            it[Keys.LAST_FORECAST_UPDATE_MILLIS] = now
            if ((it[Keys.ACTIVE_CONFIG_TIMESTAMP] ?: 0L) == 0L) {
                it[Keys.ACTIVE_CONFIG_TIMESTAMP] = now
            }
        }
    }

    suspend fun updateNotificationSettings(
        serviceStart: Boolean,
        serviceEnd: Boolean,
        nextRestore: Boolean,
        newForecast: Boolean,
        leadMinutes: Int,
        favoritesOnly: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.NOTIFY_SERVICE_START] = serviceStart
            it[Keys.NOTIFY_SERVICE_END] = serviceEnd
            it[Keys.NOTIFY_NEXT_RESTORE] = nextRestore
            it[Keys.NOTIFY_NEW_FORECAST] = newForecast
            it[Keys.NOTIFY_LEAD_MINUTES] = leadMinutes
            it[Keys.NOTIFY_FAVORITES_ONLY] = favoritesOnly
        }
    }

    suspend fun setWidgetCircuit(appWidgetId: Int, circuitCode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WIDGET_CIRCUIT_MAP]?.let { raw ->
                runCatching { json.decodeFromString<Map<Int, String>>(raw) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = current.toMutableMap().apply { put(appWidgetId, circuitCode.trim().uppercase()) }
            prefs[Keys.WIDGET_CIRCUIT_MAP] = json.encodeToString(updated)
        }
    }

    suspend fun removeWidgetCircuits(appWidgetIds: IntArray) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WIDGET_CIRCUIT_MAP]?.let { raw ->
                runCatching { json.decodeFromString<Map<Int, String>>(raw) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = current.toMutableMap().apply {
                appWidgetIds.forEach { remove(it) }
            }
            prefs[Keys.WIDGET_CIRCUIT_MAP] = json.encodeToString(updated)
        }
    }
}
