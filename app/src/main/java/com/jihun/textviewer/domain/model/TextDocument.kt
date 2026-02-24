package com.jihun.textviewer.domain.model

data class TextDocument(
    val uri: String,
    val fileName: String?,
    val content: String,
    val pages: List<String>,
) {
    val totalPages: Int = pages.size
}
