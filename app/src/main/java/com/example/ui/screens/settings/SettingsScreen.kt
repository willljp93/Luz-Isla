package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.sync.SunriseSunsetService
import com.example.ui.components.CenteredScrollableChipRow
import com.example.ui.components.TimezonePickerDialog
import com.example.ui.theme.LocalBottomOverlayPadding
import kotlinx.coroutines.delay

enum class SettingsCategoryTab(
    val label: String,
    val icon: ImageVector
) {
    STATUS_TIME("Estado y Hora", Icons.Filled.Schedule),
    APPEARANCE("Apariencia", Icons.Filled.Palette),
    DATA_SYNC("Datos y Sync", Icons.Filled.CloudSync),
    NOTIFICATIONS_APP("Avisos y App", Icons.Filled.Notifications)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + bottomOverlayPadding
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "settings_title") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ajustes",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        CenteredScrollableChipRow {
                            SettingsCategoryTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("settings_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                when (selectedTab) {
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
