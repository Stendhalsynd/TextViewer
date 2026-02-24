package com.jihun.textviewer.domain.repository

import com.jihun.textviewer.domain.model.ReadingHistory

interface ReadingHistoryRepository {
    suspend fun saveHistory(history: ReadingHistory)
    suspend fun getHistory(fileUri: String): ReadingHistory?
    suspend fun getLatestHistory(): ReadingHistory?
    suspend fun getAllHistory(): List<ReadingHistory>
    suspend fun clearHistory(fileUri: String)
}
