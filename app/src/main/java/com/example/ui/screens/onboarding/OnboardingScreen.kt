package com.example.ui.screens.onboarding

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.ScheduledState
import com.example.domain.model.ThemeMode
import com.example.domain.version.AppVersionConfig
import com.example.ui.components.IslaEmblemBadge
import com.example.ui.components.TimezonePickerDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.time.ZoneId

@Composable
fun OnboardingScreen(
    initialThemeMode: ThemeMode,
    initialDynamicColor: Boolean,
    initialUseSystemTimezone: Boolean,
    initialManualTimezoneId: String,
    initialUse24h: Boolean,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onFinish: (useSystemTz: Boolean, manualTz: String, use24h: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var themeMode by remember(initialThemeMode) { mutableStateOf(initialThemeMode) }
    var dynamicColor by remember(initialDynamicColor) { mutableStateOf(initialDynamicColor) }
    var useSystemTz by remember(initialUseSystemTimezone) { mutableStateOf(initialUseSystemTimezone) }
    var manualTz by remember(initialManualTimezoneId) { mutableStateOf(initialManualTimezoneId) }
    var use24h by remember(initialUse24h) { mutableStateOf(initialUse24h) }
    var showTzPicker by remember { mutableStateOf(false) }

    val nowMillis = remember { System.currentTimeMillis() }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IslaEmblemBadge(
                            state = ScheduledState.UNKNOWN,
                            enableAnimation = false,
                            size = 56.dp
                        )
                        Column {
                            Text(
                                text = "Bienvenido a Luz Isla",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Paso ${step + 1} de 3 • Configuración inicial • v${AppVersionConfig.APP_VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (step + 1) / 3f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    when (step) {
                        0 -> {
                            Text(
                                text = "1. Apariencia de la aplicación",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Elige el tema visual con el que te sientas más cómodo de día y de noche.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ThemeChoiceCard(
                                title = "Automático del sistema",
                                subtitle = "Se adapta al modo claro u oscuro de tu teléfono",
                                icon = Icons.Filled.SettingsBrightness,
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    themeMode = ThemeMode.SYSTEM
                                    onThemeChanged(ThemeMode.SYSTEM)
                                }
                            )
                            ThemeChoiceCard(
                                title = "Tema claro",
                                subtitle = "Alta legibilidad con luz solar",
                                icon = Icons.Filled.LightMode,
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = {
                                    themeMode = ThemeMode.LIGHT
                                    onThemeChanged(ThemeMode.LIGHT)
                                }
                            )
                            ThemeChoiceCard(
                                title = "Tema oscuro",
                                subtitle = "Descansa la vista durante cortes nocturnos",
                                icon = Icons.Filled.DarkMode,
                                selected = themeMode == ThemeMode.DARK,
                                onClick = {
                                    themeMode = ThemeMode.DARK
                                    onThemeChanged(ThemeMode.DARK)
                                }
                            )

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Usar colores dinámicos del sistema (Android 12+)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = dynamicColor,
                                            onCheckedChange = {
                                                dynamicColor = it
                                                onDynamicColorChanged(it)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            Text(
                                text = "2. Zona horaria de referencia",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Define cómo calcular el inicio y fin de cada bloque eléctrico respecto a la hora local.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val sysZone = remember { ZoneId.systemDefault() }
                            val sysOffsetLabel = remember {
                                ScheduleEngine.formatGmtOffset(sysZone)
                            }
                            val manualZoneObj = remember(manualTz) {
                                ScheduleEngine.parseManualZoneId(manualTz)
                            }
                            val manualSummary = remember(manualZoneObj) {
                                ScheduleEngine.formatZoneSummary(manualZoneObj)
                            }
                            ThemeChoiceCard(
                                title = "Automática ($sysOffsetLabel)",
                                subtitle = "${ScheduleEngine.formatZoneSummary(sysZone)} • Hora: ${ScheduleEngine.formatTime(nowMillis, use24h, sysZone)}",
                                icon = Icons.Filled.Public,
                                selected = useSystemTz,
                                onClick = { useSystemTz = true }
                            )
                            ThemeChoiceCard(
                                title = "Manual ($manualSummary)",
                                subtitle = "Hora resultante: ${ScheduleEngine.formatTime(nowMillis, use24h, manualZoneObj)}",
                                icon = Icons.Filled.Public,
                                selected = !useSystemTz,
                                onClick = {
                                    useSystemTz = false
                                    showTzPicker = true
                                }
                            )

                            if (!useSystemTz) {
                                OutlinedButton(
                                    onClick = { showTzPicker = true },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Cambiar zona ($manualSummary)",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        2 -> {
                            Text(
                                text = "3. Formato de hora",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Se aplicará en relojes, tarjetas, calendario, widgets, líneas 3D y mensajes compartidos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val zone = ScheduleEngine.effectiveZone(useSystemTz, manualTz)
                            ThemeChoiceCard(
                                title = "12 horas (AM / PM)",
                                subtitle = "Ejemplo actual: ${ScheduleEngine.formatTime12h(nowMillis, zone)}",
                                icon = Icons.Filled.AccessTime,
                                selected = !use24h,
                                onClick = { use24h = false }
                            )
                            ThemeChoiceCard(
                                title = "24 horas",
                                subtitle = "Ejemplo actual: ${ScheduleEngine.formatTime24h(nowMillis, zone)}",
                                icon = Icons.Filled.AccessTime,
                                selected = use24h,
                                onClick = { use24h = true }
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Vista previa de horario:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${ScheduleEngine.formatMinuteOfDay(9 * 60, use24h)} – ${ScheduleEngine.formatMinuteOfDay(17 * 60, use24h)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Anterior", maxLines = 1, softWrap = false)
                        }
                    } else {
                        TextButton(
                            onClick = { onFinish(useSystemTz, manualTz, use24h) },
                            modifier = Modifier.testTag("onboarding_skip_button")
                        ) {
                            Text("Omitir", maxLines = 1, softWrap = false)
                        }
                    }

                    if (step < 2) {
                        Button(
                            onClick = { step++ },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("onboarding_next_button")
                        ) {
                            Text("Siguiente", maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = { onFinish(useSystemTz, manualTz, use24h) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("onboarding_finish_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Comenzar", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    if (showTzPicker) {
        TimezonePickerDialog(
            currentZoneId = manualTz,
            use24h = use24h,
            isUsingSystemTimezone = useSystemTz,
            onUseSystemTimezone = {
                useSystemTz = true
                showTzPicker = false
            },
            onSelect = {
                manualTz = it
                useSystemTz = false
                showTzPicker = false
            },
            onDismiss = { showTzPicker = false }
        )
    }
}

@Composable
private fun ThemeChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}


