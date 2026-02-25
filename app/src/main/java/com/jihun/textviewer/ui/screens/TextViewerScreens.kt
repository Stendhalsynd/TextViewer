package com.jihun.textviewer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jihun.textviewer.BuildConfig
import com.jihun.textviewer.domain.model.ReadingHistory
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
    onSetTotalPages: (Int) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.currentDocument != null) {
            ReaderInteractionSurface(
                state = state,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onGoToPage = onGoToPage,
                onSetTotalPages = onSetTotalPages,
                onToggleTheme = onToggleTheme,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            EmptyReaderHome(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        if (state.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp),
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

        if (state.currentDocument == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .padding(horizontal = 12.dp),
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

        if (state.isLoading && state.errorMessage == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(18.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
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
private fun EmptyReaderHome(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "텍스트 파일을 선택해 읽기를 시작하세요",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "TXT 열기 또는 이어읽기를 누르면 읽기 화면으로 진입합니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (history.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "아직 읽기 기록이 없습니다",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "TXT 파일을 열면 자동으로 기록됩니다.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedButton(
                            onClick = onOpenNewFile,
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            Text("TXT 열기")
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "읽기 테마",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "눈의 피로를 줄이기 위해 낮/밤 대비를 전환합니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "현재 버전: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onToggleTheme,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Text(if (darkTheme) "라이트 모드 사용" else "다크 모드 사용")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "릴리즈 노트",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}: 페이지 카운터 표시 및 화면 상단 테마 토글 추가",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "v1.0.0: 텍스트 파일 읽기, 기록 저장, 기본 제스처 제어",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
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
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.fileName ?: entry.fileUri,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "최근 열람: $updatedText",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onContinueReading,
                modifier = Modifier.padding(top = 12.dp),
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
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                shape,
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
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
