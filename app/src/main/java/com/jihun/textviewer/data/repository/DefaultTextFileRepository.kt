package com.jihun.textviewer.data.repository

import android.net.Uri
import com.jihun.textviewer.data.source.SafTextFileLoader
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.repository.TextFileRepository
import com.jihun.textviewer.domain.util.TextFileValidator

class DefaultTextFileRepository(
    private val loader: SafTextFileLoader,
) : TextFileRepository {

    override suspend fun loadTextFile(uri: Uri): Result<TextDocument> {
        val fileName = loader.getDisplayNameWithTimeout(uri) ?: uri.lastPathSegment
        val mimeType = loader.getMimeType(uri)

        if (!TextFileValidator.shouldAllowFallbackLoad(uri, fileName, mimeType)) {
            return Result.failure(
                IllegalArgumentException(
                    "지원되지 않는 파일 형식입니다. TXT/텍스트 파일만 열 수 있습니다.\n" +
                        "URI: $uri\n" +
                        "MIME: ${mimeType ?: "확인 안 됨"}",
                ),
            )
        }

        return loader.loadText(uri).mapCatching { content ->
            if (content.contains('\u0000')) {
                throw IllegalArgumentException("바이너리 데이터로 감지된 파일은 열 수 없습니다: $uri")
            }
            TextDocument(
                uri = uri.toString(),
                fileName = fileName,
                content = content,
            )
        }
    }

    override fun isTxtFile(uri: Uri): Boolean {
        val fileName = loader.getDisplayName(uri) ?: uri.lastPathSegment
        val mimeType = loader.getMimeType(uri)
        return TextFileValidator.shouldAllowFallbackLoad(uri, fileName, mimeType)
    }
}
