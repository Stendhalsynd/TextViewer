package com.jihun.textviewer.domain.util

import android.net.Uri

object TextFileValidator {
    fun isTxtFile(uri: Uri, displayName: String? = null): Boolean {
        val candidateName = displayName ?: uri.lastPathSegment
        return candidateName
            ?.trim()
            ?.lowercase()
            ?.endsWith(".txt") == true
    }
}
