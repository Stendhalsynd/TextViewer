package com.jihun.textviewer.domain.util

import android.net.Uri
import java.util.Locale
import java.util.Locale.ROOT

object TextFileValidator {
    fun isTxtFile(uri: Uri, displayName: String? = null, mimeType: String? = null): Boolean {
        val resolvedMimeType = mimeType?.trim()?.lowercase(ROOT)
        if (!resolvedMimeType.isNullOrEmpty() && resolvedMimeType.startsWith("text/")) {
            return true
        }

        val candidateName = displayName ?: uri.lastPathSegment
        val lowerName = candidateName?.trim()?.lowercase(Locale.getDefault()) ?: return false
        return lowerName.endsWith(".txt")
            || lowerName.endsWith(".text")
            || lowerName.endsWith(".md")
            || lowerName.endsWith(".log")
            || lowerName.endsWith(".readme")
    }
}
