package com.jihun.textviewer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationUtilTest {

    @Test
    fun paginate_returnsSingleEmptyPage_whenInputIsEmpty() {
        val pages = PaginationUtil.paginate(content = "", pageCharLimit = 100)

        assertEquals(1, pages.size)
        assertEquals("", pages.first())
    }

    @Test
    fun paginate_prefersLineBreakBeforeLimit() {
        val content = "Alpha line\nBeta line\nGamma line"

        val pages = PaginationUtil.paginate(content = content, pageCharLimit = 15)

        assertTrue(pages.first().endsWith("\n"))
        assertEquals(content, pages.joinToString(""))
    }

    @Test
    fun paginate_fallsBackToWhitespaceSplit_whenNoLineBreak() {
        val content = "alpha beta gamma delta"

        val pages = PaginationUtil.paginate(content = content, pageCharLimit = 10)

        assertEquals(content, pages.joinToString(""))
        assertTrue(pages.size > 1)
    }

    @Test
    fun clampPage_boundsToAvailablePages() {
        assertEquals(0, PaginationUtil.clampPage(requestedPage = -5, totalPages = 4))
        assertEquals(2, PaginationUtil.clampPage(requestedPage = 2, totalPages = 4))
        assertEquals(3, PaginationUtil.clampPage(requestedPage = 40, totalPages = 4))
        assertEquals(0, PaginationUtil.clampPage(requestedPage = 1, totalPages = 0))
    }
}
