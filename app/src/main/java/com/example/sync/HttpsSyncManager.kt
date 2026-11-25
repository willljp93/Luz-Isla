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
    data class Updated(
        val version: Int,
        val circuitCount: Int,
        val forecastCount: Int,
        val versionName: String = ""
    ) : SyncOutcome

    data class UpToDate(
        val version: Int,
        val versionName: String = ""
    ) : SyncOutcome

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
        val normalizedUrl = normalizeSyncUrl(url)
        if (normalizedUrl.isEmpty()) {
            return@withContext SyncOutcome.Failure("Configura una dirección HTTPS válida antes de sincronizar.")
        }
        if (!normalizedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext SyncOutcome.Failure("Por seguridad, solo se permiten direcciones HTTPS.")
        }

        try {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("Accept", "application/json, text/plain;q=0.9")
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

                val bodyHash = ConfigCodec.sha256Hex(body.toByteArray(Charsets.UTF_8))
                val decoded = runCatching { ConfigCodec.decodeFromJson(body, verifySha256 = true) }
                    .getOrElse { ConfigCodec.decodeFromJson(body, verifySha256 = false) }
                val prefs = prefsRepo.getSnapshot()
                val ts = ConfigCodec.parseGeneratedAtMillis(decoded.payload.generatedAtIso)
                    ?: System.currentTimeMillis()
                val remoteVersionName = decoded.payload.displayVersion(ts)

                val sameNumeric = decoded.payload.configVersion <= prefs.lastRemoteConfigVersion
                val sameName = decoded.payload.configVersionName.isBlank() ||
                    decoded.payload.configVersionName == prefs.lastRemoteConfigVersionName
                val sameContent = prefs.lastRemoteJsonHash.isBlank() || bodyHash == prefs.lastRemoteJsonHash

                if (!forceApply && prefs.lastRemoteConfigVersion > 0 && sameNumeric && sameName && sameContent) {
                    val activeRemoteLabel = prefs.lastRemoteConfigVersionName.ifBlank {
                        "v${prefs.lastRemoteConfigVersion}"
                    }
                    val msg = "Ya tienes la versión más reciente ($activeRemoteLabel)."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.UpToDate(
                        version = prefs.lastRemoteConfigVersion,
                        versionName = activeRemoteLabel
                    )
                }

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
                    versionName = remoteVersionName,
                    remoteJsonHash = bodyHash
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
                    forecastCount = decoded.snapshot.explicitForecasts.size,
                    versionName = remoteVersionName
                )
            }
        } catch (e: Exception) {
            val msg = "Sin conexión o error al validar pronóstico: ${e.message ?: "Error de red"}. Se conservan los datos locales."
            prefsRepo.recordSyncStatus(msg)
            SyncOutcome.Failure(msg)
        }
    }

    companion object {
        const val DEFAULT_GITHUB_SYNC_URL =
            "https://raw.githubusercontent.com/willljp93/Luz-Isla/main/luz_isla_config_2026.11.24.1.json"

        private val GITHUB_BLOB_REGEX =
            Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE)

        fun normalizeSyncUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""
            val match = GITHUB_BLOB_REGEX.matchEntire(trimmed)
            return if (match != null) {
                val (owner, repo, branchAndPath) = match.destructured
                "https://raw.githubusercontent.com/$owner/$repo/$branchAndPath"
            } else {
                trimmed
            }
        }
    }
}
