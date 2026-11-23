package com.example.sync

import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class SunriseSunsetApiResponse(
    val results: SunriseSunsetResults? = null,
    val status: String = ""
)

@Serializable
data class SunriseSunsetResults(
    val date: String? = null,
    val sunrise: String = "",
    val sunset: String = "",
    @SerialName("first_light") val firstLight: String? = null,
    @SerialName("last_light") val lastLight: String? = null,
    val timezone: String? = null,
    @SerialName("utc_offset") val utcOffset: Int? = null
)

data class ParsedSolarTimes(
    val dateStr: String,
    val sunriseMinutes: Int,
    val sunsetMinutes: Int,
    val rawJson: String
)

sealed interface SolarSyncOutcome {
    data class SyncedFromNetwork(val dateStr: String, val sunriseMinutes: Int, val sunsetMinutes: Int) : SolarSyncOutcome
    data class UsedTodayCache(val dateStr: String, val sunriseMinutes: Int, val sunsetMinutes: Int) : SolarSyncOutcome
    data class SkippedManualMode(val sunriseMinutes: Int, val sunsetMinutes: Int) : SolarSyncOutcome
    data class OfflineFallback(val reason: String, val sunriseMinutes: Int, val sunsetMinutes: Int, val usedStaleCache: Boolean) : SolarSyncOutcome
}

interface SolarHttpFetcher {
    fun fetchJson(url: String): String
}

class OkHttpSolarFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : SolarHttpFetcher {
    override fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }
}

