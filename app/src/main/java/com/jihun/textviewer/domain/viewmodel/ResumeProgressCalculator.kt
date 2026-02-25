package com.jihun.textviewer.domain.viewmodel

internal object ResumeProgressCalculator {
    fun computeRestoreRatio(
        preferredPage: Int?,
        previousPageCount: Int?,
        previousPageSize: Int?,
    ): Float? {
        if (preferredPage == null || preferredPage < 0) return null
        if (previousPageCount == null || previousPageCount <= 0) return null
        if (previousPageSize != null && previousPageSize <= 0) return null

        return (preferredPage + 0.5f) / previousPageCount.toFloat()
    }

    fun resolvePageFromRatio(ratio: Float, totalPages: Int): Int {
        if (totalPages <= 0) return 0
        return ((totalPages - 1) * ratio).toInt().coerceIn(0, totalPages - 1)
    }
}
