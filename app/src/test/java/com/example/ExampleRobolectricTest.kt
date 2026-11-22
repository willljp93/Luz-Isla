package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ImportMergeMode
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.Circuit
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: LuzIslaRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        repository = LuzIslaRepository(db, prefsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        assertEquals("Luz Isla", context.getString(R.string.app_name))
    }

    @Test
    fun `seed populates 19 circuits and import merge preserves personal favorites and names`() = runTest {
        AppDatabase.seedIfEmpty(db)
        val initial = repository.getCurrentSnapshot()
        assertEquals(19, initial.circuits.size)

        // User personalizes A210
        val existingA210 = initial.circuits.first { it.code == "A210" }
        repository.upsertCircuit(
            existingA210.copy(
                customName = "Mi Hogar Personal",
                comment = "Calle 24 #102",
                isFavorite = true,
                isPrimaryHome = true
            )
        )

        // Incoming forecast update with blank personal fields + new circuit A990
        val incoming = ScheduleSnapshot(
            circuits = listOf(
                Circuit(code = "A210", customName = "", comment = "", scheduleType = ScheduleType.ROTATING, isFavorite = false, isPrimaryHome = false),
                Circuit(code = "A990", customName = "", comment = "Nuevo circuito", scheduleType = ScheduleType.NONE)
            ),
            groups = initial.groups,
            fixedRules = initial.fixedRules,
            explicitForecasts = initial.explicitForecasts,
            manualExceptions = emptyList(),
            relations = initial.relations
        )

        repository.applyImportedSnapshot(
            incoming = incoming,
            mode = ImportMergeMode.MERGE_KEEP_PERSONAL,
            includePersonalFromIncoming = false
        )

        val updated = repository.getCurrentSnapshot()
        assertEquals(20, updated.circuits.size)
        val a210After = updated.circuits.first { it.code == "A210" }
        assertEquals("Mi Hogar Personal", a210After.customName)
        assertEquals("Calle 24 #102", a210After.comment)
        assertTrue(a210After.isFavorite)
        assertTrue(a210After.isPrimaryHome)
    }
}
