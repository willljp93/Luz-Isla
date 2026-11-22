package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ImportMergeMode
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ThemeMode
import com.example.notifications.ScheduleNotificationManager
import com.example.ui.components.QrScannerDialog
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var syncUrlInput by remember(state.prefs.syncUrl) { mutableStateOf(state.prefs.syncUrl) }
    var autoSyncEnabled by remember(state.prefs.autoSyncEnabled) { mutableStateOf(state.prefs.autoSyncEnabled) }
    var syncIntervalHours by remember(state.prefs.syncIntervalHours) { mutableIntStateOf(state.prefs.syncIntervalHours) }

    var includePersonalInExport by remember { mutableStateOf(false) }
    var pasteCodeInput by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }

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

                // Appearance section
                item(key = "appearance_card") {
                    SettingsSectionCard(title = "Apariencia") {
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
                                    selected = state.prefs.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Colores dinámicos del sistema (Android 12+)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = state.prefs.dynamicColor,
                                    onCheckedChange = viewModel::setDynamicColor
                                )
                            }
                        }
                    }
                }

                // Export / Import section
                item(key = "transfer_card") {
                    SettingsSectionCard(title = "Exportar e Importar (Sin conexión)") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Incluir mis favoritos, nombres y comentarios al exportar",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = includePersonalInExport,
                                onCheckedChange = { includePersonalInExport = it }
                            )
                        }

                        Button(
                            onClick = { viewModel.generateExport(includePersonalInExport) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_generate_button")
                        ) {
                            Icon(Icons.Filled.QrCode2, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generar código alfanumérico, QR o JSON")
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_code_button")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validar código")
                            }
                            OutlinedButton(
                                onClick = { showQrScanner = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_qr_button")
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Escanear QR")
                            }
                        }

                        OutlinedButton(
                            onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_json_button")
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importar desde archivo JSON")
                        }

                        if (state.hasBackup) {
                            OutlinedButton(
                                onClick = viewModel::restoreLatestBackup,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restaurar respaldo anterior")
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sincronización automática en segundo plano", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = autoSyncEnabled,
                                onCheckedChange = {
                                    autoSyncEnabled = it
                                    viewModel.saveSyncSettings(syncUrlInput, it, syncIntervalHours)
                                }
                            )
                        }

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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar URL")
                            }
                            Button(
                                onClick = { viewModel.triggerManualSync(syncUrlInput) },
                                enabled = !state.isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_now_button")
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar")
                            }
                        }

                        val lastSyncStr = if (state.prefs.lastSyncEpochMillis > 0L) {
                            "Última descarga: ${ScheduleEngine.formatTime12h(state.prefs.lastSyncEpochMillis)} • v${state.prefs.lastRemoteConfigVersion}"
                        } else {
                            "Sin descargas recientes"
                        }
                        Text(
                            text = "$lastSyncStr\n${state.prefs.lastSyncStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Local Notifications section
                item(key = "notifications_card") {
                    SettingsSectionCard(title = "Notificaciones locales") {
                        val p = state.prefs
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

                        NotifSwitch("Aviso de inicio de servicio", p.notifyServiceStart) {
                            ensurePermAndSave(start = it)
                        }
                        NotifSwitch("Aviso de finalización del servicio", p.notifyServiceEnd) {
                            ensurePermAndSave(end = it)
                        }
                        NotifSwitch("Aviso de próximo restablecimiento", p.notifyNextRestore) {
                            ensurePermAndSave(restore = it)
                        }
                        NotifSwitch("Aviso de nuevo pronóstico descargado", p.notifyNewForecast) {
                            ensurePermAndSave(forecast = it)
                        }
                        NotifSwitch("Solo para mis circuitos favoritos", p.notifyFavoritesOnly) {
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
                                    text = "Luz Isla – Horarios Eléctricos (v1.0.0)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
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

    // Export modal dialog (Code + QR / Multi-part QR + Save JSON)
    state.exportBundle?.let { bundle ->
        var currentQrIndex by remember(bundle) { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = viewModel::dismissExportBundle,
            title = { Text("Exportación generada") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bundle.isMultiPartQr) {
                        Text(
                            text = "Configuración grande: dividida en ${bundle.qrBitmaps.size} códigos QR numerados sin truncar datos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    bundle.qrBitmaps.getOrNull(currentQrIndex)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Código QR de configuración",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    if (bundle.qrBitmaps.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { if (currentQrIndex > 0) currentQrIndex-- },
                                enabled = currentQrIndex > 0
                            ) { Text("Anterior") }
                            Text(
                                text = "Parte ${currentQrIndex + 1} / ${bundle.qrBitmaps.size}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            OutlinedButton(
                                onClick = { if (currentQrIndex < bundle.qrBitmaps.lastIndex) currentQrIndex++ },
                                enabled = currentQrIndex < bundle.qrBitmaps.lastIndex
                            ) { Text("Siguiente") }
                        }
                    }

                    Text(
                        text = "Código alfanumérico (${bundle.alphanumericCode.length} caracteres):",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bundle.alphanumericCode.take(180) + if (bundle.alphanumericCode.length > 180) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(bundle.alphanumericCode)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar")
                        }
                        OutlinedButton(
                            onClick = { viewModel.shareTextCode(bundle.alphanumericCode) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartir")
                        }
                    }
                    Button(
                        onClick = { saveJsonLauncher.launch("luz_isla_config.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar como archivo JSON")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissExportBundle) { Text("Cerrar") }
            }
        )
    }

    // Import Summary & Merge/Replace Confirmation Dialog
    state.pendingImport?.let { review ->
        val s = review.summary
        var includePersonal by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingImport,
            title = { Text("Resumen de importación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Origen: ${s.sourceName} (v${s.configVersion})", fontWeight = FontWeight.Bold)
                    Text("• Circuitos: ${s.circuitCount}")
                    Text("• Grupos rotativos: ${s.groupCount}")
                    Text("• Horarios fijos: ${s.fixedRuleCount}")
                    Text("• Pronósticos por fecha: ${s.forecastCount}")
                    Text("• Excepciones: ${s.exceptionCount} • Relaciones: ${s.relationCount}")
                    Text(
                        text = if (s.sha256Verified) "Integridad SHA-256: Verificada correctamente" else "Integridad SHA-256: No incluida en JSON externo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (s.includesPersonalData) {
                        NotifSwitch("Importar también favoritos y nombres personales", includePersonal) {
                            includePersonal = it
                        }
                    }
                    Text(
                        text = "Se guardará un respaldo automático de tu configuración actual antes de aplicar los cambios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            viewModel.confirmPendingImport(ImportMergeMode.MERGE_KEEP_PERSONAL, includePersonal)
                            pasteCodeInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Combinar (conservar mis datos)")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.confirmPendingImport(ImportMergeMode.REPLACE_SCHEDULE_KEEP_PERSONAL, includePersonal)
                            pasteCodeInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reemplazar programación")
                    }
                    TextButton(
                        onClick = viewModel::dismissPendingImport,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeAssembled = { code ->
                showQrScanner = false
                pasteCodeInput = code
                viewModel.inspectAlphanumericCode(code)
            },
            onDismiss = { showQrScanner = false }
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
private fun NotifSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
