package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.Circuit
import com.example.domain.model.ExplicitForecast
import com.example.domain.model.IntervalKind
import com.example.domain.model.ScheduleSnapshot
import com.example.domain.model.ScheduleType
import com.example.domain.model.ScheduledState
import com.example.notifications.BootAndTimeChangeReceiver
import com.example.notifications.NotificationMaintenanceWorker
import com.example.notifications.ScheduleNotificationManager
import com.example.notifications.ScheduleNotificationReceiver
import com.example.sync.HttpsSyncManager
import com.example.sync.SyncOutcome
import com.example.transfer.ConfigCodec
import com.example.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationContinuityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: LuzIslaRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    private val havanaZone: ZoneId = ScheduleEngine.HAVANA_ZONE
    private val baseDate: LocalDate = LocalDate.of(2026, 12, 1)
    private val baseNowMillis: Long = baseDate
        .atTime(8, 0)
        .atZone(havanaZone)
        .toInstant()
        .toEpochMilli()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tempFolder.newFile("notif_prefs_${System.nanoTime()}.preferences_pb") }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
        repository = LuzIslaRepository(db, prefsRepo)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ScheduleNotificationManager.setTestHooks(repository, prefsRepo)
    }

    @After
    fun tearDown() {
        ScheduleNotificationReceiver.resetTestOverrides()
        BootAndTimeChangeReceiver.resetTestOverrides()
        ScheduleNotificationManager.resetTestHooks()
        storeScope.cancel()
        db.close()
    }

    private fun scheduledIntents(): List<Pair<Long, Intent>> {
        val shadowAm = Shadows.shadowOf(alarmManager)
        return shadowAm.scheduledAlarms.map { scheduled ->
            val savedIntent = Shadows.shadowOf(scheduled.operation).savedIntent
            scheduled.triggerAtTime to savedIntent
        }
    }

    private suspend fun configureDefaults(
        serviceStart: Boolean = true,
        serviceEnd: Boolean = true,
        nextRestore: Boolean = true,
        leadMinutes: Int = 15,
        favoritesOnly: Boolean = true,
        useSystemTz: Boolean = false,
        manualTz: String = "America/Havana",
        use24h: Boolean = true
    ) {
        prefsRepo.setTimezonePreference(useSystemTz, manualTz)
        prefsRepo.setUse24HourFormat(use24h)
        prefsRepo.updateNotificationSettings(
            serviceStart = serviceStart,
            serviceEnd = serviceEnd,
            nextRestore = nextRestore,
            newForecast = true,
            leadMinutes = leadMinutes,
            favoritesOnly = favoritesOnly
        )
    }

    @Test
    fun `1_transitions beyond 24 hours up to 7 days are scheduled proactively`() = runTest {
        configureDefaults(leadMinutes = 15, favoritesOnly = true)

        repository.upsertCircuit(
            Circuit(
                code = "H001",
                customName = "Mi Hogar",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )

        // Transition 36 hours ahead (Dec 2, 20:00) and 72 hours ahead (Dec 4, 08:00)
        val dayPlus1 = baseDate.plusDays(1)
        val dayPlus3 = baseDate.plusDays(3)
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_36h",
                dateEpochDay = dayPlus1.toEpochDay(),
                startMinuteOfDay = 20 * 60,
                endMinuteOfDay = 23 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("H001")
            )
        )
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_72h",
                dateEpochDay = dayPlus3.toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("H001")
            )
        )

        val result = ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )

        val expected36hTrigger = dayPlus1.atTime(19, 45).atZone(havanaZone).toInstant().toEpochMilli()
        val expected72hTrigger = dayPlus3.atTime(7, 45).atZone(havanaZone).toInstant().toEpochMilli()

        val triggers = scheduledIntents().map { it.first }
        assertTrue("Expected +36h alarm to be scheduled", expected36hTrigger in triggers)
        assertTrue("Expected +72h alarm to be scheduled", expected72hTrigger in triggers)
        assertTrue(result.scheduledAlarms.any { it.triggerAtMillis == expected72hTrigger })
    }

    @Test
    fun `2_simultaneous circuits at identical transition time receive distinct pending intents and notification ids`() = runTest {
        configureDefaults(leadMinutes = 15, favoritesOnly = true)

        repository.upsertCircuit(
            Circuit(
                code = "SIM1",
                customName = "Circuito Norte",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        repository.upsertCircuit(
            Circuit(
                code = "SIM2",
                customName = "Circuito Sur",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = false
            )
        )

        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_sim",
                dateEpochDay = baseDate.toEpochDay(),
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 18 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("SIM1", "SIM2")
            )
        )

        ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )

        val expectedTrigger = baseDate.atTime(13, 45).atZone(havanaZone).toInstant().toEpochMilli()
        val at1345 = scheduledIntents().filter { it.first == expectedTrigger }
        assertEquals("Both simultaneous circuits must be scheduled", 2, at1345.size)

        val codes = at1345.map { it.second.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE) }.toSet()
        assertEquals(setOf("SIM1", "SIM2"), codes)

        val notifIds = at1345.map { it.second.getIntExtra(ScheduleNotificationManager.EXTRA_NOTIF_ID, -1) }
        assertEquals("Notification IDs must be distinct", 2, notifIds.toSet().size)

        // Deliver both broadcasts and verify 2 distinct notifications appear
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ScheduleNotificationManager.ioDispatcher = dispatcher
        ScheduleNotificationReceiver.backgroundDispatcher = dispatcher
        ScheduleNotificationReceiver.nowMillisProvider = { expectedTrigger + 1_000L }
        val receiver = ScheduleNotificationReceiver()
        at1345.forEach { (_, intent) ->
            receiver.onReceive(context, intent)
            ScheduleNotificationReceiver.awaitLastDispatch()
        }

        val posted = Shadows.shadowOf(notificationManager).allNotifications
        assertEquals(2, posted.size)
    }

    @Test
    fun `3_more than 24 candidate events prioritize primary home and favorites over earlier non-favorites`() = runTest {
        configureDefaults(serviceStart = true, serviceEnd = false, nextRestore = true, leadMinutes = 0, favoritesOnly = false)

        // 28 non-favorite circuits transitioning earlier (09:00..11:45)
        for (idx in 1..28) {
            val code = String.format("OTH%02d", idx)
            repository.upsertCircuit(
                Circuit(
                    code = code,
                    scheduleType = ScheduleType.CUSTOM,
                    isActive = true,
                    isFavorite = false,
                    isPrimaryHome = false
                )
            )
            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = "fc_$code",
                    dateEpochDay = baseDate.toEpochDay(),
                    startMinuteOfDay = 9 * 60 + idx * 5,
                    endMinuteOfDay = 13 * 60,
                    intervalKind = IntervalKind.SERVICE,
                    circuitCodes = listOf(code)
                )
            )
        }

        // Primary home and favorite transitioning AFTER the 28 non-favorites (12:30 and 12:45)
        repository.upsertCircuit(
            Circuit(
                code = "HOME1",
                customName = "Mi Casa",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        repository.upsertCircuit(
            Circuit(
                code = "FAV1",
                customName = "Trabajo",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = false
            )
        )
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_home_fav",
                dateEpochDay = baseDate.toEpochDay(),
                startMinuteOfDay = 12 * 60 + 30,
                endMinuteOfDay = 16 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("HOME1", "FAV1")
            )
        )

        val result = ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )

        assertEquals(30, result.totalCandidatesCount)
        assertEquals(ScheduleNotificationManager.MAX_SCHEDULED_ALARMS, result.scheduledAlarms.size)
        assertEquals(6, result.droppedByCapCount)

        val scheduledCodes = scheduledIntents()
            .mapNotNull { it.second.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE) }
            .toSet()

        assertEquals(ScheduleNotificationManager.MAX_SCHEDULED_ALARMS, scheduledCodes.size)
        assertTrue("Primary home MUST be prioritized above non-favorites", "HOME1" in scheduledCodes)
        assertTrue("Favorite MUST be prioritized above non-favorites", "FAV1" in scheduledCodes)
    }

    @Test
    fun `4_removing favorite or deactivating circuit cancels and excludes its alarms`() = runTest {
        configureDefaults(leadMinutes = 15, favoritesOnly = true)

        repository.upsertCircuit(
            Circuit(
                code = "FAV_REM",
                customName = "Taller",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = false
            )
        )
        repository.upsertCircuit(
            Circuit(
                code = "HOME_OFF",
                customName = "Inactivo",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_rem",
                dateEpochDay = baseDate.toEpochDay(),
                startMinuteOfDay = 15 * 60,
                endMinuteOfDay = 18 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("FAV_REM", "HOME_OFF")
            )
        )

        ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )
        val initialCodes = scheduledIntents()
            .mapNotNull { it.second.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE) }
            .toSet()
        assertEquals(setOf("FAV_REM", "HOME_OFF"), initialCodes)

        // Remove favorite on FAV_REM and deactivate HOME_OFF
        repository.toggleFavorite("FAV_REM")
        repository.toggleCircuitActive("HOME_OFF")

        ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )
        assertTrue("All alarms for unfavorited and inactive circuits must be cancelled", scheduledIntents().isEmpty())
    }

    @Test
    fun `5_timezone change via settings and broadcast updates scheduled alarm times`() = runTest {
        configureDefaults(leadMinutes = 0, favoritesOnly = true, useSystemTz = false, manualTz = "America/Havana", use24h = true)

        repository.upsertCircuit(
            Circuit(
                code = "TZ1",
                customName = "Casa TZ",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        // 12:00..16:00 local in the active zone
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_tz",
                dateEpochDay = baseDate.toEpochDay(),
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 16 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("TZ1")
            )
        )

        ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )
        val havanaTrigger = scheduledIntents().first().first
        val expectedHavana12 = baseDate.atTime(12, 0).atZone(havanaZone).toInstant().toEpochMilli()
        assertEquals(expectedHavana12, havanaTrigger)

        // Switch timezone in SettingsViewModel to Europe/Madrid (UTC+1 in Dec, 6h ahead of Havana UTC-5)
        prefsRepo.setAutoSolarSyncEnabled(false)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        ScheduleNotificationManager.ioDispatcher = testDispatcher
        Dispatchers.setMain(testDispatcher)
        val vmStore = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                vmStore,
                SettingsViewModel.Factory(context, repository, prefsRepo)
            )[SettingsViewModel::class.java]
            vm.setTimezonePreference(useSystem = false, manualTimezoneId = "Europe/Madrid")
        } finally {
            vmStore.clear()
            Dispatchers.resetMain()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        // Also simulate TIMEZONE_CHANGED broadcast at madridNow (08:00 Madrid -> 12:00 Madrid is 4h ahead)
        val madridZone = ZoneId.of("Europe/Madrid")
        val madridNow = baseDate.atTime(8, 0).atZone(madridZone).toInstant().toEpochMilli()
        BootAndTimeChangeReceiver.backgroundDispatcher = testDispatcher
        BootAndTimeChangeReceiver.nowMillisProvider = { madridNow }
        val bootReceiver = BootAndTimeChangeReceiver()
        bootReceiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        BootAndTimeChangeReceiver.awaitLastDispatch()

        val expectedMadrid12 = baseDate.atTime(12, 0).atZone(madridZone).toInstant().toEpochMilli()
        val updatedTrigger = scheduledIntents().first().first
        assertNotEquals(havanaTrigger, updatedTrigger)
        assertEquals(expectedMadrid12, updatedTrigger)

        // Verify BOOT_COMPLETED and TIME_CHANGED also reschedule cleanly
        bootReceiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        BootAndTimeChangeReceiver.awaitLastDispatch()
        assertEquals(expectedMadrid12, scheduledIntents().first().first)
        bootReceiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        BootAndTimeChangeReceiver.awaitLastDispatch()
        assertEquals(expectedMadrid12, scheduledIntents().first().first)
    }

    @Test
    fun `6_delivering notification via receiver automatically replenishes next pending alarm beyond 24`() = runTest {
        configureDefaults(serviceStart = true, serviceEnd = false, nextRestore = true, leadMinutes = 0, favoritesOnly = true)

        // Create 26 favorite circuits transitioning sequentially every 30 minutes starting at 09:00
        for (i in 1..26) {
            val code = String.format("SEQ%02d", i)
            repository.upsertCircuit(
                Circuit(
                    code = code,
                    scheduleType = ScheduleType.CUSTOM,
                    isActive = true,
                    isFavorite = true,
                    isPrimaryHome = (i == 1)
                )
            )
            val startMin = 9 * 60 + (i - 1) * 30
            repository.upsertExplicitForecast(
                ExplicitForecast(
                    id = "fc_$code",
                    dateEpochDay = baseDate.toEpochDay(),
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = startMin + 15,
                    intervalKind = IntervalKind.SERVICE,
                    circuitCodes = listOf(code)
                )
            )
        }

        val initial = ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )
        assertEquals(26, initial.totalCandidatesCount)
        assertEquals(24, initial.scheduledAlarms.size)
        val initialCodes = scheduledIntents()
            .mapNotNull { it.second.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE) }
            .toSet()
        assertFalse("SEQ25 should initially wait beyond the 24-alarm cap", "SEQ25" in initialCodes)

        // Fire the 1st alarm (SEQ01 at 09:00) via ScheduleNotificationReceiver at 09:01
        val firstAlarm = scheduledIntents().minBy { it.first }
        val afterFirstMillis = baseDate.atTime(9, 1).atZone(havanaZone).toInstant().toEpochMilli()

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ScheduleNotificationManager.ioDispatcher = dispatcher
        ScheduleNotificationReceiver.backgroundDispatcher = dispatcher
        ScheduleNotificationReceiver.nowMillisProvider = { afterFirstMillis }
        ScheduleNotificationReceiver().onReceive(context, firstAlarm.second)
        ScheduleNotificationReceiver.awaitLastDispatch()

        // Notification was posted
        assertEquals(1, Shadows.shadowOf(notificationManager).allNotifications.size)

        // Next wave was replenished: SEQ25 is now scheduled!
        val replenishedCodes = scheduledIntents()
            .mapNotNull { it.second.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE) }
            .toSet()
        assertEquals(24, replenishedCodes.size)
        assertTrue("Post-delivery reschedule must promote SEQ25 into the active queue", "SEQ25" in replenishedCodes)
    }

    @Test
    fun `7_avoids duplicate alarms for same circuit and transition across flags and midnight`() = runTest {
        configureDefaults(serviceStart = true, serviceEnd = true, nextRestore = true, leadMinutes = 10, favoritesOnly = true)

        repository.upsertCircuit(
            Circuit(
                code = "DUP1",
                customName = "Sin Duplicados",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )

        // Service crossing midnight seamlessly: Dec 1 22:00-24:00 + Dec 2 00:00-04:00
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_dup_d1",
                dateEpochDay = baseDate.toEpochDay(),
                startMinuteOfDay = 22 * 60,
                endMinuteOfDay = 24 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("DUP1")
            )
        )
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_dup_d2",
                dateEpochDay = baseDate.plusDays(1).toEpochDay(),
                startMinuteOfDay = 0,
                endMinuteOfDay = 4 * 60,
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("DUP1")
            )
        )

        val result = ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = baseNowMillis,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )

        // Exactly 2 transitions: UNKNOWN -> SERVICE at 22:00, and SERVICE -> UNKNOWN? Wait: UNKNOWN is skipped,
        // only SERVICE_SCHEDULED transition at 22:00 (not duplicated at 00:00 and not duplicated by notifyServiceStart + notifyNextRestore).
        val serviceAlarms = result.scheduledAlarms.filter { it.targetState == ScheduledState.SERVICE_SCHEDULED }
        assertEquals("Same transition must never generate duplicate alarms", 1, serviceAlarms.size)
        assertEquals(baseDate.atTime(21, 50).atZone(havanaZone).toInstant().toEpochMilli(), serviceAlarms.first().triggerAtMillis)
    }

    @Test
    fun `8_sync update replaces stale scheduled alarms automatically`() = runTest {
        configureDefaults(leadMinutes = 15, favoritesOnly = true)
        ScheduleNotificationManager.ioDispatcher = UnconfinedTestDispatcher(testScheduler)

        // Local user marks A210 as Mi Casa (personal preference preserved across syncs)
        repository.upsertCircuit(
            Circuit(
                code = "A210",
                customName = "Mi Casa",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )

        val realNow = System.currentTimeMillis()
        val v1Zdt = java.time.Instant.ofEpochMilli(realNow + 3 * 3600_000L).atZone(havanaZone)
        val v1Date = v1Zdt.toLocalDate()
        val v1StartMin = v1Zdt.hour * 60 + v1Zdt.minute
        val v1EndMin = (v1StartMin + 60).coerceAtMost(1440)

        val v2Zdt = java.time.Instant.ofEpochMilli(realNow + 7 * 3600_000L).atZone(havanaZone)
        val v2Date = v2Zdt.toLocalDate()
        val v2StartMin = v2Zdt.hour * 60 + v2Zdt.minute
        val v2EndMin = (v2StartMin + 60).coerceAtMost(1440)

        fun buildPayloadJson(version: Int, versionName: String, forecastId: String, date: LocalDate, startMin: Int, endMin: Int): String {
            val snapshot = ScheduleSnapshot(
                circuits = listOf(
                    Circuit(
                        code = "A210",
                        scheduleType = ScheduleType.CUSTOM,
                        isActive = true,
                        isFavorite = false,
                        isPrimaryHome = false
                    )
                ),
                groups = emptyList(),
                fixedRules = emptyList(),
                explicitForecasts = listOf(
                    ExplicitForecast(
                        id = forecastId,
                        dateEpochDay = date.toEpochDay(),
                        startMinuteOfDay = startMin,
                        endMinuteOfDay = endMin,
                        intervalKind = IntervalKind.SERVICE,
                        circuitCodes = listOf("A210")
                    )
                ),
                manualExceptions = emptyList(),
                relations = emptyList(),
                cycles = emptyList()
            )
            val payload = ConfigCodec.snapshotToPayload(
                snapshot = snapshot,
                configVersion = version,
                configVersionName = versionName,
                includePersonalData = false,
                nowIso = java.time.Instant.ofEpochMilli(realNow).toString()
            )
            return ConfigCodec.encodeToJson(payload)
        }

        var currentResponseJson = buildPayloadJson(
            version = 2026112701,
            versionName = "2026.11.27.1",
            forecastId = "fc_remote_v1",
            date = v1Date,
            startMin = v1StartMin,
            endMin = v1EndMin
        )

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(currentResponseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val syncManager = HttpsSyncManager(
            repository = repository,
            prefsRepo = prefsRepo,
            client = mockClient,
            appContext = context
        )

        // Sync v1 -> schedules v1 alarm automatically
        val outcomeV1 = syncManager.syncFromUrl("https://example.com/luz_isla_config.json", forceApply = true)
        assertTrue(outcomeV1 is SyncOutcome.Updated)

        val expectedV1Trigger = v1Date
            .atTime(LocalTime.of(v1StartMin / 60, v1StartMin % 60))
            .atZone(havanaZone)
            .toInstant()
            .toEpochMilli() - 15 * 60_000L
        assertTrue("v1 synced transition must be scheduled", expectedV1Trigger in scheduledIntents().map { it.first })

        // Sync v2 -> replaces v1 remote forecast and automatically updates alarms
        currentResponseJson = buildPayloadJson(
            version = 2026112702,
            versionName = "2026.11.27.2",
            forecastId = "fc_remote_v2",
            date = v2Date,
            startMin = v2StartMin,
            endMin = v2EndMin
        )
        val outcomeV2 = syncManager.syncFromUrl("https://example.com/luz_isla_config.json", forceApply = false)
        assertTrue(outcomeV2 is SyncOutcome.Updated)

        val expectedV2Trigger = v2Date
            .atTime(LocalTime.of(v2StartMin / 60, v2StartMin % 60))
            .atZone(havanaZone)
            .toInstant()
            .toEpochMilli() - 15 * 60_000L

        val triggersAfterSync = scheduledIntents().map { it.first }
        assertFalse("Old v1 alarm must be removed after remote v2 sync", expectedV1Trigger in triggersAfterSync)
        assertTrue("New v2 synced transition alarm must be scheduled automatically", expectedV2Trigger in triggersAfterSync)
        assertTrue("Personal primaryHome flag on A210 was preserved", repository.getCurrentSnapshot().circuits.first { it.code == "A210" }.isPrimaryHome)
    }

    @Test
    fun `9_maintenance worker schedules periodic work and refreshes alarms offline`() = runTest {
        runCatching {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        configureDefaults(leadMinutes = 15, favoritesOnly = true)

        val realNow = System.currentTimeMillis()
        val futureZdt = java.time.Instant.ofEpochMilli(realNow + 4 * 3600_000L).atZone(havanaZone)
        val startMin = futureZdt.hour * 60 + futureZdt.minute
        repository.upsertCircuit(
            Circuit(
                code = "MNT1",
                customName = "Mantenimiento",
                scheduleType = ScheduleType.CUSTOM,
                isActive = true,
                isFavorite = true,
                isPrimaryHome = true
            )
        )
        repository.upsertExplicitForecast(
            ExplicitForecast(
                id = "fc_mnt",
                dateEpochDay = futureZdt.toLocalDate().toEpochDay(),
                startMinuteOfDay = startMin,
                endMinuteOfDay = (startMin + 90).coerceAtMost(1440),
                intervalKind = IntervalKind.SERVICE,
                circuitCodes = listOf("MNT1")
            )
        )

        assertTrue("Alarms start empty before worker runs", scheduledIntents().isEmpty())

        val workResult = NotificationMaintenanceWorker.executeMaintenance(context)
        assertEquals(ListenableWorker.Result.success(), workResult)
        assertTrue("Maintenance worker must schedule upcoming alarms", scheduledIntents().isNotEmpty())

        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(NotificationMaintenanceWorker.WORK_NAME).get()
        assertTrue(
            "Periodic maintenance work should be enqueued when alerts are enabled",
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        )

        // Turning off all alert toggles cancels scheduled alarms and periodic maintenance work
        configureDefaults(serviceStart = false, serviceEnd = false, nextRestore = false)
        val disabledResult = ScheduleNotificationManager.rescheduleUpcomingNotifications(
            context = context,
            nowMillis = realNow,
            repositoryOverride = repository,
            prefsRepoOverride = prefsRepo
        )
        assertFalse(disabledResult.maintenanceWorkerActive)
        assertTrue(scheduledIntents().isEmpty())
    }
}
