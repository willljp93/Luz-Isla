package com.example.transfer

import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.Inflater

@Serializable
data class LuzIslaConfigPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val configVersion: Int = 1,
    val generatedAtIso: String = "",
    val sourceName: String = "Luz Isla",
    val includesPersonalData: Boolean = false,
    val circuits: List<Circuit> = emptyList(),
    val groups: List<ScheduleGroup> = emptyList(),
    val fixedRules: List<FixedScheduleRule> = emptyList(),
    val explicitForecasts: List<ExplicitForecast> = emptyList(),
    val manualExceptions: List<ManualException> = emptyList(),
    val relations: List<CircuitRelation> = emptyList(),
    val sha256: String = ""
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class DecodedConfigResult(
    val payload: LuzIslaConfigPayload,
    val snapshot: ScheduleSnapshot,
    val sha256Verified: Boolean
)

data class ImportSummary(
    val configVersion: Int,
    val sourceName: String,
    val generatedAtIso: String,
    val includesPersonalData: Boolean,
    val circuitCount: Int,
    val groupCount: Int,
    val fixedRuleCount: Int,
    val forecastCount: Int,
    val exceptionCount: Int,
    val relationCount: Int,
    val sha256Verified: Boolean
)

data class QrPartPacket(
    val sessionId: String,
    val partIndex: Int,
    val totalParts: Int,
    val sha8: String,
    val chunk: String
)

object ConfigCodec {
    const val CODE_PREFIX = "LUZ1-"
    const val QR_PART_PREFIX = "LUZ1P|"
    const val SINGLE_QR_MAX_CHARS = 900
    private const val QR_CHUNK_CHARS = 700
    private const val MAX_DECOMPRESSED_BYTES = 2 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun snapshotToPayload(
        snapshot: ScheduleSnapshot,
        configVersion: Int,
        includePersonalData: Boolean,
        sourceName: String = "Luz Isla",
        nowIso: String = Instant.now().toString()
    ): LuzIslaConfigPayload {
        val sanitizedCircuits = snapshot.circuits.map { circuit ->
            if (includePersonalData) {
                circuit
            } else {
                circuit.copy(
                    customName = "",
                    comment = "",
                    isFavorite = false,
                    isPrimaryHome = false,
                    displayOrder = 0
                )
            }
        }
        val unsigned = LuzIslaConfigPayload(
            schemaVersion = LuzIslaConfigPayload.CURRENT_SCHEMA_VERSION,
            configVersion = configVersion,
            generatedAtIso = nowIso,
            sourceName = sourceName,
            includesPersonalData = includePersonalData,
            circuits = sanitizedCircuits,
            groups = snapshot.groups,
            fixedRules = snapshot.fixedRules,
            explicitForecasts = snapshot.explicitForecasts,
            manualExceptions = snapshot.manualExceptions,
            relations = snapshot.relations,
            sha256 = ""
        )
        val canonical = json.encodeToString(unsigned)
        val digest = sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        return unsigned.copy(sha256 = digest)
    }

    fun encodeToJson(payload: LuzIslaConfigPayload, pretty: Boolean = true): String {
        val normalized = if (payload.sha256.isBlank()) {
            val unsignedJson = json.encodeToString(payload.copy(sha256 = ""))
            payload.copy(sha256 = sha256Hex(unsignedJson.toByteArray(Charsets.UTF_8)))
        } else {
            payload
        }
        return if (pretty) prettyJson.encodeToString(normalized) else json.encodeToString(normalized)
    }

    fun decodeFromJson(rawJson: String): DecodedConfigResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("El archivo JSON está vacío.")

        val parsed = runCatching { json.decodeFromString<LuzIslaConfigPayload>(trimmed) }
            .getOrElse { throw IllegalArgumentException("Formato JSON inválido: ${it.message}") }

