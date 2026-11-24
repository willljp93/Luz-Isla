package com.example.sync

import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.transfer.ConfigCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface SyncOutcome {
    data class Updated(val version: Int, val circuitCount: Int, val forecastCount: Int) : SyncOutcome
    data class UpToDate(val version: Int) : SyncOutcome
    data class Failure(val reason: String) : SyncOutcome
}

class HttpsSyncManager(
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun syncFromUrl(url: String, forceApply: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return@withContext SyncOutcome.Failure("Configura una dirección HTTPS válida antes de sincronizar.")
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext SyncOutcome.Failure("Por seguridad, solo se permiten direcciones HTTPS.")
        }

        try {
            val request = Request.Builder()
                .url(trimmed)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "El servidor respondió con código HTTP ${response.code}."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg)
                }

                val body = response.body?.string().orEmpty()
                if (body.length > 2 * 1024 * 1024) {
                    val msg = "El archivo remoto excede el límite seguro de 2 MB."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg)
                }

                val decoded = ConfigCodec.decodeFromJson(body)
                val prefs = prefsRepo.getSnapshot()
                if (!forceApply && decoded.payload.configVersion <= prefs.lastRemoteConfigVersion && prefs.lastRemoteConfigVersion > 0) {
                    val activeRemoteLabel = prefs.lastRemoteConfigVersionName.ifBlank {
                        "v${prefs.lastRemoteConfigVersion}"
                    }
                    val msg = "Ya tienes la versión más reciente ($activeRemoteLabel)."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.UpToDate(prefs.lastRemoteConfigVersion)
                }

                val ts = ConfigCodec.parseGeneratedAtMillis(decoded.payload.generatedAtIso) ?: System.currentTimeMillis()
                val remoteVersionName = decoded.payload.displayVersion(ts)

                // Backup current config before updating
                val currentSnapshot = repository.getCurrentSnapshot()
                val backupPayload = ConfigCodec.snapshotToPayload(
                    snapshot = currentSnapshot,
                    configVersion = prefs.activeConfigVersion.coerceAtLeast(1),
                    configVersionName = prefs.activeConfigVersionName,
                    includePersonalData = true,
                    sourceName = "Respaldo previo a sincronización"
                )
                repository.saveBackup(
                    reason = "Antes de sincronización v$remoteVersionName",
                    payloadJson = ConfigCodec.encodeToJson(backupPayload, pretty = false)
                )

                // Preserve local personal names, comments, favorites and primary home
                repository.applyImportedSnapshot(
                    incoming = decoded.snapshot,
                    mode = ImportMergeMode.MERGE_KEEP_PERSONAL,
                    includePersonalFromIncoming = false
                )

                val status = "Actualizado a v$remoteVersionName (${decoded.snapshot.circuits.size} circuitos)"
                prefsRepo.recordSyncSuccess(
                    version = decoded.payload.configVersion,
                    forecastUpdateMillis = System.currentTimeMillis(),
                    statusMessage = status,
                    versionName = remoteVersionName
                )
                prefsRepo.recordActiveConfigMetadata(
                    version = decoded.payload.configVersion,
                    timestampMillis = ts,
                    source = "Sincronización HTTPS (${decoded.payload.sourceName.ifBlank { "Remoto" }})",
                    versionName = remoteVersionName
                )

                SyncOutcome.Updated(
                    version = decoded.payload.configVersion,
                    circuitCount = decoded.snapshot.circuits.size,
                    forecastCount = decoded.snapshot.explicitForecasts.size
                )
            }
        } catch (e: Exception) {
            val msg = "Sin conexión o error al validar pronóstico: ${e.message ?: "Error de red"}. Se conservan los datos locales."
            prefsRepo.recordSyncStatus(msg)
            SyncOutcome.Failure(msg)
        }
    }
}
