package com.jihun.textviewer.data.source

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class SafTextFileLoader(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Input stream is unavailable for URI: $uri")
            inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        }.recoverCatching { throwable ->
            if (throwable is SecurityException) {
                throw IOException("Read permission denied for URI: $uri", throwable)
            }
            if (throwable is IOException) {
                throw throwable
            }
            throw IOException("Failed to read text file from URI: $uri (${throwable.message})", throwable)
        }
    }

    fun getMimeType(uri: Uri): String? {
        return runCatching { contentResolver.getType(uri) }
            .getOrNull()
    }

    fun getDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex < 0) return null
            return cursor.getString(columnIndex)
        }
        return null
    }
}
