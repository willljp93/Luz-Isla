package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Displays chips in a wrapping multi-line FlowRow so options never clip horizontally.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CenteredScrollableChipRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing, horizontalAlignment),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        content()
    }
}
