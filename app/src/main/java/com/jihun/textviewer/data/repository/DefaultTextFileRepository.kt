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
        if (!TextFileValidator.isTxtFile(uri, fileName)) {
            return Result.failure(IllegalArgumentException("Only .txt files are supported"))
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
        return TextFileValidator.isTxtFile(uri, fileName)
    }

    companion object {
        const val DEFAULT_PAGE_CHAR_LIMIT: Int = 1800
    }
}
