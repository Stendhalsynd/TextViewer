package com.jihun.textviewer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jihun.textviewer.BuildConfig
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.domain.viewmodel.TextViewerState
import com.jihun.textviewer.ui.ReaderInteractionSurface

@Composable
fun TextViewerHomeScreen(
    state: TextViewerState,
    onOpenFileClick: () -> Unit,
    onResumeClick: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    onPageRangesUpdated: (String, List<TextPageRange>) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.currentDocument != null) {
            ReaderInteractionSurface(
                state = state,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onGoToPage = onGoToPage,
                onToggleTheme = onToggleTheme,
                onPageRangesUpdated = onPageRangesUpdated,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            SlackHomePrompt(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                onOpenFileClick = onOpenFileClick,
                onResumeClick = onResumeClick,
            )
        }

        if (state.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = "오류: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        if (state.isLoading && state.errorMessage == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(18.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "텍스트 파일을 불러오는 중...",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlackHomePrompt(
    modifier: Modifier,
    onOpenFileClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ),
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    Text(
                        text = "TXT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = "읽기 시작하기",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "TXT 파일 열기 또는 이어읽기 버튼으로 텍스트 세션을 시작하세요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassActionChip(
                        text = "TXT 열기",
                        onClick = onOpenFileClick,
                        modifier = Modifier.weight(1f),
                    )
                    GlassActionChip(
                        text = "이어읽기",
                        onClick = onResumeClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun TextViewerHistoryScreen(
    history: List<ReadingHistory>,
    onContinueReading: (ReadingHistory) -> Unit,
    onOpenNewFile: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "이력",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    tonalElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "아직 읽기 기록이 없습니다",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "TXT 파일을 열면 최근 기록이 자동 저장됩니다.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(
                            onClick = onOpenNewFile,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text("새 파일 열기")
                        }
                    }
                }
            }
        } else {
            items(items = history, key = { it.fileUri }) { entry ->
                HistoryItem(
                    entry = entry,
                    onContinueReading = { onContinueReading(entry) },
                )
            }
        }
    }
}

@Composable
fun TextViewerSettingsScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "테마",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "밝기/어두운 모드를 전환해 눈 피로를 줄입니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onToggleTheme,
                    modifier = Modifier.padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(if (darkTheme) "라이트 모드 전환" else "다크 모드 전환")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "앱 정보",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "버전: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "릴리즈 노트는 홈 화면에서 확인할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: ReadingHistory,
    onContinueReading: () -> Unit,
) {
    val currentPage = (entry.currentPage + 1).coerceAtLeast(1)
    val totalPages = entry.totalPages.coerceAtLeast(1)
    val progressText = "페이지 $currentPage / $totalPages"
    val updatedText = DateUtils.getRelativeTimeSpanString(
        entry.updatedAtMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = entry.fileName ?: entry.fileUri,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "최근 열람: $updatedText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )
            Button(
                onClick = onContinueReading,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("이어서 읽기")
            }
        }
    }
}

@Composable
private fun GlassActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                shape = shape,
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text = text, maxLines = 1)
    }
}
