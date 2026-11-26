package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.example.domain.model.BottomBarBehavior
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
import com.example.ui.theme.LocalBottomOverlayPadding
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
                                bottomBarBehavior = currentPrefs.bottomBarBehavior,
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
    bottomBarBehavior: BottomBarBehavior,
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

    LaunchedEffect(currentDestination?.route, useNavRail, bottomBarBehavior) {
        isBottomBarVisible = true
    }

    val density = LocalDensity.current
    val hideThresholdPx = remember(density) { with(density) { 18.dp.toPx() } }
    val showThresholdPx = remember(density) { with(density) { 6.dp.toPx() } }
    var downScrollAccum by remember { mutableFloatStateOf(0f) }
    var upScrollAccum by remember { mutableFloatStateOf(0f) }

    val bottomBarNestedScroll = remember(hideThresholdPx, showThresholdPx, bottomBarBehavior, useNavRail) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (useNavRail || bottomBarBehavior != BottomBarBehavior.AUTO) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy < -1f) {
                    upScrollAccum = 0f
                    downScrollAccum += -dy
                    if (downScrollAccum >= hideThresholdPx && isBottomBarVisible) {
                        isBottomBarVisible = false
                        downScrollAccum = 0f
                    }
                } else if (dy > 1f) {
                    downScrollAccum = 0f
                    upScrollAccum += dy
                    if (upScrollAccum >= showThresholdPx && !isBottomBarVisible) {
                        isBottomBarVisible = true
                        upScrollAccum = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    fun navigateToTab(route: Any) {
        isBottomBarVisible = true
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = if (useNavRail) {
            WindowInsets.safeDrawing
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        }
    ) { innerPadding ->
        val gestureBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        var visibleBarHeightPx by remember { mutableIntStateOf(0) }
        val bottomOverlayPadding = when {
            useNavRail -> 0.dp
            isBottomBarVisible -> maxOf(with(density) { visibleBarHeightPx.toDp() }, gestureBottomDp)
            else -> gestureBottomDp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            CompositionLocalProvider(LocalBottomOverlayPadding provides bottomOverlayPadding) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(bottomBarNestedScroll)
                        .pointerInput(isBottomBarVisible, showThresholdPx, bottomBarBehavior, useNavRail) {
                            if (!useNavRail && bottomBarBehavior == BottomBarBehavior.AUTO && !isBottomBarVisible) {
                                awaitPointerEventScope {
                                    var upwardDragAccum = 0f
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (!change.pressed) {
                                            upwardDragAccum = 0f
                                            continue
                                        }
                                        val dy = change.position.y - change.previousPosition.y
                                        if (dy > 0f) {
                                            upwardDragAccum += dy
                                            if (upwardDragAccum >= showThresholdPx) {
                                                isBottomBarVisible = true
                                                upwardDragAccum = 0f
                                            }
                                        } else if (dy < -2f) {
                                            upwardDragAccum = 0f
                                        }
                                    }
                                }
                            }
                        }
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

            if (!useNavRail) {
                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = slideInVertically(animationSpec = tween(durationMillis = 170)) { it } +
                        fadeIn(animationSpec = tween(durationMillis = 140)),
                    exit = slideOutVertically(animationSpec = tween(durationMillis = 220)) { it } +
                        fadeOut(animationSpec = tween(durationMillis = 160)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { visibleBarHeightPx = it.height }
                    ) {
                        if (bottomBarBehavior == BottomBarBehavior.PILL_HANDLE) {
                            Surface(
                                onClick = { isBottomBarVisible = false },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.56f),
                                tonalElevation = 1.dp,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .defaultMinSize(minWidth = 56.dp, minHeight = 32.dp)
                                    .pointerInput(Unit) {
                                        var dragY = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { dragY = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                dragY += dragAmount
                                                if (dragY > 12.dp.toPx()) {
                                                    isBottomBarVisible = false
                                                    dragY = 0f
                                                }
                                            }
                                        )
                                    }
                                    .testTag("bottom_bar_collapse_pill")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Ocultar barra de navegación",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        NavigationBar(modifier = Modifier.testTag("main_bottom_bar")) {
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
                    visible = !isBottomBarVisible && bottomBarBehavior == BottomBarBehavior.PILL_HANDLE,
                    enter = slideInVertically(animationSpec = tween(durationMillis = 180)) { it } +
                        fadeIn(animationSpec = tween(durationMillis = 150)),
                    exit = slideOutVertically(animationSpec = tween(durationMillis = 150)) { it } +
                        fadeOut(animationSpec = tween(durationMillis = 120)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 8.dp)
                    ) {
                        Surface(
                            onClick = { isBottomBarVisible = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.28f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 60.dp, minHeight = 34.dp)
                                .pointerInput(Unit) {
                                    var dragY = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { dragY = 0f },
                                        onVerticalDrag = { _, dragAmount ->
                                            dragY += dragAmount
                                            if (dragY < -12.dp.toPx()) {
                                                isBottomBarVisible = true
                                                dragY = 0f
                                            }
                                        }
                                    )
                                }
                                .testTag("bottom_bar_restore_pill")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Mostrar barra de navegación",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
