package com.jihun.textviewer.domain.viewmodel

import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.model.TextPageRange

data class TextViewerState(
    val isLoading: Boolean = false,
    val currentDocument: TextDocument? = null,
    val currentPage: Int = 0,
    val currentOffset: Int = 0,
    val pageRanges: List<TextPageRange> = emptyList(),
    val pageContent: String = "",
    val totalPages: Int = 0,
    val history: List<ReadingHistory> = emptyList(),
    val errorMessage: String? = null,
)