        if (parsed.schemaVersion != LuzIslaConfigPayload.CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "Versión de esquema incompatible: ${parsed.schemaVersion}. Se requiere versión ${LuzIslaConfigPayload.CURRENT_SCHEMA_VERSION}."
            )
        }

        val shaVerified = if (parsed.sha256.isNotBlank()) {
            val canonicalUnsigned = json.encodeToString(parsed.copy(sha256 = ""))
            val expectedHash = sha256Hex(canonicalUnsigned.toByteArray(Charsets.UTF_8))
            if (!expectedHash.equals(parsed.sha256.trim(), ignoreCase = true)) {
                throw IllegalArgumentException("Fallo de integridad SHA-256: los datos fueron alterados o están dañados.")
            }
            true
        } else {
            false
        }

        val deduplicatedCircuits = parsed.circuits
            .filter { it.code.isNotBlank() }
            .distinctBy { it.code.trim().uppercase() }
            .map { it.copy(code = it.code.trim().uppercase()) }

        val snapshot = ScheduleSnapshot(
            circuits = deduplicatedCircuits,
            groups = parsed.groups.distinctBy { it.id },
            fixedRules = parsed.fixedRules.distinctBy { it.id },
            explicitForecasts = parsed.explicitForecasts.distinctBy { it.id },
            manualExceptions = parsed.manualExceptions.distinctBy { it.id },
            relations = parsed.relations.distinctBy { it.id }
        )
        return DecodedConfigResult(
            payload = parsed.copy(circuits = deduplicatedCircuits),
            snapshot = snapshot,
            sha256Verified = shaVerified
        )
    }

    fun encodeToAlphanumericCode(payload: LuzIslaConfigPayload): String {
        val compactJson = encodeToJson(payload, pretty = false)
        val jsonBytes = compactJson.toByteArray(Charsets.UTF_8)
        val fullHash = sha256Bytes(jsonBytes)
        val compressed = deflate(jsonBytes)

        // Envelope: [4-byte SHA-256 prefix] + [Deflate compressed JSON]
        val envelope = ByteArray(4 + compressed.size)
        System.arraycopy(fullHash, 0, envelope, 0, 4)
        System.arraycopy(compressed, 0, envelope, 4, compressed.size)

        val encoded = CrockfordBase32.encode(envelope)
        return "$CODE_PREFIX$encoded"
    }

    fun decodeFromAlphanumericCode(codeInput: String): DecodedConfigResult {
        val trimmed = codeInput.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("El código está vacío.")
        val normalized = trimmed.replace("\\s+".toRegex(), "")
        if (!normalized.startsWith(CODE_PREFIX, ignoreCase = true)) {
            val maybeVersion = normalized.take(4)
            if (maybeVersion.startsWith("LUZ", ignoreCase = true)) {
                throw IllegalArgumentException("Versión de código incompatible ($maybeVersion). Actualiza la aplicación.")
            }
            throw IllegalArgumentException("Formato de código no reconocido. Debe comenzar con $CODE_PREFIX")
        }

        val body = normalized.substring(CODE_PREFIX.length)
        if (body.length < 8) {
            throw IllegalArgumentException("El código está incompleto o truncado.")
        }

        val envelope = CrockfordBase32.decode(body)
        if (envelope.size <= 4) {
            throw IllegalArgumentException("El código es demasiado corto o está dañado.")
        }

        val embeddedCheck = envelope.copyOfRange(0, 4)
        val compressed = envelope.copyOfRange(4, envelope.size)
        val jsonBytes = inflate(compressed)

        val computedCheck = sha256Bytes(jsonBytes).copyOfRange(0, 4)
        if (!embeddedCheck.contentEquals(computedCheck)) {
            throw IllegalArgumentException("Error de integridad: el código está incompleto o contiene caracteres alterados.")
        }

        return decodeFromJson(String(jsonBytes, Charsets.UTF_8))
    }

    fun buildQrPayloads(alphanumericCode: String): List<String> {
        if (alphanumericCode.length <= SINGLE_QR_MAX_CHARS) {
            return listOf(alphanumericCode)
        }
        val sha8 = sha256Hex(alphanumericCode.toByteArray(Charsets.UTF_8)).take(8).uppercase()
        val sessionId = sha8.take(4)
        val chunks = alphanumericCode.chunked(QR_CHUNK_CHARS)
        val total = chunks.size
        return chunks.mapIndexed { idx, chunk ->
            "$QR_PART_PREFIX$sessionId|${idx + 1}|$total|$sha8|$chunk"
        }
    }

    fun parseQrPart(raw: String): QrPartPacket? {
        if (!raw.startsWith(QR_PART_PREFIX)) return null
        val parts = raw.split("|", limit = 6)
        if (parts.size != 6) return null
        val idx = parts[2].toIntOrNull() ?: return null
        val total = parts[3].toIntOrNull() ?: return null
        if (idx < 1 || total < 1 || idx > total) return null
        return QrPartPacket(
            sessionId = parts[1],
            partIndex = idx,
            totalParts = total,
            sha8 = parts[4],
            chunk = parts[5]
        )
    }

    fun buildSummary(decoded: DecodedConfigResult): ImportSummary {
        val p = decoded.payload
        return ImportSummary(
            configVersion = p.configVersion,
            sourceName = p.sourceName.ifBlank { "Configuración compartida" },
            generatedAtIso = p.generatedAtIso,
            includesPersonalData = p.includesPersonalData,
            circuitCount = decoded.snapshot.circuits.size,
            groupCount = decoded.snapshot.groups.size,
            fixedRuleCount = decoded.snapshot.fixedRules.size,
            forecastCount = decoded.snapshot.explicitForecasts.size,
            exceptionCount = decoded.snapshot.manualExceptions.size,
            relationCount = decoded.snapshot.relations.size,
            sha256Verified = decoded.sha256Verified
        )
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            var totalRead = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw IllegalArgumentException("Datos comprimidos incompletos.")
                    }
                    break
                }
                totalRead += count
                if (totalRead > MAX_DECOMPRESSED_BYTES) {
                    throw IllegalArgumentException("El paquete supera el tamaño máximo permitido (2 MB).")
                }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("No se pudo descomprimir el código: ${e.message}")
        } finally {
            inflater.end()
        }
    }

    fun sha256Bytes(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String =
        sha256Bytes(data).joinToString("") { "%02x".format(it) }
}

