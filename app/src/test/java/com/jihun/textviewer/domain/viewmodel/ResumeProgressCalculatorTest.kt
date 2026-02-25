package com.jihun.textviewer.domain.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeProgressCalculatorTest {
    @Test
    fun computeRestoreRatio_returnsNullForInvalidHistory() {
        assertNull(ResumeProgressCalculator.computeRestoreRatio(preferredPage = null, previousPageCount = 10, previousPageSize = 1800))
        assertNull(ResumeProgressCalculator.computeRestoreRatio(preferredPage = 4, previousPageCount = null, previousPageSize = 1800))
        assertNull(ResumeProgressCalculator.computeRestoreRatio(preferredPage = 4, previousPageCount = 0, previousPageSize = 1800))
        assertNull(ResumeProgressCalculator.computeRestoreRatio(preferredPage = -1, previousPageCount = 10, previousPageSize = 1800))
        assertNull(ResumeProgressCalculator.computeRestoreRatio(preferredPage = 4, previousPageCount = 10, previousPageSize = 0))
    }

    @Test
    fun computeRestoreRatio_calculatesRatioForValidHistory() {
        val ratio = ResumeProgressCalculator.computeRestoreRatio(
            preferredPage = 4,
            previousPageCount = 10,
            previousPageSize = 1800,
        )

        assertEquals(0.45f, ratio ?: -1f, 0.000_001f)
    }

    @Test
    fun resolvePageFromRatio_mapsToSafePage() {
        val page = ResumeProgressCalculator.resolvePageFromRatio(ratio = 0.45f, totalPages = 21)

        assertEquals(9, page)
    }
}
