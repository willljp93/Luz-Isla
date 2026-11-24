package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ScheduleEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private data class IanaListEntry(
    val zoneId: String,
    val title: String,
    val subtitle: String,
    val gmtLabel: String,
    val isSystemDefault: Boolean
)

@Composable
fun TimezonePickerDialog(
    currentZoneId: String,
    use24h: Boolean = false,
    isUsingSystemTimezone: Boolean = false,
    onUseSystemTimezone: (() -> Unit)? = null,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val nowInstant = remember(nowMillis) { Instant.ofEpochMilli(nowMillis) }
    val systemZone = remember { ZoneId.systemDefault() }
    val systemGmtLabel = remember(nowInstant) {
        ScheduleEngine.formatGmtOffset(systemZone, nowInstant)
    }
    val systemHourOffset = remember(systemGmtLabel) {
        ScheduleEngine.parseGmtOffsetHours(systemGmtLabel)
    }

    var pendingZoneId by remember(currentZoneId) { mutableStateOf(currentZoneId) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Husos GMT (-12..+14), 1 = Zonas IANA
    var searchQuery by remember { mutableStateOf("") }

    val pendingZone = remember(pendingZoneId) { ScheduleEngine.parseManualZoneId(pendingZoneId) }
    val pendingGmtLabel = remember(pendingZone, nowInstant) {
        ScheduleEngine.formatGmtOffset(pendingZone, nowInstant)
    }
    val pendingHourOffset = remember(pendingGmtLabel) {
        ScheduleEngine.parseGmtOffsetHours(pendingGmtLabel)
    }
    val pendingCityHint = remember(pendingZone) {
        ScheduleEngine.presetLabelForZone(pendingZone)
    }

    val allHours = remember { (-12..14).toList() }

    val ianaEntries = remember(searchQuery, nowInstant) {
        val q = searchQuery.trim()
        val presetMap = ScheduleEngine.realTimezonePresets.associateBy { it.zoneId }

        if (q.isEmpty()) {
            ScheduleEngine.realTimezonePresets.map { preset ->
                val z = ScheduleEngine.parseManualZoneId(preset.zoneId)
                IanaListEntry(
                    zoneId = preset.zoneId,
                    title = preset.zoneId,
                    subtitle = "${preset.displayName} • ${preset.regionLabel}",
                    gmtLabel = ScheduleEngine.formatGmtOffset(z, nowInstant),
                    isSystemDefault = preset.zoneId.equals(systemZone.id, ignoreCase = true)
                )
            }
        } else {
            val presetMatches = ScheduleEngine.realTimezonePresets.filter { preset ->
                val z = ScheduleEngine.parseManualZoneId(preset.zoneId)
                val gmt = ScheduleEngine.formatGmtOffset(z, nowInstant)
                preset.zoneId.contains(q, ignoreCase = true) ||
                    preset.displayName.contains(q, ignoreCase = true) ||
                    preset.regionLabel.contains(q, ignoreCase = true) ||
                    gmt.contains(q, ignoreCase = true)
            }.map { preset ->
                val z = ScheduleEngine.parseManualZoneId(preset.zoneId)
                IanaListEntry(
                    zoneId = preset.zoneId,
                    title = preset.zoneId,
                    subtitle = "${preset.displayName} • ${preset.regionLabel}",
                    gmtLabel = ScheduleEngine.formatGmtOffset(z, nowInstant),
                    isSystemDefault = preset.zoneId.equals(systemZone.id, ignoreCase = true)
                )
            }

            val existingIds = presetMatches.map { it.zoneId }.toHashSet()
            val extraIds = ZoneId.getAvailableZoneIds()
                .asSequence()
                .filter { id ->
                    id !in existingIds && !id.startsWith("SystemV/") && (
                        id.contains(q, ignoreCase = true) ||
                            ScheduleEngine.formatGmtOffset(ZoneId.of(id), nowInstant).contains(q, ignoreCase = true)
                        )
                }
                .sorted()
                .take(45)
                .map { id ->
                    val z = ZoneId.of(id)
                    IanaListEntry(
                        zoneId = id,
                        title = id,
                        subtitle = id.substringAfter('/').replace('_', ' '),
                        gmtLabel = ScheduleEngine.formatGmtOffset(z, nowInstant),
                        isSystemDefault = id.equals(systemZone.id, ignoreCase = true)
                    )
                }
                .toList()

            presetMatches + extraIds
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Zona horaria",
                maxLines = 1,
                softWrap = false
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Live detection + selected clock verification card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Dispositivo: ${systemZone.id} ($systemGmtLabel)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onUseSystemTimezone != null && !isUsingSystemTimezone) {
                                TextButton(
                                    onClick = onUseSystemTimezone,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("tz_use_system_button")
                                ) {
                                    Text(
                                        text = "Usar auto",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ScheduleEngine.formatZoneSummary(pendingZone, nowInstant),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!pendingCityHint.isNullOrBlank()) {
                                    Text(
                                        text = pendingCityHint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = ScheduleEngine.formatTime(nowMillis, use24h, pendingZone),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode switch tabs: ordered GMT grid vs searchable real IANA catalog
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        leadingIcon = {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text("GMT (-12 a +14)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tz_tab_gmt")
                    )
                    FilterChip(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = {
                            Text("Zonas IANA", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("tz_tab_iana")
                    )
                }

                if (activeTab == 0) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 285.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        allHours.chunked(3).forEach { rowHours ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowHours.forEach { offsetHour ->
                                    val gmtLabel = ScheduleEngine.formatHourOffsetId(offsetHour)
                                    val preset = remember(offsetHour, nowInstant) {
                                        ScheduleEngine.representativePresetForOffset(offsetHour, nowInstant)
                                    }
                                    val cityShort = remember(preset) {
                                        preset.displayName
                                            .substringBefore(" (")
                                            .substringBefore(" /")
                                    }
                                    val isSelected = pendingHourOffset == offsetHour &&
                                        !pendingGmtLabel.contains(':')
                                    val isDeviceOffset = systemHourOffset == offsetHour &&
                                        !systemGmtLabel.contains(':')

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isDeviceOffset -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                            else -> MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected || isDeviceOffset) 1.5.dp else 1.dp,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isDeviceOffset -> MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                pendingZoneId = ScheduleEngine.representativeZoneIdForOffset(
                                                    offsetHour,
                                                    nowInstant
                                                )
                                            }
                                            .testTag("tz_offset_${gmtLabel.lowercase()}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = gmtLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                if (isDeviceOffset) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PhoneAndroid,
                                                        contentDescription = "Zona del dispositivo",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = cityShort,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    CompactSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Buscar país, ciudad o GMT…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tz_iana_search_input")
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(ianaEntries, key = { it.zoneId }) { entry ->
                            val z = remember(entry.zoneId) { ScheduleEngine.parseManualZoneId(entry.zoneId) }
                            val isSelected = pendingZone.id.equals(entry.zoneId, ignoreCase = true)
                            val localTime = ScheduleEngine.formatTime(nowMillis, use24h, z)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { pendingZoneId = entry.zoneId }
                                    .testTag("tz_iana_${entry.zoneId}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (entry.isSystemDefault) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "Dispositivo",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = entry.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = localTime,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = entry.gmtLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(pendingZoneId) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("tz_dialog_apply_button")
            ) {
                Text(
                    text = "Aplicar ($pendingGmtLabel)",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", maxLines = 1, softWrap = false)
            }
        }
    )
}
