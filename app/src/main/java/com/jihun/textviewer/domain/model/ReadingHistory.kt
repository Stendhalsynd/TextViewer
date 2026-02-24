package com.jihun.textviewer.domain.model

data class ReadingHistory(
    val fileUri: String,
    val fileName: String?,
    val currentPage: Int,
    val pageSize: Int,
    val totalPages: Int,
    val updatedAtMillis: Long,
)
