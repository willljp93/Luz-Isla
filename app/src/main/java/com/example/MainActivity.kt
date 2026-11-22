package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.notifications.ScheduleNotificationManager
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.calendar.CalendarViewModel
import com.example.ui.screens.calendar.CalendarViewMode
import com.example.ui.screens.circuits.CircuitsScreen
import com.example.ui.screens.circuits.CircuitsViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.LuzIslaTheme
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object CircuitsRoute
@Serializable data object SettingsRoute

private data class NavTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val deepLinkCircuitCode = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkCircuitCode.value = intent?.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE)

        val database = AppDatabase.getInstance(applicationContext)
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val repository = LuzIslaRepository(database, prefsRepo)

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            LuzIslaTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor
            ) {
                LuzIslaApp(
                    appContext = applicationContext,
                    repository = repository,
                    prefsRepo = prefsRepo,
                    initialCircuitCode = deepLinkCircuitCode.value,
                    onConsumeDeepLink = { deepLinkCircuitCode.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkCircuitCode.value = intent.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE)
    }
}

@Composable
private fun LuzIslaApp(
    appContext: android.content.Context,
    repository: LuzIslaRepository,
    prefsRepo: UserPreferencesRepository,
    initialCircuitCode: String?,
    onConsumeDeepLink: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(appContext, repository, prefsRepo)
    )
    val calendarViewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.Factory(repository)
    )
    val circuitsViewModel: CircuitsViewModel = viewModel(
        factory = CircuitsViewModel.Factory(appContext, repository)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(appContext, repository, prefsRepo)
    )

    LaunchedEffect(initialCircuitCode) {
        if (!initialCircuitCode.isNullOrBlank()) {
            homeViewModel.openCircuitDetail(initialCircuitCode)
            onConsumeDeepLink()
        }
    }

    val tabs = listOf(
        NavTabItem(HomeRoute, "Inicio", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        NavTabItem(CalendarRoute, "Calendario", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
        NavTabItem(CircuitsRoute, "Circuitos", Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_circuits"),
        NavTabItem(SettingsRoute, "Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    val configuration = LocalConfiguration.current
    val useNavRail = configuration.screenWidthDp >= 600

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!useNavRail) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useNavRail) {
                NavigationRail {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.weight(1f)
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onOpenInCalendar = { code ->
                            calendarViewModel.selectCircuit(code)
                            calendarViewModel.selectViewMode(CalendarViewMode.BY_CIRCUIT)
                            navigateToTab(CalendarRoute)
                        }
                    )
                }
                composable<CalendarRoute> {
                    CalendarScreen(viewModel = calendarViewModel)
                }
                composable<CircuitsRoute> {
                    CircuitsScreen(viewModel = circuitsViewModel)
                }
                composable<SettingsRoute> {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
