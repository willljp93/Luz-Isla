package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduledState
import com.example.domain.model.ThemeMode
import com.example.notifications.ScheduleNotificationManager
import com.example.sync.AppUpdateService
import com.example.sync.DistributionChannel
import com.example.sync.ForecastSyncWorker
import com.example.sync.HttpsSyncManager
import com.example.sync.ShareHelper
import com.example.sync.SolarSyncOutcome
import com.example.sync.SunriseSunsetService
import com.example.sync.SyncOutcome
import com.example.sync.UpdateCheckResult
import com.example.transfer.ConfigCodec
import com.example.transfer.DecodedConfigResult
import com.example.transfer.ImportSummary
import com.example.widget.LauncherIconManager
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class ExportBundle(
    val alphanumericCode: String,
    val jsonText: String,
    val generatedAtMillis: Long,
    val configVersion: Int
)

data class PendingImportReview(
    val decoded: DecodedConfigResult,
    val summary: ImportSummary
)

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val isSolarSyncing: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateAvailable: Boolean = false,
    val distributionChannels: List<DistributionChannel> = AppUpdateService.defaultDistributionChannels(),
    val hasBackup: Boolean = false,
    val hasPrimaryHome: Boolean = false,
    val primaryHomeState: ScheduledState? = null,
    val primaryHomeLabel: String = "",
    val exportBundle: ExportBundle? = null,
    val pendingImport: PendingImportReview? = null,
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val appContext: Context,
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)
    private val isSolarSyncing = MutableStateFlow(false)
    private val isCheckingUpdates = MutableStateFlow(false)
    private val exportBundle = MutableStateFlow<ExportBundle?>(null)
    private val pendingImport = MutableStateFlow<PendingImportReview?>(null)
    private val feedbackMessage = MutableStateFlow<String?>(null)

    private val syncManager = HttpsSyncManager(repository, prefsRepo)
    private val solarSyncService = SunriseSunsetService(prefsRepo)
    private val appUpdateService = AppUpdateService(prefsRepo)

    private val prefsAndBackupsFlow = combine(
        prefsRepo.preferencesFlow,
        repository.backupsFlow,
        repository.snapshotFlow
    ) { prefs, backups, snapshot ->
        Triple(prefs, backups.isNotEmpty(), snapshot)
    }

    private val syncingStateFlow = combine(
        isSyncing,
        isSolarSyncing,
        isCheckingUpdates
    ) { urlSync, solarSync, updateCheck ->
        Triple(urlSync, solarSync, updateCheck)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsAndBackupsFlow,
        syncingStateFlow,
        exportBundle,
        pendingImport,
        feedbackMessage
    ) { base, syncFlags, export, pending, msg ->
        val prefs = base.first
        val hasBackup = base.second
        val snapshot: ScheduleSnapshot = base.third
        val zoneId = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        val primaryCircuit = snapshot.circuits.firstOrNull { it.isActive && it.isPrimaryHome }
        val primaryEval = primaryCircuit?.let {
            ScheduleEngine.evaluateCircuit(it, snapshot, Instant.now(), zoneId)
        }
        val updateAvailable = AppUpdateService.isRemoteVersionNewer(
            remoteTag = prefs.latestKnownTag,
            releaseTitle = prefs.latestKnownTitle,
            releaseBody = prefs.latestKnownNotes,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            remoteVersionCodeOverride = prefs.latestKnownVersionCode.takeIf { it > 0 }
        )

        SettingsUiState(
            prefs = prefs,
            isSyncing = syncFlags.first,
            isSolarSyncing = syncFlags.second,
            isCheckingUpdates = syncFlags.third,
            updateAvailable = updateAvailable,
            distributionChannels = AppUpdateService.defaultDistributionChannels(),
            hasBackup = hasBackup,
            hasPrimaryHome = primaryCircuit != null,
            primaryHomeState = primaryEval?.state,
            primaryHomeLabel = primaryCircuit?.displayTitle().orEmpty(),
            exportBundle = export,
            pendingImport = pending,
            feedbackMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColor(enabled) }
    }

    fun setTimezonePreference(useSystem: Boolean, manualTimezoneId: String) {
        viewModelScope.launch {
            prefsRepo.setTimezonePreference(useSystem, manualTimezoneId)
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
            val updated = prefsRepo.getSnapshot()
            if (updated.autoSolarSyncEnabled) {
                val zone = ScheduleEngine.effectiveZone(updated.useSystemTimezone, updated.manualTimezoneId)
                isSolarSyncing.value = true
                try {
                    solarSyncService.syncIfNeeded(zoneId = zone, force = false)
                } finally {
                    isSolarSyncing.value = false
                }
            }
        }
    }

    fun setUse24HourFormat(use24h: Boolean) {
        viewModelScope.launch {
            prefsRepo.setUse24HourFormat(use24h)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
        }
    }

    fun setDayNightTimes(sunriseMinutes: Int, sunsetMinutes: Int) {
        viewModelScope.launch {
            prefsRepo.setDayNightTimes(sunriseMinutes, sunsetMinutes, manualOverride = true)
            feedbackMessage.value = "Horario Día/Noche guardado en modo manual."
        }
    }

    fun setDayNightAutoMode(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                prefsRepo.setAutoSolarSyncEnabled(false, statusMessage = "Modo manual personalizado")
                return@launch
            }
            prefsRepo.setAutoSolarSyncEnabled(true)
            syncSolarInternal(force = false, showFeedback = true)
        }
    }

    fun syncSolarTimesNow() {
        viewModelScope.launch {
            syncSolarInternal(force = true, showFeedback = true)
        }
    }

    private suspend fun syncSolarInternal(force: Boolean, showFeedback: Boolean) {
        val prefs = prefsRepo.getSnapshot()
        val zone = ScheduleEngine.effectiveZone(prefs.useSystemTimezone, prefs.manualTimezoneId)
        isSolarSyncing.value = true
        val outcome = try {
            solarSyncService.syncIfNeeded(zoneId = zone, force = force)
        } finally {
            isSolarSyncing.value = false
        }
        if (showFeedback) {
            val use24 = prefs.use24HourFormat
            feedbackMessage.value = when (outcome) {
                is SolarSyncOutcome.SyncedFromNetwork ->
                    "Sol sincronizado (Nueva Gerona): alba ${ScheduleEngine.formatMinuteOfDay(outcome.sunriseMinutes, use24)} · ocaso ${ScheduleEngine.formatMinuteOfDay(outcome.sunsetMinutes, use24)}."
                is SolarSyncOutcome.UsedTodayCache ->
                    "Horarios solares de hoy ya actualizados en caché local."
                is SolarSyncOutcome.OfflineFallback ->
                    if (outcome.usedStaleCache) {
                        "Sin conexión: usando caché solar local."
                    } else {
                        "Sin conexión: usando valores locales configurados."
                    }
                is SolarSyncOutcome.SkippedManualMode -> null
            }
        }
    }

    fun setEnableElectricAnimations(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEnableElectricAnimations(enabled)
        }
    }

    fun setDynamicLauncherIconEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setDynamicLauncherIconEnabled(enabled)
            LauncherIconManager.syncLauncherIcon(
                context = appContext,
                dynamicEnabled = enabled,
                primaryHomeState = uiState.value.primaryHomeState
            )
            feedbackMessage.value = if (enabled) {
                "Ícono dinámico activado. El lanzador puede tardar unos segundos en actualizarse."
            } else {
                "Ícono predeterminado de Luz Isla restaurado."
            }
        }
    }

    fun checkAppUpdatesNow() {
        viewModelScope.launch {
            isCheckingUpdates.value = true
            val result = try {
                appUpdateService.checkForUpdates(force = true)
            } finally {
                isCheckingUpdates.value = false
            }
            feedbackMessage.value = when (result) {
                is UpdateCheckResult.UpdateAvailable ->
                    "Nueva versión disponible: ${result.latestTag}."
                is UpdateCheckResult.UpToDate ->
                    "Ya tienes la última versión publicada (${result.latestTag})."
                is UpdateCheckResult.NoReleasesYet ->
                    "Aún no hay lanzamientos publicados en GitHub Releases."
                is UpdateCheckResult.CheckFailed ->
                    result.reason
            }
        }
    }

    fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!opened) {
            feedbackMessage.value = "No se encontró un navegador para abrir el enlace."
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            prefsRepo.setOnboardingCompleted(false)
        }
    }

    fun saveSyncSettings(url: String, autoSync: Boolean, intervalHours: Int) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://", ignoreCase = true)) {
            feedbackMessage.value = "La dirección de sincronización debe usar HTTPS."
            return
        }
        viewModelScope.launch {
            prefsRepo.setSyncConfig(trimmed, autoSync, intervalHours)
            ForecastSyncWorker.schedule(appContext, autoSync && trimmed.isNotEmpty(), intervalHours)
            feedbackMessage.value = "Configuración de sincronización guardada."
        }
    }

    fun triggerManualSync(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            isSyncing.value = true
            val currentPrefs = prefsRepo.getSnapshot()
            prefsRepo.setSyncConfig(trimmed, currentPrefs.autoSyncEnabled, currentPrefs.syncIntervalHours)
            val outcome = syncManager.syncFromUrl(trimmed, forceApply = true)
            isSyncing.value = false
            when (outcome) {
                is SyncOutcome.Updated -> {
                    ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
                    WidgetUpdateHelper.refreshAllWidgets(appContext)
                    feedbackMessage.value = "Pronóstico actualizado (v${outcome.version}): ${outcome.circuitCount} circuitos."
                }
                is SyncOutcome.UpToDate -> {
                    feedbackMessage.value = "Ya cuentas con la última versión (v${outcome.version})."
                }
                is SyncOutcome.Failure -> {
                    feedbackMessage.value = outcome.reason
                }
            }
        }
    }

    fun generateExport(includePersonalData: Boolean) {
        viewModelScope.launch {
            val bundle = withContext(Dispatchers.Default) {
                val snapshot = repository.getCurrentSnapshot()
                val version = uiState.value.prefs.activeConfigVersion.coerceAtLeast(1)
                val nowMillis = System.currentTimeMillis()
                val payload = ConfigCodec.snapshotToPayload(
                    snapshot = snapshot,
                    configVersion = version,
                    includePersonalData = includePersonalData,
                    nowIso = Instant.ofEpochMilli(nowMillis).toString()
                )
                val jsonText = ConfigCodec.encodeToJson(payload, pretty = true)
                val code = ConfigCodec.encodeToAlphanumericCode(payload)
                ExportBundle(
                    alphanumericCode = code,
                    jsonText = jsonText,
                    generatedAtMillis = nowMillis,
                    configVersion = version
                )
            }
            exportBundle.value = bundle
        }
    }

    fun dismissExportBundle() {
        exportBundle.value = null
    }

    fun inspectAlphanumericCode(rawCode: String) {
        viewModelScope.launch {
            try {
                val decoded = withContext(Dispatchers.Default) {
                    ConfigCodec.decodeFromAlphanumericCode(rawCode)
                }
                pendingImport.value = PendingImportReview(
                    decoded = decoded,
                    summary = ConfigCodec.buildSummary(decoded)
                )
            } catch (e: Exception) {
                feedbackMessage.value = e.message ?: "El código ingresado no es válido."
            }
        }
    }

    fun inspectJsonUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado.")
                }
                val decoded = withContext(Dispatchers.Default) {
                    ConfigCodec.decodeFromJson(rawJson)
                }
                pendingImport.value = PendingImportReview(
                    decoded = decoded,
                    summary = ConfigCodec.buildSummary(decoded)
                )
            } catch (e: Exception) {
                feedbackMessage.value = e.message ?: "Archivo JSON inválido."
            }
        }
    }

    fun writeExportJsonToUri(uri: Uri) {
        val jsonText = exportBundle.value?.jsonText ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(jsonText)
                    } ?: throw IllegalStateException("No se pudo escribir en el archivo.")
                }
                feedbackMessage.value = "Archivo JSON exportado correctamente."
            } catch (e: Exception) {
                feedbackMessage.value = "Error al guardar JSON: ${e.message}"
            }
        }
    }

    fun confirmPendingImport(mode: ImportMergeMode, includePersonalFromIncoming: Boolean) {
        val review = pendingImport.value ?: return
        viewModelScope.launch {
            val currentSnapshot = repository.getCurrentSnapshot()
            val backupPayload = ConfigCodec.snapshotToPayload(
                snapshot = currentSnapshot,
                configVersion = uiState.value.prefs.activeConfigVersion.coerceAtLeast(1),
                includePersonalData = true,
                sourceName = "Respaldo previo a importación"
            )
            repository.saveBackup(
                reason = "Antes de importación (${review.summary.sourceName})",
                payloadJson = ConfigCodec.encodeToJson(backupPayload, pretty = false)
            )

            repository.applyImportedSnapshot(
                incoming = review.decoded.snapshot,
                mode = mode,
                includePersonalFromIncoming = includePersonalFromIncoming
            )
            val importedTs = ConfigCodec.parseGeneratedAtMillis(review.summary.generatedAtIso)
                ?: System.currentTimeMillis()
            prefsRepo.recordActiveConfigMetadata(
                version = review.summary.configVersion,
                timestampMillis = importedTs,
                source = "Importado (${review.summary.sourceName})"
            )

            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
            pendingImport.value = null
            feedbackMessage.value = "Configuración v${review.summary.configVersion} importada (${review.summary.circuitCount} circuitos)."
        }
    }

    fun dismissPendingImport() {
        pendingImport.value = null
    }

    fun restoreLatestBackup() {
        viewModelScope.launch {
            val backup = repository.getLatestBackup()
            if (backup == null) {
                feedbackMessage.value = "No hay copias de seguridad guardadas."
                return@launch
            }
            try {
                val decoded = ConfigCodec.decodeFromJson(backup.payloadJson)
                repository.applyImportedSnapshot(
                    incoming = decoded.snapshot,
                    mode = ImportMergeMode.REPLACE_ALL_INCLUDING_PERSONAL,
                    includePersonalFromIncoming = true
                )
                val restoredTs = ConfigCodec.parseGeneratedAtMillis(decoded.payload.generatedAtIso)
                    ?: backup.createdAtMillis
                prefsRepo.recordActiveConfigMetadata(
                    version = decoded.payload.configVersion,
                    timestampMillis = restoredTs,
                    source = "Respaldo restaurado"
                )
                ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
                WidgetUpdateHelper.refreshAllWidgets(appContext)
                feedbackMessage.value = "Respaldo restaurado (${backup.reason})."
            } catch (e: Exception) {
                feedbackMessage.value = "No se pudo restaurar el respaldo: ${e.message}"
            }
        }
    }

    fun updateNotificationPrefs(
        serviceStart: Boolean,
        serviceEnd: Boolean,
        nextRestore: Boolean,
        newForecast: Boolean,
        leadMinutes: Int,
        favoritesOnly: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updateNotificationSettings(
                serviceStart = serviceStart,
                serviceEnd = serviceEnd,
                nextRestore = nextRestore,
                newForecast = newForecast,
                leadMinutes = leadMinutes,
                favoritesOnly = favoritesOnly
            )
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
        }
    }

    fun shareTextCode(code: String) {
        ShareHelper.shareText(
            context = appContext,
            title = "Configuración Luz Isla",
            text = "Configuración Luz Isla (copia y pega todo el código en Ajustes -> Importar):\n\n$code"
        )
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val repository: LuzIslaRepository,
        private val prefsRepo: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context.applicationContext, repository, prefsRepo) as T
        }
    }
}
