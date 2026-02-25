package com.jihun.textviewer.data.repository

import android.net.Uri
import com.jihun.textviewer.data.source.SafTextFileLoader
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.repository.TextFileRepository
import com.jihun.textviewer.domain.util.PaginationUtil
import com.jihun.textviewer.domain.util.TextFileValidator

class DefaultTextFileRepository(
    private val loader: SafTextFileLoader,
    private val pageCharLimit: Int = DEFAULT_PAGE_CHAR_LIMIT,
) : TextFileRepository {

    override suspend fun loadTextFile(uri: Uri): Result<TextDocument> {
        val fileName = loader.getDisplayName(uri) ?: uri.lastPathSegment
        val mimeType = loader.getMimeType(uri)
        if (!TextFileValidator.isTxtFile(uri, fileName, mimeType)) {
            return Result.failure(
                IllegalArgumentException("지원되지 않는 파일 형식입니다. TXT/텍스트 파일만 열 수 있습니다."),
            )
        }

        return loader.loadText(uri).map { content ->
            TextDocument(
                uri = uri.toString(),
                fileName = fileName,
                content = content,
                pages = PaginationUtil.paginate(content = content, pageCharLimit = pageCharLimit),
            )
        }
    }

    override fun isTxtFile(uri: Uri): Boolean {
        val fileName = loader.getDisplayName(uri) ?: uri.lastPathSegment
        val mimeType = loader.getMimeType(uri)
        return TextFileValidator.isTxtFile(uri, fileName, mimeType)
    }

    companion object {
        const val DEFAULT_PAGE_CHAR_LIMIT: Int = 1800
    }
}
