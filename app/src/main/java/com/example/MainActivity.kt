package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.sync.AppUpdateService
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.calendar.CalendarViewModel
import com.example.ui.screens.calendar.CalendarViewMode
import com.example.ui.screens.circuits.CircuitsScreen
import com.example.ui.screens.circuits.CircuitsViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.LuzIslaTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
            val prefsOrNull by prefsRepo.preferencesFlow
                .map<UserPreferences, UserPreferences?> { it }
                .collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            val currentPrefs = prefsOrNull

            LuzIslaTheme(
                themeMode = currentPrefs?.themeMode ?: com.example.domain.model.ThemeMode.SYSTEM,
                dynamicColor = currentPrefs?.dynamicColor ?: false
            ) {
                if (currentPrefs == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Crossfade(targetState = currentPrefs.onboardingCompleted, label = "onboarding_gate") { completed ->
                        if (!completed) {
                            OnboardingScreen(
                                initialThemeMode = currentPrefs.themeMode,
                                initialDynamicColor = currentPrefs.dynamicColor,
                                initialUseSystemTimezone = currentPrefs.useSystemTimezone,
                                initialManualTimezoneId = currentPrefs.manualTimezoneId,
                                initialUse24h = currentPrefs.use24HourFormat,
                                onThemeChanged = { mode ->
                                    scope.launch { prefsRepo.setThemeMode(mode) }
                                },
                                onDynamicColorChanged = { enabled ->
                                    scope.launch { prefsRepo.setDynamicColor(enabled) }
                                },
                                onFinish = { useSystemTz, manualTz, use24h ->
                                    scope.launch {
                                        prefsRepo.setTimezonePreference(useSystemTz, manualTz)
                                        prefsRepo.setUse24HourFormat(use24h)
                                        prefsRepo.setOnboardingCompleted(true)
                                    }
                                }
                            )
                        } else {
                            LuzIslaApp(
                                appContext = applicationContext,
                                repository = repository,
                                prefsRepo = prefsRepo,
                                hasAppUpdateAvailable = AppUpdateService.isRemoteVersionNewer(
                                    remoteTag = currentPrefs.latestKnownTag,
                                    releaseTitle = currentPrefs.latestKnownTitle,
                                    releaseBody = currentPrefs.latestKnownNotes,
                                    currentVersionName = BuildConfig.VERSION_NAME,
                                    currentVersionCode = BuildConfig.VERSION_CODE,
                                    remoteVersionCodeOverride = currentPrefs.latestKnownVersionCode.takeIf { it > 0 }
                                ),
                                initialCircuitCode = deepLinkCircuitCode.value,
                                onConsumeDeepLink = { deepLinkCircuitCode.value = null }
                            )
                        }
                    }
                }
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
    hasAppUpdateAvailable: Boolean,
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
        factory = CalendarViewModel.Factory(repository, prefsRepo)
    )
    val circuitsViewModel: CircuitsViewModel = viewModel(
        factory = CircuitsViewModel.Factory(appContext, repository, prefsRepo)
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
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 3.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .clickable { isBottomBarVisible = false }
                                    .testTag("bottom_bar_hide_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Ocultar menú inferior",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            NavigationBar {
                                tabs.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    val showUpdateDot = tab.route == SettingsRoute && hasAppUpdateAvailable
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (showUpdateDot) {
                                                        Badge()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isBottomBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .clickable { isBottomBarVisible = true }
                                .testTag("bottom_bar_show_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Mostrar menú inferior",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
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
                        val showUpdateDot = tab.route == SettingsRoute && hasAppUpdateAvailable
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (showUpdateDot) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                }
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
