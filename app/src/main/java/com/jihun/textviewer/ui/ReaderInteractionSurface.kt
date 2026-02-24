package com.jihun.textviewer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jihun.textviewer.domain.viewmodel.TextViewerState
import androidx.compose.animation.core.animateFloatAsState

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ReaderInteractionSurface(
    state: TextViewerState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val totalPages = state.totalPages.coerceAtLeast(1)
    val pageNumber = (state.currentPage + 1).coerceAtLeast(1)

    var jumpPageText by remember { mutableStateOf(pageNumber.toString()) }
    var showJumpPanel by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(1f) }
    var centerZoneWidthPx by remember { mutableStateOf(1f) }
    var brightnessLevel by remember(activity) {
        mutableFloatStateOf(readCurrentBrightness(activity))
    }

    val jumpPage = jumpPageText.toIntOrNull()
    val jumpEnabled = jumpPage != null && jumpPage in 1..totalPages
    val jumpPageFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val jumpToggleSource = remember { MutableInteractionSource() }

    val sideGestureWidth = 64.dp

    LaunchedEffect(state.currentPage, totalPages, showJumpPanel) {
        if (!showJumpPanel) {
            jumpPageText = pageNumber.toString()
        }
    }

    LaunchedEffect(showJumpPanel) {
        if (showJumpPanel) {
            jumpPageText = pageNumber.toString()
            jumpPageFocus.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val jumpToPage = {
        val target = jumpPage
        if (target != null) {
            onGoToPage((target - 1).coerceIn(0, totalPages - 1))
            showJumpPanel = false
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    containerHeightPx = size.height.toFloat().coerceAtLeast(1f)
                },
        ) {
            val dragThresholdPx = containerHeightPx * 0.22f
            val animatedOffset by animateFloatAsState(targetValue = dragOffset, label = "reader-page-offset")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .graphicsLayer { translationY = animatedOffset },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.currentDocument?.fileName ?: "텍스트 파일을 선택해 주세요",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                )
                if (state.currentDocument == null) {
                    Text(
                        text = "하단 절반 좌/우 터치, 중앙 상하 드래그, 볼륨 키로 이동",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = state.pageContent.ifBlank {
                        "TXT 파일을 열면 이 영역에서 읽을 수 있습니다."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showJumpPanel && state.currentDocument != null,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.06f))
                        .pointerInput(Unit) {
                            detectTapGestures { showJumpPanel = false }
                        },
                ) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 52.dp)
                            .width(300.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {})
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "페이지 이동",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = jumpPageText,
                                    onValueChange = { value ->
                                        jumpPageText = value.filter { it.isDigit() }.take(5)
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { jumpToPage() }),
                                    singleLine = true,
                                    label = { Text("이동할 페이지") },
                                    modifier = Modifier
                                        .width(126.dp)
                                        .focusRequester(jumpPageFocus),
                                )
                                Text(
                                    text = "/ $totalPages",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Button(
                                    onClick = jumpToPage,
                                    enabled = jumpEnabled,
                                ) {
                                    Text("이동")
                                }
                            }
                        }
                    }
                }
            }

            if (state.currentDocument != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .height(24.dp)
                        .width(88.dp)
                        .zIndex(3f)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.001f),
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = jumpToggleSource,
                            indication = null,
                        ) {
                            showJumpPanel = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "페이지 이동",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0f),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.001f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(sideGestureWidth)
                        .pointerInput(onPreviousPage, containerHeightPx) {
                            detectTapGestures { offset ->
                                if (offset.y >= (containerHeightPx * 0.5f)) {
                                    onPreviousPage()
                                }
                            }
                        }
                        .pointerInput(activity, containerHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    brightnessLevel = adjustBrightness(
                                        activity = activity,
                                        current = brightnessLevel,
                                        dragAmount = dragAmount,
                                        heightPx = containerHeightPx,
                                    )
                                },
                            )
                        },
                ) {
                    Text(
                        text = "밝기",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = -90f },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { size ->
                            centerZoneWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        }
                        .pointerInput(onPreviousPage, onNextPage, containerHeightPx, centerZoneWidthPx) {
                            detectTapGestures { offset ->
                                val isBottomHalf = offset.y >= (containerHeightPx * 0.5f)
                                if (!isBottomHalf) return@detectTapGestures

                                if (offset.x < centerZoneWidthPx * 0.5f) {
                                    onPreviousPage()
                                } else {
                                    onNextPage()
                                }
                            }
                        }
                        .pointerInput(onPreviousPage, onNextPage, dragThresholdPx, containerHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffset = (dragOffset + dragAmount).coerceIn(-containerHeightPx, containerHeightPx)
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
                                onDragEnd = { dragOffset = 0f },
                                onDragCancel = { dragOffset = 0f },
                            )
                        },
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(sideGestureWidth)
                        .pointerInput(onNextPage, containerHeightPx) {
                            detectTapGestures { offset ->
                                if (offset.y >= (containerHeightPx * 0.5f)) {
                                    onNextPage()
                                }
                            }
                        }
                        .pointerInput(activity, containerHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    brightnessLevel = adjustBrightness(
                                        activity = activity,
                                        current = brightnessLevel,
                                        dragAmount = dragAmount,
                                        heightPx = containerHeightPx,
                                    )
                                },
                            )
                        },
                ) {
                    Text(
                        text = "밝기",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = 90f },
                    )
                }
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
    val sensitivity = 1.6f
    val delta = (-dragAmount / heightPx) * sensitivity
    val next = (current + delta).coerceIn(0.05f, 1f)
    if (activity != null) {
        val attributes = activity.window.attributes
        attributes.screenBrightness = next
        activity.window.attributes = attributes
    }
    return next
}

private fun readCurrentBrightness(activity: Activity?): Float {
    val current = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (current in 0.01f..1f) current else 0.45f
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
