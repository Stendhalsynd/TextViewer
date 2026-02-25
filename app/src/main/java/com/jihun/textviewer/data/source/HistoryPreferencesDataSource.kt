package com.jihun.textviewer.data.source

import android.content.SharedPreferences
import android.net.Uri
import com.jihun.textviewer.domain.model.ReadingHistory
import org.json.JSONObject

class HistoryPreferencesDataSource(
    private val preferences: SharedPreferences,
) {
    fun save(history: ReadingHistory) {
        val key = historyKey(history.fileUri)
        preferences.edit()
            .putString(key, history.toJson().toString())
            .putString(KEY_LAST_URI, history.fileUri)
            .apply()
    }

    fun get(fileUri: String): ReadingHistory? {
        val raw = preferences.getString(historyKey(fileUri), null) ?: return null
        return parseHistory(raw)
    }

    fun getLatest(): ReadingHistory? {
        val uri = preferences.getString(KEY_LAST_URI, null) ?: return null
        return get(uri)
    }

    fun getAll(): List<ReadingHistory> {
        return preferences.all
            .asSequence()
            .filter { (key, value) -> key.startsWith(KEY_HISTORY_PREFIX) && value is String }
            .mapNotNull { (_, value) -> parseHistory(value as String) }
            .sortedByDescending { it.updatedAtMillis }
            .toList()
    }

    fun clear(fileUri: String) {
        preferences.edit().remove(historyKey(fileUri)).apply()
        val lastUri = preferences.getString(KEY_LAST_URI, null)
        if (lastUri == fileUri) {
            preferences.edit().remove(KEY_LAST_URI).apply()
        }
    }

    private fun historyKey(fileUri: String): String = KEY_HISTORY_PREFIX + Uri.encode(fileUri)

    private fun parseHistory(raw: String): ReadingHistory? {
        return runCatching {
            val json = JSONObject(raw)
            ReadingHistory(
                fileUri = json.getString("fileUri"),
                fileName = if (json.isNull("fileName")) null else json.getString("fileName"),
                currentPage = json.optInt("currentPage", 0),
                currentOffset = json.optInt("currentOffset", -1),
                pageSize = json.optInt("pageSize", 0),
                totalPages = json.optInt("totalPages", 0),
                updatedAtMillis = json.optLong("updatedAtMillis", 0L),
            )
        }.getOrNull()
    }

    private fun ReadingHistory.toJson(): JSONObject = JSONObject().apply {
        put("fileUri", fileUri)
        put("fileName", fileName)
        put("currentPage", currentPage)
        put("currentOffset", currentOffset)
        put("pageSize", pageSize)
        put("totalPages", totalPages)
        put("updatedAtMillis", updatedAtMillis)
    }

    companion object {
        private const val KEY_HISTORY_PREFIX = "history_"
        private const val KEY_LAST_URI = "last_uri"
    }
}
