package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.ThemeMode
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.calendar.CalendarViewModel
import com.example.ui.screens.circuits.CircuitsScreen
import com.example.ui.screens.circuits.CircuitsViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.settings.SettingsCategoryTab
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.LuzIslaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlobalUiUxAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: LuzIslaRepository

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tmpFolder.newFile("global_ui_${System.nanoTime()}.preferences_pb") }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
        repository = LuzIslaRepository(db, prefsRepo)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `home screen renders primary home spotlight, sync banner, empty reset, and 48dp targets across themes and widths`() {
        runBlocking<Unit> {
            prefsRepo.setCheckAllOnAppOpen(false)
            prefsRepo.setEnableElectricAnimations(false)
            AppDatabase.seedIfEmpty(db)
            val snap = repository.getCurrentSnapshot()
            val primary = snap.circuits.first { it.code == "A888" }
            repository.upsertCircuit(primary.copy(isPrimaryHome = true, isFavorite = true))
            prefsRepo.recordSyncStatus("Error de conexión al sincronizar")
        }

        val viewModel = HomeViewModel(context, repository, prefsRepo)
        var activeWidth by mutableStateOf(320.dp)
        var activeTheme by mutableStateOf(ThemeMode.LIGHT)
        var activeFontScale by mutableFloatStateOf(1.35f)
        var capturedDensity = 1f

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            capturedDensity = baseDensity.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = activeFontScale)
            ) {
                LuzIslaTheme(themeMode = activeTheme, dynamicColor = false) {
                    Box(modifier = Modifier.width(activeWidth).height(840.dp)) {
                        HomeScreen(viewModel = viewModel, onOpenInCalendar = {})
                    }
                }
            }
        }

        val configs = listOf(
            Triple(320.dp, ThemeMode.LIGHT, 1.35f),
            Triple(360.dp, ThemeMode.DARK, 1.0f),
            Triple(393.dp, ThemeMode.SYSTEM, 1.15f),
            Triple(600.dp, ThemeMode.DARK, 1.3f)
        )

        configs.forEach { (w, theme, scale) ->
            composeRule.runOnIdle {
                activeWidth = w
                activeTheme = theme
                activeFontScale = scale
            }
            composeRule.waitUntil(3_000) { !viewModel.uiState.value.isLoading }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Luz Isla").assertIsDisplayed()
            composeRule.onNodeWithTag("home_sync_error_banner").assertIsDisplayed()
            composeRule.onNodeWithTag("home_compact_toggle").assertIsDisplayed()

            val toggleBounds = composeRule.onNodeWithTag("home_compact_toggle")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "home_compact_toggle height (${toggleBounds.height}) must be >= 48dp at $w",
                toggleBounds.height >= (48f * capturedDensity) - 1f
            )

            composeRule.onNodeWithTag("home_lazy_column")
                .performScrollToNode(hasTestTag("circuit_card_A888"))
            composeRule.onNodeWithTag("circuit_card_A888").assertIsDisplayed()
            composeRule.onNodeWithTag("home_lazy_column")
                .performScrollToIndex(0)
        }

        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("home_search_input")))
            .performScrollTo()
            .performTextInput("ZZZ_NO_MATCH")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_empty_filtered_card"))
        composeRule.onNodeWithTag("home_empty_filtered_card").assertIsDisplayed()
        composeRule.onNodeWithText("Restablecer filtros").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("circuit_card_A888"))
        composeRule.onNodeWithTag("circuit_card_A888").assertIsDisplayed()
    }

    @Test
    fun `home screen renders explicit empty catalog card when no active circuits exist`() {
        runBlocking<Unit> {
            prefsRepo.setCheckAllOnAppOpen(false)
            prefsRepo.setEnableElectricAnimations(false)
            AppDatabase.seedIfEmpty(db)
        }
        val viewModel = HomeViewModel(context, repository, prefsRepo)

        composeRule.setContent {
            LuzIslaTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                Box(modifier = Modifier.width(360.dp).height(840.dp)) {
                    HomeScreen(viewModel = viewModel, onOpenInCalendar = {})
                }
            }
        }

        composeRule.waitUntil(3_000) { !viewModel.uiState.value.isLoading }
        runBlocking<Unit> {
            repository.getCurrentSnapshot().circuits.forEach { circuit ->
                repository.upsertCircuit(circuit.copy(isActive = false))
            }
        }
        composeRule.waitUntil(3_000) { viewModel.uiState.value.totalActiveCount == 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_empty_catalog_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `calendar screen keeps date bar and view modes clean across 320dp, 360dp, 393dp, 600dp and large font`() {
        runBlocking<Unit> {
            prefsRepo.setCheckAllOnAppOpen(false)
            prefsRepo.setEnableElectricAnimations(false)
            AppDatabase.seedIfEmpty(db)
        }
        val viewModel = CalendarViewModel(repository, prefsRepo)
        var activeWidth by mutableStateOf(320.dp)
        var activeTheme by mutableStateOf(ThemeMode.DARK)
        var activeFontScale by mutableFloatStateOf(1.35f)
        var capturedDensity = 1f

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            capturedDensity = baseDensity.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = activeFontScale)
            ) {
                LuzIslaTheme(themeMode = activeTheme, dynamicColor = false) {
                    Box(modifier = Modifier.width(activeWidth).height(840.dp)) {
                        CalendarScreen(viewModel = viewModel)
                    }
                }
            }
        }

        listOf(
            Triple(320.dp, ThemeMode.DARK, 1.35f),
            Triple(360.dp, ThemeMode.LIGHT, 1.0f),
            Triple(393.dp, ThemeMode.SYSTEM, 1.2f),
            Triple(600.dp, ThemeMode.DARK, 1.3f)
        ).forEach { (w, theme, scale) ->
            composeRule.runOnIdle {
                activeWidth = w
                activeTheme = theme
                activeFontScale = scale
            }
            composeRule.waitUntil(3_000) { !viewModel.uiState.value.isLoading }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Calendario y Línea de Tiempo").assertIsDisplayed()
            composeRule.onNodeWithTag("calendar_today_button").assertIsDisplayed()
            composeRule.onNodeWithTag("calendar_prev_day").assertIsDisplayed()
            composeRule.onNodeWithTag("calendar_next_day").assertIsDisplayed()

            val todayBounds = composeRule.onNodeWithTag("calendar_today_button")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(todayBounds.height >= (48f * capturedDensity) - 1f)
        }

        composeRule.onNodeWithTag("calendar_mode_weekly_7_days").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar_mode_groups").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `circuits screen separates primary tabs and programming subtabs cleanly across widths and themes`() {
        runBlocking<Unit> {
            AppDatabase.seedIfEmpty(db)
        }
        val viewModel = CircuitsViewModel(context, repository, prefsRepo)
        var activeWidth by mutableStateOf(320.dp)
        var activeTheme by mutableStateOf(ThemeMode.SYSTEM)

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.35f)
            ) {
                LuzIslaTheme(themeMode = activeTheme, dynamicColor = false) {
                    Box(modifier = Modifier.width(activeWidth).height(840.dp)) {
                        CircuitsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        listOf(
            320.dp to ThemeMode.LIGHT,
            360.dp to ThemeMode.DARK,
            393.dp to ThemeMode.SYSTEM,
            600.dp to ThemeMode.LIGHT
        ).forEach { (w, theme) ->
            composeRule.runOnIdle {
                activeWidth = w
                activeTheme = theme
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Circuitos y Programación").assertIsDisplayed()
            composeRule.onNodeWithTag("admin_add_fab").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("circuits_primary_tab_programming").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_tab_cycles").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_groups").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_tab_relations").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `settings tabs stay readable within bounds at 320dp, 360dp, 393dp, 600dp with large fontScale and TalkBack semantics`() {
        val viewModel = SettingsViewModel(context, repository, prefsRepo)
        var activeWidth by mutableStateOf(320.dp)
        var activeTheme by mutableStateOf(ThemeMode.LIGHT)
        var activeFontScale by mutableFloatStateOf(1.4f)
        var capturedDensity = 1f

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            capturedDensity = baseDensity.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = activeFontScale)
            ) {
                LuzIslaTheme(themeMode = activeTheme, dynamicColor = false) {
                    Box(modifier = Modifier.width(activeWidth).height(840.dp)) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        val cases = listOf(
            Triple(320.dp, ThemeMode.LIGHT, 1.4f),
            Triple(360.dp, ThemeMode.DARK, 1.3f),
            Triple(393.dp, ThemeMode.SYSTEM, 1.2f),
            Triple(600.dp, ThemeMode.DARK, 1.35f)
        )

        cases.forEach { (widthDp, themeMode, scale) ->
            composeRule.runOnIdle {
                activeWidth = widthDp
                activeTheme = themeMode
                activeFontScale = scale
            }
            composeRule.waitForIdle()

            val rowNode = composeRule.onNodeWithTag("settings_primary_tab_row")
            rowNode.assertIsDisplayed()
            val rowBounds = rowNode.fetchSemanticsNode().boundsInRoot

            SettingsCategoryTab.entries.forEach { tab ->
                val tabNode = composeRule.onNodeWithTag("settings_tab_${tab.name.lowercase()}")
                tabNode.assertIsDisplayed()
                tabNode.assert(
                    SemanticsMatcher("has full TalkBack contentDescription '${tab.label}'") { node ->
                        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                            .contains(tab.label)
                    }
                )
                val bounds = tabNode.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "Tab ${tab.name} overflowed at $widthDp with scale=$scale (${bounds.left}..${bounds.right} vs ${rowBounds.left}..${rowBounds.right})",
                    bounds.left >= rowBounds.left - 1f && bounds.right <= rowBounds.right + 1f
                )
                assertTrue(
                    "Tab ${tab.name} height (${bounds.height}) must be >= 48dp at $widthDp",
                    bounds.height >= (48f * capturedDensity) - 1f
                )
            }
        }

        composeRule.onNodeWithTag("settings_tab_status_time").assertIsSelected()
        composeRule.onNodeWithTag("settings_tab_appearance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_appearance").assertIsSelected()
        composeRule.onNodeWithTag("settings_tab_data_sync").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_data_sync").assertIsSelected()
        composeRule.onNodeWithTag("settings_tab_notifications_app").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_notifications_app").assertIsSelected()
    }
}
