package com.jihun.textviewer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.domain.viewmodel.TextViewerState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val SIDE_GESTURE_WIDTH_DP = 64
private const val MIN_GESTURE_TARGET_SIZE_DP = 44
private const val SIZE_BUCKET_PX = 8
private const val PAGE_LAYOUT_TIMEOUT_MS = 1_500L

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ReaderInteractionSurface(
    state: TextViewerState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    onPageRangesUpdated: (String, List<TextPageRange>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val totalPages = state.totalPages
    val hasValidTotalPages = totalPages > 0
    val pageNumber = (state.currentPage + 1).coerceAtLeast(1)
    val safeTotalPages = if (hasValidTotalPages) totalPages else 0

    var jumpPageText by remember { mutableStateOf(TextFieldValue(pageNumber.toString())) }
    var showJumpPanel by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    var contentWidthPx by remember { mutableFloatStateOf(1f) }
    var contentHeightPx by remember { mutableFloatStateOf(1f) }
    var brightnessLevel by remember(activity) {
        mutableFloatStateOf(readCurrentBrightness(activity))
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    var lastComputedRanges by remember { mutableStateOf<List<TextPageRange>>(emptyList()) }
    val textStyle = MaterialTheme.typography.bodyLarge
    val textMeasurer = remember(context, density, layoutDirection) {
        TextMeasurer(
            fallbackFontFamilyResolver = createFontFamilyResolver(context),
            fallbackDensity = density,
            fallbackLayoutDirection = layoutDirection,
            cacheSize = 16,
        )
    }

    val jumpPage = jumpPageText.text.toIntOrNull()
    val jumpEnabled =
        state.currentDocument != null &&
            hasValidTotalPages &&
            jumpPage != null &&
            jumpPage in 1..totalPages

    val jumpPageFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sideGestureWidth = SIDE_GESTURE_WIDTH_DP.dp

    val displayedText = when {
        state.currentDocument == null -> "TXT 파일을 열면 이 영역에서 읽을 수 있습니다."
        state.pageContent.isNotBlank() -> state.pageContent
        else -> "페이지 레이아웃을 계산 중입니다..."
    }

    val stableContentWidthPx = ((contentWidthPx / SIZE_BUCKET_PX).toInt() * SIZE_BUCKET_PX)
        .coerceAtLeast(1)
    val stableContentHeightPx = ((contentHeightPx / SIZE_BUCKET_PX).toInt() * SIZE_BUCKET_PX)
        .coerceAtLeast(1)

    LaunchedEffect(
        state.currentDocument?.uri,
        state.currentDocument?.content,
        stableContentWidthPx,
        stableContentHeightPx,
        textStyle,
        textMeasurer,
    ) {
        val document = state.currentDocument ?: return@LaunchedEffect
        val widthPx = stableContentWidthPx
        val heightPx = stableContentHeightPx
        if (widthPx <= 0 || heightPx <= 0 || document.content.isEmpty()) return@LaunchedEffect

        // Debounce repeated size/layout updates and avoid expensive repeated recalculation.
        delay(80L)

        val estimate = estimatePageRanges(
            text = document.content,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            availableWidthPx = widthPx,
            availableHeightPx = heightPx,
        )
        if (estimate != lastComputedRanges) {
            lastComputedRanges = estimate
            onPageRangesUpdated(document.uri, estimate)
        }

        val ranges = runCatching {
            withTimeout(PAGE_LAYOUT_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    calculatePageRanges(
                        text = document.content,
                        textStyle = textStyle,
                        textMeasurer = textMeasurer,
                        availableWidthPx = widthPx,
                        availableHeightPx = heightPx,
                    )
                }
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            if (throwable !is TimeoutCancellationException) {
                Log.w("ReaderInteractionSurface", "Fallback to estimate page ranges", throwable)
            }
            estimate
        }

        if (ranges != lastComputedRanges) {
            lastComputedRanges = ranges
            onPageRangesUpdated(document.uri, ranges)
        }
    }

    LaunchedEffect(state.currentPage, state.currentDocument?.uri, showJumpPanel) {
        if (!showJumpPanel) {
            jumpPageText = TextFieldValue(pageNumber.toString())
        }
    }

    LaunchedEffect(showJumpPanel) {
        if (showJumpPanel) {
            jumpPageText = TextFieldValue(pageNumber.toString())
            jumpPageFocus.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val jumpToPage = {
        val target = jumpPage
        if (target != null && hasValidTotalPages) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
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
                        text = "하단 절반 좌/우 터치, 볼륨 키로 이동",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp)
                        .onSizeChanged { size ->
                            contentWidthPx = size.width.toFloat().coerceAtLeast(1f)
                            contentHeightPx = size.height.toFloat().coerceAtLeast(1f)
                        },
                ) {
                    Text(
                        text = displayedText,
                        style = textStyle,
                    )
                }
            }

            if (showJumpPanel && state.currentDocument != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(20f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.06f)),
                ) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 52.dp)
                            .width(300.dp)
                            .zIndex(30f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "페이지 이동",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                TextButton(onClick = { showJumpPanel = false }) {
                                    Text("닫기")
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = jumpPageText,
                                    onValueChange = { value ->
                                        val digits = value.text.filter { it.isDigit() }.take(5)
                                        val selection = value.selection.let { textRange ->
                                            TextRange(
                                                textRange.start.coerceIn(0, digits.length),
                                                textRange.end.coerceIn(0, digits.length),
                                            )
                                        }
                                        jumpPageText = value.copy(text = digits, selection = selection)
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
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.001f))
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 12.dp)
                            .zIndex(30f)
                            .sizeIn(minHeight = MIN_GESTURE_TARGET_SIZE_DP.dp, minWidth = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggleTheme() }
                            .semantics { contentDescription = "테마 전환" }
                            .testTag("theme-toggle-button"),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = if (hasValidTotalPages) "$pageNumber / $safeTotalPages" else "계산 중",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .height(24.dp)
                            .width(88.dp)
                            .zIndex(3f)
                            .semantics { contentDescription = "페이지 이동" }
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.001f),
                                shape = CircleShape,
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showJumpPanel = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }

                if (!showJumpPanel) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.001f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(sideGestureWidth),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .pointerInput(onPreviousPage) {
                                            detectTapGestures {
                                                onPreviousPage()
                                            }
                                        }
                                        .pointerInput(activity) {
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
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(sideGestureWidth),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .pointerInput(onNextPage) {
                                            detectTapGestures {
                                                onNextPage()
                                            }
                                        }
                                        .pointerInput(activity) {
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
