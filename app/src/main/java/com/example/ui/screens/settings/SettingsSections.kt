package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.BottomBarBehavior
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetCardStyle
import com.example.domain.version.AppVersionConfig
import com.example.notifications.ScheduleNotificationManager
import com.example.sync.AppUpdateService
import com.example.sync.UpdateChannelStatus
import com.example.ui.components.IslaEmblemBadge
import com.example.ui.theme.LocalElectricStatusColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun ActiveConfigStatusCard(
    prefs: UserPreferences,
    activeZone: ZoneId
) {
    SettingsSectionCard(title = "Estado de la aplicación y configuración") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Luz Isla v${AppVersionConfig.APP_VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Compilación #${AppVersionConfig.APP_VERSION_CODE} • Datos v${prefs.activeConfigVersionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "v${prefs.activeConfigVersionName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        val lastTimestamp = prefs.activeConfigTimestampMillis.takeIf { it > 0L }
            ?: prefs.lastForecastUpdateEpochMillis.takeIf { it > 0L }
        Text(
            text = buildString {
                append("Origen: ${prefs.activeConfigSource}")
                if (lastTimestamp != null) {
                    append(" • ${ScheduleEngine.formatDateTimeReadable(lastTimestamp, prefs.use24HourFormat, activeZone)}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimezoneAndFormatSection(
    prefs: UserPreferences,
    activeZone: ZoneId,
    currentNowMillis: Long,
    onSetTimezonePreference: (Boolean, String) -> Unit,
    onOpenTimezoneDialog: () -> Unit,
    onSetUse24HourFormat: (Boolean) -> Unit
) {
    val nowInstant = Instant.ofEpochMilli(currentNowMillis)
    val systemZone = ZoneId.systemDefault()
    val detectedLabel = ScheduleEngine.formatReadableZoneTitle(systemZone, nowInstant)
    val activeZoneSummary = ScheduleEngine.formatReadableZoneTitle(activeZone, nowInstant)

    SettingsSectionCard(title = "Zona horaria y formato de hora") {
        Text("Origen de zona horaria", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = prefs.useSystemTimezone,
                onClick = { onSetTimezonePreference(true, prefs.manualTimezoneId) },
                leadingIcon = {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = "Automático (${ScheduleEngine.formatGmtOffset(systemZone, nowInstant)})",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("settings_tz_auto_chip")
            )
            FilterChip(
                selected = !prefs.useSystemTimezone,
                onClick = onOpenTimezoneDialog,
                leadingIcon = {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = if (!prefs.useSystemTimezone) {
                            "Manual (${ScheduleEngine.formatGmtOffset(activeZone, nowInstant)})"
                        } else {
                            "Manual (elegir)"
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier.testTag("settings_tz_manual_chip")
            )
        }

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
                    text = "Hora actual: ${ScheduleEngine.formatTime(currentNowMillis, prefs.use24HourFormat, activeZone)} ($activeZoneSummary)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dispositivo detectado: $detectedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onOpenTimezoneDialog,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_tz_picker_button")
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Seleccionar huso horario / GMT",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Text("Formato de hora", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !prefs.use24HourFormat,
                onClick = { onSetUse24HourFormat(false) },
                leadingIcon = {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("12 horas (AM/PM)", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("settings_format_12h_chip")
            )
            FilterChip(
                selected = prefs.use24HourFormat,
                onClick = { onSetUse24HourFormat(true) },
                leadingIcon = {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("24 horas", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("settings_format_24h_chip")
            )
        }
    }
}

@Composable
fun AppearanceSection(
    state: SettingsUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetEnableElectricAnimations: (Boolean) -> Unit,
    onSetBottomBarBehavior: (BottomBarBehavior) -> Unit,
    onSetCenterTimelineOnNow: (Boolean) -> Unit,
    onSetWidgetCardStyle: (WidgetCardStyle) -> Unit,
    onOpenDayNightDialog: () -> Unit,
    onSyncSolarNow: () -> Unit,
    onOpenSolarAttribution: () -> Unit,
    onSetDynamicLauncherIcon: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val p = state.prefs
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(title = "Línea de tiempo") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Centrar línea de tiempo en la hora actual",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "En Hoy muestra una ventana móvil de 12 h antes y 12 h después con Ahora fijo al centro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = p.centerTimelineOnNow,
                    onCheckedChange = onSetCenterTimelineOnNow,
                    modifier = Modifier.testTag("settings_center_timeline_switch")
                )
            }
        }

        SettingsSectionCard(title = "Tema, animaciones y barra") {
            Text("Tema visual", style = MaterialTheme.typography.labelLarge)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(ThemeMode.SYSTEM, "Sistema", Icons.Filled.BrightnessAuto),
                    Triple(ThemeMode.LIGHT, "Claro", Icons.Filled.LightMode),
                    Triple(ThemeMode.DARK, "Oscuro", Icons.Filled.DarkMode)
                ).forEach { (mode, label, icon) ->
                    FilterChip(
                        selected = p.themeMode == mode,
                        onClick = { onSetThemeMode(mode) },
                        leadingIcon = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(label, maxLines = 1, softWrap = false) }
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingSwitchRow(
                    label = "Colores dinámicos del sistema (Android 12+)",
                    checked = p.dynamicColor,
                    onCheckedChange = onSetDynamicColor
                )
            }

            SettingSwitchRow(
                label = "Animaciones sutiles de estado eléctrico",
                checked = p.enableElectricAnimations,
                onCheckedChange = onSetEnableElectricAnimations
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text("Comportamiento del menú inferior", style = MaterialTheme.typography.labelLarge)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BottomBarBehavior.entries.forEach { behavior ->
                    val behaviorIcon = when (behavior) {
                        BottomBarBehavior.AUTO -> Icons.Filled.SwapVert
                        BottomBarBehavior.PILL_HANDLE -> Icons.Filled.DragHandle
                    }
                    FilterChip(
                        selected = p.bottomBarBehavior == behavior,
                        onClick = { onSetBottomBarBehavior(behavior) },
                        leadingIcon = {
                            Icon(imageVector = behaviorIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(behavior.label, maxLines = 1, softWrap = false) },
                        modifier = Modifier.testTag("settings_bottom_bar_${behavior.name.lowercase()}")
                    )
                }
            }
            Text(
                text = p.bottomBarBehavior.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text("Tarjetas de widgets", style = MaterialTheme.typography.labelLarge)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetCardStyle.entries.forEach { style ->
                    val styleIcon = when (style) {
                        WidgetCardStyle.SYSTEM -> Icons.Filled.AutoAwesome
                        WidgetCardStyle.LIGHT -> Icons.Filled.LightMode
                        WidgetCardStyle.DARK -> Icons.Filled.DarkMode
                        WidgetCardStyle.TRANSPARENT -> Icons.Filled.Opacity
                    }
                    FilterChip(
                        selected = p.widgetCardStyle == style,
                        onClick = { onSetWidgetCardStyle(style) },
                        leadingIcon = {
                            Icon(imageVector = styleIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(style.label, maxLines = 1, softWrap = false) },
                        modifier = Modifier.testTag("settings_widget_style_${style.name.lowercase()}")
                    )
                }
            }
        }

        SettingsSectionCard(title = "Ciclo solar, ícono y bienvenida") {
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
                        text = "Día / Noche: ${ScheduleEngine.formatMinuteOfDay(p.sunriseMinutes, p.use24HourFormat)}\u2060–\u2060${ScheduleEngine.formatMinuteOfDay(p.sunsetMinutes, p.use24HourFormat)}",
                        style = MaterialTheme.typography.titleSmall,
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

                    ResponsiveButtonPair(
                        first = { modifier ->
                            OutlinedButton(
                                onClick = onOpenDayNightDialog,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_day_night_config_button")
                            ) {
                                Text(
                                    text = "Configurar",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        second = { modifier ->
                            Button(
                                onClick = onSyncSolarNow,
                                enabled = !state.isSolarSyncing,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_solar_sync_now_button")
                            ) {
                                if (state.isSolarSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Sincronizar",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onOpenSolarAttribution)
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                            .testTag("settings_solar_attribution_link")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
                        onCheckedChange = onSetDynamicLauncherIcon
                    )
                }
            }

            OutlinedButton(
                onClick = onReplayOnboarding,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("settings_replay_onboarding_button")
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Repetir asistente inicial",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DataAndSyncSection(
    state: SettingsUiState,
    activeZone: ZoneId,
    syncUrlInput: String,
    onSyncUrlChange: (String) -> Unit,
    autoSyncEnabled: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    syncIntervalHours: Int,
    onSyncIntervalChange: (Int) -> Unit,
    includePersonalInExport: Boolean,
    onIncludePersonalChange: (Boolean) -> Unit,
    pasteCodeInput: String,
    onPasteCodeChange: (String) -> Unit,
    onSetCheckAllOnAppOpen: (Boolean) -> Unit,
    onRunUnifiedCheckNow: () -> Unit,
    onSaveSyncSettings: () -> Unit,
    onTriggerManualSync: () -> Unit,
    onGenerateExport: () -> Unit,
    onInspectCode: () -> Unit,
    onOpenJsonImport: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val p = state.prefs
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(title = "Comprobación unificada") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Comprobar todo al abrir la app",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = p.checkAllOnAppOpen,
                    onCheckedChange = onSetCheckAllOnAppOpen,
                    modifier = Modifier.testTag("settings_check_all_on_open_switch")
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedStatusRow("Configuración JSON", p.lastUnifiedJsonStatus)
                    UnifiedStatusRow("Horarios solares", p.lastUnifiedSolarStatus)
                    UnifiedStatusRow("Versión de la app", p.lastUnifiedUpdateStatus)
                    if (p.lastUnifiedCheckEpochMillis > 0L) {
                        Text(
                            text = "Última comprobación: ${ScheduleEngine.formatDateTimeReadable(p.lastUnifiedCheckEpochMillis, p.use24HourFormat, activeZone)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onRunUnifiedCheckNow,
                enabled = !state.isCheckingUnified,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("settings_check_all_now_button")
            ) {
                if (state.isCheckingUnified) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isCheckingUnified) "Comprobando todo…" else "Comprobar todo ahora",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SettingsSectionCard(title = "Sincronización por URL (HTTPS)") {
            OutlinedTextField(
                value = syncUrlInput,
                onValueChange = onSyncUrlChange,
                label = { Text("URL HTTPS de la configuración JSON") },
                placeholder = { Text(com.example.sync.HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_url_input")
            )
            if (syncUrlInput.trim() != com.example.sync.HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL) {
                OutlinedButton(
                    onClick = { onSyncUrlChange(com.example.sync.HttpsSyncManager.DEFAULT_GITHUB_SYNC_URL) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("sync_reset_default_url_button")
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Usar URL GitHub predeterminada", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            SettingSwitchRow(
                label = "Sincronización automática de URL",
                checked = autoSyncEnabled,
                onCheckedChange = onAutoSyncChange
            )

            @OptIn(ExperimentalMaterial3Api::class)
            run {
                val intervalOptions = listOf(
                    Triple(0, "Al abrir la app", Icons.Filled.Sync),
                    Triple(1, "Cada 1h", Icons.Filled.Schedule),
                    Triple(24, "Cada 24h", Icons.Filled.Update)
                )
                val selectedOption = intervalOptions.firstOrNull { it.first == syncIntervalHours } ?: intervalOptions.first()
                var intervalExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = !intervalExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedOption.second,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Frecuencia de sincronización") },
                        leadingIcon = {
                            Icon(
                                imageVector = selectedOption.third,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("sync_interval_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        intervalOptions.forEach { (hrs, label, icon) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    onSyncIntervalChange(hrs)
                                    intervalExpanded = false
                                },
                                modifier = Modifier.testTag(
                                    if (hrs == 0) "sync_interval_app_open" else "sync_interval_${hrs}h"
                                )
                            )
                        }
                    }
                }
            }

            ResponsiveButtonPair(
                first = { modifier ->
                    OutlinedButton(
                        onClick = onSaveSyncSettings,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Guardar URL",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                second = { modifier ->
                    Button(
                        onClick = onTriggerManualSync,
                        enabled = !state.isSyncing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = modifier
                            .defaultMinSize(minHeight = 48.dp)
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )

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

        SettingsSectionCard(title = "Exportar e Importar (JSON y Código)") {
            SettingSwitchRow(
                label = "Incluir mis favoritos, nombres y comentarios al exportar",
                checked = includePersonalInExport,
                onCheckedChange = onIncludePersonalChange
            )

            Button(
                onClick = onGenerateExport,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("export_generate_button")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exportar código o JSON",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedTextField(
                value = pasteCodeInput,
                onValueChange = onPasteCodeChange,
                label = { Text("Pegar código alfanumérico (LUZ1-...)") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_code_input")
            )

            ResponsiveButtonPair(
                first = { modifier ->
                    Button(
                        onClick = onInspectCode,
                        enabled = pasteCodeInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("import_code_button")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Validar código",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                second = { modifier ->
                    OutlinedButton(
                        onClick = onOpenJsonImport,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("import_json_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Importar JSON",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )

            if (state.hasBackup) {
                OutlinedButton(
                    onClick = onRestoreBackup,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Restaurar respaldo anterior",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsAndAppSection(
    state: SettingsUiState,
    activeZone: ZoneId,
    context: android.content.Context,
    notifPermLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onUpdateNotificationPrefs: (Boolean, Boolean, Boolean, Boolean, Int, Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val p = state.prefs
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                onUpdateNotificationPrefs(start, end, restore, forecast, lead, favOnly)
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
            SettingSwitchRow("Aviso de nueva configuración de circuitos", p.notifyNewForecast) {
                ensurePermAndSave(forecast = it)
            }
            SettingSwitchRow("Solo para mis circuitos favoritos", p.notifyFavoritesOnly) {
                ensurePermAndSave(favOnly = it)
            }

            @OptIn(ExperimentalMaterial3Api::class)
            run {
                val leadOptions = listOf(
                    0 to "Sin antelación",
                    5 to "5 min",
                    10 to "10 min",
                    15 to "15 min",
                    30 to "30 min",
                    60 to "1 hora"
                )
                val selectedLeadLabel = leadOptions.firstOrNull { it.first == p.notifyLeadMinutes }?.second
                    ?: when (p.notifyLeadMinutes) {
                        0 -> "Sin antelación"
                        60 -> "1 hora"
                        else -> "${p.notifyLeadMinutes} min"
                    }
                var leadExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = leadExpanded,
                    onExpandedChange = { leadExpanded = !leadExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedLeadLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Anticipación del aviso") },
                        leadingIcon = {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = leadExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("settings_notify_lead_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = leadExpanded,
                        onDismissRequest = { leadExpanded = false }
                    ) {
                        leadOptions.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    ensurePermAndSave(lead = mins)
                                    leadExpanded = false
                                },
                                modifier = Modifier.testTag("settings_notify_lead_$mins")
                            )
                        }
                    }
                }
            }

            val summary = state.notificationSummary
            val isMissingFavScope = summary.anyAlertEnabled && summary.favoritesOnly && summary.monitoredCircuitCount == 0
            val scopeBg = if (isMissingFavScope) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val scopeFg = if (isMissingFavScope) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scopeBg,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isMissingFavScope) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_notification_scope_card")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    val scopeMessage = when {
                        !summary.anyAlertEnabled ->
                            "Los avisos de horario están desactivados. Activa inicio, fin o restablecimiento para programar recordatorios."
                        summary.favoritesOnly && summary.monitoredCircuitCount == 0 ->
                            "No tienes Mi vivienda ni circuitos favoritos activos: no recibirás avisos hasta marcar uno o desactivar \"Solo para mis circuitos favoritos\"."
                        summary.favoritesOnly ->
                            "Monitoreando ${summary.monitoredCircuitCount} circuito(s) (Mi vivienda y Favoritos). Se omiten ${summary.excludedNonFavoriteCount} circuito(s) no favoritos."
                        else ->
                            "Monitoreando ${summary.monitoredCircuitCount} circuito(s) activos. Límite de ${ScheduleNotificationManager.MAX_SCHEDULED_ALARMS} alarmas en cola con prioridad para Mi vivienda y Favoritos, más reposición automática."
                    }
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = scopeFg,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = scopeMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = scopeFg,
                            softWrap = true
                        )
                    }
                    Text(
                        text = "Precisión aproximada (ahorro de batería): las alertas usan alarmas optimizadas sin pedir permisos de alarma exacta, por lo que Android puede retrasarlas unos minutos en modo reposo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scopeFg.copy(alpha = 0.9f),
                        softWrap = true,
                        modifier = Modifier.testTag("settings_notification_battery_note")
                    )
                }
            }
        }

        SettingsSectionCard(title = "Actualizaciones y descargas") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Versión instalada: v${AppVersionConfig.APP_VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
                Text(
                    text = "Código interno: ${AppVersionConfig.APP_VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                "Al día • v${AppVersionConfig.APP_VERSION_NAME}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.updateAvailable) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (p.latestKnownStatus.isNotBlank()) {
                        Text(
                            text = p.latestKnownStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.updateAvailable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            softWrap = true,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            if (p.lastUpdateCheckEpochMillis > 0L) {
                Text(
                    text = "Última comprobación: ${ScheduleEngine.formatDateTimeReadable(p.lastUpdateCheckEpochMillis, p.use24HourFormat, activeZone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val cleanReleaseTitle = AppUpdateService.formatReleaseNotesForDisplay(
                p.latestKnownTitle.ifBlank { p.latestKnownTag }
            )
            val cleanReleaseNotes = AppUpdateService.formatReleaseNotesForDisplay(p.latestKnownNotes)
            if (state.updateAvailable && cleanReleaseNotes.isNotBlank()) {
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
                            text = cleanReleaseTitle.ifBlank { p.latestKnownTag },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            softWrap = true
                        )
                        Text(
                            text = cleanReleaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (p.latestKnownApkName.isNotBlank()) {
                            Text(
                                text = "Archivo recomendado: ${p.latestKnownApkName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            ResponsiveButtonPair(
                first = { modifier ->
                    OutlinedButton(
                        onClick = onCheckUpdatesNow,
                        enabled = !state.isCheckingUpdates,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_check_updates_button")
                    ) {
                        if (state.isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (state.isCheckingUpdates) "Buscando…" else "Comprobar",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                second = { modifier ->
                    val targetDownloadUrl = if (state.updateAvailable && p.latestKnownDownloadUrl.isNotBlank()) {
                        p.latestKnownDownloadUrl
                    } else {
                        p.latestKnownReleasePageUrl.ifBlank { AppUpdateService.GITHUB_RELEASES_URL }
                    }
                    Button(
                        onClick = { onOpenExternalUrl(targetDownloadUrl) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_download_update_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (state.updateAvailable) "Actualizar" else "Ver Releases",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )

            Text(
                text = "Canales de descarga",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.distributionChannels.forEach { channel ->
                    val channelUrl = channel.url
                    val isActive = channel.status == UpdateChannelStatus.ACTIVE && !channelUrl.isNullOrBlank()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isActive) {
                                    Modifier.clickable { onOpenExternalUrl(channelUrl) }
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

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
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
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Versión v${AppVersionConfig.APP_VERSION_NAME} (Compilación ${AppVersionConfig.APP_VERSION_CODE}) • Config. v${p.activeConfigVersionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Esta aplicación NO es una aplicación oficial de la Empresa Eléctrica ni realiza mediciones en tiempo real de la red. Todos los estados mostrados corresponden a horarios y reglas programadas configuradas localmente o sincronizadas mediante archivo JSON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponsiveButtonPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    if (fontScale >= 1.25f) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun UnifiedStatusRow(title: String, status: String) {
    val statusColors = LocalElectricStatusColors.current
    val lower = status.lowercase()
    val (icon, tint) = when {
        "error" in lower || "fallo" in lower || "rechaz" in lower || "no se pudo" in lower || "sin conexión" in lower ->
            Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        "pendiente" in lower || "sin comprobar" in lower || "desactivad" in lower || "omitid" in lower ->
            Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            Icons.Filled.CheckCircle to statusColors.onServiceContainer
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                softWrap = true
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
            modifier = Modifier.padding(start = 22.dp)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
