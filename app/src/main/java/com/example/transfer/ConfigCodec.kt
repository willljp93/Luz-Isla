package com.example.transfer

import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.ManualException
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.version.AppVersionConfig
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
    val configVersionName: String = "",
    val generatedAtIso: String = "",
    val sourceName: String = "Luz Isla",
    val includesPersonalData: Boolean = false,
    val circuits: List<Circuit> = emptyList(),
    val groups: List<ScheduleGroup> = emptyList(),
    val fixedRules: List<FixedScheduleRule> = emptyList(),
    val explicitForecasts: List<ExplicitForecast> = emptyList(),
    val manualExceptions: List<ManualException> = emptyList(),
    val relations: List<CircuitRelation> = emptyList(),
    val cycles: List<RotationCycle> = emptyList(),
    val sha256: String = ""
) {
    fun displayVersion(fallbackMillis: Long = 0L): String =
        AppVersionConfig.formatConfigVersionDisplay(
            numericVersion = configVersion,
            versionName = configVersionName,
            fallbackTimestampMillis = fallbackMillis
        )

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
    val configVersionName: String = "",
    val sourceName: String,
    val generatedAtIso: String,
    val includesPersonalData: Boolean,
    val circuitCount: Int,
    val groupCount: Int,
    val cycleCount: Int,
    val fixedRuleCount: Int,
    val forecastCount: Int,
    val exceptionCount: Int,
    val relationCount: Int,
    val sha256Verified: Boolean
) {
    val displayVersion: String
        get() = AppVersionConfig.formatConfigVersionDisplay(
            numericVersion = configVersion,
            versionName = configVersionName,
            fallbackTimestampMillis = ConfigCodec.parseGeneratedAtMillis(generatedAtIso) ?: 0L
        )
}

object ConfigCodec {
    const val CODE_PREFIX = "LUZ1-"
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
        nowIso: String = Instant.now().toString(),
        configVersionName: String = ""
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
            configVersionName = configVersionName,
            generatedAtIso = nowIso,
            sourceName = sourceName,
            includesPersonalData = includePersonalData,
            circuits = sanitizedCircuits,
            groups = snapshot.groups,
            fixedRules = snapshot.fixedRules,
            explicitForecasts = snapshot.explicitForecasts,
            manualExceptions = snapshot.manualExceptions,
            relations = snapshot.relations,
            cycles = snapshot.cycles,
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

    fun decodeFromJson(rawJson: String, verifySha256: Boolean = true): DecodedConfigResult {
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
            val matches = expectedHash.equals(parsed.sha256.trim(), ignoreCase = true)
            if (!matches && verifySha256) {
                throw IllegalArgumentException("Fallo de integridad SHA-256: los datos fueron alterados o están dañados.")
            }
            matches
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
            relations = parsed.relations.distinctBy { it.id },
            cycles = parsed.cycles.distinctBy { it.id }
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

    fun buildSummary(decoded: DecodedConfigResult): ImportSummary {
        val p = decoded.payload
        return ImportSummary(
            configVersion = p.configVersion,
            configVersionName = p.configVersionName,
            sourceName = p.sourceName.ifBlank { "Configuración compartida" },
            generatedAtIso = p.generatedAtIso,
            includesPersonalData = p.includesPersonalData,
            circuitCount = decoded.snapshot.circuits.size,
            groupCount = decoded.snapshot.groups.size,
            cycleCount = decoded.snapshot.cycles.size,
            fixedRuleCount = decoded.snapshot.fixedRules.size,
            forecastCount = decoded.snapshot.explicitForecasts.size,
            exceptionCount = decoded.snapshot.manualExceptions.size,
            relationCount = decoded.snapshot.relations.size,
            sha256Verified = decoded.sha256Verified
        )
    }

    fun parseGeneratedAtMillis(isoString: String): Long? {
        if (isoString.isBlank()) return null
        return runCatching { Instant.parse(isoString.trim()).toEpochMilli() }.getOrNull()
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
