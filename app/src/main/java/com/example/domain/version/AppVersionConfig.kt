package com.example.domain.version

import com.example.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object AppVersionConfig {

    val APP_VERSION_NAME: String
        get() = BuildConfig.VERSION_NAME

    val APP_VERSION_CODE: Int
        get() = BuildConfig.VERSION_CODE

    fun formatDateBuildVersion(date: LocalDate, buildNumber: Int): String {
        val safeBuild = buildNumber.coerceAtLeast(1)
        return String.format(
            Locale.US,
            "%d.%02d.%02d.%d",
            date.year,
            date.monthValue,
            date.dayOfMonth,
            safeBuild
        )
    }

    fun encodeCompactVersionCode(date: LocalDate, buildNumber: Int): Int {
        val yy = (date.year % 100).coerceIn(0, 99)
        val mm = date.monthValue.coerceIn(1, 12)
        val dd = date.dayOfMonth.coerceIn(1, 31)
        val nn = buildNumber.coerceIn(1, 99)
        return (yy * 1_000_000) + (mm * 10_000) + (dd * 100) + nn
    }

    fun formatConfigVersionDisplay(
        numericVersion: Int,
        versionName: String = "",
        fallbackTimestampMillis: Long = 0L,
        zoneId: ZoneId = ZoneId.of("America/Havana")
    ): String {
        val cleanName = versionName.trim().removePrefix("v").removePrefix("V")
        if (cleanName.isNotBlank()) return cleanName

        if (numericVersion in 10_000_000..99_999_999) {
            val yy = numericVersion / 1_000_000
            val mm = (numericVersion / 10_000) % 100
            val dd = (numericVersion / 100) % 100
            val build = numericVersion % 100
            if (mm in 1..12 && dd in 1..31 && build >= 1) {
                return String.format(Locale.US, "20%02d.%02d.%02d.%d", yy, mm, dd, build)
            }
        }

        if (fallbackTimestampMillis > 0L) {
            val date = Instant.ofEpochMilli(fallbackTimestampMillis).atZone(zoneId).toLocalDate()
            return formatDateBuildVersion(date, numericVersion.coerceAtLeast(1))
        }

        return APP_VERSION_NAME
    }

    fun nextDailyExportVersion(
        today: LocalDate,
        lastExportIsoDate: String,
        lastExportDayBuild: Int
    ): Pair<String, Int> {
        val todayIso = today.toString()
        val nextBuild = if (lastExportIsoDate == todayIso) {
            (lastExportDayBuild + 1).coerceAtLeast(1)
        } else {
            1
        }
        return formatDateBuildVersion(today, nextBuild) to nextBuild
    }

    fun nextBuildLabelAfter(currentVersionName: String, forDate: LocalDate): String {
        val parts = currentVersionName.trim().split('.')
        val curYear = parts.getOrNull(0)?.toIntOrNull()
        val curMonth = parts.getOrNull(1)?.toIntOrNull()
        val curDay = parts.getOrNull(2)?.toIntOrNull()
        val curBuild = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val nextBuild = if (curYear == forDate.year && curMonth == forDate.monthValue && curDay == forDate.dayOfMonth) {
            curBuild + 1
        } else {
            1
        }
        return formatDateBuildVersion(forDate, nextBuild)
    }
}
