package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.sync.SunriseSunsetService
import com.example.ui.components.TimezonePickerDialog
import com.example.ui.theme.LocalBottomOverlayPadding
import kotlinx.coroutines.delay

enum class SettingsCategoryTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    STATUS_TIME("Estado y Hora", Icons.Filled.Schedule, Icons.Outlined.Schedule),
    APPEARANCE("Apariencia", Icons.Filled.Palette, Icons.Outlined.Palette),
    DATA_SYNC("Datos y Sync", Icons.Filled.CloudSync, Icons.Outlined.CloudSync),
    NOTIFICATIONS_APP("Avisos y App", Icons.Filled.Notifications, Icons.Outlined.Notifications)
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    val p = state.prefs
    val activeZone = remember(p.useSystemTimezone, p.manualTimezoneId) {
        ScheduleEngine.effectiveZone(p.useSystemTimezone, p.manualTimezoneId)
    }
    var currentNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeZone, p.use24HourFormat) {
        currentNowMillis = System.currentTimeMillis()
        while (true) {
            delay(5_000L)
            currentNowMillis = System.currentTimeMillis()
        }
    }

    var syncUrlInput by remember(p.syncUrl) { mutableStateOf(p.syncUrl) }
    var autoSyncEnabled by remember(p.autoSyncEnabled) { mutableStateOf(p.autoSyncEnabled) }
    var syncIntervalHours by remember(p.syncIntervalHours) { mutableIntStateOf(p.syncIntervalHours) }

    var includePersonalInExport by remember { mutableStateOf(false) }
    var pasteCodeInput by remember { mutableStateOf("") }
    var showTimezoneDialog by remember { mutableStateOf(false) }
    var showDayNightDialog by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(SettingsCategoryTab.STATUS_TIME) }
    val bottomOverlayPadding = LocalBottomOverlayPadding.current

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.inspectJsonUri(uri)
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.writeExportJsonToUri(uri)
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val tabScrollStates = SettingsCategoryTab.entries.associateWith { rememberLazyListState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomOverlayPadding)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = "Ajustes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    SettingsCategoryPrimaryTabs(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it }
                    )
                }

                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 180),
                    label = "settings_section_transition",
                    modifier = Modifier.weight(1f)
                ) { activeTab ->
                    LazyColumn(
                        state = tabScrollStates.getValue(activeTab),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 4.dp,
                            end = 16.dp,
                            bottom = 16.dp + bottomOverlayPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (activeTab) {
                            SettingsCategoryTab.STATUS_TIME -> {
                                item(key = "active_config_version_card") {
                                    ActiveConfigStatusCard(prefs = p, activeZone = activeZone)
                                }
                                item(key = "timezone_format_card") {
                                    TimezoneAndFormatSection(
                                        prefs = p,
                                        activeZone = activeZone,
                                        currentNowMillis = currentNowMillis,
                                        onSetTimezonePreference = viewModel::setTimezonePreference,
                                        onOpenTimezoneDialog = { showTimezoneDialog = true },
                                        onSetUse24HourFormat = viewModel::setUse24HourFormat
                                    )
                                }
                            }

                            SettingsCategoryTab.APPEARANCE -> {
                                item(key = "appearance_section") {
                                    AppearanceSection(
                                        state = state,
                                        onSetThemeMode = viewModel::setThemeMode,
                                        onSetDynamicColor = viewModel::setDynamicColor,
                                        onSetEnableElectricAnimations = viewModel::setEnableElectricAnimations,
                                        onSetBottomBarBehavior = viewModel::setBottomBarBehavior,
                                        onSetCenterTimelineOnNow = viewModel::setCenterTimelineOnNow,
                                        onSetWidgetCardStyle = viewModel::setWidgetCardStyle,
                                        onOpenDayNightDialog = { showDayNightDialog = true },
                                        onSyncSolarNow = viewModel::syncSolarTimesNow,
                                        onOpenSolarAttribution = {
                                            runCatching { uriHandler.openUri(SunriseSunsetService.ATTRIBUTION_URL) }
                                        },
                                        onSetDynamicLauncherIcon = viewModel::setDynamicLauncherIconEnabled,
                                        onReplayOnboarding = viewModel::replayOnboarding
                                    )
                                }
                            }

                            SettingsCategoryTab.DATA_SYNC -> {
                                item(key = "data_sync_section") {
                                    DataAndSyncSection(
                                        state = state,
                                        activeZone = activeZone,
                                        syncUrlInput = syncUrlInput,
                                        onSyncUrlChange = { syncUrlInput = it },
                                        autoSyncEnabled = autoSyncEnabled,
                                        onAutoSyncChange = {
                                            autoSyncEnabled = it
                                            viewModel.saveSyncSettings(syncUrlInput, it, syncIntervalHours)
                                        },
                                        syncIntervalHours = syncIntervalHours,
                                        onSyncIntervalChange = { hrs ->
                                            syncIntervalHours = hrs
                                            viewModel.saveSyncSettings(syncUrlInput, autoSyncEnabled, hrs)
                                        },
                                        includePersonalInExport = includePersonalInExport,
                                        onIncludePersonalChange = { includePersonalInExport = it },
                                        pasteCodeInput = pasteCodeInput,
                                        onPasteCodeChange = { pasteCodeInput = it },
                                        onSetCheckAllOnAppOpen = viewModel::setCheckAllOnAppOpen,
                                        onRunUnifiedCheckNow = viewModel::runUnifiedSyncCheckNow,
                                        onSaveSyncSettings = {
                                            viewModel.saveSyncSettings(syncUrlInput, autoSyncEnabled, syncIntervalHours)
                                        },
                                        onTriggerManualSync = { viewModel.triggerManualSync(syncUrlInput) },
                                        onGenerateExport = { viewModel.generateExport(includePersonalInExport) },
                                        onInspectCode = { viewModel.inspectAlphanumericCode(pasteCodeInput) },
                                        onOpenJsonImport = {
                                            openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        },
                                        onRestoreBackup = viewModel::restoreLatestBackup
                                    )
                                }
                            }

                            SettingsCategoryTab.NOTIFICATIONS_APP -> {
                                item(key = "notifications_app_section") {
                                    NotificationsAndAppSection(
                                        state = state,
                                        activeZone = activeZone,
                                        context = context,
                                        notifPermLauncher = notifPermLauncher,
                                        onUpdateNotificationPrefs = viewModel::updateNotificationPrefs,
                                        onCheckUpdatesNow = viewModel::checkAppUpdatesNow,
                                        onOpenExternalUrl = viewModel::openExternalUrl
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.exportBundle?.let { bundle ->
        ExportConfigDialog(
            bundle = bundle,
            use24h = p.use24HourFormat,
            zoneId = activeZone,
            onCopyCode = { clipboard.setText(AnnotatedString(it)) },
            onShareCode = viewModel::shareTextCode,
            onSaveJson = { suggestedName -> saveJsonLauncher.launch(suggestedName) },
            onDismiss = viewModel::dismissExportBundle
        )
    }

    state.pendingImport?.let { review ->
        ImportReviewDialog(
            review = review,
            use24h = p.use24HourFormat,
            zoneId = activeZone,
            onConfirm = { mode, includePersonal ->
                viewModel.confirmPendingImport(mode, includePersonal)
                pasteCodeInput = ""
            },
            onDismiss = viewModel::dismissPendingImport
        )
    }

    if (showTimezoneDialog) {
        TimezonePickerDialog(
            currentZoneId = p.manualTimezoneId,
            use24h = p.use24HourFormat,
            isUsingSystemTimezone = p.useSystemTimezone,
            onUseSystemTimezone = {
                viewModel.setTimezonePreference(useSystem = true, manualTimezoneId = p.manualTimezoneId)
                showTimezoneDialog = false
            },
            onSelect = { zoneIdStr ->
                viewModel.setTimezonePreference(useSystem = false, manualTimezoneId = zoneIdStr)
                showTimezoneDialog = false
            },
            onDismiss = { showTimezoneDialog = false }
        )
    }

    if (showDayNightDialog) {
        DayNightTimeDialog(
            isAutoMode = p.autoSolarSyncEnabled,
            isSyncing = state.isSolarSyncing,
            statusMessage = p.solarLastStatus,
            cachedDate = p.solarCachedDate,
            initialSunriseMinutes = p.sunriseMinutes,
            initialSunsetMinutes = p.sunsetMinutes,
            use24h = p.use24HourFormat,
            onSyncNow = viewModel::syncSolarTimesNow,
            onSelectAuto = {
                viewModel.setDayNightAutoMode(true)
                showDayNightDialog = false
            },
            onSaveManual = { sunrise, sunset ->
                viewModel.setDayNightTimes(sunrise, sunset)
                showDayNightDialog = false
            },
            onDismiss = { showDayNightDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCategoryPrimaryTabs(
    selectedTab: SettingsCategoryTab,
    onSelectTab: (SettingsCategoryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useHorizontalTabLayout = maxWidth >= 560.dp
            val isVeryNarrow = maxWidth < 330.dp

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier
                            .tabIndicatorOffset(selectedTab.ordinal, matchContentSize = false)
                            .padding(horizontal = 10.dp),
                        width = Dp.Unspecified,
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_primary_tab_row")
            ) {
                SettingsCategoryTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val activeHighlightColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 180),
                        label = "tab_bg_${tab.name}"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 180),
                        label = "tab_tint_${tab.name}"
                    )

                    Tab(
                        selected = isSelected,
                        onClick = { onSelectTab(tab) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_tab_${tab.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp, vertical = 4.dp)
                                .defaultMinSize(minHeight = if (useHorizontalTabLayout) 40.dp else 54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(activeHighlightColor)
                                .padding(
                                    horizontal = if (useHorizontalTabLayout) 8.dp else 3.dp,
                                    vertical = if (useHorizontalTabLayout) 10.dp else 6.dp
                                )
                        ) {
                            if (useHorizontalTabLayout) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null,
                                        tint = contentTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentTint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null,
                                        tint = contentTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = if (isVeryNarrow) 10.sp else 11.sp,
                                            lineHeight = if (isVeryNarrow) 12.sp else 13.sp,
                                            letterSpacing = (-0.1).sp
                                        ),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentTint,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
