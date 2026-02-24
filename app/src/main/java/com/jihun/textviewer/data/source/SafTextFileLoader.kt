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
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: throw IOException("Unable to open input stream for URI: $uri")
        }
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