class MultiPartQrCollector {
    private var activeSessionId: String? = null
    private var expectedTotal: Int = 0
    private var expectedSha8: String = ""
    private val chunks = linkedMapOf<Int, String>()

    sealed interface ScanProgress {
        data class Complete(val fullCode: String) : ScanProgress
        data class Partial(val collected: Int, val total: Int, val missingIndices: List<Int>) : ScanProgress
        data class Error(val message: String) : ScanProgress
    }

    fun reset() {
        activeSessionId = null
        expectedTotal = 0
        expectedSha8 = ""
        chunks.clear()
    }

    fun acceptScan(rawText: String): ScanProgress {
        val trimmed = rawText.trim()
        if (trimmed.startsWith(ConfigCodec.CODE_PREFIX, ignoreCase = true)) {
            return ScanProgress.Complete(trimmed)
        }
        val packet = ConfigCodec.parseQrPart(trimmed)
            ?: return ScanProgress.Error("El código QR escaneado no pertenece a Luz Isla.")

        if (activeSessionId == null) {
            activeSessionId = packet.sessionId
            expectedTotal = packet.totalParts
            expectedSha8 = packet.sha8
        } else if (activeSessionId != packet.sessionId || expectedTotal != packet.totalParts || expectedSha8 != packet.sha8) {
            return ScanProgress.Error("Este fragmento pertenece a otra exportación diferente. Reinicia el escáner si deseas cambiar.")
        }

        chunks[packet.partIndex] = packet.chunk
        if (chunks.size == expectedTotal) {
            val assembled = (1..expectedTotal).joinToString("") { chunks[it].orEmpty() }
            val actualSha8 = ConfigCodec.sha256Hex(assembled.toByteArray(Charsets.UTF_8)).take(8).uppercase()
            if (actualSha8 != expectedSha8) {
                reset()
                return ScanProgress.Error("Error de integridad al unir los fragmentos QR.")
            }
            return ScanProgress.Complete(assembled)
        }

        val missing = (1..expectedTotal).filter { it !in chunks.keys }
        return ScanProgress.Partial(chunks.size, expectedTotal, missing)
    }
}
