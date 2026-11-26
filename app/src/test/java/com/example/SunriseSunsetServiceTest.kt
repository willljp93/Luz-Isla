package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.preferences.UserPreferencesRepository
import com.example.sync.SolarHttpFetcher
import com.example.sync.SolarSyncOutcome
import com.example.sync.SunriseSunsetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SunriseSunsetServiceTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tmpFolder.newFile("solar_prefs_${System.nanoTime()}.preferences_pb") }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `buildRequestUrl includes Nueva Gerona coordinates and time_format 24`() {
        val url = SunriseSunsetService.buildRequestUrl(
            date = LocalDate.of(2026, 11, 23),
            zoneId = ZoneId.of("America/Havana")
        )
        assertTrue(url.startsWith("https://api.sunrisesunset.io/json?"))
        assertTrue(url.contains("lat=21.8867"))
        assertTrue(url.contains("lng=-82.8056"))
        assertTrue(url.contains("date=2026-11-23"))
        assertTrue(url.contains("time_format=24"))
    }

    @Test
    fun `parseResponseJson parses 24h and 12h formats and converts between timezones`() {
        val raw24 = """
            {
              "results": {
                "date": "2026-01-15",
                "sunrise": "06:42:10",
                "sunset": "17:39:45",
                "timezone": "America/Havana"
              },
              "status": "OK"
            }
        """.trimIndent()
        val parsedSameZone = SunriseSunsetService.parseResponseJson(
            rawJson = raw24,
            fallbackDate = LocalDate.of(2026, 1, 15),
            targetZoneId = ZoneId.of("America/Havana")
        )
        assertNotNull(parsedSameZone)
        assertEquals(6 * 60 + 42, parsedSameZone!!.sunriseMinutes)
        assertEquals(17 * 60 + 39, parsedSameZone.sunsetMinutes)

        // Jan 15: Havana is UTC-5, Madrid is UTC+1 (+6h difference across all tzdata versions)
        val parsedMadrid = SunriseSunsetService.parseResponseJson(
            rawJson = raw24,
            fallbackDate = LocalDate.of(2026, 1, 15),
            targetZoneId = ZoneId.of("Europe/Madrid")
        )
        assertNotNull(parsedMadrid)
        assertEquals(12 * 60 + 42, parsedMadrid!!.sunriseMinutes)
        assertEquals(23 * 60 + 39, parsedMadrid.sunsetMinutes)

        val raw12 = """
            {
              "results": {
                "date": "2026-01-15",
                "sunrise": "6:45:00 AM",
                "sunset": "5:40:00 PM",
                "timezone": "America/Havana"
              },
              "status": "OK"
            }
        """.trimIndent()
        val parsed12 = SunriseSunsetService.parseResponseJson(
            rawJson = raw12,
            fallbackDate = LocalDate.of(2026, 1, 15),
            targetZoneId = ZoneId.of("America/Havana")
        )
        assertNotNull(parsed12)
        assertEquals(6 * 60 + 45, parsed12!!.sunriseMinutes)
        assertEquals(17 * 60 + 40, parsed12.sunsetMinutes)
    }

    @Test
    fun `syncIfNeeded caches today response and avoids duplicate network calls`() = runTest {
        var callCount = 0
        val fetcher = object : SolarHttpFetcher {
            override fun fetchJson(url: String): String {
                callCount++
                return """
                    {
                      "results": {
                        "date": "2026-11-23",
                        "sunrise": "06:41:00",
                        "sunset": "17:43:00",
                        "timezone": "America/Havana"
                      },
                      "status": "OK"
                    }
                """.trimIndent()
            }
        }
        val service = SunriseSunsetService(prefsRepo, fetcher)
        val havana = ZoneId.of("America/Havana")
        val day1 = LocalDate.of(2026, 11, 23)

        val first = service.syncIfNeeded(havana, today = day1, force = false)
        assertTrue(first is SolarSyncOutcome.SyncedFromNetwork)
        assertEquals(1, callCount)

        val second = service.syncIfNeeded(havana, today = day1, force = false)
        assertTrue(second is SolarSyncOutcome.UsedTodayCache)
        assertEquals(1, callCount)

        val prefs = prefsRepo.getSnapshot()
        assertEquals(6 * 60 + 41, prefs.sunriseMinutes)
        assertEquals(17 * 60 + 43, prefs.sunsetMinutes)
        assertEquals("2026-11-23", prefs.solarCachedDate)

        // Changing day triggers a new fetch
        val day2 = LocalDate.of(2026, 11, 24)
        service.syncIfNeeded(havana, today = day2, force = false)
        assertEquals(2, callCount)
    }

    @Test
    fun `offline failure falls back silently to stale cache without error or losing data`() = runTest {
        val havana = ZoneId.of("America/Havana")
        var shouldFail = false
        val fetcher = object : SolarHttpFetcher {
            override fun fetchJson(url: String): String {
                if (shouldFail) throw IOException("No network")
                return """
                    {
                      "results": {
                        "date": "2026-11-23",
                        "sunrise": "06:38:00",
                        "sunset": "17:44:00",
                        "timezone": "America/Havana"
                      },
                      "status": "OK"
                    }
                """.trimIndent()
            }
        }
        val service = SunriseSunsetService(prefsRepo, fetcher)

        // Populate cache on day 1
        service.syncIfNeeded(havana, today = LocalDate.of(2026, 11, 23))

        // Go offline on day 2
        shouldFail = true
        val offlineOutcome = service.syncIfNeeded(havana, today = LocalDate.of(2026, 11, 24))
        assertTrue(offlineOutcome is SolarSyncOutcome.OfflineFallback)
        offlineOutcome as SolarSyncOutcome.OfflineFallback
        assertTrue(offlineOutcome.usedStaleCache)
        assertEquals(6 * 60 + 38, offlineOutcome.sunriseMinutes)
        assertEquals(17 * 60 + 44, offlineOutcome.sunsetMinutes)

        val prefs = prefsRepo.getSnapshot()
        assertEquals(6 * 60 + 38, prefs.sunriseMinutes)
        assertEquals(17 * 60 + 44, prefs.sunsetMinutes)
        assertTrue(prefs.solarLastStatus.contains("caché", ignoreCase = true))
    }

    @Test
    fun `manual override disables auto sync and preserves user values until force sync`() = runTest {
        var callCount = 0
        val fetcher = object : SolarHttpFetcher {
            override fun fetchJson(url: String): String {
                callCount++
                return """
                    {
                      "results": {
                        "date": "2026-11-23",
                        "sunrise": "06:40:00",
                        "sunset": "17:42:00",
                        "timezone": "America/Havana"
                      },
                      "status": "OK"
                    }
                """.trimIndent()
            }
        }
        val service = SunriseSunsetService(prefsRepo, fetcher)
        val havana = ZoneId.of("America/Havana")
        val day = LocalDate.of(2026, 11, 23)

        prefsRepo.setDayNightTimes(sunriseMinutes = 7 * 60, sunsetMinutes = 19 * 60, manualOverride = true)
        assertFalse(prefsRepo.getSnapshot().autoSolarSyncEnabled)

        val skipped = service.syncIfNeeded(havana, today = day, force = false)
        assertTrue(skipped is SolarSyncOutcome.SkippedManualMode)
        assertEquals(0, callCount)
        assertEquals(7 * 60, prefsRepo.getSnapshot().sunriseMinutes)
        assertEquals(19 * 60, prefsRepo.getSnapshot().sunsetMinutes)

        // Explicit force sync updates values and re-enables auto sync
        val forced = service.syncIfNeeded(havana, today = day, force = true)
        assertTrue(forced is SolarSyncOutcome.SyncedFromNetwork)
        assertEquals(1, callCount)
        assertTrue(prefsRepo.getSnapshot().autoSolarSyncEnabled)
        assertEquals(6 * 60 + 40, prefsRepo.getSnapshot().sunriseMinutes)
    }
}
