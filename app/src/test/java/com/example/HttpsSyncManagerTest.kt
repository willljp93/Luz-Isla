package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.Circuit
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.sync.HttpsSyncManager
import com.example.sync.SyncErrorType
import com.example.sync.SyncOutcome
import com.example.transfer.ConfigCodec
import com.example.transfer.LuzIslaConfigPayload
import com.example.transfer.Sha256Status
import com.example.ui.screens.settings.SettingsViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
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
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HttpsSyncManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private data class TestHarness(
        val context: Context,
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

    private suspend fun createHarness(): TestHarness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tempFolder.newFile("sync_prefs_${System.nanoTime()}.preferences_pb") }
        )
        val prefsRepo = UserPreferencesRepository(dataStore)
        val repository = LuzIslaRepository(db, prefsRepo)

        // Estado local personalizado inicial que debe preservarse ante fallos y conservar respaldo
        repository.upsertGroup(
            ScheduleGroup(
                id = "grp_local",
                name = "Grupo Local",
                referenceEpochMillis = 1_700_000_000_000L
            )
        )
        repository.upsertCircuit(
            Circuit(
                code = "A210",
                customName = "Mi Casa Local",
                comment = "Comentario local",
                groupId = "grp_local",
                scheduleType = ScheduleType.ROTATING,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        repository.saveBackup(
            reason = "Respaldo existente previo",
            payloadJson = """{"schemaVersion":1,"configVersion":1,"circuits":[{"code":"A210"}]}"""
        )

        return TestHarness(context, db, storeScope, prefsRepo, repository)
    }

    private fun buildClient(interceptor: Interceptor): OkHttpClient =
        HttpsSyncManager.defaultClient()
            .newBuilder()
            .addInterceptor(interceptor)
            .build()

    private fun sampleValidJson(version: Int = 26112701, versionName: String = "2026.11.27.1"): String {
        val snapshot = ScheduleSnapshot(
            circuits = listOf(
                Circuit(code = "A210", groupId = "grp_remote", scheduleType = ScheduleType.ROTATING),
                Circuit(code = "A888", groupId = "grp_remote", scheduleType = ScheduleType.ROTATING)
            ),
            groups = listOf(
                ScheduleGroup(
                    id = "grp_remote",
                    name = "G1 Remoto",
                    referenceEpochMillis = 1_700_000_000_000L
                )
            ),
            fixedRules = emptyList(),
            explicitForecasts = emptyList(),
            manualExceptions = emptyList(),
            relations = emptyList(),
            cycles = emptyList()
        )
        val payload = ConfigCodec.snapshotToPayload(
            snapshot = snapshot,
            configVersion = version,
            configVersionName = versionName,
            includePersonalData = false,
            sourceName = "GitHub Oficial",
            nowIso = "2026-11-27T10:00:00Z"
        )
        return ConfigCodec.encodeToJson(payload, pretty = true)
    }

    private suspend fun assertLocalDataPreserved(harness: TestHarness) {
        val snap = harness.repository.getCurrentSnapshot()
        assertEquals(listOf("A210"), snap.circuits.map { it.code })
        val a210 = snap.circuits.first()
        assertEquals("Mi Casa Local", a210.customName)
        assertEquals("Comentario local", a210.comment)
        assertTrue(a210.isFavorite)
        assertTrue(a210.isPrimaryHome)
        assertEquals("grp_local", a210.groupId)
        assertEquals(listOf("grp_local"), snap.groups.map { it.id })

        val latestBackup = harness.repository.getLatestBackup()
        assertNotNull(latestBackup)
        assertEquals("Respaldo existente previo", latestBackup?.reason)
    }

    @Test
    fun `valid json with SHA-256 succeeds, marks verified, saves pre-sync backup and keeps personal data`() = runTest {
        val harness = createHarness()
        try {
            val validJson = sampleValidJson()
            val client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, client)
            val outcome = manager.syncFromUrl("https://github.com/willljp93/Luz-Isla/blob/main/ConfCircuitos/luz_isla_config.json")

            assertTrue(outcome is SyncOutcome.Updated)
            val updated = outcome as SyncOutcome.Updated
            assertEquals(Sha256Status.VERIFIED, updated.sha256Status)
            assertEquals(2, updated.circuitCount)

            val snap = harness.repository.getCurrentSnapshot()
            assertEquals(setOf("A210", "A888"), snap.circuits.map { it.code }.toSet())
            val a210 = snap.circuits.first { it.code == "A210" }
            assertEquals("Mi Casa Local", a210.customName)
            assertEquals("Comentario local", a210.comment)
            assertTrue(a210.isFavorite)
            assertTrue(a210.isPrimaryHome)

            val backup = harness.repository.getLatestBackup()
            assertNotNull(backup)
            assertTrue(backup!!.reason.contains("Antes de sincronización"))
            assertTrue(harness.prefsRepo.getSnapshot().lastSyncStatus.contains("SHA-256 verificado"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun `valid json without SHA-256 succeeds for compatibility and clearly reports unverified integrity`() = runTest {
        val harness = createHarness()
        try {
            val jsonWithoutHash = """
                {
                  "schemaVersion": 1,
                  "configVersion": 26112702,
                  "configVersionName": "2026.11.27.2",
                  "sourceName": "Edición Manual",
                  "circuits": [
                    { "code": "A210", "scheduleType": "CUSTOM" },
                    { "code": "A060", "scheduleType": "NONE" }
                  ],
                  "sha256": ""
                }
            """.trimIndent()
            val client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonWithoutHash.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, client)
            val outcome = manager.syncFromUrl("https://example.com/luz_isla_config.json")

            assertTrue(outcome is SyncOutcome.Updated)
            val updated = outcome as SyncOutcome.Updated
            assertEquals(Sha256Status.MISSING, updated.sha256Status)

            val prefs = harness.prefsRepo.getSnapshot()
            assertTrue(prefs.lastSyncStatus.contains("sin SHA-256"))
            assertTrue(prefs.activeConfigSource.contains("sin SHA-256"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun `json with mismatched SHA-256 is strictly rejected during sync and preserves local data`() = runTest {
        val harness = createHarness()
        try {
            val tampered = sampleValidJson().replace("A888", "A999")
            val client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(tampered.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, client)
            val outcome = manager.syncFromUrl("https://example.com/tampered.json")

            assertTrue(outcome is SyncOutcome.Failure)
            val failure = outcome as SyncOutcome.Failure
            assertEquals(SyncErrorType.HASH_MISMATCH, failure.errorType)
            assertLocalDataPreserved(harness)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `malformed json is rejected with MALFORMED_JSON and preserves local data`() = runTest {
        val harness = createHarness()
        try {
            val client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{ malformed json ::: ".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, client)
            val outcome = manager.syncFromUrl("https://example.com/broken.json")

            assertTrue(outcome is SyncOutcome.Failure)
            assertEquals(SyncErrorType.MALFORMED_JSON, (outcome as SyncOutcome.Failure).errorType)
            assertLocalDataPreserved(harness)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `incompatible schema and incompatible config are distinguished clearly`() = runTest {
        val harness = createHarness()
        try {
            val wrongSchema = """{"schemaVersion":99,"configVersion":1,"circuits":[{"code":"A210"}]}"""
            val schemaClient = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(wrongSchema.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val manager1 = HttpsSyncManager(harness.repository, harness.prefsRepo, schemaClient)
            val outcome1 = manager1.syncFromUrl("https://example.com/schema99.json")
            assertEquals(SyncErrorType.INCOMPATIBLE_SCHEMA, (outcome1 as SyncOutcome.Failure).errorType)

            val unrelatedJson = """{"message":"GitHub rate limit","documentation_url":"https://docs.github.com"}"""
            val unrelatedClient = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(unrelatedJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val manager2 = HttpsSyncManager(harness.repository, harness.prefsRepo, unrelatedClient)
            val outcome2 = manager2.syncFromUrl("https://example.com/unrelated.json")
            assertEquals(SyncErrorType.INCOMPATIBLE_CONFIG, (outcome2 as SyncOutcome.Failure).errorType)

            assertLocalDataPreserved(harness)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `non-2xx HTTP response fails with HTTP_ERROR and preserves local data`() = runTest {
        val harness = createHarness()
        try {
            val client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("Not Found".toResponseBody("text/plain".toMediaType()))
                    .build()
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, client)
            val outcome = manager.syncFromUrl("https://example.com/missing.json")

            assertTrue(outcome is SyncOutcome.Failure)
            val failure = outcome as SyncOutcome.Failure
            assertEquals(SyncErrorType.HTTP_ERROR, failure.errorType)
            assertTrue(failure.reason.contains("404"))
            assertLocalDataPreserved(harness)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `response larger than 2 MB aborts reading stream early and rejects by declared length too`() = runTest {
        val harness = createHarness()
        try {
            // Caso A: Content-Length declarado > 2 MB -> no debe leer ni 1 byte del stream
            var headerCaseBytesRead = 0L
            val headerLimitClient = buildClient { chain ->
                val trackedBody = object : ResponseBody() {
                    override fun contentType(): MediaType = "application/json".toMediaType()
                    override fun contentLength(): Long = (ConfigCodec.MAX_CONFIG_BYTES + 100).toLong()
                    override fun source(): BufferedSource = object : Source {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            headerCaseBytesRead += byteCount
                            return -1L
                        }
                        override fun timeout(): Timeout = Timeout.NONE
                        override fun close() {}
                    }.buffer()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", (ConfigCodec.MAX_CONFIG_BYTES + 100).toString())
                    .body(trackedBody)
                    .build()
            }

            val managerA = HttpsSyncManager(harness.repository, harness.prefsRepo, headerLimitClient)
            val outcomeA = managerA.syncFromUrl("https://example.com/huge-header.json")
            assertEquals(SyncErrorType.FILE_TOO_LARGE, (outcomeA as SyncOutcome.Failure).errorType)
            assertEquals(0L, headerCaseBytesRead)

            // Caso B: Chunked sin Content-Length de 10 MB -> debe interrumpir apenas supera 2 MB
            var streamedBytesRead = 0L
            val totalSimulatedBytes = 10L * 1024L * 1024L
            val streamingClient = buildClient { chain ->
                val streamingBody = object : ResponseBody() {
                    override fun contentType(): MediaType = "application/json".toMediaType()
                    override fun contentLength(): Long = -1L
                    override fun source(): BufferedSource = object : Source {
                        private var remaining = totalSimulatedBytes
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            if (remaining <= 0L) return -1L
                            val toWrite = minOf(byteCount, 4096L, remaining).toInt()
                            sink.write(ByteArray(toWrite) { 'a'.code.toByte() })
                            remaining -= toWrite
                            streamedBytesRead += toWrite
                            return toWrite.toLong()
                        }
                        override fun timeout(): Timeout = Timeout.NONE
                        override fun close() {}
                    }.buffer()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(streamingBody)
                    .build()
            }

            val managerB = HttpsSyncManager(harness.repository, harness.prefsRepo, streamingClient)
            val outcomeB = managerB.syncFromUrl("https://example.com/huge-stream.json")
            assertEquals(SyncErrorType.FILE_TOO_LARGE, (outcomeB as SyncOutcome.Failure).errorType)
            assertTrue(
                "Expected read to abort right around 2 MB, got $streamedBytesRead bytes",
                streamedBytesRead <= ConfigCodec.MAX_CONFIG_BYTES + 65_536L
            )
            assertLocalDataPreserved(harness)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `redirect to non-HTTPS URL is rejected before processing body and HTTPS redirect succeeds`() = runTest {
        val harness = createHarness()
        try {
            // Caso 1: 302 hacia http:// detenido por followSslRedirects(false)
            val redirect302Client = buildClient { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "http://insecure.example.com/luz_isla_config.json")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            val manager1 = HttpsSyncManager(harness.repository, harness.prefsRepo, redirect302Client)
            val outcome1 = manager1.syncFromUrl("https://example.com/redirect")
            assertEquals(SyncErrorType.INSECURE_URL, (outcome1 as SyncOutcome.Failure).errorType)

            // Caso 2: Cliente con URL final en http:// tras redirección
            val finalHttpClient = buildClient { chain ->
                val downgradedRequest = Request.Builder()
                    .url("http://insecure.example.com/luz_isla_config.json")
                    .build()
                Response.Builder()
                    .request(downgradedRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleValidJson().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val manager2 = HttpsSyncManager(harness.repository, harness.prefsRepo, finalHttpClient)
            val outcome2 = manager2.syncFromUrl("https://example.com/redirect-final")
            assertEquals(SyncErrorType.INSECURE_URL, (outcome2 as SyncOutcome.Failure).errorType)
            assertLocalDataPreserved(harness)

            // Caso 3: Redirección legítima HTTPS -> HTTPS funciona correctamente
            val httpsRedirectClient = buildClient { chain ->
                val finalHttpsRequest = Request.Builder()
                    .url("https://cdn.githubusercontent.com/willljp93/Luz-Isla/main/ConfCircuitos/luz_isla_config.json")
                    .build()
                Response.Builder()
                    .request(finalHttpsRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleValidJson().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val manager3 = HttpsSyncManager(harness.repository, harness.prefsRepo, httpsRedirectClient)
            val outcome3 = manager3.syncFromUrl("https://github.com/willljp93/Luz-Isla/blob/main/ConfCircuitos/luz_isla_config.json")
            assertTrue(outcome3 is SyncOutcome.Updated)
        } finally {
            harness.db.close()
        }
    }

    @Test
    fun `connection error fails with NETWORK_ERROR and preserves existing local data`() = runTest {
        val harness = createHarness()
        try {
            val failingClient = buildClient {
                throw SocketTimeoutException("connect timed out")
            }

            val manager = HttpsSyncManager(harness.repository, harness.prefsRepo, failingClient)
            val outcome = manager.syncFromUrl("https://example.com/timeout.json")

            assertTrue(outcome is SyncOutcome.Failure)
            assertEquals(SyncErrorType.NETWORK_ERROR, (outcome as SyncOutcome.Failure).errorType)
            assertLocalDataPreserved(harness)

            val ioClient = buildClient {
                throw IOException("Network unreachable")
            }
            val outcomeIo = HttpsSyncManager(harness.repository, harness.prefsRepo, ioClient)
                .syncFromUrl("https://example.com/offline.json")
            assertEquals(SyncErrorType.NETWORK_ERROR, (outcomeIo as SyncOutcome.Failure).errorType)
            assertLocalDataPreserved(harness)
        } finally {
            harness.db.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `manual json import requires explicit confirmation when SHA-256 mismatches`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val harness = createHarness()
        val vmStore = ViewModelStore()
        var collectJob: kotlinx.coroutines.Job? = null
        try {
            val viewModel = ViewModelProvider(
                vmStore,
                SettingsViewModel.Factory(harness.context, harness.repository, harness.prefsRepo)
            )[SettingsViewModel::class.java]
            collectJob = backgroundScope.launch(testDispatcher) {
                viewModel.uiState.collect {}
            }
            val tampered = sampleValidJson().replace("A888", "A777")

            // Paso 1: inspección manual detecta discrepancia -> NO abre ImportReviewDialog directamente
            viewModel.inspectManualJsonText(tampered)
            val afterInspect = viewModel.uiState.first { it.pendingModifiedImportWarning != null }
            assertNotNull(afterInspect.pendingModifiedImportWarning)
            assertNull(afterInspect.pendingImport)

            // Paso 2: cancelar descarta advertencia sin modificar datos
            viewModel.dismissModifiedJsonWarning()
            val afterCancel = viewModel.uiState.first {
                it.pendingModifiedImportWarning == null && it.pendingImport == null
            }
            assertNull(afterCancel.pendingModifiedImportWarning)
            assertNull(afterCancel.pendingImport)
            assertLocalDataPreserved(harness)

            // Paso 3: confirmar explícitamente permite revisar con estado MISMATCH_IGNORED
            viewModel.inspectManualJsonText(tampered)
            viewModel.uiState.first { it.pendingModifiedImportWarning != null }
            viewModel.confirmInspectModifiedJsonNow()

            val reviewState = viewModel.uiState.first { it.pendingImport != null }
            val review = reviewState.pendingImport!!
            assertEquals(Sha256Status.MISMATCH_IGNORED, review.summary.sha256Status)
            assertFalse(review.summary.sha256Verified)
        } finally {
            collectJob?.cancel()
            vmStore.clear()
            Dispatchers.resetMain()
            harness.close()
        }
    }
}
