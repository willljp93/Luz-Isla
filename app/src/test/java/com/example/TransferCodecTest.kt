package com.example

import com.example.domain.model.Circuit
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.version.AppVersionConfig
import com.example.transfer.ConfigCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransferCodecTest {

    @Test
    fun `alphanumeric code round-trip preserves circuits and strips personal data when requested`() {
        val snapshot = ScheduleSnapshot(
            circuits = listOf(
                Circuit(
                    code = "A210",
                    customName = "Mi Casa",
                    comment = "Reparto Camilo",
                    scheduleType = ScheduleType.ROTATING,
                    groupId = "g1",
                    isFavorite = true,
                    isPrimaryHome = true
                ),
                Circuit(code = "A030", scheduleType = ScheduleType.FIXED)
            ),
            groups = listOf(ScheduleGroup("g1", "Grupo 1", referenceEpochMillis = 1_700_000_000_000L)),
            fixedRules = emptyList(),
            explicitForecasts = emptyList(),
            manualExceptions = emptyList(),
            relations = emptyList(),
            cycles = listOf(
                RotationCycle(
                    id = "cyc1",
                    name = "Ciclo 4x12",
                    serviceDurationMinutes = 240,
                    outageDurationMinutes = 720,
                    referenceEpochMillis = 1_700_000_000_000L,
                    orderedGroupIds = listOf("g1")
                )
            )
        )

        val payload = ConfigCodec.snapshotToPayload(
            snapshot = snapshot,
            configVersion = 3,
            includePersonalData = false,
            nowIso = "2026-11-22T14:30:00Z"
        )
        val code = ConfigCodec.encodeToAlphanumericCode(payload)
        assertTrue(code.startsWith("LUZ1-"))

        val decoded = ConfigCodec.decodeFromAlphanumericCode(code)
        assertTrue(decoded.sha256Verified)
        assertEquals(3, decoded.payload.configVersion)
        assertEquals(1_795_357_800_000L, ConfigCodec.parseGeneratedAtMillis(decoded.payload.generatedAtIso))
        assertEquals(2, decoded.snapshot.circuits.size)
        assertEquals(1, decoded.snapshot.cycles.size)
        assertEquals("Ciclo 4x12", decoded.snapshot.cycles.first().name)
        val a210 = decoded.snapshot.circuits.first { it.code == "A210" }
        assertEquals("", a210.customName)
        assertEquals("", a210.comment)
        assertFalse(a210.isFavorite)
        assertFalse(a210.isPrimaryHome)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupted alphanumeric code is rejected without altering data`() {
        val snapshot = ScheduleSnapshot(
            circuits = listOf(Circuit("A210", scheduleType = ScheduleType.NONE)),
            groups = emptyList(),
            fixedRules = emptyList(),
            explicitForecasts = emptyList(),
            manualExceptions = emptyList(),
            relations = emptyList()
        )
        val payload = ConfigCodec.snapshotToPayload(snapshot, configVersion = 1, includePersonalData = false)
        val code = ConfigCodec.encodeToAlphanumericCode(payload)
        val chars = code.toCharArray()
        val mid = chars.size / 2
        chars[mid] = if (chars[mid] == 'A') 'B' else 'A'
        ConfigCodec.decodeFromAlphanumericCode(String(chars))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `incompatible code version throws informative error`() {
        ConfigCodec.decodeFromAlphanumericCode("LUZ9-ABCDEFGHJKLMNPQRST")
    }

    @Test
    fun `json export and import preserves cycles, version and summary counts`() {
        val largeCircuits = (1..40).map { i ->
            Circuit(
                code = "A${100 + i}",
                customName = "Circuito Prueba $i",
                comment = "Reparto $i",
                scheduleType = ScheduleType.ROTATING,
                groupId = "g1"
            )
        }
        val snapshot = ScheduleSnapshot(
            circuits = largeCircuits,
            groups = listOf(ScheduleGroup("g1", "Grupo 1", referenceEpochMillis = 1_700_000_000_000L)),
            fixedRules = emptyList(),
            explicitForecasts = emptyList(),
            manualExceptions = emptyList(),
            relations = emptyList(),
            cycles = listOf(
                RotationCycle(
                    id = "cyc_json",
                    name = "Ciclo General",
                    serviceDurationMinutes = 240,
                    outageDurationMinutes = 720,
                    referenceEpochMillis = 1_700_000_000_000L,
                    orderedGroupIds = listOf("g1")
                )
            )
        )
        val payload = ConfigCodec.snapshotToPayload(
            snapshot = snapshot,
            configVersion = 26112301,
            configVersionName = "2026.11.23.1",
            includePersonalData = true,
            sourceName = "Exportación Local",
            nowIso = "2026-11-22T18:00:00Z"
        )
        val jsonText = ConfigCodec.encodeToJson(payload, pretty = true)
        val decoded = ConfigCodec.decodeFromJson(jsonText)
        val summary = ConfigCodec.buildSummary(decoded)

        assertTrue(decoded.sha256Verified)
        assertEquals(26112301, summary.configVersion)
        assertEquals("2026.11.23.1", summary.configVersionName)
        assertEquals("2026.11.23.1", summary.displayVersion)
        assertEquals(40, summary.circuitCount)
        assertEquals(1, summary.cycleCount)
        assertNotNull(ConfigCodec.parseGeneratedAtMillis(summary.generatedAtIso))
    }

    @Test
    fun `AppVersionConfig computes YYYY_MM_DD_N daily exports and build codes accurately`() {
        val nov23 = LocalDate.of(2026, 11, 23)
        val nov24 = LocalDate.of(2026, 11, 24)

        val (firstName, firstBuild) = AppVersionConfig.nextDailyExportVersion(nov23, "", 0)
        assertEquals("2026.11.23.1", firstName)
        assertEquals(1, firstBuild)
        assertEquals(26112301, AppVersionConfig.encodeCompactVersionCode(nov23, firstBuild))

        val (secondName, secondBuild) = AppVersionConfig.nextDailyExportVersion(nov23, "2026-11-23", firstBuild)
        assertEquals("2026.11.23.2", secondName)
        assertEquals(2, secondBuild)
        assertEquals(26112302, AppVersionConfig.encodeCompactVersionCode(nov23, secondBuild))

        val (nextDayName, nextDayBuild) = AppVersionConfig.nextDailyExportVersion(nov24, "2026-11-23", secondBuild)
        assertEquals("2026.11.24.1", nextDayName)
        assertEquals(1, nextDayBuild)
        assertEquals("2026.11.24.1", AppVersionConfig.formatConfigVersionDisplay(26112401))
    }
}

