package com.jihun.textviewer.domain.util

import android.net.Uri
import java.util.Locale
import java.util.Locale.ROOT

object TextFileValidator {
    fun isTxtFile(uri: Uri, displayName: String? = null, mimeType: String? = null): Boolean {
        val resolvedMimeType = mimeType?.trim()?.lowercase(ROOT)
        if (!resolvedMimeType.isNullOrEmpty()) {
            if (resolvedMimeType.startsWith("text/")) {
                return true
            }

            if (resolvedMimeType.startsWith("image/")
                || resolvedMimeType.startsWith("audio/")
                || resolvedMimeType.startsWith("video/")
                || resolvedMimeType.startsWith("application/pdf")
                || resolvedMimeType.startsWith("application/zip")
                || resolvedMimeType.startsWith("application/ogg")
                || resolvedMimeType.startsWith("application/msword")
                || resolvedMimeType.startsWith("application/vnd.openxmlformats-officedocument")
                || resolvedMimeType.startsWith("application/vnd")
            ) {
                return false
            }
        }

        val candidateName = displayName ?: uri.lastPathSegment ?: return true
        val lowerName = candidateName.trim().lowercase(Locale.ROOT)
        return lowerName.endsWith(".txt")
            || lowerName.endsWith(".text")
            || lowerName.endsWith(".md")
            || lowerName.endsWith(".log")
            || lowerName.endsWith(".readme")
            || lowerName.contains(".txt")
            || lowerName.endsWith("/")
            || lowerName.startsWith("text")
            || resolvedMimeType == "application/octet-stream"
    }

    fun shouldAllowFallbackLoad(uri: Uri, displayName: String? = null, mimeType: String? = null): Boolean {
        return isTxtFile(uri, displayName, mimeType)
    }

    fun maybeTextFileName(uri: Uri, displayName: String? = null): String {
        return displayName ?: uri.lastPathSegment ?: "unknown.txt"
    }
}
