package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetCardStyle
import com.example.domain.version.AppVersionConfig
import com.example.notifications.ScheduleNotificationManager
import com.example.sync.AppUpdateService
import com.example.sync.SunriseSunsetService
import com.example.sync.UpdateChannelStatus
import com.example.ui.components.IslaEmblemBadge
import com.example.ui.components.TimezonePickerDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import java.time.ZoneId

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "settings_title") {
                    Text(
                        text = "Ajustes, Transferencia y Sincronización",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Active configuration version and timestamp badge
                item(key = "active_config_version_card") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Configuración activa: v${p.activeConfigVersionName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = if (p.use24HourFormat) "24h" else "12h",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Origen: ${p.activeConfigSource}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val timestampLabel = if (p.activeConfigTimestampMillis > 0L) {
                                ScheduleEngine.formatDateTimeReadable(
                                    p.activeConfigTimestampMillis,
                                    p.use24HourFormat,
                                    activeZone
                                )
                            } else {
                                "Base inicial incorporada"
                            }
                            Text(
                                text = "Fecha/hora de carga: $timestampLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Timezone & Clock Format
                item(key = "timezone_format_card") {
                    SettingsSectionCard(title = "Zona horaria y formato de hora") {
                        val systemZone = remember { ZoneId.systemDefault() }
                        val systemGmtLabel = remember(currentNowMillis) {
                            ScheduleEngine.formatGmtOffset(systemZone)
                        }
                        val manualZone = remember(p.manualTimezoneId) {
                            ScheduleEngine.parseManualZoneId(p.manualTimezoneId)
                        }
                        val manualGmtLabel = remember(manualZone, currentNowMillis) {
                            ScheduleEngine.formatGmtOffset(manualZone)
                        }
                        val manualSummary = remember(manualZone, currentNowMillis) {
                            ScheduleEngine.formatZoneSummary(manualZone)
                        }
                        val activeSummary = remember(activeZone, currentNowMillis) {
                            ScheduleEngine.formatZoneSummary(activeZone)
                        }

                        Text("Zona horaria", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = p.useSystemTimezone,
                                onClick = { viewModel.setTimezonePreference(true, p.manualTimezoneId) },
                                label = { Text("Automática ($systemGmtLabel)", maxLines = 1, softWrap = false) }
                            )
                            FilterChip(
                                selected = !p.useSystemTimezone,
                                onClick = {
                                    viewModel.setTimezonePreference(false, p.manualTimezoneId)
                                    showTimezoneDialog = true
                                },
                                label = { Text("Manual ($manualGmtLabel)", maxLines = 1, softWrap = false) }
                            )
                        }

                        if (!p.useSystemTimezone) {
                            OutlinedButton(
                                onClick = { showTimezoneDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Zona manual: $manualSummary",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text("Formato de reloj", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !p.use24HourFormat,
                                onClick = { viewModel.setUse24HourFormat(false) },
                                leadingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("12 horas (AM/PM)", maxLines = 1, softWrap = false) }
                            )
                            FilterChip(
                                selected = p.use24HourFormat,
                                onClick = { viewModel.setUse24HourFormat(true) },
                                leadingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("24 horas", maxLines = 1, softWrap = false) }
                            )
                        }

                        Text(
                            text = "Hora actual en zona activa: ${ScheduleEngine.formatTime(currentNowMillis, p.use24HourFormat, activeZone)} ($activeSummary)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Dispositivo detectado: ${systemZone.id} ($systemGmtLabel)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Appearance, Day/Night, Animations & Dynamic Launcher Icon
                item(key = "appearance_card") {
                    SettingsSectionCard(title = "Apariencia, Día/Noche e Ícono") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ThemeMode.LIGHT to "Claro",
                                ThemeMode.DARK to "Oscuro",
                                ThemeMode.SYSTEM to "Automático"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = p.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingSwitchRow(
                                label = "Colores dinámicos del sistema (Android 12+)",
                                checked = p.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor
                            )
                        }

                        SettingSwitchRow(
                            label = "Animaciones sutiles de estado eléctrico",
                            checked = p.enableElectricAnimations,
                            onCheckedChange = viewModel::setEnableElectricAnimations
                        )

                        Text(
                            text = "Tarjetas de widgets",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WidgetCardStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = p.widgetCardStyle == style,
                                    onClick = { viewModel.setWidgetCardStyle(style) },
                                    label = { Text(style.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("settings_widget_style_${style.name.lowercase()}")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.WbTwilight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Amanecer y atardecer",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (p.autoSolarSyncEnabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = if (p.autoSolarSyncEnabled) "Automático" else "Manual",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Día / Noche: ${ScheduleEngine.formatMinuteOfDay(p.sunriseMinutes, p.use24HourFormat)} – ${ScheduleEngine.formatMinuteOfDay(p.sunsetMinutes, p.use24HourFormat)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = buildString {
                                        append(p.solarLastStatus)
                                        if (p.solarCachedDate.isNotBlank()) {
                                            append(" • Caché: ${p.solarCachedDate}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showDayNightDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_day_night_config_button")
                                    ) {
                                        Text(
                                            text = "Configurar",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = viewModel::syncSolarTimesNow,
                                        enabled = !state.isSolarSyncing,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_solar_sync_now_button")
                                    ) {
                                        if (state.isSolarSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Sincronizar",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clickable {
                                            runCatching { uriHandler.openUri(SunriseSunsetService.ATTRIBUTION_URL) }
                                        }
                                        .padding(vertical = 2.dp)
                                        .testTag("settings_solar_attribution_link")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenInNew,
                                        contentDescription = "Abrir sunrisesunset.io",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Horarios solares de Nueva Gerona por sunrisesunset.io",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                IslaEmblemBadge(
                                    state = if (p.dynamicLauncherIconEnabled) state.primaryHomeState else null,
                                    enableAnimation = p.enableElectricAnimations,
                                    size = 48.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ícono dinámico del lanzador",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (state.hasPrimaryHome) {
                                            "Vinculado a ${state.primaryHomeLabel}: verde con servicio, rojo sin servicio, azul predeterminado."
                                        } else {
                                            "Marca un circuito como 'Mi vivienda' en Inicio para que el ícono cambie según su estado."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = p.dynamicLauncherIconEnabled,
                                    onCheckedChange = viewModel::setDynamicLauncherIconEnabled
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::replayOnboarding,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_replay_onboarding_button")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Repetir asistente inicial",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Export / Import section (JSON & Alphanumeric ONLY — no QR)
                item(key = "transfer_card") {
                    SettingsSectionCard(title = "Exportar e Importar (JSON y Código)") {
                        SettingSwitchRow(
                            label = "Incluir mis favoritos, nombres y comentarios al exportar",
                            checked = includePersonalInExport,
                            onCheckedChange = { includePersonalInExport = it }
                        )

                        Button(
                            onClick = { viewModel.generateExport(includePersonalInExport) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_generate_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exportar código o JSON",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedTextField(
                            value = pasteCodeInput,
                            onValueChange = { pasteCodeInput = it },
                            label = { Text("Pegar código alfanumérico (LUZ1-...)") },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_code_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.inspectAlphanumericCode(pasteCodeInput) },
                                enabled = pasteCodeInput.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_code_button")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Validar código",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_json_button")
                            ) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Importar JSON",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (state.hasBackup) {
                            OutlinedButton(
                                onClick = viewModel::restoreLatestBackup,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Restaurar respaldo anterior",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // HTTPS Sync section
                item(key = "sync_card") {
                    SettingsSectionCard(title = "Sincronización automática por URL (HTTPS)") {
                        OutlinedTextField(
                            value = syncUrlInput,
                            onValueChange = { syncUrlInput = it },
                            label = { Text("URL HTTPS del pronóstico JSON") },
                            placeholder = { Text("https://midominio.com/horarios/configuracion.json") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_url_input")
                        )

                        SettingSwitchRow(
                            label = "Sincronización automática en segundo plano",
                            checked = autoSyncEnabled,
                            onCheckedChange = {
                                autoSyncEnabled = it
                                viewModel.saveSyncSettings(syncUrlInput, it, syncIntervalHours)
                            }
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 6, 12, 24).forEach { hrs ->
                                FilterChip(
                                    selected = syncIntervalHours == hrs,
                                    onClick = {
                                        syncIntervalHours = hrs
                                        viewModel.saveSyncSettings(syncUrlInput, autoSyncEnabled, hrs)
                                    },
                                    label = { Text("Cada ${hrs}h") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.saveSyncSettings(syncUrlInput, autoSyncEnabled, syncIntervalHours) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Guardar URL",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { viewModel.triggerManualSync(syncUrlInput) },
                                enabled = !state.isSyncing,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_now_button")
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Sincronizar",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val lastSyncStr = if (p.lastSyncEpochMillis > 0L) {
                            val remoteVerLabel = AppVersionConfig.formatConfigVersionDisplay(
                                numericVersion = p.lastRemoteConfigVersion,
                                versionName = p.lastRemoteConfigVersionName,
                                fallbackTimestampMillis = p.lastSyncEpochMillis
                            )
                            "Última descarga: ${ScheduleEngine.formatDateTimeReadable(p.lastSyncEpochMillis, p.use24HourFormat, activeZone)} • v$remoteVerLabel"
                        } else {
                            "Sin descargas recientes"
                        }
                        Text(
                            text = "$lastSyncStr\n${p.lastSyncStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Local Notifications section
                item(key = "notifications_card") {
                    SettingsSectionCard(title = "Notificaciones locales") {
                        fun ensurePermAndSave(
                            start: Boolean = p.notifyServiceStart,
                            end: Boolean = p.notifyServiceEnd,
                            restore: Boolean = p.notifyNextRestore,
                            forecast: Boolean = p.notifyNewForecast,
                            lead: Int = p.notifyLeadMinutes,
                            favOnly: Boolean = p.notifyFavoritesOnly
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !ScheduleNotificationManager.hasNotificationPermission(context)
                            ) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.updateNotificationPrefs(start, end, restore, forecast, lead, favOnly)
                        }

                        SettingSwitchRow("Aviso de inicio de servicio", p.notifyServiceStart) {
                            ensurePermAndSave(start = it)
                        }
                        SettingSwitchRow("Aviso de finalización del servicio", p.notifyServiceEnd) {
                            ensurePermAndSave(end = it)
                        }
                        SettingSwitchRow("Aviso de próximo restablecimiento", p.notifyNextRestore) {
                            ensurePermAndSave(restore = it)
                        }
                        SettingSwitchRow("Aviso de nuevo pronóstico descargado", p.notifyNewForecast) {
                            ensurePermAndSave(forecast = it)
                        }
                        SettingSwitchRow("Solo para mis circuitos favoritos", p.notifyFavoritesOnly) {
                            ensurePermAndSave(favOnly = it)
                        }

                        Text("Anticipación del aviso", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                FilterChip(
                                    selected = p.notifyLeadMinutes == mins,
                                    onClick = { ensurePermAndSave(lead = mins) },
                                    label = { Text("$mins min") }
                                )
                            }
                        }
                    }
                }

                // App updates & download channels
                item(key = "app_updates_card") {
                    SettingsSectionCard(title = "Actualizaciones y descargas") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Versión instalada: v${AppVersionConfig.APP_VERSION_NAME}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Código interno: ${AppVersionConfig.APP_VERSION_CODE} • ${p.latestKnownStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.updateAvailable) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.updateAvailable) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = if (state.updateAvailable) {
                                        "Disponible ${AppUpdateService.normalizeVersionLabel(p.latestKnownTag)}"
                                    } else {
                                        "v${AppVersionConfig.APP_VERSION_NAME}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (p.lastUpdateCheckEpochMillis > 0L) {
                            Text(
                                text = "Última comprobación: ${ScheduleEngine.formatDateTimeReadable(p.lastUpdateCheckEpochMillis, p.use24HourFormat, activeZone)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.updateAvailable && p.latestKnownNotes.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = p.latestKnownTitle.ifBlank { p.latestKnownTag },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = p.latestKnownNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (p.latestKnownApkName.isNotBlank()) {
                                        Text(
                                            text = "Archivo recomendado: ${p.latestKnownApkName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::checkAppUpdatesNow,
                                enabled = !state.isCheckingUpdates,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_check_updates_button")
                            ) {
                                if (state.isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (state.isCheckingUpdates) "Buscando…" else "Comprobar",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val targetDownloadUrl = if (state.updateAvailable && p.latestKnownDownloadUrl.isNotBlank()) {
                                p.latestKnownDownloadUrl
                            } else {
                                p.latestKnownReleasePageUrl.ifBlank { AppUpdateService.GITHUB_RELEASES_URL }
                            }
                            Button(
                                onClick = { viewModel.openExternalUrl(targetDownloadUrl) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_download_update_button")
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (state.updateAvailable) "Actualizar" else "Ver Releases",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "Canales de descarga",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.distributionChannels.forEach { channel ->
                                val isActive = channel.status == UpdateChannelStatus.ACTIVE && !channel.url.isNullOrBlank()
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isActive) {
                                                Modifier.clickable { viewModel.openExternalUrl(channel.url!!) }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .testTag("update_channel_${channel.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = channel.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        ) {
                                            Text(
                                                text = if (isActive) "Abrir" else "Próximamente",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isActive) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Disclaimer & App info
                item(key = "disclaimer_card") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Acerca de · Luz Isla",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Versión v${AppVersionConfig.APP_VERSION_NAME} (Compilación ${AppVersionConfig.APP_VERSION_CODE}) • Config. v${p.activeConfigVersionName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Esta aplicación NO es una aplicación oficial de la Empresa Eléctrica ni realiza mediciones en tiempo real de la red. Todos los estados mostrados corresponden a pronósticos y reglas programadas configuradas localmente o sincronizadas mediante archivo JSON. Los datos del 19 y 20 de noviembre de 2026 incluidos inicialmente son únicamente registros históricos de prueba.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
