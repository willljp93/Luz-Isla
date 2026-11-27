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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class Sha256Status {
    VERIFIED,
    MISSING,
    MISMATCH_IGNORED
}

sealed class ConfigCodecException(message: String) : IllegalArgumentException(message) {
    class MalformedJsonException(message: String) : ConfigCodecException(message)
    class IncompatibleSchemaException(message: String) : ConfigCodecException(message)
    class HashMismatchException(message: String) : ConfigCodecException(message)
    class IncompatibleConfigException(message: String) : ConfigCodecException(message)
    class PayloadTooLargeException(message: String) : ConfigCodecException(message)
}

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
    val sha256Verified: Boolean,
    val sha256Status: Sha256Status = if (sha256Verified) Sha256Status.VERIFIED else Sha256Status.MISSING
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
    val sha256Verified: Boolean,
    val sha256Status: Sha256Status = if (sha256Verified) Sha256Status.VERIFIED else Sha256Status.MISSING
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
    const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = MAX_CONFIG_BYTES

    private val COMPATIBLE_ROOT_KEYS = setOf(
        "circuits",
        "groups",
        "cycles",
        "fixedRules",
        "explicitForecasts",
        "manualExceptions",
        "relations"
    )

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
        if (trimmed.isEmpty()) {
            throw ConfigCodecException.MalformedJsonException("El archivo JSON está vacío.")
        }

        val rootElement = runCatching { json.parseToJsonElement(trimmed) }
            .getOrElse {
                throw ConfigCodecException.MalformedJsonException(
                    "Formato JSON inválido o malformado: ${it.message}"
                )
            }
        if (rootElement !is JsonObject) {
            throw ConfigCodecException.MalformedJsonException(
                "El archivo JSON debe contener un objeto de configuración en la raíz."
            )
        }

        val parsed = runCatching { json.decodeFromJsonElement<LuzIslaConfigPayload>(rootElement) }
            .getOrElse {
                throw ConfigCodecException.MalformedJsonException(
                    "Formato JSON inválido o tipos de datos incorrectos: ${it.message}"
                )
            }

        if (parsed.schemaVersion != LuzIslaConfigPayload.CURRENT_SCHEMA_VERSION) {
            throw ConfigCodecException.IncompatibleSchemaException(
                "Versión de esquema incompatible: ${parsed.schemaVersion}. Se requiere versión ${LuzIslaConfigPayload.CURRENT_SCHEMA_VERSION}."
            )
        }

        val hasCompatibleSection = COMPATIBLE_ROOT_KEYS.any { it in rootElement }
        if (!hasCompatibleSection || parsed.configVersion < 1) {
            throw ConfigCodecException.IncompatibleConfigException(
                "Configuración no compatible con Luz Isla: el archivo no contiene secciones válidas de horarios o la versión es inválida."
            )
        }

        val shaStatus = if (parsed.sha256.isNotBlank()) {
            val canonicalUnsigned = json.encodeToString(parsed.copy(sha256 = ""))
            val expectedHash = sha256Hex(canonicalUnsigned.toByteArray(Charsets.UTF_8))
            val matches = expectedHash.equals(parsed.sha256.trim(), ignoreCase = true)
            if (!matches && verifySha256) {
                throw ConfigCodecException.HashMismatchException(
                    "Fallo de integridad SHA-256: el hash del archivo no coincide con su contenido (fue alterado o está dañado)."
                )
            }
            if (matches) Sha256Status.VERIFIED else Sha256Status.MISMATCH_IGNORED
        } else {
            Sha256Status.MISSING
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
            sha256Verified = shaStatus == Sha256Status.VERIFIED,
            sha256Status = shaStatus
        )
    }

    fun readBytesWithLimit(
        input: InputStream,
        maxBytes: Int = MAX_CONFIG_BYTES,
        expectedLengthHint: Long = -1L
    ): ByteArray {
        val initialCapacity = expectedLengthHint
            .takeIf { it in 1L..maxBytes.toLong() }
            ?.toInt()
            ?: 8192
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(8192)
        var totalRead = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes.toLong()) {
                throw ConfigCodecException.PayloadTooLargeException(
                    "El archivo supera el límite seguro de ${maxBytes / (1024 * 1024)} MB."
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
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
        if (trimmed.isEmpty()) {
            throw ConfigCodecException.MalformedJsonException("El código está vacío.")
        }
        val normalized = trimmed.replace("\\s+".toRegex(), "")
        if (!normalized.startsWith(CODE_PREFIX, ignoreCase = true)) {
            val maybeVersion = normalized.take(4)
            if (maybeVersion.startsWith("LUZ", ignoreCase = true)) {
                throw ConfigCodecException.IncompatibleSchemaException(
                    "Versión de código incompatible ($maybeVersion). Actualiza la aplicación."
                )
            }
            throw ConfigCodecException.IncompatibleConfigException(
                "Formato de código no reconocido. Debe comenzar con $CODE_PREFIX"
            )
        }

        val body = normalized.substring(CODE_PREFIX.length)
        if (body.length < 8) {
            throw ConfigCodecException.MalformedJsonException("El código está incompleto o truncado.")
        }

        val envelope = CrockfordBase32.decode(body)
        if (envelope.size <= 4) {
            throw ConfigCodecException.MalformedJsonException("El código es demasiado corto o está dañado.")
        }

        val embeddedCheck = envelope.copyOfRange(0, 4)
        val compressed = envelope.copyOfRange(4, envelope.size)
        val jsonBytes = inflate(compressed)

        val computedCheck = sha256Bytes(jsonBytes).copyOfRange(0, 4)
        if (!embeddedCheck.contentEquals(computedCheck)) {
            throw ConfigCodecException.HashMismatchException(
                "Error de integridad: el código está incompleto o contiene caracteres alterados."
            )
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
            sha256Verified = decoded.sha256Verified,
            sha256Status = decoded.sha256Status
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
                        throw ConfigCodecException.MalformedJsonException("Datos comprimidos incompletos.")
                    }
                    break
                }
                totalRead += count
                if (totalRead > MAX_DECOMPRESSED_BYTES) {
                    throw ConfigCodecException.PayloadTooLargeException(
                        "El paquete supera el tamaño máximo permitido (2 MB)."
                    )
                }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        } catch (e: ConfigCodecException) {
            throw e
        } catch (e: Exception) {
            throw ConfigCodecException.MalformedJsonException("No se pudo descomprimir el código: ${e.message}")
        } finally {
            inflater.end()
        }
    }

    fun sha256Bytes(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String =
        sha256Bytes(data).joinToString("") { "%02x".format(it) }
}
