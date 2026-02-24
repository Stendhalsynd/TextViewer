package com.jihun.textviewer.domain.viewmodel

import android.net.Uri

sealed interface TextViewerAction {
    data class OpenFile(val uri: Uri) : TextViewerAction
    data class OpenHistoryEntry(val fileUri: String, val page: Int) : TextViewerAction
    data object LoadHistory : TextViewerAction
    data object ResumeLastSession : TextViewerAction
    data class GoToPage(val page: Int) : TextViewerAction
    data object NextPage : TextViewerAction
    data object PreviousPage : TextViewerAction
}
