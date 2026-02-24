package com.jihun.textviewer.data.repository

import com.jihun.textviewer.data.source.HistoryPreferencesDataSource
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultReadingHistoryRepository(
    private val dataSource: HistoryPreferencesDataSource,
) : ReadingHistoryRepository {
    override suspend fun saveHistory(history: ReadingHistory) = withContext(Dispatchers.IO) {
        dataSource.save(history)
    }

    override suspend fun getHistory(fileUri: String): ReadingHistory? = withContext(Dispatchers.IO) {
        dataSource.get(fileUri)
    }

    override suspend fun getLatestHistory(): ReadingHistory? = withContext(Dispatchers.IO) {
        dataSource.getLatest()
    }

    override suspend fun getAllHistory(): List<ReadingHistory> = withContext(Dispatchers.IO) {
        dataSource.getAll()
    }

    override suspend fun clearHistory(fileUri: String) = withContext(Dispatchers.IO) {
        dataSource.clear(fileUri)
    }
}
