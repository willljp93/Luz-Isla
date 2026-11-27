package com.example.sync

import android.content.Context
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.notifications.ScheduleNotificationManager
import com.example.transfer.ConfigCodec
import com.example.transfer.ConfigCodecException
import com.example.transfer.Sha256Status
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SyncErrorType {
    INSECURE_URL,
    NETWORK_ERROR,
    HTTP_ERROR,
    FILE_TOO_LARGE,
    MALFORMED_JSON,
    INCOMPATIBLE_SCHEMA,
    HASH_MISMATCH,
    INCOMPATIBLE_CONFIG
}

sealed interface SyncOutcome {
    data class Updated(
        val version: Int,
        val circuitCount: Int,
        val forecastCount: Int,
        val versionName: String = "",
        val sha256Status: Sha256Status = Sha256Status.VERIFIED
    ) : SyncOutcome

    data class UpToDate(
        val version: Int,
        val versionName: String = ""
    ) : SyncOutcome

    data class Failure(
        val reason: String,
        val errorType: SyncErrorType = SyncErrorType.NETWORK_ERROR
    ) : SyncOutcome
}

class HttpsSyncManager(
    private val repository: LuzIslaRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val client: OkHttpClient = defaultClient(),
    private val appContext: Context? = null
) {
    suspend fun syncFromUrl(url: String, forceApply: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeSyncUrl(url)
        if (normalizedUrl.isEmpty()) {
            return@withContext SyncOutcome.Failure(
                reason = "Configura una dirección HTTPS válida antes de sincronizar.",
                errorType = SyncErrorType.INSECURE_URL
            )
        }
        val parsedUrl = normalizedUrl.toHttpUrlOrNull()
        if (parsedUrl == null || !parsedUrl.isHttps) {
            val msg = "Por seguridad, solo se permiten direcciones HTTPS válidas."
            prefsRepo.recordSyncStatus(msg)
            return@withContext SyncOutcome.Failure(msg, SyncErrorType.INSECURE_URL)
        }

        try {
            val request = Request.Builder()
                .url(parsedUrl)
                .header("Accept", "application/json, text/plain;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                // 1. Verificar que la URL final y toda la cadena de redirección mantengan HTTPS
                if (response.isRedirect) {
                    val location = response.header("Location")
                    val resolvedRedirect = location?.let { response.request.url.resolve(it) }
                    if (resolvedRedirect == null || !resolvedRedirect.isHttps) {
                        val msg = "La URL redirigió a una dirección no segura (HTTP). Por seguridad, solo se permite HTTPS."
                        prefsRepo.recordSyncStatus(msg)
                        return@withContext SyncOutcome.Failure(msg, SyncErrorType.INSECURE_URL)
                    }
                    val msg = "La descarga no pudo completarse tras redirecciones HTTP ${response.code}."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.HTTP_ERROR)
                }

                val chainAllHttps = generateSequence(response) { it.priorResponse }
                    .all { it.request.url.isHttps }
                if (!response.request.url.isHttps || !chainAllHttps) {
                    val msg = "La URL final tras la redirección no utiliza HTTPS. Descarga rechazada."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.INSECURE_URL)
                }

                // 2. Verificar estado HTTP exitoso antes de leer cuerpo
                if (!response.isSuccessful) {
                    val msg = "El servidor respondió con error HTTP ${response.code}. Se conservan los datos locales."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.HTTP_ERROR)
                }

                // 3. Verificar Content-Length cuando esté disponible
                val responseBody = response.body
                val declaredLength = responseBody?.contentLength()?.takeIf { it >= 0L }
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0L }
                if (declaredLength != null && declaredLength > ConfigCodec.MAX_CONFIG_BYTES.toLong()) {
                    val msg = "El archivo remoto excede el límite seguro de 2 MB. Se conservan los datos locales."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.FILE_TOO_LARGE)
                }

                if (responseBody == null) {
                    val msg = "La respuesta del servidor no contiene datos JSON."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.MALFORMED_JSON)
                }

                // 4. Leer flujo en bytes con límite estricto (evita cargar archivos enormes en memoria)
                val bodyBytes = try {
                    responseBody.byteStream().use { stream ->
                        ConfigCodec.readBytesWithLimit(
                            input = stream,
                            maxBytes = ConfigCodec.MAX_CONFIG_BYTES,
                            expectedLengthHint = declaredLength ?: -1L
                        )
                    }
                } catch (tooLarge: ConfigCodecException.PayloadTooLargeException) {
                    val msg = "El archivo remoto supera el límite seguro de 2 MB durante la descarga. Se conservan los datos locales."
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, SyncErrorType.FILE_TOO_LARGE)
                }

                val body = String(bodyBytes, Charsets.UTF_8)
                val bodyHash = ConfigCodec.sha256Hex(bodyBytes)

                // 5. Decodificar y validar estrictamente SHA-256 (nunca desactivar verificación en discrepancia)
                val decoded = try {
                    ConfigCodec.decodeFromJson(body, verifySha256 = true)
                } catch (e: ConfigCodecException) {
                    val (errorType, msg) = when (e) {
                        is ConfigCodecException.HashMismatchException ->
                            SyncErrorType.HASH_MISMATCH to
                                "Hash SHA-256 incorrecto: el archivo remoto no coincide con su suma de verificación. Se conservan los datos locales."
                        is ConfigCodecException.IncompatibleSchemaException ->
                            SyncErrorType.INCOMPATIBLE_SCHEMA to
                                (e.message ?: "Esquema de configuración incompatible.")
                        is ConfigCodecException.IncompatibleConfigException ->
                            SyncErrorType.INCOMPATIBLE_CONFIG to
                                (e.message ?: "Configuración no compatible con Luz Isla.")
                        is ConfigCodecException.PayloadTooLargeException ->
                            SyncErrorType.FILE_TOO_LARGE to
                                (e.message ?: "El archivo supera el límite de 2 MB.")
                        is ConfigCodecException.MalformedJsonException ->
                            SyncErrorType.MALFORMED_JSON to
                                (e.message ?: "El archivo remoto contiene JSON malformado.")
                    }
                    prefsRepo.recordSyncStatus(msg)
                    return@withContext SyncOutcome.Failure(msg, errorType)
                }

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

                // Respaldo previo antes de aplicar la actualización
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

                // Conservar nombres personales, comentarios, favoritos y vivienda principal locales
                repository.applyImportedSnapshot(
                    incoming = decoded.snapshot,
                    mode = ImportMergeMode.MERGE_KEEP_PERSONAL,
                    includePersonalFromIncoming = false
                )

                val integrityNote = when (decoded.sha256Status) {
                    Sha256Status.VERIFIED -> "SHA-256 verificado"
                    Sha256Status.MISSING -> "sin SHA-256 (integridad no verificada)"
                    Sha256Status.MISMATCH_IGNORED -> "SHA-256 omitido"
                }
                val status = "Actualizado a v$remoteVersionName (${decoded.snapshot.circuits.size} circuitos • $integrityNote)"
                prefsRepo.recordSyncSuccess(
                    version = decoded.payload.configVersion,
                    forecastUpdateMillis = System.currentTimeMillis(),
                    statusMessage = status,
                    versionName = remoteVersionName,
                    remoteJsonHash = bodyHash
                )
                val sourcePrefix = if (decoded.sha256Status == Sha256Status.VERIFIED) {
                    "Sincronización HTTPS"
                } else {
                    "Sincronización HTTPS sin SHA-256"
                }
                prefsRepo.recordActiveConfigMetadata(
                    version = decoded.payload.configVersion,
                    timestampMillis = ts,
                    source = "$sourcePrefix (${decoded.payload.sourceName.ifBlank { "Remoto" }})",
                    versionName = remoteVersionName
                )

                appContext?.let { ctx ->
                    ScheduleNotificationManager.rescheduleUpcomingNotifications(ctx)
                    WidgetUpdateHelper.refreshAllWidgets(ctx)
                }

                SyncOutcome.Updated(
                    version = decoded.payload.configVersion,
                    circuitCount = decoded.snapshot.circuits.size,
                    forecastCount = decoded.snapshot.explicitForecasts.size,
                    versionName = remoteVersionName,
                    sha256Status = decoded.sha256Status
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (io: IOException) {
            val msg = "Error de red al conectar con el servidor: ${io.message ?: "Sin conexión"}. Se conservan los datos locales."
            prefsRepo.recordSyncStatus(msg)
            SyncOutcome.Failure(msg, SyncErrorType.NETWORK_ERROR)
        } catch (e: Exception) {
            val msg = "Error inesperado al procesar la sincronización: ${e.message ?: "Error desconocido"}. Se conservan los datos locales."
            prefsRepo.recordSyncStatus(msg)
            SyncOutcome.Failure(msg, SyncErrorType.NETWORK_ERROR)
        }
    }

    companion object {
        const val LEGACY_GITHUB_SYNC_URL =
            "https://raw.githubusercontent.com/willljp93/Luz-Isla/main/luz_isla_config_2026.11.24.1.json"

        const val LEGACY_SPACED_GITHUB_SYNC_URL =
            "https://raw.githubusercontent.com/willljp93/Luz-Isla/main/Conf%20Circuitos/luz_isla_config_2026.11.24.1.json"

        const val DEFAULT_GITHUB_SYNC_URL =
            "https://raw.githubusercontent.com/willljp93/Luz-Isla/main/ConfCircuitos/luz_isla_config.json"

        private val GITHUB_BLOB_REGEX =
            Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()

        fun normalizeSyncUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""
            val match = GITHUB_BLOB_REGEX.matchEntire(trimmed)
            val resolved = if (match != null) {
                val (owner, repo, branchAndPath) = match.destructured
                "https://raw.githubusercontent.com/$owner/$repo/$branchAndPath"
            } else {
                trimmed
            }
            val encoded = resolved.replace(" ", "%20")
            return if (
                encoded.equals(LEGACY_GITHUB_SYNC_URL, ignoreCase = true) ||
                encoded.equals(LEGACY_SPACED_GITHUB_SYNC_URL, ignoreCase = true)
            ) {
                DEFAULT_GITHUB_SYNC_URL
            } else {
                encoded
            }
        }
    }
}
