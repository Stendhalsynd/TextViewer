package com.jihun.textviewer.domain.model

data class TextDocument(
    val uri: String,
    val fileName: String?,
    val content: String,
)
