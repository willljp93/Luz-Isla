package com.example.sync

import android.os.Build
import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

data class ParsedAppVersion(
    val parts: List<Int>,
    val buildCode: Int?
)

enum class UpdateChannelStatus {
    ACTIVE,
    COMING_SOON
}

data class DistributionChannel(
    val id: String,
    val name: String,
    val subtitle: String,
    val url: String?,
    val status: UpdateChannelStatus
)

data class ReleaseHttpResponse(
    val code: Int,
    val body: String
)

interface ReleaseHttpFetcher {
    fun get(url: String): ReleaseHttpResponse
}

class OkHttpReleaseFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : ReleaseHttpFetcher {
    override fun get(url: String): ReleaseHttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Luz-Isla-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { resp ->
            return ReleaseHttpResponse(
                code = resp.code,
                body = resp.body?.string().orEmpty()
            )
        }
    }
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val latestTag: String,
        val versionLabel: String,
        val remoteVersionCode: Int,
        val releaseTitle: String,
        val releaseNotes: String,
        val publishedAt: String,
        val releasePageUrl: String,
        val preferredApkUrl: String,
        val preferredApkName: String,
        val apkSizeBytes: Long
    ) : UpdateCheckResult

    data class UpToDate(
        val latestTag: String,
        val versionLabel: String,
        val releasePageUrl: String
    ) : UpdateCheckResult

    data class NoReleasesYet(
        val releasesPageUrl: String
    ) : UpdateCheckResult

    data class CheckFailed(
        val reason: String,
        val cachedLatestTag: String,
        val cachedReleaseUrl: String
    ) : UpdateCheckResult
}

