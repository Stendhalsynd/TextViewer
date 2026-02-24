package com.jihun.textviewer.domain.repository

import android.net.Uri
import com.jihun.textviewer.domain.model.TextDocument

interface TextFileRepository {
    suspend fun loadTextFile(uri: Uri): Result<TextDocument>
    fun isTxtFile(uri: Uri): Boolean
}
