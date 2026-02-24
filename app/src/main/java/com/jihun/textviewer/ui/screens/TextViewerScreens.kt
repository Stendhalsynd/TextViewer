package com.jihun.textviewer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onToggleTheme: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Comfortable reading with clear contrast",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Open a .txt file and continue where you left off.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFileClick,
                ) {
                    Text("Choose TXT file")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onResumeClick,
                ) {
                    Text("Continue reading")
                }
            }
        }
        item {
            if (state.isLoading) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading file...",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                ReaderInteractionSurface(
                    state = state,
                    onPreviousPage = onPreviousPage,
                    onNextPage = onNextPage,
                    onToggleTheme = onToggleTheme,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
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
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reading history",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Tap continue to reopen a file at your saved page.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "No history yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Open a TXT file from Home to create a reading history.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        OutlinedButton(
                            onClick = onOpenNewFile,
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            Text("Choose TXT file")
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
        item { Spacer(modifier = Modifier.height(16.dp)) }
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Readability",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Switch between day and night contrast profiles.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = onToggleTheme) {
            Text(if (darkTheme) "Use day mode" else "Use night mode")
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
    val progressText = "Page $currentPage of $totalPages"
    val updatedText = DateUtils.getRelativeTimeSpanString(
        entry.updatedAtMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
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
                text = "Updated $updatedText",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onContinueReading,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Continue reading")
            }
        }
    }
}
