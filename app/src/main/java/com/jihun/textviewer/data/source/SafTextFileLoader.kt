package com.jihun.textviewer.data.source

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.IOException

class SafTextFileLoader(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(READ_TIMEOUT_MS) {
                runInterruptible {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IOException("Input stream is unavailable for URI: $uri")
                    inputStream.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val buffer = CharArray(TEXT_BUFFER_SIZE)
                            val builder = StringBuilder()
                            var totalChars = 0
                            while (true) {
                                val read = reader.read(buffer)
                                if (read == -1) break
                                totalChars += read
                                if (totalChars > MAX_TEXT_CHAR_COUNT) {
                                    throw IOException(
                                        "파일이 너무 큽니다 (최대 ${MAX_TEXT_CHAR_COUNT}자): $uri",
                                    )
                                }
                                builder.append(buffer, 0, read)
                            }
                            builder.toString()
                        }
                    }
                }
            }
        }.recoverCatching { throwable ->
            if (throwable is SecurityException) {
                throw IOException("Read permission denied for URI: $uri", throwable)
            }
            if (throwable is TimeoutCancellationException) {
                throw IOException("Timed out while reading file: $uri", throwable)
            }
            if (throwable is IOException) {
                throw throwable
            }
            throw IOException("Failed to read text file from URI: $uri (${throwable.message})", throwable)
        }
    }

    suspend fun getDisplayNameWithTimeout(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(QUERY_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) { getDisplayName(uri) }
                }
            }.getOrNull()
        }
    }

    fun getMimeType(uri: Uri): String? {
        return runCatching { contentResolver.getType(uri) }
            .getOrNull()
    }

    fun getDisplayName(uri: Uri): String? {
        return try {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex < 0) return null
                cursor.getString(columnIndex)
            }
        } catch (ignored: Exception) {
            null
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 15_000L
        const val QUERY_TIMEOUT_MS = 5_000L
        const val TEXT_BUFFER_SIZE = 8_192
        const val MAX_TEXT_CHAR_COUNT = 1_000_000
    }
}
