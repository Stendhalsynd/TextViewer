package com.jihun.textviewer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jihun.textviewer.domain.viewmodel.TextViewerState

@Composable
fun ReaderInteractionSurface(
    state: TextViewerState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val totalPages = state.totalPages.coerceAtLeast(1)
    val pageNumber = (state.currentPage + 1).coerceAtLeast(1)
    var dragOffset by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(1f) }
    var screenBrightness by remember(activity) {
        mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f } ?: 1f)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    containerHeightPx = size.height.toFloat().coerceAtLeast(1f)
                },
        ) {
            val dragThresholdPx = containerHeightPx * 0.22f
            val animatedOffset by animateFloatAsState(
                targetValue = dragOffset,
                label = "reader-page-offset",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .graphicsLayer {
                        translationY = animatedOffset
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.currentDocument?.fileName ?: "No document selected",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.currentDocument != null) {
                        "Page $pageNumber of $totalPages"
                    } else {
                        "Open a text file to start reading"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = state.pageContent.ifBlank {
                        "Open a text file and use left/right tap zones, center drag, or volume keys to move pages."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            TextButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                onClick = onToggleTheme,
            ) {
                Text("Theme")
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.001f)),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(onPreviousPage) {
                            detectTapGestures(
                                onTap = { onPreviousPage() },
                            )
                        }
                        .pointerInput(activity, containerHeightPx, screenBrightness) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    screenBrightness = adjustBrightness(
                                        activity = activity,
                                        current = screenBrightness,
                                        dragAmount = dragAmount,
                                        heightPx = containerHeightPx,
                                    )
                                },
                            )
                        },
                )

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxSize()
                        .pointerInput(onPreviousPage, onNextPage, dragThresholdPx, containerHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset = (dragOffset + dragAmount).coerceIn(
                                        -containerHeightPx,
                                        containerHeightPx,
                                    )
                                    when {
                                        dragOffset <= -dragThresholdPx -> {
                                            onNextPage()
                                            dragOffset = 0f
                                        }

                                        dragOffset >= dragThresholdPx -> {
                                            onPreviousPage()
                                            dragOffset = 0f
                                        }
                                    }
                                },
                                onDragEnd = {
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragOffset = 0f
                                },
                            )
                        },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(onNextPage) {
                            detectTapGestures(
                                onTap = { onNextPage() },
                            )
                        }
                        .pointerInput(activity, containerHeightPx, screenBrightness) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    screenBrightness = adjustBrightness(
                                        activity = activity,
                                        current = screenBrightness,
                                        dragAmount = dragAmount,
                                        heightPx = containerHeightPx,
                                    )
                                },
                            )
                        },
                )
            }
        }
    }
}

private fun adjustBrightness(
    activity: Activity?,
    current: Float,
    dragAmount: Float,
    heightPx: Float,
): Float {
    val delta = -dragAmount / heightPx
    val next = (current + delta).coerceIn(0.05f, 1f)
    if (activity != null) {
        val attributes = activity.window.attributes
        attributes.screenBrightness = next
        activity.window.attributes = attributes
    }
    return next
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
