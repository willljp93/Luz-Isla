package com.example.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.CircuitStatusEvaluation
import com.example.domain.model.ScheduledState
import com.example.domain.version.AppVersionConfig
import com.example.ui.components.CenteredScrollableChipRow
import com.example.ui.components.CircuitDetailBottomSheet
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.DayNightAmbientScreenBrush
import com.example.ui.components.DayNightChip
import com.example.ui.components.IslaEmblemBadge
import com.example.ui.components.rememberHeroDayNightBrush
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBottomOverlayPadding
import com.example.ui.theme.LocalElectricStatusColors
import java.time.Instant

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenInCalendar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nowZdt = remember(state.nowMillis, state.zoneId) {
        Instant.ofEpochMilli(state.nowMillis).atZone(state.zoneId)
    }
    var shareMenuExpanded by remember { mutableStateOf(false) }

    // Single lightweight shared pulse value when animations are enabled
    val electricPulse = if (state.enableElectricAnimations) {
        val transition = rememberInfiniteTransition(label = "electric_status_pulse")
        val value by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        value
    } else {
        1f
    }

    val screenBackdrop = DayNightAmbientScreenBrush(
        nowMillis = state.nowMillis,
        zoneId = state.zoneId,
        sunriseMinutes = state.sunriseMinutes,
        sunsetMinutes = state.sunsetMinutes,
        themeMode = state.themeMode
    )
    val heroBrush = rememberHeroDayNightBrush(
        nowMillis = state.nowMillis,
        zoneId = state.zoneId,
        sunriseMinutes = state.sunriseMinutes,
        sunsetMinutes = state.sunsetMinutes,
        themeMode = state.themeMode
    )

    val bottomOverlayPadding = LocalBottomOverlayPadding.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackdrop),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 12.dp + bottomOverlayPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_banner") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .background(heroBrush)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IslaEmblemBadge(
                                    state = state.primaryHomeState,
                                    enableAnimation = state.enableElectricAnimations,
                                    size = 48.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = "Luz Isla",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = ScheduleEngine.formatHeroDateEs(nowZdt.toLocalDate()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.90f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ScheduleEngine.formatTime(state.nowMillis, state.use24h, state.zoneId),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Box {
                                    IconButton(
                                        onClick = { shareMenuExpanded = true },
                                        modifier = Modifier.testTag("home_share_menu_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = "Compartir resumen",
                                            tint = Color.White
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = shareMenuExpanded,
                                        onDismissRequest = { shareMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Compartir todos hoy (texto)") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                viewModel.shareSummaryText("ALL")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Compartir favoritos (texto)") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                viewModel.shareSummaryText("FAVORITES")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Compartir con servicio (texto)") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                viewModel.shareSummaryText("WITH_SERVICE")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Compartir próximos horarios") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                viewModel.shareSummaryText("UPCOMING")
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                            text = { Text("Generar imagen resumen") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                viewModel.shareSummaryImage()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DayNightChip(
                                nowMillis = state.nowMillis,
                                zoneId = state.zoneId,
                                isDaytime = state.isDaytime,
                                sunriseMinutes = state.sunriseMinutes,
                                sunsetMinutes = state.sunsetMinutes,
                                use24h = state.use24h,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = ScheduleEngine.formatGmtOffset(state.zoneId),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryCountPill(
                                label = "Con servicio",
                                count = state.totalServiceCount,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCountPill(
                                label = "Sin servicio",
                                count = state.totalOutageCount,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCountPill(
                                label = "Sin info",
                                count = state.totalUnknownCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "search_row") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::updateSearch,
                        placeholder = "Buscar circuito o reparto…",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_search_input")
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = viewModel::toggleCompactMode,
                            modifier = Modifier.testTag("home_compact_toggle")
                        ) {
                            Icon(
                                imageVector = if (state.compactMode) Icons.Filled.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = if (state.compactMode) "Vista en tarjetas" else "Vista compacta",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item(key = "filter_chips") {
                val visibleFilters = HomeFilter.entries.filter { filter ->
                    val count = when (filter) {
                        HomeFilter.ALL -> 1
                        HomeFilter.FAVORITES -> state.totalFavoritesCount
                        HomeFilter.WITH_SERVICE -> state.totalServiceCount
                        HomeFilter.OUTAGE -> state.totalOutageCount
                        HomeFilter.UNKNOWN -> state.totalUnknownCount
                    }
                    filter == HomeFilter.ALL || state.selectedFilter == filter || count > 0
                }
                CenteredScrollableChipRow {
                    visibleFilters.forEach { filter ->
                        val icon = when (filter) {
                            HomeFilter.ALL -> Icons.Filled.Apps
                            HomeFilter.FAVORITES -> Icons.Filled.Star
                            HomeFilter.WITH_SERVICE -> Icons.Filled.Bolt
                            HomeFilter.OUTAGE -> Icons.Filled.PowerOff
                            HomeFilter.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
                        }
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(filter.label, maxLines = 1, softWrap = false) },
                            modifier = Modifier.testTag("home_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (state.evaluations.isEmpty()) {
                item(key = "empty_state") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No hay circuitos para mostrar",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Ajusta los filtros o agrega circuitos en la pestaña Circuitos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.evaluations, key = { it.circuit.code }) { eval ->
                    if (state.compactMode) {
                        CompactCircuitRow(
                            eval = eval,
                            use24h = state.use24h,
                            zoneId = state.zoneId,
                            electricPulse = electricPulse,
                            enableAnimations = state.enableElectricAnimations,
                            onClick = { viewModel.openCircuitDetail(eval.circuit.code) },
                            onToggleFavorite = { viewModel.toggleFavorite(eval.circuit.code) }
                        )
                    } else {
                        RichCircuitCard(
                            eval = eval,
                            use24h = state.use24h,
                            zoneId = state.zoneId,
                            electricPulse = electricPulse,
                            enableAnimations = state.enableElectricAnimations,
                            onClick = { viewModel.openCircuitDetail(eval.circuit.code) },
                            onToggleFavorite = { viewModel.toggleFavorite(eval.circuit.code) }
                        )
                    }
                }
            }

            item(key = "home_version_footer") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Luz Isla · v${AppVersionConfig.APP_VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    state.selectedEvaluation?.let { detailEval ->
        CircuitDetailBottomSheet(
            evaluation = detailEval,
            snapshot = state.snapshot,
            nowMillis = state.nowMillis,
            use24h = state.use24h,
            zoneId = state.zoneId,
            sunriseMinutes = state.sunriseMinutes,
            sunsetMinutes = state.sunsetMinutes,
            enableElectricAnimations = state.enableElectricAnimations,
            centerTimelineOnNow = state.centerTimelineOnNow,
            onDismiss = viewModel::closeCircuitDetail,
            onToggleFavorite = viewModel::toggleFavorite,
            onSetPrimaryHome = viewModel::setPrimaryHome,
            onShareText = viewModel::shareSingleCircuit,
            onOpenInCalendar = { code ->
                viewModel.closeCircuitDetail()
                onOpenInCalendar(code)
            }
        )
    }
}

@Composable
private fun SummaryCountPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RichCircuitCard(
    eval: CircuitStatusEvaluation,
    use24h: Boolean,
    zoneId: java.time.ZoneId,
    electricPulse: Float,
    enableAnimations: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val statusColors = LocalElectricStatusColors.current
    val circuit = eval.circuit
    val isHighlighted = circuit.isPrimaryHome || circuit.isFavorite

    val (accent, container, icon, stateText) = when (eval.state) {
        ScheduledState.SERVICE_SCHEDULED -> CardVisual(
            statusColors.service,
            statusColors.serviceContainer,
            Icons.Filled.Bolt,
            "Con servicio programado"
        )
        ScheduledState.OUTAGE_SCHEDULED -> CardVisual(
            statusColors.outage,
            statusColors.outageContainer,
            Icons.Filled.PowerOff,
            "Sin servicio programado"
        )
        ScheduledState.UNKNOWN -> CardVisual(
            statusColors.unknown,
            statusColors.unknownContainer,
            Icons.AutoMirrored.Filled.HelpOutline,
            "Sin información"
        )
    }

    val animatedBorderColor = when {
        circuit.isPrimaryHome && eval.state == ScheduledState.SERVICE_SCHEDULED && enableAnimations ->
            statusColors.service.copy(alpha = 0.45f + 0.55f * electricPulse)
        circuit.isPrimaryHome -> statusColors.favorite
        circuit.isFavorite && eval.state == ScheduledState.SERVICE_SCHEDULED && enableAnimations ->
            accent.copy(alpha = 0.3f + 0.4f * electricPulse)
        circuit.isFavorite -> accent.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (circuit.isPrimaryHome) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(if (circuit.isPrimaryHome) 2.dp else 1.2.dp, animatedBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("circuit_card_${circuit.code}")
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusEmblemIconBox(
                    state = eval.state,
                    accent = accent,
                    container = container,
                    icon = icon,
                    contentDescription = stateText,
                    electricPulse = electricPulse,
                    enableAnimations = enableAnimations,
                    boxSize = 44.dp,
                    iconSize = 23.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = circuit.code,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (circuit.customName.isNotBlank()) {
                            Text(
                                text = " • ${circuit.customName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (circuit.comment.isNotBlank()) {
                        Text(
                            text = circuit.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (circuit.isPrimaryHome) {
                    Surface(
                        color = statusColors.favoriteContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "Mi vivienda",
                            tint = statusColors.favorite,
                            modifier = Modifier.padding(6.dp).size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("fav_btn_${circuit.code}")
                ) {
                    Icon(
                        imageVector = if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (circuit.isFavorite) "Quitar favorito" else "Agregar favorito",
                        tint = if (circuit.isFavorite) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = container,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (eval.remainingMillis != null && eval.state != ScheduledState.UNKNOWN) {
                    val remainingPrefix = if (eval.state == ScheduledState.SERVICE_SCHEDULED) "Quedan" else "Faltan"
                    Text(
                        text = "$remainingPrefix ${ScheduleEngine.formatDurationHms(eval.remainingMillis)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val periodStr = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                    "Período: ${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                } else {
                    "Período: Sin datos"
                }
                Text(
                    text = periodStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val nextStr = if (eval.nextServiceStartMillis != null) {
                    "Próx: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
                } else {
                    "Próx: --"
                }
                Text(
                    text = nextStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CompactCircuitRow(
    eval: CircuitStatusEvaluation,
    use24h: Boolean,
    zoneId: java.time.ZoneId,
    electricPulse: Float,
    enableAnimations: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val statusColors = LocalElectricStatusColors.current
    val circuit = eval.circuit
    val (accent, container, icon) = when (eval.state) {
        ScheduledState.SERVICE_SCHEDULED -> Triple(statusColors.service, statusColors.serviceContainer, Icons.Filled.Bolt)
        ScheduledState.OUTAGE_SCHEDULED -> Triple(statusColors.outage, statusColors.outageContainer, Icons.Filled.PowerOff)
        ScheduledState.UNKNOWN -> Triple(statusColors.unknown, statusColors.unknownContainer, Icons.AutoMirrored.Filled.HelpOutline)
    }
    val statusShort = when (eval.state) {
        ScheduledState.SERVICE_SCHEDULED -> "Con servicio"
        ScheduledState.OUTAGE_SCHEDULED -> "Sin servicio"
        ScheduledState.UNKNOWN -> "Sin info"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("circuit_compact_${circuit.code}")
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            StatusEmblemIconBox(
                state = eval.state,
                accent = accent,
                container = container,
                icon = icon,
                contentDescription = statusShort,
                electricPulse = electricPulse,
                enableAnimations = enableAnimations,
                boxSize = 32.dp,
                iconSize = 17.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = circuit.displayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                    "$statusShort • ${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                } else {
                    statusShort
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent
                )
            }
            if (eval.remainingMillis != null && eval.state != ScheduledState.UNKNOWN) {
                Text(
                    text = ScheduleEngine.formatDurationHms(eval.remainingMillis),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorito",
                    tint = if (circuit.isFavorite) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusEmblemIconBox(
    state: ScheduledState,
    accent: Color,
    container: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    electricPulse: Float,
    enableAnimations: Boolean,
    boxSize: Dp,
    iconSize: Dp
) {
    val isService = state == ScheduledState.SERVICE_SCHEDULED
    val isOutage = state == ScheduledState.OUTAGE_SCHEDULED

    val borderColor = when {
        enableAnimations && isService -> accent.copy(alpha = 0.42f + 0.58f * electricPulse)
        enableAnimations && isOutage -> accent.copy(alpha = 0.16f + 0.24f * (1f - electricPulse))
        else -> accent.copy(alpha = 0.35f)
    }

    val haloBrush = when {
        enableAnimations && isService -> Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.36f * electricPulse),
                container
            )
        )
        enableAnimations && isOutage -> Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.16f * electricPulse),
                container.copy(alpha = 0.72f)
            )
        )
        else -> Brush.linearGradient(listOf(container, container))
    }

    val iconScale = when {
        !enableAnimations -> 1f
        isService -> 0.94f + 0.14f * electricPulse
        isOutage -> 0.92f + 0.05f * (1f - electricPulse)
        else -> 1f
    }

    val iconAlpha = when {
        !enableAnimations -> 1f
        isService -> 0.85f + 0.15f * electricPulse
        isOutage -> 0.55f + 0.28f * (1f - electricPulse)
        else -> 1f
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(if (enableAnimations && isService) 1.6.dp else 1.dp, borderColor),
        modifier = Modifier.size(boxSize)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(haloBrush)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    }
            )
        }
    }
}

private data class CardVisual(
    val accent: Color,
    val container: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)
