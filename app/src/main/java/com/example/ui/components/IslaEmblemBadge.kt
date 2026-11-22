package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ScheduledState
import com.example.ui.theme.LocalElectricStatusColors

@Composable
fun IslaEmblemBadge(
    state: ScheduledState?,
    enableAnimation: Boolean = true,
    size: Dp = 54.dp,
    modifier: Modifier = Modifier
) {
    val statusColors = LocalElectricStatusColors.current
    val (drawableRes, ringColor, desc) = when (state) {
        ScheduledState.SERVICE_SCHEDULED -> Triple(
            R.drawable.luz_isla_service,
            statusColors.service,
            "Mi vivienda con servicio programado"
        )
        ScheduledState.OUTAGE_SCHEDULED -> Triple(
            R.drawable.luz_isla_outage,
            statusColors.outage,
            "Mi vivienda sin servicio programado"
        )
        ScheduledState.UNKNOWN, null -> Triple(
            R.drawable.luz_isla_base,
            Color(0xFFFAD601),
            "Emblema Luz Isla"
        )
    }

    val pulseScale = if (enableAnimation && state == ScheduledState.SERVICE_SCHEDULED) {
        val transition = rememberInfiniteTransition(label = "isla_emblem_pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        value
    } else {
        1f
    }

    Surface(
        shape = CircleShape,
        color = Color(0xFF161B25),
        border = BorderStroke(2.dp, ringColor.copy(alpha = 0.85f)),
        shadowElevation = 3.dp,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF161B25))
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = desc,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}
