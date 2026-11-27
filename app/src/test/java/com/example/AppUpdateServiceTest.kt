package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.preferences.UserPreferencesRepository
import com.example.sync.AppUpdateService
import com.example.sync.GitHubAssetDto
import com.example.sync.ReleaseHttpFetcher
import com.example.sync.ReleaseHttpResponse
import com.example.sync.UpdateChannelStatus
import com.example.sync.UpdateCheckResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateServiceTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tmpFolder.newFile("update_prefs_${System.nanoTime()}.preferences_pb") }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `version comparison handles semver tags and build numbers accurately`() {
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2.1",
                currentVersionName = "2.0",
                currentVersionCode = 2
            )
        )
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2.0-b3",
                currentVersionName = "2.0",
                currentVersionCode = 2
            )
        )
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2.0",
                releaseTitle = "Luz Isla v2.0 (Build 5)",
                currentVersionName = "2.0",
                currentVersionCode = 2
            )
        )
        assertFalse(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2.0-b2",
                currentVersionName = "2.0",
                currentVersionCode = 2
            )
        )
        assertFalse(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v1.9.5",
                currentVersionName = "2.0",
                currentVersionCode = 2
            )
        )
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.23.2-b26112302",
                currentVersionName = "2026.11.23.1",
                currentVersionCode = 26112301
            )
        )
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.24.1-b26112401",
                currentVersionName = "2026.11.23.5",
                currentVersionCode = 26112305
            )
        )
        assertFalse(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.23.1-b26112301",
                currentVersionName = "2026.11.23.1",
                currentVersionCode = 26112301
            )
        )
    }

    @Test
    fun `pickPreferredApkAsset selects device ABI or falls back to universal`() {
        val assets = listOf(
            GitHubAssetDto("LuzIsla-v2.1-b3-release-armeabi-v7a.apk", "https://example.com/v7a.apk"),
            GitHubAssetDto("LuzIsla-v2.1-b3-release-arm64-v8a.apk", "https://example.com/arm64.apk"),
            GitHubAssetDto("LuzIsla-v2.1-b3-release-universal.apk", "https://example.com/universal.apk")
        )

        val arm64 = AppUpdateService.pickPreferredApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("https://example.com/arm64.apk", arm64?.browserDownloadUrl)

        val x86Fallback = AppUpdateService.pickPreferredApkAsset(assets, listOf("x86_64"))
        assertEquals("https://example.com/universal.apk", x86Fallback?.browserDownloadUrl)
    }

    @Test
    fun `checkForUpdates detects newer GitHub release, caches result, and handles offline fallback`() = runTest {
        var calls = 0
        var offline = false
        val fetcher = object : ReleaseHttpFetcher {
            override fun get(url: String): ReleaseHttpResponse {
                calls++
                if (offline) throw IOException("Sin red")
                return ReleaseHttpResponse(
                    code = 200,
                    body = """
                        {
                          "tag_name": "v2.1-b3",
                          "name": "Luz Isla v2.1 (Build 3)",
                          "body": "Mejoras solares y correcciones",
                          "prerelease": false,
                          "draft": false,
                          "published_at": "2026-11-23T12:00:00Z",
                          "html_url": "https://github.com/willljp93/Luz-Isla/releases/tag/v2.1-b3",
                          "assets": [
                            {
                              "name": "LuzIsla-v2.1-b3-release-arm64-v8a.apk",
                              "browser_download_url": "https://github.com/willljp93/Luz-Isla/releases/download/v2.1-b3/LuzIsla-v2.1-b3-release-arm64-v8a.apk",
                              "size": 12345678
                            }
                          ]
                        }
                    """.trimIndent()
                )
            }
        }
        val service = AppUpdateService(prefsRepo, fetcher)

        val first = service.checkForUpdates(
            currentVersionName = "2.0",
            currentVersionCode = 2,
            supportedAbis = listOf("arm64-v8a"),
            force = true
        )
        assertTrue(first is UpdateCheckResult.UpdateAvailable)
        first as UpdateCheckResult.UpdateAvailable
        assertEquals("v2.1-b3", first.latestTag)
        assertEquals(3, first.remoteVersionCode)
        assertTrue(first.preferredApkUrl.endsWith("arm64-v8a.apk"))
        assertEquals(1, calls)

        // Cached within 12h cooldown
        val second = service.checkForUpdates(
            currentVersionName = "2.0",
            currentVersionCode = 2,
            supportedAbis = listOf("arm64-v8a"),
            force = false
        )
        assertTrue(second is UpdateCheckResult.UpdateAvailable)
        assertEquals(1, calls)

        // Forced check while offline preserves cached release URL without throwing
        offline = true
        val third = service.checkForUpdates(
            currentVersionName = "2.0",
            currentVersionCode = 2,
            supportedAbis = listOf("arm64-v8a"),
            force = true
        )
        assertTrue(third is UpdateCheckResult.CheckFailed)
        third as UpdateCheckResult.CheckFailed
        assertEquals("v2.1-b3", third.cachedLatestTag)
        assertTrue(third.cachedReleaseUrl.contains("willljp93/Luz-Isla"))
    }

    @Test
    fun `checkForUpdates handles empty releases cleanly and exposes upcoming channels`() = runTest {
        val fetcher = object : ReleaseHttpFetcher {
            override fun get(url: String): ReleaseHttpResponse {
                return if (url.endsWith("/latest")) {
                    ReleaseHttpResponse(404, """{"message":"Not Found"}""")
                } else {
                    ReleaseHttpResponse(200, "[]")
                }
            }
        }
        val service = AppUpdateService(prefsRepo, fetcher)
        val result = service.checkForUpdates(force = true)
        assertTrue(result is UpdateCheckResult.NoReleasesYet)

        val channels = AppUpdateService.defaultDistributionChannels()
        assertEquals(UpdateChannelStatus.ACTIVE, channels.first { it.id == "github" }.status)
        assertEquals(
            setOf("apklis", "telegram", "whatsapp"),
            channels.filter { it.status == UpdateChannelStatus.COMING_SOON }.map { it.id }.toSet()
        )
    }

    @Test
    fun `formatReleaseNotesForDisplay strips raw markdown tokens cleanly`() {
        val raw = """
            ### **Luz Isla v2026.11.25.2** (`26112502`)
            - Mejora en **Actualizaciones** y `Configuración JSON`.
            * Revisar [GitHub Releases](https://github.com/willljp93/Luz-Isla/releases)
            ---
            > Nota importante
        """.trimIndent()

        val cleaned = AppUpdateService.formatReleaseNotesForDisplay(raw)
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("`"))
        assertFalse(cleaned.contains("###"))
        assertTrue(cleaned.contains("Luz Isla v2026.11.25.2 (26112502)"))
        assertTrue(cleaned.contains("• Mejora en Actualizaciones y Configuración JSON."))
        assertTrue(cleaned.contains("• Revisar GitHub Releases"))
        assertTrue(cleaned.contains("Nota importante"))
    }

    @Test
    fun `verify identical lower and higher remote version behavior`() {
        val currentVersion = "2026.11.27.3"
        val currentCode = 26112703

        // Identical version should NOT be newer
        assertFalse(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.27.3-b26112703",
                currentVersionName = currentVersion,
                currentVersionCode = currentCode
            )
        )

        // Lower remote version should NOT be newer
        assertFalse(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.27.2-b26112702",
                currentVersionName = currentVersion,
                currentVersionCode = currentCode
            )
        )

        // Higher remote version SHOULD be newer
        assertTrue(
            AppUpdateService.isRemoteVersionNewer(
                remoteTag = "v2026.11.27.4-b26112704",
                currentVersionName = currentVersion,
                currentVersionCode = currentCode
            )
        )
    }

    @Test
    fun `verify BuildConfig version metadata is present and valid`() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE > 0)
    }
}
