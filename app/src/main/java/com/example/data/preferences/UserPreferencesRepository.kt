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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val compactHomeCards: Boolean = false,
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val COMPACT_HOME_CARDS = booleanPreferencesKey("compact_home_cards")
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
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            compactHomeCards = prefs[Keys.COMPACT_HOME_CARDS] ?: false,
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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCompactHomeCards(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_HOME_CARDS] = compact }
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
        context.dataStore.edit {
            it[Keys.LAST_FORECAST_UPDATE_MILLIS] = System.currentTimeMillis()
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
