package com.jihun.textviewer.domain.viewmodel

import android.net.Uri
import com.jihun.textviewer.domain.model.TextPageRange

sealed interface TextViewerAction {
    data class OpenFile(val uri: Uri) : TextViewerAction
    data class OpenHistoryEntry(
        val fileUri: String,
        val page: Int,
        val totalPages: Int,
        val pageSize: Int,
        val currentOffset: Int = -1,
    ) : TextViewerAction

    data object LoadHistory : TextViewerAction
    data object ResumeLastSession : TextViewerAction
    data object CloseDocument : TextViewerAction
    data class GoToPage(val page: Int) : TextViewerAction
    data object NextPage : TextViewerAction
    data object PreviousPage : TextViewerAction
    data class SetPageRanges(
        val documentUri: String,
        val ranges: List<TextPageRange>,
    ) : TextViewerAction
}
