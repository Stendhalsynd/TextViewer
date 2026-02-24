package com.jihun.textviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class LiquidDestination(
    val route: String,
    val label: String,
)

@Composable
fun LiquidGlassBottomBar(
    destinations: List<LiquidDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val shape = RoundedCornerShape(20.dp)
                    val interactionSource = remember(destination.route) { MutableInteractionSource() }
                    val selectedContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    val selectedBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                    val textColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                    }

                    Box(
                        modifier = Modifier
                            .semantics {
                                contentDescription = destination.label
                            }
                            .border(
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) selectedBorder else Color.Transparent,
                                ),
                                shape = shape,
                            )
                            .background(
                                color = if (selected) selectedContainer else Color.Transparent,
                                shape = shape,
                            )
                            .clip(shape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onNavigate(destination.route) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}
