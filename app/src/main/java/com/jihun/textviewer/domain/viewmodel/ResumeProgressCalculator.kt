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

    fun resolvePageFromHistory(
        preferredPage: Int?,
        previousPageCount: Int?,
        previousPageSize: Int?,
        currentPageCount: Int,
        currentPageSize: Int,
    ): Int {
        if (currentPageCount <= 0) return 0
        if (preferredPage == null || preferredPage < 0) return 0

        val safePreferredPage = preferredPage.coerceIn(0, currentPageCount - 1)

        if (
            previousPageCount == currentPageCount &&
            (previousPageSize == null || previousPageSize == currentPageSize)
        ) {
            return safePreferredPage
        }

        val restoreRatio = computeRestoreRatio(
            preferredPage = preferredPage,
            previousPageCount = previousPageCount,
            previousPageSize = previousPageSize,
        )
        return restoreRatio?.let { ratio ->
            resolvePageFromRatio(ratio = ratio, totalPages = currentPageCount)
        } ?: safePreferredPage
    }
}
