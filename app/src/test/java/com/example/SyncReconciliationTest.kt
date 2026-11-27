package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordOrigin
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.RotationCycle
import com.example.domain.model.RuleSource
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import com.example.sync.HttpsSyncManager
import com.example.sync.SyncErrorType
import com.example.sync.SyncOutcome
import com.example.transfer.ConfigCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncReconciliationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private data class Harness(
        val db: AppDatabase,
        val storeScope: CoroutineScope,
        val prefsRepo: UserPreferencesRepository,
        val repository: LuzIslaRepository
    ) {
        fun close() {
            storeScope.cancel()
            db.close()
        }
    }

    private fun createHarness(): Harness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tempFolder.newFile("reconcile_${System.nanoTime()}.preferences_pb") }
        )
        val prefsRepo = UserPreferencesRepository(dataStore)
        val repository = LuzIslaRepository(db, prefsRepo)
        return Harness(db, storeScope, prefsRepo, repository)
    }

    private fun clientReturning(jsonProvider: () -> String, statusCode: Int = 200): OkHttpClient =
        HttpsSyncManager.defaultClient()
            .newBuilder()
            .addInterceptor(Interceptor { chain ->
                val bodyText = jsonProvider()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(if (statusCode == 200) "OK" else "Error")
                    .body(bodyText.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

    private fun encodeSnapshot(
        snapshot: ScheduleSnapshot,
        version: Int,
        versionName: String
    ): String {
        val payload = ConfigCodec.snapshotToPayload(
            snapshot = snapshot,
            configVersion = version,
            configVersionName = versionName,
            includePersonalData = false,
            sourceName = "GitHub Comunitario",
            nowIso = "2026-11-27T12:00:00Z"
        )
        return ConfigCodec.encodeToJson(payload, pretty = true)
    }

    private fun baseRemoteSnapshot(
        forecasts: List<ExplicitForecast> = emptyList(),
        relations: List<CircuitRelation> = emptyList(),
        fixedRules: List<FixedScheduleRule> = emptyList(),
        groups: List<ScheduleGroup> = listOf(
            ScheduleGroup(
                id = "grp_g1",
                name = "G1",
                serviceDurationMinutes = 240,
                outageDurationMinutes = 720,
                referenceEpochMillis = LocalDate.of(2026, 11, 22)
                    .atTime(12, 0)
                    .atZone(ScheduleEngine.HAVANA_ZONE)
                    .toInstant()
                    .toEpochMilli()
            )
        ),
        circuits: List<Circuit> = listOf(
            Circuit(code = "A210", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),
            Circuit(code = "A888", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING)
        ),
        cycles: List<RotationCycle> = listOf(
            RotationCycle(
                id = "cyc_12x4",
                name = "Ciclo 12x4",
                referenceEpochMillis = LocalDate.of(2026, 11, 22)
                    .atTime(12, 0)
                    .atZone(ScheduleEngine.HAVANA_ZONE)
                    .toInstant()
                    .toEpochMilli(),
                orderedGroupIds = groups.map { it.id }
            )
        )
    ): ScheduleSnapshot = ScheduleSnapshot(
        circuits = circuits,
        groups = groups,
        fixedRules = fixedRules,
        explicitForecasts = forecasts,
        manualExceptions = emptyList(),
        relations = relations,
        cycles = cycles
    )

    @Test
    fun `1_and_2_remote forecast is added and modified cleanly across syncs`() = runTest {
        val harness = createHarness()
        try {
            val targetDate = LocalDate.of(2026, 11, 28)
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL

            // Sync 1: sin pronósticos todavía
            val v1Json = encodeSnapshot(baseRemoteSnapshot(), version = 26112701, versionName = "2026.11.27.1")
            val m1 = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
            assertTrue(m1.syncFromUrl(syncUrl) is SyncOutcome.Updated)
            assertTrue(harness.repository.getCurrentSnapshot().explicitForecasts.isEmpty())

            // Sync 2: se añade un pronóstico remoto (OUTAGE 08:00-12:00)
            val fc1 = ExplicitForecast(
                id = "fc_rem_1",
                dateEpochDay = targetDate.toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
                circuitCodes = listOf("A210"),
                intervalKind = IntervalKind.OUTAGE,
                notes = "Nota v2"
            )
            val v2Json = encodeSnapshot(baseRemoteSnapshot(forecasts = listOf(fc1)), version = 26112702, versionName = "2026.11.27.2")
            val m2 = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
            assertTrue(m2.syncFromUrl(syncUrl) is SyncOutcome.Updated)

            val snap2 = harness.repository.getCurrentSnapshot()
            assertEquals(1, snap2.explicitForecasts.size)
            assertEquals("Nota v2", snap2.explicitForecasts.first().notes)
            val evalAt10v2 = ScheduleEngine.evaluateCircuit(
                snap2.circuits.first { it.code == "A210" },
                snap2,
                targetDate.atTime(10, 0).atZone(ScheduleEngine.HAVANA_ZONE).toInstant()
            )
            assertEquals(ScheduledState.OUTAGE_SCHEDULED, evalAt10v2.state)
            assertEquals(RuleSource.EXPLICIT_FORECAST, evalAt10v2.ruleSource)

            // Sync 3: se modifica el pronóstico remoto (SERVICE 08:00-14:00)
            val fc1Modified = fc1.copy(
                endMinuteOfDay = 14 * 60,
                intervalKind = IntervalKind.SERVICE,
                notes = "Nota v3 actualizada"
            )
            val v3Json = encodeSnapshot(baseRemoteSnapshot(forecasts = listOf(fc1Modified)), version = 26112703, versionName = "2026.11.27.3")
            val m3 = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v3Json }))
            assertTrue(m3.syncFromUrl(syncUrl) is SyncOutcome.Updated)

            val snap3 = harness.repository.getCurrentSnapshot()
            assertEquals(1, snap3.explicitForecasts.size)
            assertEquals("Nota v3 actualizada", snap3.explicitForecasts.first().notes)
            assertEquals(14 * 60, snap3.explicitForecasts.first().endMinuteOfDay)
            val evalAt10v3 = ScheduleEngine.evaluateCircuit(
                snap3.circuits.first { it.code == "A210" },
                snap3,
                targetDate.atTime(10, 0).atZone(ScheduleEngine.HAVANA_ZONE).toInstant()
            )
            assertEquals(ScheduledState.SERVICE_SCHEDULED, evalAt10v3.state)
            assertEquals(RuleSource.EXPLICIT_FORECAST, evalAt10v3.ruleSource)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `3_obsolete remote forecast disappears from remote and stops affecting ScheduleEngine while local forecast stays`() = runTest {
        val harness = createHarness()
        try {
            val targetDate = LocalDate.of(2026, 11, 28)
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL

            val remoteForecast = ExplicitForecast(
                id = "fc_remote_old",
                dateEpochDay = targetDate.toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
                circuitCodes = listOf("A210"),
                intervalKind = IntervalKind.SERVICE,
                notes = "Pronóstico remoto viejo"
            )
            val v1Json = encodeSnapshot(baseRemoteSnapshot(forecasts = listOf(remoteForecast)), version = 26112701, versionName = "2026.11.27.1")
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            // Usuario crea también un pronóstico local distinto
            val localForecast = ExplicitForecast(
                id = "fc_local_user",
                dateEpochDay = targetDate.plusDays(1).toEpochDay(),
                startMinuteOfDay = 18 * 60,
                endMinuteOfDay = 20 * 60,
                circuitCodes = listOf("A888"),
                intervalKind = IntervalKind.OUTAGE,
                notes = "Pronóstico local"
            )
            harness.repository.upsertExplicitForecast(localForecast)

            // Confirmamos antes del cambio que a las 10:00 A210 aplica EXPLICIT_FORECAST con SERVICE_SCHEDULED
            val snapBefore = harness.repository.getCurrentSnapshot()
            val evalBefore = ScheduleEngine.evaluateCircuit(
                snapBefore.circuits.first { it.code == "A210" },
                snapBefore,
                targetDate.atTime(10, 0).atZone(ScheduleEngine.HAVANA_ZONE).toInstant()
            )
            assertEquals(ScheduledState.SERVICE_SCHEDULED, evalBefore.state)
            assertEquals(RuleSource.EXPLICIT_FORECAST, evalBefore.ruleSource)

            // Sync 2: el pronóstico remoto ya NO aparece en GitHub
            val v2Json = encodeSnapshot(baseRemoteSnapshot(forecasts = emptyList()), version = 26112702, versionName = "2026.11.27.2")
            val outcome = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
                .syncFromUrl(syncUrl)
            assertTrue(outcome is SyncOutcome.Updated)

            val snapAfter = harness.repository.getCurrentSnapshot()
            assertFalse(snapAfter.explicitForecasts.any { it.id == "fc_remote_old" })
            assertTrue(snapAfter.explicitForecasts.any { it.id == "fc_local_user" })

            // El motor vuelve a evaluar según el grupo G1 (ya no está regido por el pronóstico eliminado)
            val evalAfter = ScheduleEngine.evaluateCircuit(
                snapAfter.circuits.first { it.code == "A210" },
                snapAfter,
                targetDate.atTime(10, 0).atZone(ScheduleEngine.HAVANA_ZONE).toInstant()
            )
            assertEquals(RuleSource.ROTATING_GROUP, evalAfter.ruleSource)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `4_removed remote relation disappears while local relation is preserved`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL
            val remoteRelA = CircuitRelation(
                id = "rel_rem_a",
                sourceCircuitCode = "A210",
                targetCircuitCode = "A888",
                relationType = RelationType.MISMO_HORARIO,
                notes = "Remota A"
            )
            val remoteRelB = CircuitRelation(
                id = "rel_rem_b",
                sourceCircuitCode = "A888",
                targetCircuitCode = "A210",
                relationType = RelationType.INFORMATIVA,
                notes = "Remota B"
            )
            val v1Json = encodeSnapshot(
                baseRemoteSnapshot(relations = listOf(remoteRelA, remoteRelB)),
                version = 26112701,
                versionName = "2026.11.27.1"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            val localRel = CircuitRelation(
                id = "rel_local_user",
                sourceCircuitCode = "A210",
                targetCircuitCode = "A888",
                relationType = RelationType.DEPENDIENTE,
                notes = "Relación creada por usuario"
            )
            harness.repository.upsertRelation(localRel)

            // Sync 2 elimina rel_rem_a y conserva rel_rem_b
            val v2Json = encodeSnapshot(
                baseRemoteSnapshot(relations = listOf(remoteRelB)),
                version = 26112702,
                versionName = "2026.11.27.2"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
                .syncFromUrl(syncUrl)

            val relations = harness.repository.getCurrentSnapshot().relations
            assertEquals(setOf("rel_rem_b", "rel_local_user"), relations.map { it.id }.toSet())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `5_local manual exception and locally edited schedule record are preserved on sync`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL
            val targetDate = LocalDate.of(2026, 11, 29)

            // Usuario crea excepción manual local
            harness.repository.upsertCircuit(Circuit(code = "A210", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING))
            val localException = ManualException(
                id = "exc_local_1",
                circuitCode = "A210",
                dateEpochDay = targetDate.toEpochDay(),
                startMinuteOfDay = 0,
                endMinuteOfDay = 6 * 60,
                hasService = true,
                reason = "Planta local de respaldo"
            )
            harness.repository.upsertManualException(localException)

            // Config remota trae una excepción remota propia y regla fija
            val remoteException = ManualException(
                id = "exc_remote_1",
                circuitCode = "A888",
                dateEpochDay = targetDate.toEpochDay(),
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 14 * 60,
                hasService = false,
                reason = "Avería remota"
            )
            val remoteRule = FixedScheduleRule(
                id = "fix_remote_1",
                circuitCode = "A888",
                startMinuteOfDay = 540,
                endMinuteOfDay = 1020,
                label = "Fijo remoto"
            )
            val v1Json = encodeSnapshot(
                baseRemoteSnapshot(
                    fixedRules = listOf(remoteRule)
                ).copy(manualExceptions = listOf(remoteException)),
                version = 26112701,
                versionName = "2026.11.27.1"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            // Usuario edita localmente la regla fija remota (pasa a LOCAL)
            harness.repository.upsertFixedRule(remoteRule.copy(endMinuteOfDay = 1140, label = "Editada por usuario"))

            // Sync 2 elimina exc_remote_1 y cambia fix_remote_1, sin mencionar exc_local_1
            val v2Json = encodeSnapshot(
                baseRemoteSnapshot(
                    fixedRules = listOf(remoteRule.copy(endMinuteOfDay = 900, label = "Intento sobrescritura"))
                ).copy(manualExceptions = emptyList()),
                version = 26112702,
                versionName = "2026.11.27.2"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
                .syncFromUrl(syncUrl)

            val snap = harness.repository.getCurrentSnapshot()
            // Excepción local sigue intacta, excepción remota desaparecida fue borrada
            assertEquals(listOf("exc_local_1"), snap.manualExceptions.map { it.id })
            // Regla editada por el usuario conserva su edición local
            val savedRule = snap.fixedRules.first { it.id == "fix_remote_1" }
            assertEquals("Editada por usuario", savedRule.label)
            assertEquals(1140, savedRule.endMinuteOfDay)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `6_favorites custom names comments and primary home survive remote updates and personal circuit edits`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL

            // Sync inicial remota incluye un circuito remoto sin personalización (C888)
            val v1Snapshot = baseRemoteSnapshot(
                circuits = listOf(
                    Circuit(code = "A210", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING),
                    Circuit(code = "C888", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING)
                )
            )
            val v1Json = encodeSnapshot(v1Snapshot, version = 26112701, versionName = "2026.11.27.1")
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            // Usuario personaliza A210 (sin cambiar groupId/scheduleType) -> conserva origen remoto para programación
            val currentA210 = harness.repository.getCurrentSnapshot().circuits.first { it.code == "A210" }
            harness.repository.upsertCircuit(
                currentA210.copy(
                    customName = "Mi Casa Principal",
                    comment = "Calle 24 #15",
                    isFavorite = true,
                    isPrimaryHome = true
                )
            )
            val entityAfterPersonalEdit = harness.db.circuitDao().getByCode("A210")!!
            assertEquals(RecordOrigin.REMOTE.name, entityAfterPersonalEdit.dataOrigin)

            // Sync 2 cambia grupo de A210 a grp_g2 y borra C888 (sin personalización) + grp_g1 del JSON remoto
            val g2 = ScheduleGroup(id = "grp_g2", name = "G2", referenceEpochMillis = 1_700_000_000_000L)
            val v2Snapshot = baseRemoteSnapshot(
                groups = listOf(g2),
                circuits = listOf(Circuit(code = "A210", groupId = "grp_g2", scheduleType = ScheduleType.ROTATING))
            )
            val v2Json = encodeSnapshot(v2Snapshot, version = 26112702, versionName = "2026.11.27.2")
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
                .syncFromUrl(syncUrl)

            val snap2 = harness.repository.getCurrentSnapshot()
            val a210After = snap2.circuits.first { it.code == "A210" }
            assertEquals("Mi Casa Principal", a210After.customName)
            assertEquals("Calle 24 #15", a210After.comment)
            assertTrue(a210After.isFavorite)
            assertTrue(a210After.isPrimaryHome)
            assertEquals("grp_g2", a210After.groupId)
            // C888 era remoto sin personalización y desapareció -> se eliminó
            assertNull(snap2.circuits.firstOrNull { it.code == "C888" })
            // grp_g1 era remoto y desapareció -> se eliminó (mientras que grp_g2 y demás grupos semilla no adoptados siguen íntegros)
            assertFalse(snap2.groups.any { it.id == "grp_g1" })
            assertTrue(snap2.groups.any { it.id == "grp_g2" })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `7_repeating the exact same sync is idempotent without duplicates or unintended timestamp mutations`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL
            val remoteJson = encodeSnapshot(
                baseRemoteSnapshot(
                    forecasts = listOf(
                        ExplicitForecast(
                            id = "fc_1",
                            dateEpochDay = 20785L,
                            startMinuteOfDay = 120,
                            endMinuteOfDay = 360,
                            circuitCodes = listOf("A210"),
                            intervalKind = IntervalKind.SERVICE
                        )
                    ),
                    relations = listOf(
                        CircuitRelation(
                            id = "rel_1",
                            sourceCircuitCode = "A210",
                            targetCircuitCode = "A888",
                            relationType = RelationType.INFORMATIVA
                        )
                    )
                ),
                version = 26112701,
                versionName = "2026.11.27.1"
            )
            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ remoteJson }))

            val first = manager.syncFromUrl(syncUrl, forceApply = false)
            assertTrue(first is SyncOutcome.Updated)
            val snapshotAfterFirst = harness.repository.getCurrentSnapshot()

            // Repetición automática sin forceApply -> UpToDate
            val secondAuto = manager.syncFromUrl(syncUrl, forceApply = false)
            assertTrue(secondAuto is SyncOutcome.UpToDate)

            // Repetición forzada aplicando el mismo snapshot -> sin duplicados ni cambios en updatedAt
            val thirdForced = manager.syncFromUrl(syncUrl, forceApply = true)
            assertTrue(thirdForced is SyncOutcome.Updated)
            val snapshotAfterForced = harness.repository.getCurrentSnapshot()

            assertEquals(snapshotAfterFirst, snapshotAfterForced)
            assertEquals(19, snapshotAfterForced.circuits.size)
            assertEquals(1, snapshotAfterForced.circuits.count { it.code == "A210" })
            assertEquals(1, snapshotAfterForced.explicitForecasts.size)
            assertEquals(1, snapshotAfterForced.relations.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `8_sync error preserves all previous remote and local data as well as backups`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL
            val v1Snapshot = baseRemoteSnapshot(
                forecasts = listOf(
                    ExplicitForecast(
                        id = "fc_rem_keep",
                        dateEpochDay = 20786L,
                        startMinuteOfDay = 60,
                        endMinuteOfDay = 180,
                        circuitCodes = listOf("A210"),
                        intervalKind = IntervalKind.SERVICE
                    )
                )
            )
            val v1Json = encodeSnapshot(v1Snapshot, version = 26112701, versionName = "2026.11.27.1")
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            harness.repository.upsertManualException(
                ManualException(
                    id = "exc_local_keep",
                    circuitCode = "A210",
                    dateEpochDay = 20786L,
                    startMinuteOfDay = 300,
                    endMinuteOfDay = 420,
                    hasService = false,
                    reason = "Excepción local"
                )
            )
            val beforeSnapshot = harness.repository.getCurrentSnapshot()
            val backupBeforeFailure = harness.repository.getLatestBackup()
            assertNotNull(backupBeforeFailure)

            // Fallo de sincronización por hash SHA-256 manipulado
            val tamperedJson = v1Json.replace("A888", "A999")
            val failOutcome = HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ tamperedJson }))
                .syncFromUrl(syncUrl, forceApply = true)
            assertTrue(failOutcome is SyncOutcome.Failure)
            assertEquals(SyncErrorType.HASH_MISMATCH, (failOutcome as SyncOutcome.Failure).errorType)

            // Todos los datos y el respaldo previo siguen exactamente intactos
            val afterSnapshot = harness.repository.getCurrentSnapshot()
            assertEquals(beforeSnapshot, afterSnapshot)
            val backupAfterFailure = harness.repository.getLatestBackup()
            assertEquals(backupBeforeFailure?.id, backupAfterFailure?.id)
            assertEquals(backupBeforeFailure?.payloadJson, backupAfterFailure?.payloadJson)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `removed remote group cleans up local ROTATING circuit reference safely without dangling groupId`() = runTest {
        val harness = createHarness()
        try {
            val syncUrl = HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL
            val g1 = ScheduleGroup(id = "grp_g1", name = "G1", referenceEpochMillis = 1_700_000_000_000L)
            val g2 = ScheduleGroup(id = "grp_g2", name = "G2", referenceEpochMillis = 1_700_000_000_000L)
            val v1Json = encodeSnapshot(
                baseRemoteSnapshot(groups = listOf(g1, g2)),
                version = 26112701,
                versionName = "2026.11.27.1"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v1Json }))
                .syncFromUrl(syncUrl)

            // Usuario asigna circuito local C900 al grupo remoto grp_g1
            harness.repository.upsertCircuit(
                Circuit(code = "C900", customName = "Mi Circuito", groupId = "grp_g1", scheduleType = ScheduleType.ROTATING)
            )

            // Sync 2 elimina grp_g1 de la config remota
            val v2Json = encodeSnapshot(
                baseRemoteSnapshot(
                    groups = listOf(g2),
                    circuits = listOf(Circuit(code = "A210", groupId = "grp_g2", scheduleType = ScheduleType.ROTATING))
                ),
                version = 26112702,
                versionName = "2026.11.27.2"
            )
            HttpsSyncManager(harness.repository, harness.prefsRepo, clientReturning({ v2Json }))
                .syncFromUrl(syncUrl)

            val snap = harness.repository.getCurrentSnapshot()
            val c900 = snap.circuits.first { it.code == "C900" }
            assertNull(c900.groupId)
            assertEquals(ScheduleType.CUSTOM, c900.scheduleType)
            assertEquals("Mi Circuito", c900.customName)
            assertEquals(listOf("grp_g2"), snap.cycles.first { it.id == "cyc_12x4" }.orderedGroupIds)
        } finally {
            harness.close()
        }
    }
}
