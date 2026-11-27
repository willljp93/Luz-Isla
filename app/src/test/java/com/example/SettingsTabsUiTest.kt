package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.ThemeMode
import com.example.ui.screens.settings.SettingsCategoryTab
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.LuzIslaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsTabsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tmpFolder.newFile("settings_ui_${System.nanoTime()}.preferences_pb") }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
        val repository = LuzIslaRepository(db, prefsRepo)
        viewModel = SettingsViewModel(context, repository, prefsRepo)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        db.close()
    }

    @Test
    fun `primary tabs switch all settings sections cleanly and fit within narrow screens`() {
        composeRule.setContent {
            LuzIslaTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.width(320.dp)) {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }

        composeRule.waitForIdle()

        // Title and primary tab bar are displayed
        composeRule.onNodeWithText("Ajustes").assertIsDisplayed()
        val tabRowNode = composeRule.onNodeWithTag("settings_primary_tab_row")
        tabRowNode.assertIsDisplayed()
        val rowBounds = tabRowNode.fetchSemanticsNode().boundsInRoot

        // All tabs exist, are displayed inside the tab bar without horizontal overflow, and default tab is selected
        SettingsCategoryTab.entries.forEach { tab ->
            val tabNode = composeRule.onNodeWithTag("settings_tab_${tab.name.lowercase()}")
            tabNode.assertIsDisplayed()
            val bounds = tabNode.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Tab ${tab.name} overflowed horizontally (${bounds.left}..${bounds.right} vs ${rowBounds.left}..${rowBounds.right})",
                bounds.left >= rowBounds.left - 1f && bounds.right <= rowBounds.right + 1f
            )
        }
        composeRule.onNodeWithTag("settings_tab_status_time").assertIsSelected()
        composeRule.onNodeWithText("Estado de la aplicación y configuración").assertIsDisplayed()

        // Switch to Apariencia
        composeRule.onNodeWithTag("settings_tab_appearance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_appearance").assertIsSelected()
        composeRule.onNodeWithText("Tema, animaciones y barra").assertIsDisplayed()

        // Switch to Datos y Sync
        composeRule.onNodeWithTag("settings_tab_data_sync").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_data_sync").assertIsSelected()
        composeRule.onNodeWithText("Comprobación unificada").assertIsDisplayed()

        // Switch to Avisos y App
        composeRule.onNodeWithTag("settings_tab_notifications_app").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_tab_notifications_app").assertIsSelected()
        composeRule.onNodeWithText("Notificaciones locales").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_notification_scope_card").assertExists()
        composeRule.onNodeWithTag("settings_notification_battery_note").assertExists()
    }
}
