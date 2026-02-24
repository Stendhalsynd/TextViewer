package com.jihun.textviewer.domain.viewmodel

sealed interface TextViewerEffect {
    data class ShowError(val message: String) : TextViewerEffect
    data object FileOpened : TextViewerEffect
    data object Resumed : TextViewerEffect
}
