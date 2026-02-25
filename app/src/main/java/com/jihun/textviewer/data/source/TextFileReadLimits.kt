package com.jihun.textviewer.data.source

internal object TextFileReadLimits {
    const val MAX_TEXT_CHAR_COUNT = 5_000_000

    fun shouldReject(totalChars: Int): Boolean = totalChars > MAX_TEXT_CHAR_COUNT
}
