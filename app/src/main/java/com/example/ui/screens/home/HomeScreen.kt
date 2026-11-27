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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.example.ui.components.ScreenLoadingCard
import com.example.ui.components.StateNoticeCard
import com.example.ui.components.StatusBadgePill
import com.example.ui.components.rememberHeroDayNightBrush
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBottomOverlayPadding
import com.example.ui.theme.LocalElectricStatusColors
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenInCalendar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalElectricStatusColors.current
    val nowZdt = remember(state.nowMillis, state.zoneId) {
        Instant.ofEpochMilli(state.nowMillis).atZone(state.zoneId)
    }
    var shareMenuExpanded by remember { mutableStateOf(false) }

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
                .widthIn(max = 840.dp)
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + bottomOverlayPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_banner") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .background(heroBrush)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Luz Isla",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Text(
                                        text = ScheduleEngine.formatHeroDateEs(nowZdt.toLocalDate()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.90f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ScheduleEngine.formatTime(state.nowMillis, state.use24h, state.zoneId),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
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

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DayNightChip(
                                nowMillis = state.nowMillis,
                                zoneId = state.zoneId,
                                isDaytime = state.isDaytime,
                                sunriseMinutes = state.sunriseMinutes,
                                sunsetMinutes = state.sunsetMinutes,
                                use24h = state.use24h,
                                themeMode = state.themeMode
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.92f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = ScheduleEngine.formatGmtOffset(state.zoneId),
                                        style = MaterialTheme.typography.labelMedium,
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
                                icon = Icons.Filled.Bolt,
                                iconTint = Color(0xFF86EFAC),
                                selected = state.selectedFilter == HomeFilter.WITH_SERVICE,
                                onClick = {
                                    val next = if (state.selectedFilter == HomeFilter.WITH_SERVICE) HomeFilter.ALL else HomeFilter.WITH_SERVICE
                                    viewModel.selectFilter(next)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCountPill(
                                label = "Sin servicio",
                                count = state.totalOutageCount,
                                icon = Icons.Filled.PowerOff,
                                iconTint = Color(0xFFFCA5A5),
                                selected = state.selectedFilter == HomeFilter.OUTAGE,
                                onClick = {
                                    val next = if (state.selectedFilter == HomeFilter.OUTAGE) HomeFilter.ALL else HomeFilter.OUTAGE
                                    viewModel.selectFilter(next)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCountPill(
                                label = "Sin info",
                                count = state.totalUnknownCount,
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                iconTint = Color(0xFFE2E8F0),
                                selected = state.selectedFilter == HomeFilter.UNKNOWN,
                                onClick = {
                                    val next = if (state.selectedFilter == HomeFilter.UNKNOWN) HomeFilter.ALL else HomeFilter.UNKNOWN
                                    viewModel.selectFilter(next)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (!state.isLoading && state.hasSyncError && state.syncStatusMessage.isNotBlank()) {
                item(key = "home_sync_notice") {
                    StateNoticeCard(
                        icon = Icons.Filled.CloudOff,
                        title = "Estado de sincronización",
                        subtitle = state.syncStatusMessage,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        iconTint = MaterialTheme.colorScheme.error,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        centered = false,
                        testTag = "home_sync_error_banner"
                    )
                }
            }

            item(key = "home_controls_panel") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
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
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = viewModel::toggleCompactMode,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("home_compact_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (state.compactMode) Icons.Filled.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = if (state.compactMode) "Vista en tarjetas" else "Vista compacta",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

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
                        CenteredScrollableChipRow(spacing = 6.dp) {
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
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("home_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                item(key = "home_loading") {
                    ScreenLoadingCard(
                        title = "Cargando circuitos…",
                        subtitle = "Calculando estados actuales y próximos cambios.",
                        testTag = "home_loading_card"
                    )
                }
            } else {
                if (state.totalActiveCount > 0 &&
                    state.totalUnknownCount == state.totalActiveCount &&
                    state.selectedFilter == HomeFilter.ALL &&
                    state.searchQuery.isBlank()
                ) {
                    item(key = "no_schedule_today_notice") {
                        StateNoticeCard(
                            icon = Icons.Filled.EventBusy,
                            title = "Sin programación cargada para hoy",
                            subtitle = "La aplicación no muestra horarios estimados ni ficticios. Configura reglas o sincroniza datos para ver estados.",
                            containerColor = statusColors.unknownContainer.copy(alpha = 0.72f),
                            contentColor = statusColors.onUnknownContainer,
                            iconTint = statusColors.unknown,
                            borderColor = statusColors.unknown.copy(alpha = 0.35f),
                            centered = false,
                            testTag = "home_no_schedule_notice"
                        )
                    }
                }

                if (state.evaluations.isEmpty()) {
                    item(key = "empty_state") {
                        if (state.totalActiveCount > 0) {
                            StateNoticeCard(
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                title = "Sin coincidencias",
                                subtitle = "Ningún circuito coincide con el filtro o búsqueda actual.",
                                actionLabel = "Restablecer filtros",
                                onAction = {
                                    viewModel.updateSearch("")
                                    viewModel.selectFilter(HomeFilter.ALL)
                                },
                                testTag = "home_empty_filtered_card"
                            )
                        } else {
                            StateNoticeCard(
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                title = "No hay circuitos activos",
                                subtitle = "Activa o crea circuitos en la pestaña Circuitos para comenzar a monitorearlos.",
                                testTag = "home_empty_catalog_card"
                            )
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.15f),
        border = if (selected) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)) else null,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    val (accent, container, onContainer, icon, stateText) = when (eval.state) {
        ScheduledState.SERVICE_SCHEDULED -> CardVisual(
            statusColors.service,
            statusColors.serviceContainer,
            statusColors.onServiceContainer,
            Icons.Filled.Bolt,
            "Con servicio"
        )
        ScheduledState.OUTAGE_SCHEDULED -> CardVisual(
            statusColors.outage,
            statusColors.outageContainer,
            statusColors.onOutageContainer,
            Icons.Filled.PowerOff,
            "Sin servicio"
        )
        ScheduledState.UNKNOWN -> CardVisual(
            statusColors.unknown,
            statusColors.unknownContainer,
            statusColors.onUnknownContainer,
            Icons.AutoMirrored.Filled.HelpOutline,
            "Sin información"
        )
    }

    val animatedBorderColor = when {
        circuit.isPrimaryHome && eval.state == ScheduledState.SERVICE_SCHEDULED && enableAnimations ->
            statusColors.service.copy(alpha = 0.50f + 0.50f * electricPulse)
        circuit.isPrimaryHome -> statusColors.favorite
        circuit.isFavorite && eval.state == ScheduledState.SERVICE_SCHEDULED && enableAnimations ->
            accent.copy(alpha = 0.35f + 0.45f * electricPulse)
        circuit.isFavorite -> accent.copy(alpha = 0.48f)
        eval.state == ScheduledState.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
        else -> accent.copy(alpha = 0.28f)
    }

    val cardContainer = when {
        circuit.isPrimaryHome -> MaterialTheme.colorScheme.surfaceContainerLow
        eval.state == ScheduledState.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = BorderStroke(if (circuit.isPrimaryHome) 2.dp else 1.2.dp, animatedBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (circuit.isPrimaryHome) 3.dp else if (isHighlighted) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("circuit_card_${circuit.code}")
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (circuit.isPrimaryHome) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusBadgePill(
                        label = "Mi vivienda",
                        icon = Icons.Filled.Home,
                        containerColor = statusColors.favoriteContainer,
                        contentColor = statusColors.onFavoriteContainer,
                        borderColor = statusColors.favorite.copy(alpha = 0.40f)
                    )
                    Text(
                        text = "Prioridad en alertas",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColors.favorite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                    boxSize = 46.dp,
                    iconSize = 24.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = circuit.code,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (circuit.customName.isNotBlank()) {
                            Text(
                                text = "• ${circuit.customName}",
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fav_btn_${circuit.code}")
                ) {
                    Icon(
                        imageVector = if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (circuit.isFavorite) "Quitar favorito" else "Agregar favorito",
                        tint = if (circuit.isFavorite) statusColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadgePill(
                    label = stateText,
                    icon = icon,
                    containerColor = container,
                    contentColor = onContainer,
                    borderColor = accent.copy(alpha = 0.25f)
                )

                if (eval.remainingMillis != null && eval.state != ScheduledState.UNKNOWN) {
                    val remainingPrefix = if (eval.state == ScheduledState.SERVICE_SCHEDULED) "Quedan" else "Faltan"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$remainingPrefix ${ScheduleEngine.formatDurationHms(eval.remainingMillis)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val periodStr = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                    "${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                } else {
                    "Sin datos de horario"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = periodStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val nextStr = if (eval.nextServiceStartMillis != null) {
                    "Próx. servicio: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
                } else {
                    "Próx. servicio: --"
                }
                Text(
                    text = nextStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val (accent, container, _, icon, statusShort) = when (eval.state) {
        ScheduledState.SERVICE_SCHEDULED -> CardVisual(
            statusColors.service,
            statusColors.serviceContainer,
            statusColors.onServiceContainer,
            Icons.Filled.Bolt,
            "Con servicio"
        )
        ScheduledState.OUTAGE_SCHEDULED -> CardVisual(
            statusColors.outage,
            statusColors.outageContainer,
            statusColors.onOutageContainer,
            Icons.Filled.PowerOff,
            "Sin servicio"
        )
        ScheduledState.UNKNOWN -> CardVisual(
            statusColors.unknown,
            statusColors.unknownContainer,
            statusColors.onUnknownContainer,
            Icons.AutoMirrored.Filled.HelpOutline,
            "Sin info"
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (circuit.isPrimaryHome) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = if (circuit.isPrimaryHome) 1.6.dp else 1.dp,
            color = if (circuit.isPrimaryHome) statusColors.favorite else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("circuit_compact_${circuit.code}")
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            StatusEmblemIconBox(
                state = eval.state,
                accent = accent,
                container = container,
                icon = icon,
                contentDescription = statusShort,
                electricPulse = electricPulse,
                enableAnimations = enableAnimations,
                boxSize = 36.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (circuit.isPrimaryHome) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Mi vivienda",
                            tint = statusColors.favorite,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = circuit.displayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val sub = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                    "$statusShort • ${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)}\u2060–\u2060${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
                } else {
                    statusShort
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (eval.remainingMillis != null && eval.state != ScheduledState.UNKNOWN) {
                Text(
                    text = ScheduleEngine.formatDurationHms(eval.remainingMillis),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (circuit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (circuit.isFavorite) "Quitar favorito" else "Agregar favorito",
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
    icon: ImageVector,
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
                Color.Black.copy(alpha = 0.14f * electricPulse),
                container.copy(alpha = 0.78f)
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
        isOutage -> 0.65f + 0.25f * (1f - electricPulse)
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
    val onContainer: Color,
    val icon: ImageVector,
    val label: String
)

