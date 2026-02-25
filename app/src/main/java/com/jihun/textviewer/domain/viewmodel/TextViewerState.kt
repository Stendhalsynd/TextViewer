package com.jihun.textviewer.domain.viewmodel

import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument

data class TextViewerState(
    val isLoading: Boolean = false,
    val currentDocument: TextDocument? = null,
    val currentPage: Int = 0,
    val pageContent: String = "",
    val totalPages: Int = 0,
    val pendingRestoreRatio: Float? = null,
    val history: List<ReadingHistory> = emptyList(),
    val errorMessage: String? = null,
)