class AppUpdateService(
    private val prefsRepo: UserPreferencesRepository,
    private val fetcher: ReleaseHttpFetcher = OkHttpReleaseFetcher()
) {
    suspend fun checkForUpdates(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        force: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getSnapshot()
        val now = System.currentTimeMillis()
        val recentlyChecked = (now - prefs.lastUpdateCheckEpochMillis) in 0 until AUTO_CHECK_COOLDOWN_MS

        if (!force && recentlyChecked && prefs.latestKnownTag.isNotBlank()) {
            val cachedNewer = isRemoteVersionNewer(
                remoteTag = prefs.latestKnownTag,
                releaseTitle = prefs.latestKnownTitle,
                releaseBody = prefs.latestKnownNotes,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                remoteVersionCodeOverride = prefs.latestKnownVersionCode.takeIf { it > 0 }
            )
            return@withContext if (cachedNewer) {
                UpdateCheckResult.UpdateAvailable(
                    latestTag = prefs.latestKnownTag,
                    versionLabel = normalizeVersionLabel(prefs.latestKnownTag),
                    remoteVersionCode = prefs.latestKnownVersionCode,
                    releaseTitle = prefs.latestKnownTitle.ifBlank { prefs.latestKnownTag },
                    releaseNotes = prefs.latestKnownNotes,
                    publishedAt = prefs.latestKnownPublishedAt,
                    releasePageUrl = prefs.latestKnownReleasePageUrl.ifBlank { GITHUB_RELEASES_URL },
                    preferredApkUrl = prefs.latestKnownDownloadUrl.ifBlank {
                        prefs.latestKnownReleasePageUrl.ifBlank { GITHUB_RELEASES_URL }
                    },
                    preferredApkName = prefs.latestKnownApkName,
                    apkSizeBytes = 0L
                )
            } else {
                UpdateCheckResult.UpToDate(
                    latestTag = prefs.latestKnownTag,
                    versionLabel = normalizeVersionLabel(prefs.latestKnownTag),
                    releasePageUrl = prefs.latestKnownReleasePageUrl.ifBlank { GITHUB_RELEASES_URL }
                )
            }
        }

        try {
            val release = fetchLatestPublishedRelease()
            if (release == null) {
                prefsRepo.recordUpdateCheckStatus(
                    checkEpochMillis = now,
                    statusMessage = "Aún no hay versiones publicadas en GitHub Releases"
                )
                return@withContext UpdateCheckResult.NoReleasesYet(GITHUB_RELEASES_URL)
            }

            val parsed = parseVersion(
                tagOrName = release.tagName,
                releaseTitle = release.name,
                releaseBody = release.body
            )
            val remoteCode = parsed.buildCode ?: 0
            val preferredApk = pickPreferredApkAsset(release.assets, supportedAbis)
            val releasePage = release.htmlUrl.ifBlank { GITHUB_RELEASES_URL }
            val downloadUrl = preferredApk?.browserDownloadUrl?.ifBlank { releasePage } ?: releasePage
            val title = release.name?.trim().orEmpty().ifBlank { release.tagName }
            val notes = release.body?.trim().orEmpty()
            val published = release.publishedAt?.trim().orEmpty()
            val isNewer = isRemoteVersionNewer(
                remoteTag = release.tagName,
                releaseTitle = title,
                releaseBody = notes,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                remoteVersionCodeOverride = remoteCode.takeIf { it > 0 }
            )

            val status = if (isNewer) {
                "Nueva versión disponible: ${release.tagName}"
            } else {
                "Estás usando la última versión (${release.tagName})"
            }

            prefsRepo.saveUpdateCheckSuccess(
                checkEpochMillis = now,
                latestTag = release.tagName,
                latestTitle = title,
                latestNotes = notes,
                downloadUrl = downloadUrl,
                releasePageUrl = releasePage,
                apkName = preferredApk?.name.orEmpty(),
                versionCode = remoteCode,
                publishedAt = published,
                statusMessage = status
            )

            if (isNewer) {
                UpdateCheckResult.UpdateAvailable(
                    latestTag = release.tagName,
                    versionLabel = normalizeVersionLabel(release.tagName),
                    remoteVersionCode = remoteCode,
                    releaseTitle = title,
                    releaseNotes = notes,
                    publishedAt = published,
                    releasePageUrl = releasePage,
                    preferredApkUrl = downloadUrl,
                    preferredApkName = preferredApk?.name.orEmpty(),
                    apkSizeBytes = preferredApk?.size ?: 0L
                )
            } else {
                UpdateCheckResult.UpToDate(
                    latestTag = release.tagName,
                    versionLabel = normalizeVersionLabel(release.tagName),
                    releasePageUrl = releasePage
                )
            }
        } catch (e: Exception) {
            val status = if (prefs.latestKnownTag.isNotBlank()) {
                "Sin conexión; última versión conocida: ${prefs.latestKnownTag}"
            } else {
                "No se pudo comprobar actualizaciones (sin conexión)"
            }
            prefsRepo.recordUpdateCheckStatus(
                checkEpochMillis = prefs.lastUpdateCheckEpochMillis,
                statusMessage = status
            )
            UpdateCheckResult.CheckFailed(
                reason = status,
                cachedLatestTag = prefs.latestKnownTag,
                cachedReleaseUrl = prefs.latestKnownReleasePageUrl.ifBlank { GITHUB_RELEASES_URL }
            )
        }
    }

    private fun fetchLatestPublishedRelease(): GitHubReleaseDto? {
        val latestResp = fetcher.get(GITHUB_LATEST_API_URL)
        if (latestResp.code in 200..299 && latestResp.body.isNotBlank()) {
            val dto = runCatching { json.decodeFromString<GitHubReleaseDto>(latestResp.body) }.getOrNull()
            if (dto != null && dto.tagName.isNotBlank() && !dto.draft) {
                return dto
            }
        }
        if (latestResp.code != 404 && latestResp.code !in 200..299) {
            throw IllegalStateException("GitHub HTTP ${latestResp.code}")
        }

        val listResp = fetcher.get(GITHUB_RELEASES_API_URL)
        if (listResp.code !in 200..299) {
            throw IllegalStateException("GitHub HTTP ${listResp.code}")
        }
        val releases = runCatching { json.decodeFromString<List<GitHubReleaseDto>>(listResp.body) }
            .getOrDefault(emptyList())
            .filter { !it.draft && it.tagName.isNotBlank() }

        return releases.firstOrNull { !it.prerelease } ?: releases.firstOrNull()
    }

    companion object {
        const val GITHUB_REPO_URL = "https://github.com/willljp93/Luz-Isla"
        const val GITHUB_RELEASES_URL = "https://github.com/willljp93/Luz-Isla/releases"
        const val GITHUB_LATEST_API_URL = "https://api.github.com/repos/willljp93/Luz-Isla/releases/latest"
        const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/willljp93/Luz-Isla/releases?per_page=10"
        private const val AUTO_CHECK_COOLDOWN_MS = 12L * 60L * 60L * 1000L

        private val json = Json { ignoreUnknownKeys = true }
        private val BUILD_REGEX = Regex("""(?:[-+]b|build[\s#:_-]*|versionCode[:\s=]+)([0-9]+)""", RegexOption.IGNORE_CASE)
        private val SEMVER_PREFIX_REGEX = Regex("""^[vV]?\s*([0-9]+(?:\.[0-9]+)*)""")

        fun defaultDistributionChannels(): List<DistributionChannel> = listOf(
            DistributionChannel(
                id = "github",
                name = "GitHub Releases",
                subtitle = "willljp93/Luz-Isla • Canal principal activo",
                url = GITHUB_RELEASES_URL,
                status = UpdateChannelStatus.ACTIVE
            ),
            DistributionChannel(
                id = "play_store",
                name = "Google Play Store",
                subtitle = "Tienda oficial con actualización automática",
                url = null,
                status = UpdateChannelStatus.COMING_SOON
            ),
            DistributionChannel(
                id = "apklis",
                name = "Apklis",
                subtitle = "Centro de aplicaciones Android en Cuba",
                url = null,
                status = UpdateChannelStatus.COMING_SOON
            ),
            DistributionChannel(
                id = "telegram",
                name = "Canal de Telegram",
                subtitle = "Descarga manual de APK y avisos",
                url = null,
                status = UpdateChannelStatus.COMING_SOON
            ),
            DistributionChannel(
                id = "whatsapp",
                name = "Canal de WhatsApp",
                subtitle = "Novedades y enlaces directos de actualización",
                url = null,
                status = UpdateChannelStatus.COMING_SOON
            )
        )

        fun normalizeVersionLabel(tagOrVersion: String): String {
            val trimmed = tagOrVersion.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
        }

        fun parseVersion(
            tagOrName: String,
            releaseTitle: String? = null,
            releaseBody: String? = null
        ): ParsedAppVersion {
            val cleanedTag = tagOrName.trim()
            val semverMatch = SEMVER_PREFIX_REGEX.find(cleanedTag)
            val parts = semverMatch?.groupValues?.getOrNull(1)
                ?.split('.')
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(0)

            val buildCode = BUILD_REGEX.find(cleanedTag)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: releaseTitle?.let { BUILD_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                ?: releaseBody?.let { BUILD_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

            return ParsedAppVersion(parts = parts, buildCode = buildCode)
        }

        fun isRemoteVersionNewer(
            remoteTag: String,
            releaseTitle: String? = null,
            releaseBody: String? = null,
            currentVersionName: String,
            currentVersionCode: Int,
            remoteVersionCodeOverride: Int? = null
        ): Boolean {
            if (remoteTag.isBlank()) return false
            val remote = parseVersion(remoteTag, releaseTitle, releaseBody)
            val local = parseVersion(currentVersionName)

            val maxLen = maxOf(remote.parts.size, local.parts.size)
            for (i in 0 until maxLen) {
                val r = remote.parts.getOrElse(i) { 0 }
                val l = local.parts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }

            val remoteCode = remoteVersionCodeOverride ?: remote.buildCode ?: 0
            return remoteCode > currentVersionCode
        }

        fun pickPreferredApkAsset(
            assets: List<GitHubAssetDto>,
            supportedAbis: List<String>
        ): GitHubAssetDto? {
            val apkAssets = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            }
            if (apkAssets.isEmpty()) return null

            for (abi in supportedAbis) {
                val normalizedAbi = abi.lowercase(Locale.US)
                val match = apkAssets.firstOrNull {
                    it.name.lowercase(Locale.US).contains(normalizedAbi)
                }
                if (match != null) return match
            }

            return apkAssets.firstOrNull {
                it.name.lowercase(Locale.US).contains("universal")
            } ?: apkAssets.first()
        }
    }
}