class SunriseSunsetService(
    private val prefsRepo: UserPreferencesRepository,
    private val fetcher: SolarHttpFetcher = OkHttpSolarFetcher()
) {
    suspend fun syncIfNeeded(
        zoneId: ZoneId,
        today: LocalDate = LocalDate.now(zoneId),
        force: Boolean = false
    ): SolarSyncOutcome = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getSnapshot()
        if (!force && !prefs.autoSolarSyncEnabled) {
            return@withContext SolarSyncOutcome.SkippedManualMode(
                sunriseMinutes = prefs.sunriseMinutes,
                sunsetMinutes = prefs.sunsetMinutes
            )
        }

        val todayStr = today.toString()
        val hasValidTodayCache = prefs.solarCachedDate == todayStr &&
            prefs.solarCachedLat == DEFAULT_LAT &&
            prefs.solarCachedLng == DEFAULT_LNG &&
            prefs.solarCachedZoneId == zoneId.id &&
            prefs.solarCachedRawJson.isNotBlank()

        if (!force && hasValidTodayCache) {
            if (prefs.sunriseMinutes != prefs.solarCachedSunriseMinutes ||
                prefs.sunsetMinutes != prefs.solarCachedSunsetMinutes
            ) {
                prefsRepo.setAutoSolarSyncEnabled(
                    enabled = true,
                    statusMessage = "Sincronizado Nueva Gerona ($todayStr · caché local)"
                )
            }
            return@withContext SolarSyncOutcome.UsedTodayCache(
                dateStr = todayStr,
                sunriseMinutes = prefs.solarCachedSunriseMinutes,
                sunsetMinutes = prefs.solarCachedSunsetMinutes
            )
        }

        val url = buildRequestUrl(
            lat = DEFAULT_LAT,
            lng = DEFAULT_LNG,
            date = today,
            zoneId = zoneId
        )

        try {
            val rawJson = fetcher.fetchJson(url)
            val parsed = parseResponseJson(
                rawJson = rawJson,
                fallbackDate = today,
                targetZoneId = zoneId
            ) ?: throw IllegalStateException("Respuesta solar inválida")

            prefsRepo.saveSolarSyncSuccess(
                dateStr = parsed.dateStr,
                lat = DEFAULT_LAT,
                lng = DEFAULT_LNG,
                zoneId = zoneId.id,
                sunriseMinutes = parsed.sunriseMinutes,
                sunsetMinutes = parsed.sunsetMinutes,
                rawJson = parsed.rawJson,
                syncEpochMillis = System.currentTimeMillis(),
                enableAutoMode = true,
                statusMessage = "Sincronizado Nueva Gerona (${parsed.dateStr})"
            )

            SolarSyncOutcome.SyncedFromNetwork(
                dateStr = parsed.dateStr,
                sunriseMinutes = parsed.sunriseMinutes,
                sunsetMinutes = parsed.sunsetMinutes
            )
        } catch (e: Exception) {
            val hasStaleCache = prefs.solarCachedRawJson.isNotBlank() && prefs.solarCachedDate.isNotBlank()
            val fallbackSunrise = if (prefs.autoSolarSyncEnabled && hasStaleCache) {
                prefs.solarCachedSunriseMinutes
            } else {
                prefs.sunriseMinutes
            }
            val fallbackSunset = if (prefs.autoSolarSyncEnabled && hasStaleCache) {
                prefs.solarCachedSunsetMinutes
            } else {
                prefs.sunsetMinutes
            }

            val reason = if (hasStaleCache) {
                "Sin conexión; usando caché solar (${prefs.solarCachedDate})"
            } else {
                "Sin conexión; usando horas locales configuradas"
            }

            if (force) {
                // Re-enable auto mode if user explicitly pressed Sync Now, without wiping stored fallback hours
                prefsRepo.setAutoSolarSyncEnabled(true, statusMessage = reason)
            } else {
                prefsRepo.recordSolarStatusSilently(reason)
            }

            SolarSyncOutcome.OfflineFallback(
                reason = reason,
                sunriseMinutes = fallbackSunrise,
                sunsetMinutes = fallbackSunset,
                usedStaleCache = hasStaleCache
            )
        }
    }

    companion object {
        const val BASE_ENDPOINT = "https://api.sunrisesunset.io/json"
        const val ATTRIBUTION_URL = "https://sunrisesunset.io/"
        const val DEFAULT_LAT = "21.8867"
        const val DEFAULT_LNG = "-82.8056"
        const val DEFAULT_LOCATION_LABEL = "Nueva Gerona (21.8867, -82.8056)"

        private val json = Json { ignoreUnknownKeys = true }

        fun buildRequestUrl(
            lat: String = DEFAULT_LAT,
            lng: String = DEFAULT_LNG,
            date: LocalDate,
            zoneId: ZoneId
        ): String {
            val encodedTz = URLEncoder.encode(zoneId.id, "UTF-8")
            return "$BASE_ENDPOINT?lat=$lat&lng=$lng&date=$date&timezone=$encodedTz&time_format=24"
        }

        fun parseResponseJson(
            rawJson: String,
            fallbackDate: LocalDate,
            targetZoneId: ZoneId
        ): ParsedSolarTimes? {
            if (rawJson.isBlank()) return null
            val response = runCatching { json.decodeFromString<SunriseSunsetApiResponse>(rawJson) }
                .getOrNull() ?: return null
            if (!response.status.equals("OK", ignoreCase = true)) return null
            val res = response.results ?: return null

            val date = res.date?.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it.trim()) }.getOrNull()
            } ?: fallbackDate

            val sourceZone = res.timezone?.takeIf { it.isNotBlank() }?.let { tz ->
                runCatching { ZoneId.of(tz.trim()) }.getOrNull()
            } ?: targetZoneId

            val srLocal = parseApiLocalTime(res.sunrise) ?: return null
            val ssLocal = parseApiLocalTime(res.sunset) ?: return null

            val srTarget = convertTimeBetweenZones(date, srLocal, sourceZone, targetZoneId)
            val ssTarget = convertTimeBetweenZones(date, ssLocal, sourceZone, targetZoneId)

            return ParsedSolarTimes(
                dateStr = date.toString(),
                sunriseMinutes = (srTarget.hour * 60 + srTarget.minute).coerceIn(0, 1439),
                sunsetMinutes = (ssTarget.hour * 60 + ssTarget.minute).coerceIn(0, 1439),
                rawJson = rawJson
            )
        }

        fun parseApiLocalTime(raw: String): LocalTime? {
            val cleaned = raw.trim().uppercase(Locale.US)
            if (cleaned.isEmpty()) return null

            val isPm = cleaned.endsWith("PM")
            val isAm = cleaned.endsWith("AM")
            val timePart = when {
                isPm || isAm -> cleaned.removeSuffix("PM").removeSuffix("AM").trim()
                else -> cleaned
            }

            val parts = timePart.split(":")
            if (parts.size < 2) return null
            var hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            val second = parts.getOrNull(2)?.toIntOrNull() ?: 0

            if (minute !in 0..59 || second !in 0..59) return null

            if (isAm || isPm) {
                if (hour !in 1..12) return null
                hour = when {
                    isAm && hour == 12 -> 0
                    isPm && hour < 12 -> hour + 12
                    else -> hour
                }
            } else {
                if (hour !in 0..23) return null
            }

            return LocalTime.of(hour, minute, second)
        }

        private fun convertTimeBetweenZones(
            date: LocalDate,
            localTime: LocalTime,
            sourceZone: ZoneId,
            targetZone: ZoneId
        ): LocalTime {
            if (sourceZone == targetZone) return localTime
            val sourceZdt = ZonedDateTime.of(date, localTime, sourceZone)
            return sourceZdt.withZoneSameInstant(targetZone).toLocalTime()
        }
    }
}
