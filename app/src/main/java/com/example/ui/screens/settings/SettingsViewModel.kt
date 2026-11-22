package com.example.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.ThemeMode
import com.example.notifications.ScheduleNotificationManager
import com.example.sync.ForecastSyncWorker
import com.example.sync.HttpsSyncManager
import com.example.sync.ShareHelper
import com.example.sync.SyncOutcome
import com.example.transfer.ConfigCodec
import com.example.transfer.DecodedConfigResult
import com.example.transfer.ImportSummary
import com.example.transfer.QrCodeUtils
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportBundle(
    val alphanumericCode: String,
    val jsonText: String,
    val qrParts: List<String>,
    val qrBitmaps: List<Bitmap>,
    val isMultiPartQr: Boolean
)

data class PendingImportReview(
    val decoded: DecodedConfigResult,
    val summary: ImportSummary
)

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val hasBackup: Boolean = false,
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
    private val exportBundle = MutableStateFlow<ExportBundle?>(null)
    private val pendingImport = MutableStateFlow<PendingImportReview?>(null)
    private val feedbackMessage = MutableStateFlow<String?>(null)

    private val syncManager = HttpsSyncManager(repository, prefsRepo)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepo.preferencesFlow,
        repository.backupsFlow,
        isSyncing,
        exportBundle,
        pendingImport,
        feedbackMessage
    ) { values ->
        val prefs = values[0] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val backups = values[1] as List<*>
        val syncing = values[2] as Boolean
        val export = values[3] as ExportBundle?
        val pending = values[4] as PendingImportReview?
        val msg = values[5] as String?

        SettingsUiState(
            prefs = prefs,
            isSyncing = syncing,
            hasBackup = backups.isNotEmpty(),
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
                val version = uiState.value.prefs.lastRemoteConfigVersion.coerceAtLeast(1)
                val payload = ConfigCodec.snapshotToPayload(
                    snapshot = snapshot,
                    configVersion = version,
                    includePersonalData = includePersonalData
                )
                val jsonText = ConfigCodec.encodeToJson(payload, pretty = true)
                val code = ConfigCodec.encodeToAlphanumericCode(payload)
                val qrParts = ConfigCodec.buildQrPayloads(code)
                val bitmaps = qrParts.map { QrCodeUtils.generateQrBitmap(it, 640) }
                ExportBundle(
                    alphanumericCode = code,
                    jsonText = jsonText,
                    qrParts = qrParts,
                    qrBitmaps = bitmaps,
                    isMultiPartQr = qrParts.size > 1
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
                configVersion = uiState.value.prefs.lastRemoteConfigVersion.coerceAtLeast(1),
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
            ScheduleNotificationManager.rescheduleUpcomingNotifications(appContext)
            WidgetUpdateHelper.refreshAllWidgets(appContext)
            pendingImport.value = null
            feedbackMessage.value = "Configuración importada (${review.summary.circuitCount} circuitos)."
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
