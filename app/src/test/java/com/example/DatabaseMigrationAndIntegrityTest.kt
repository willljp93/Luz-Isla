package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.Circuit
import com.example.domain.model.CircuitRelation
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.FixedScheduleRule
import com.example.domain.model.IntervalKind
import com.example.domain.model.ManualException
import com.example.domain.model.RelationType
import com.example.domain.model.RotationCycle
import com.example.domain.model.ScheduleGroup
import com.example.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationAndIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun createInMemoryRepository(): Pair<AppDatabase, Pair<LuzIslaRepository, UserPreferencesRepository>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("prefs_${System.nanoTime()}.preferences_pb") }
        )
        val prefsRepo = UserPreferencesRepository(dataStore)
        val repository = LuzIslaRepository(db, prefsRepo)
        return db to (repository to prefsRepo)
    }

    @Test
    fun `migration from v1 to v5 preserves user data and adds rotation_cycles and endDateEpochDay`() {
        val dbName = "migration-v1-v5.db"
        migrationHelper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, phaseOffsetMinutes, isActive)
                VALUES ('grp_user_1', 'Grupo Usuario', 'Desc', 240, 720, 1700000000000, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuits (code, customName, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, displayOrder, createdAt, updatedAt)
                VALUES ('A010', 'Casa Playa', 'Mi comentario', 'grp_user_1', 'ROTATING', 1, 1, 1, 0, 1000, 2000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO manual_exceptions (id, circuitCode, dateEpochDay, startMinuteOfDay, endMinuteOfDay, hasService, reason)
                VALUES ('exc_v1', 'A010', 20700, 60, 180, 1, 'Excepcion v1')
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5
        )

        migratedDb.query("SELECT customName, comment, isFavorite, isPrimaryHome, groupId, scheduleType FROM circuits WHERE code = 'A010'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Casa Playa", cursor.getString(0))
            assertEquals("Mi comentario", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("grp_user_1", cursor.getString(4))
            assertEquals("ROTATING", cursor.getString(5))
        }

        migratedDb.query("SELECT reason, endDateEpochDay FROM manual_exceptions WHERE id = 'exc_v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Excepcion v1", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        migratedDb.close()
    }

    @Test
    fun `migration from v2 to v5 preserves A010 A330 A340 example-like IDs and all user customizations`() {
        val dbName = "migration-v2-v5.db"
        migrationHelper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, phaseOffsetMinutes, isActive)
                VALUES ('grp_ejemplo_1', 'Grupo Personalizado', 'Mantenido por usuario', 240, 720, 1700000000000, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO rotation_cycles (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, orderedGroupIdsCsv, isActive)
                VALUES ('cycle_ejemplo_4_12', 'Ciclo Usuario', 'Personalizado', 240, 720, 1700000000000, 'grp_ejemplo_1', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuits (code, customName, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, displayOrder, createdAt, updatedAt)
                VALUES
                ('A010', 'Mi Casa Principal', 'Avenida 5ta', 'grp_ejemplo_1', 'ROTATING', 1, 1, 1, 0, 100, 500),
                ('A330', 'Trabajo', 'Zona centro', 'grp_ejemplo_1', 'ROTATING', 1, 1, 0, 1, 100, 500),
                ('A340', 'Casa Abuela', 'Sin comentario', NULL, 'CUSTOM', 1, 0, 0, 2, 100, 500),
                ('A080', '', '', 'grp_ejemplo_1', 'ROTATING', 1, 0, 0, 3, 100, 500),
                ('A060', '', '', 'grp_ejemplo_1', 'ROTATING', 1, 0, 0, 4, 100, 500)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuit_relations (id, sourceCircuitCode, targetCircuitCode, relationType, syncRuleEnabled, notes)
                VALUES ('rel_a080_a060', 'A080', 'A060', 'INFORMATIVA', 1, 'Relacion personalizada')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO explicit_forecasts (id, dateEpochDay, startMinuteOfDay, endMinuteOfDay, circuitCodesCsv, intervalKind, notes, isHistoricalTest)
                VALUES ('fc_hist_1', 20700, 300, 600, 'A010,A330', 'SERVICE', 'Guardado por usuario', 1)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5
        )

        migratedDb.query("SELECT code, customName, comment, isFavorite, isPrimaryHome FROM circuits ORDER BY displayOrder ASC").use { cursor ->
            val codes = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val code = cursor.getString(0)
                codes.add(code)
                if (code == "A010") {
                    assertEquals("Mi Casa Principal", cursor.getString(1))
                    assertEquals("Avenida 5ta", cursor.getString(2))
                    assertEquals(1, cursor.getInt(3))
                    assertEquals(1, cursor.getInt(4))
                }
                if (code == "A330") {
                    assertEquals("Trabajo", cursor.getString(1))
                    assertEquals(1, cursor.getInt(3))
                }
                if (code == "A340") {
                    assertEquals("Casa Abuela", cursor.getString(1))
                }
            }
            assertEquals(listOf("A010", "A330", "A340", "A080", "A060"), codes)
        }

        migratedDb.query("SELECT COUNT(*) FROM schedule_groups WHERE id = 'grp_ejemplo_1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM rotation_cycles WHERE id = 'cycle_ejemplo_4_12'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM circuit_relations WHERE id = 'rel_a080_a060'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migratedDb.query("SELECT circuitCodesCsv FROM explicit_forecasts WHERE id = 'fc_hist_1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("A010,A330", it.getString(0))
        }
        migratedDb.close()
    }

    @Test
    fun `migration from v3 to v5 upgrades manual_exceptions cleanly`() {
        val dbName = "migration-v3-v5.db"
        migrationHelper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                INSERT INTO circuits (code, customName, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, displayOrder, createdAt, updatedAt)
                VALUES ('A210', 'Hogar', 'Centro', NULL, 'NONE', 1, 1, 1, 0, 100, 200)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO manual_exceptions (id, circuitCode, dateEpochDay, startMinuteOfDay, endMinuteOfDay, hasService, reason)
                VALUES ('exc_v3', 'A210', 20750, 0, 120, 0, 'Mantenimiento')
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5
        )
        migratedDb.query("SELECT circuitCode, reason, endDateEpochDay FROM manual_exceptions WHERE id = 'exc_v3'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A210", cursor.getString(0))
            assertEquals("Mantenimiento", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        migratedDb.close()
    }

    @Test
    fun `migration from v4 to v5 repairs orphan references and invalid ROTATING circuits while keeping valid user data`() {
        val dbName = "migration-v4-v5.db"
        migrationHelper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, phaseOffsetMinutes, isActive)
                VALUES ('grp_valid', 'Valido', '', 240, 720, 1700000000000, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO rotation_cycles (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, orderedGroupIdsCsv, isActive)
                VALUES ('cyc_1', 'Ciclo', '', 240, 720, 1700000000000, 'grp_valid,grp_deleted', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuits (code, customName, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, displayOrder, createdAt, updatedAt)
                VALUES
                ('A210', 'Principal', 'Válido', 'grp_valid', 'ROTATING', 1, 1, 1, 0, 100, 200),
                ('A050', 'Huérfano Rot', '', 'grp_deleted', 'ROTATING', 1, 0, 0, 1, 100, 200),
                ('A030', 'Fijo con grp viejo', '', 'grp_deleted', 'FIXED', 1, 0, 0, 2, 100, 200)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO fixed_schedule_rules (id, circuitCode, daysOfWeekMask, startMinuteOfDay, endMinuteOfDay, label)
                VALUES
                ('rule_valid', 'A030', 127, 540, 1020, 'Fijo válido'),
                ('rule_orphan', 'GONE99', 127, 0, 120, 'Regla huérfana')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO manual_exceptions (id, circuitCode, dateEpochDay, startMinuteOfDay, endMinuteOfDay, hasService, reason, endDateEpochDay)
                VALUES
                ('exc_valid', 'A210', 20750, 0, 60, 1, 'Ok', NULL),
                ('exc_orphan', 'GONE99', 20750, 0, 60, 0, 'Huérfana', NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuit_relations (id, sourceCircuitCode, targetCircuitCode, relationType, syncRuleEnabled, notes)
                VALUES
                ('rel_valid', 'A210', 'A050', 'RESPALDO', 0, 'Ok'),
                ('rel_orphan_src', 'GONE99', 'A210', 'INFORMATIVA', 0, 'Huérfana 1'),
                ('rel_orphan_dst', 'A210', 'GONE88', 'INFORMATIVA', 0, 'Huérfana 2')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO explicit_forecasts (id, dateEpochDay, startMinuteOfDay, endMinuteOfDay, circuitCodesCsv, intervalKind, notes, isHistoricalTest)
                VALUES
                ('fc_shared', 20750, 60, 120, 'GONE99,A210,GONE88', 'SERVICE', 'Compartido', 0),
                ('fc_only_orphan', 20750, 120, 180, 'GONE99', 'OUTAGE', 'Solo huérfano', 0)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        )

        migratedDb.query("SELECT groupId, scheduleType FROM circuits WHERE code = 'A210'").use {
            assertTrue(it.moveToFirst())
            assertEquals("grp_valid", it.getString(0))
            assertEquals("ROTATING", it.getString(1))
        }
        migratedDb.query("SELECT groupId, scheduleType FROM circuits WHERE code = 'A050'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertEquals("CUSTOM", it.getString(1))
        }
        migratedDb.query("SELECT groupId, scheduleType FROM circuits WHERE code = 'A030'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertEquals("FIXED", it.getString(1))
        }
        migratedDb.query("SELECT id FROM fixed_schedule_rules").use {
            val ids = mutableListOf<String>()
            while (it.moveToNext()) ids.add(it.getString(0))
            assertEquals(listOf("rule_valid"), ids)
        }
        migratedDb.query("SELECT id FROM manual_exceptions").use {
            val ids = mutableListOf<String>()
            while (it.moveToNext()) ids.add(it.getString(0))
            assertEquals(listOf("exc_valid"), ids)
        }
        migratedDb.query("SELECT id FROM circuit_relations").use {
            val ids = mutableListOf<String>()
            while (it.moveToNext()) ids.add(it.getString(0))
            assertEquals(listOf("rel_valid"), ids)
        }
        migratedDb.query("SELECT id, circuitCodesCsv FROM explicit_forecasts").use {
            assertTrue(it.moveToFirst())
            assertEquals("fc_shared", it.getString(0))
            assertEquals("A210", it.getString(1))
            assertFalse(it.moveToNext())
        }
        migratedDb.query("SELECT orderedGroupIdsCsv FROM rotation_cycles WHERE id = 'cyc_1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("grp_valid", it.getString(0))
        }
        migratedDb.close()
    }

    @Test
    fun `deleteCircuit removes rules, exceptions, relations, exclusive forecasts, and widget mappings atomically`() = runTest {
        val (db, repos) = createInMemoryRepository()
        val (repository, prefsRepo) = repos
        try {
            repository.upsertGroup(ScheduleGroup(id = "g1", name = "G1", referenceEpochMillis = 1_700_000_000_000L))
            repository.upsertCircuit(Circuit(code = "A210", groupId = "g1", scheduleType = ScheduleType.ROTATING, isFavorite = true))
            repository.upsertCircuit(Circuit(code = "A060", groupId = "g1", scheduleType = ScheduleType.ROTATING))
            repository.upsertFixedRule(
                FixedScheduleRule(
                    id = "r_210",
                    circuitCode = "A210",
                    startMinuteOfDay = 540,
                    endMinuteOfDay = 1020,
                    label = "Regla A210"
                )
            )
            repository.upsertFixedRule(
                FixedScheduleRule(
                    id = "r_060",
                    circuitCode = "A060",
                    startMinuteOfDay = 480,
                    endMinuteOfDay = 960,
                    label = "Regla A060"
                )
            )
            repository.upsertManualException(
                ManualException(
                    id = "e_210",
                    circuitCode = "A210",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 60,
                    endMinuteOfDay = 120,
                    hasService = true,
                    reason = "Extra"
                )
            )
            repository.upsertManualException(
                ManualException(
                    id = "e_060",
                    circuitCode = "A060",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 60,
                    endMinuteOfDay = 120,
                    hasService = false,
                    reason = "Avería"
                )
            )
            repository.upsertRelation(
                CircuitRelation(
                    id = "rel_src",
                    sourceCircuitCode = "A210",
                    targetCircuitCode = "A060",
                    relationType = RelationType.MISMO_HORARIO
                )
            )
            repository.upsertRelation(
                CircuitRelation(
                    id = "rel_tgt",
                    sourceCircuitCode = "A060",
                    targetCircuitCode = "A210",
                    relationType = RelationType.DEPENDIENTE
                )
            )
            repository.upsertRelation(
                CircuitRelation(
                    id = "rel_other",
                    sourceCircuitCode = "A060",
                    targetCircuitCode = "A060",
                    relationType = RelationType.INFORMATIVA
                )
            )
            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = "fc_only_210",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 60,
                    endMinuteOfDay = 180,
                    circuitCodes = listOf("A210"),
                    intervalKind = IntervalKind.SERVICE
                )
            )
            prefsRepo.setWidgetCircuit(101, "A210")
            prefsRepo.setWidgetCircuit(102, "A060")

            repository.deleteCircuit(" a210 ")

            val snapshot = repository.getCurrentSnapshot()
            assertEquals(listOf("A060"), snapshot.circuits.map { it.code })
            assertEquals(listOf("r_060"), snapshot.fixedRules.map { it.id })
            assertEquals(listOf("e_060"), snapshot.manualExceptions.map { it.id })
            assertEquals(listOf("rel_other"), snapshot.relations.map { it.id })
            assertTrue(snapshot.explicitForecasts.isEmpty())

            val widgetMap = prefsRepo.getSnapshot().widgetCircuitMap
            assertNull(widgetMap[101])
            assertEquals("A060", widgetMap[102])
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleteCircuit removes only target circuit from shared multi-circuit forecasts`() = runTest {
        val (db, repos) = createInMemoryRepository()
        val (repository, _) = repos
        try {
            repository.upsertCircuit(Circuit(code = "A210", scheduleType = ScheduleType.CUSTOM))
            repository.upsertCircuit(Circuit(code = "A060", scheduleType = ScheduleType.CUSTOM))
            repository.upsertCircuit(Circuit(code = "A070", scheduleType = ScheduleType.CUSTOM))

            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = "fc_shared",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 120,
                    endMinuteOfDay = 360,
                    circuitCodes = listOf("A210", "A060", "A070"),
                    intervalKind = IntervalKind.SERVICE,
                    notes = "Pronóstico compartido"
                )
            )

            repository.deleteCircuit("A210")

            val snapshot = repository.getCurrentSnapshot()
            assertEquals(1, snapshot.explicitForecasts.size)
            val remainingForecast = snapshot.explicitForecasts.first()
            assertEquals("fc_shared", remainingForecast.id)
            assertEquals(listOf("A060", "A070"), remainingForecast.circuitCodes)
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleteGroup converts member ROTATING circuits to CUSTOM, cleans cycles, and preserves unrelated groups`() = runTest {
        val (db, repos) = createInMemoryRepository()
        val (repository, _) = repos
        try {
            repository.upsertGroup(ScheduleGroup(id = "grp_1", name = "G1", referenceEpochMillis = 1_700_000_000_000L))
            repository.upsertGroup(ScheduleGroup(id = "grp_2", name = "G2", referenceEpochMillis = 1_700_000_000_000L))
            repository.upsertCycle(
                RotationCycle(
                    id = "cyc_main",
                    name = "Principal",
                    referenceEpochMillis = 1_700_000_000_000L,
                    orderedGroupIds = listOf("grp_1", "grp_2")
                )
            )
            repository.upsertCircuit(Circuit(code = "A100", groupId = "grp_1", scheduleType = ScheduleType.ROTATING))
            repository.upsertCircuit(Circuit(code = "A200", groupId = "grp_1", scheduleType = ScheduleType.FIXED))
            repository.upsertCircuit(Circuit(code = "A300", groupId = "grp_2", scheduleType = ScheduleType.ROTATING))

            val beforeA300UpdatedAt = repository.getCurrentSnapshot().circuits.first { it.code == "A300" }.updatedAt

            repository.deleteGroup("grp_1")

            val snap = repository.getCurrentSnapshot()
            assertEquals(listOf("grp_2"), snap.groups.map { it.id })
            assertEquals(listOf("grp_2"), snap.cycles.first { it.id == "cyc_main" }.orderedGroupIds)

            val byCode = snap.circuits.associateBy { it.code }
            val a100 = byCode.getValue("A100")
            assertNull(a100.groupId)
            assertEquals(ScheduleType.CUSTOM, a100.scheduleType)

            val a200 = byCode.getValue("A200")
            assertNull(a200.groupId)
            assertEquals(ScheduleType.FIXED, a200.scheduleType)

            val a300 = byCode.getValue("A300")
            assertEquals("grp_2", a300.groupId)
            assertEquals(ScheduleType.ROTATING, a300.scheduleType)
            assertEquals(beforeA300UpdatedAt, a300.updatedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun `recreating a deleted circuit does not resurrect previous rules, exceptions, forecasts, or relations`() = runTest {
        val (db, repos) = createInMemoryRepository()
        val (repository, _) = repos
        try {
            AppDatabase.seedIfEmpty(db)

            // Personalizar y añadir datos vinculados a A030 (que inicialmente tenía regla fija semilla)
            repository.upsertManualException(
                ManualException(
                    id = "exc_a030_old",
                    circuitCode = "A030",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 0,
                    endMinuteOfDay = 60,
                    hasService = false,
                    reason = "Vieja excepción"
                )
            )
            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = "fc_a030_old",
                    dateEpochDay = 20750L,
                    startMinuteOfDay = 60,
                    endMinuteOfDay = 120,
                    circuitCodes = listOf("A030"),
                    intervalKind = IntervalKind.OUTAGE
                )
            )
            repository.upsertRelation(
                CircuitRelation(
                    id = "rel_a030_old",
                    sourceCircuitCode = "A030",
                    targetCircuitCode = "A210",
                    relationType = RelationType.INFORMATIVA
                )
            )

            // Borrar el circuito y además eliminar grupo/ciclo semilla para verificar que seedIfEmpty no resucite nada
            repository.deleteCircuit("A030")
            repository.deleteGroup("grp_g1")
            repository.deleteCycle("cycle_12x4")

            // Recrear A030 como circuito limpio
            repository.upsertCircuit(
                Circuit(
                    code = "A030",
                    customName = "A030 Nuevo",
                    scheduleType = ScheduleType.CUSTOM
                )
            )
            val snap = repository.getCurrentSnapshot()

            val recreated = snap.circuits.find { it.code == "A030" }
            assertNotNull(recreated)
            assertEquals("A030 Nuevo", recreated?.customName)
            assertEquals(ScheduleType.CUSTOM, recreated?.scheduleType)
            assertTrue(snap.fixedRules.none { it.circuitCode == "A030" })
            assertTrue(snap.manualExceptions.none { it.circuitCode == "A030" })
            assertTrue(snap.explicitForecasts.none { "A030" in it.circuitCodes })
            assertTrue(snap.relations.none { it.sourceCircuitCode == "A030" || it.targetCircuitCode == "A030" })
            assertFalse(snap.groups.any { it.id == "grp_g1" })
            assertFalse(snap.cycles.any { it.id == "cycle_12x4" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration from v5 to v6 marks pre-existing forecasts and exceptions LEGACY and preserves them on subsequent sync`() = runTest {
        val dbName = "migration-v5-v6.db"
        migrationHelper.createDatabase(dbName, 5).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, phaseOffsetMinutes, isActive)
                VALUES ('grp_pre', 'Grupo Previo', '', 240, 720, 1700000000000, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO rotation_cycles (id, name, description, serviceDurationMinutes, outageDurationMinutes, referenceEpochMillis, orderedGroupIdsCsv, isActive)
                VALUES ('cyc_pre', 'Ciclo Previo', '', 240, 720, 1700000000000, 'grp_pre', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuits (code, customName, comment, groupId, scheduleType, isActive, isFavorite, isPrimaryHome, displayOrder, createdAt, updatedAt)
                VALUES ('A210', 'Mi Hogar v5', 'Nota v5', 'grp_pre', 'ROTATING', 1, 1, 1, 0, 100, 500)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO fixed_schedule_rules (id, circuitCode, daysOfWeekMask, startMinuteOfDay, endMinuteOfDay, label)
                VALUES ('fix_pre', 'A210', 127, 540, 1020, 'Regla previa')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO explicit_forecasts (id, dateEpochDay, startMinuteOfDay, endMinuteOfDay, circuitCodesCsv, intervalKind, notes, isHistoricalTest)
                VALUES ('fc_pre_v5', 20780, 60, 240, 'A210', 'SERVICE', 'Pronóstico previo v5', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO manual_exceptions (id, circuitCode, dateEpochDay, startMinuteOfDay, endMinuteOfDay, hasService, reason, endDateEpochDay)
                VALUES ('exc_pre_v5', 'A210', 20780, 300, 420, 0, 'Excepción previa v5', NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO circuit_relations (id, sourceCircuitCode, targetCircuitCode, relationType, syncRuleEnabled, notes)
                VALUES ('rel_pre_v5', 'A210', 'A210', 'INFORMATIVA', 0, 'Relación previa')
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )
        listOf(
            "circuits" to "code = 'A210'",
            "schedule_groups" to "id = 'grp_pre'",
            "rotation_cycles" to "id = 'cyc_pre'",
            "fixed_schedule_rules" to "id = 'fix_pre'",
            "explicit_forecasts" to "id = 'fc_pre_v5'",
            "manual_exceptions" to "id = 'exc_pre_v5'",
            "circuit_relations" to "id = 'rel_pre_v5'"
        ).forEach { (table, predicate) ->
            migratedDb.query("SELECT dataOrigin FROM `$table` WHERE $predicate").use { cursor ->
                assertTrue("Expected row in $table", cursor.moveToFirst())
                assertEquals("LEGACY", cursor.getString(0))
            }
        }
        migratedDb.close()

        // Abrir con Room sobre la BD migrada y aplicar un merge remoto que NO contiene los IDs previos
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(dbName)
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { tempFolder.newFile("mig_v6_${System.nanoTime()}.preferences_pb") }
            )
            val repo = LuzIslaRepository(roomDb, UserPreferencesRepository(dataStore))
            repo.applyImportedSnapshot(
                incoming = com.example.domain.model.ScheduleSnapshot(
                    circuits = listOf(Circuit(code = "A210", groupId = "grp_pre", scheduleType = ScheduleType.ROTATING)),
                    groups = listOf(ScheduleGroup(id = "grp_pre", name = "Grupo Previo", referenceEpochMillis = 1_700_000_000_000L)),
                    fixedRules = emptyList(),
                    explicitForecasts = listOf(
                        ExplicitForecast(
                            id = "fc_remote_new",
                            dateEpochDay = 20781L,
                            startMinuteOfDay = 0,
                            endMinuteOfDay = 120,
                            circuitCodes = listOf("A210"),
                            intervalKind = IntervalKind.OUTAGE
                        )
                    ),
                    manualExceptions = emptyList(),
                    relations = emptyList(),
                    cycles = emptyList()
                ),
                mode = com.example.data.repository.ImportMergeMode.MERGE_KEEP_PERSONAL,
                includePersonalFromIncoming = false
            )

            val snap = repo.getCurrentSnapshot()
            assertTrue(snap.explicitForecasts.any { it.id == "fc_pre_v5" })
            assertTrue(snap.explicitForecasts.any { it.id == "fc_remote_new" })
            assertTrue(snap.manualExceptions.any { it.id == "exc_pre_v5" })
            assertTrue(snap.fixedRules.any { it.id == "fix_pre" })
            assertTrue(snap.relations.any { it.id == "rel_pre_v5" })
            val a210 = snap.circuits.first { it.code == "A210" }
            assertEquals("Mi Hogar v5", a210.customName)
            assertEquals("Nota v5", a210.comment)
            assertTrue(a210.isFavorite)
            assertTrue(a210.isPrimaryHome)
        } finally {
            roomDb.close()
        }
    }
}
