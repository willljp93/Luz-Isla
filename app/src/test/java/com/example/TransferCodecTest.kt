package com.example

import com.example.domain.model.Circuit
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.transfer.ConfigCodec
import com.example.transfer.MultiPartQrCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
            relations = emptyList()
        )

        val payload = ConfigCodec.snapshotToPayload(snapshot, configVersion = 3, includePersonalData = false)
        val code = ConfigCodec.encodeToAlphanumericCode(payload)
        assertTrue(code.startsWith("LUZ1-"))

        val decoded = ConfigCodec.decodeFromAlphanumericCode(code)
        assertTrue(decoded.sha256Verified)
        assertEquals(2, decoded.snapshot.circuits.size)
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
        // Flip a character near the middle
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
    fun `large catalog splits into multi-part QR packets and reassembles cleanly`() {
        val largeCircuits = (1..120).map { i ->
            Circuit(
                code = "A${100 + i}",
                customName = "Circuito Prueba $i",
                comment = "Reparto y descripción extendida número $i para probar partición QR",
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
            relations = emptyList()
        )
        val payload = ConfigCodec.snapshotToPayload(snapshot, configVersion = 5, includePersonalData = true)
        val code = ConfigCodec.encodeToAlphanumericCode(payload)
        val qrParts = ConfigCodec.buildQrPayloads(code)

        assertTrue("Expected multi-part QR for large config", qrParts.size > 1)

        val collector = MultiPartQrCollector()
        var assembled: String? = null
        // Scan in reverse order to prove order independence
        qrParts.reversed().forEach { part ->
            when (val res = collector.acceptScan(part)) {
                is MultiPartQrCollector.ScanProgress.Complete -> assembled = res.fullCode
                is MultiPartQrCollector.ScanProgress.Error -> throw AssertionError(res.message)
                is MultiPartQrCollector.ScanProgress.Partial -> Unit
            }
        }
        assertEquals(code, assembled)
        val decoded = ConfigCodec.decodeFromAlphanumericCode(assembled!!)
        assertEquals(120, decoded.snapshot.circuits.size)
    }
}
