package com.jihun.textviewer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.domain.viewmodel.TextViewerState
import com.jihun.textviewer.ui.theme.TextViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderInteractionSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeToggle_isStableAfterManyPageStateChanges() {
        val documentContent = "테마 토글 버튼의 반복 사용을 검증하기 위한 텍스트입니다. ".repeat(80)
        val pageRanges = listOf(
            TextPageRange(0, 60),
            TextPageRange(60, 120),
            TextPageRange(120, 180),
            TextPageRange(180, 240),
            TextPageRange(240, 300),
            TextPageRange(300, documentContent.length),
        )
        var themeToggleCount = 0
        var currentPage by mutableIntStateOf(0)

        composeRule.setContent {
            val current = currentPage
            val state = TextViewerState(
                currentDocument = TextDocument(
                    uri = "content://test/document.txt",
                    fileName = "document.txt",
                    content = documentContent,
                ),
                currentPage = current,
                currentOffset = pageRanges[current].startOffset,
                pageRanges = pageRanges,
                pageContent = documentContent.substring(
                    pageRanges[current].startOffset,
                    pageRanges[current].endOffset,
                ),
                totalPages = pageRanges.size,
            )

            TextViewerTheme {
                ReaderInteractionSurface(
                    state = state,
                    onPreviousPage = {
                        currentPage = (currentPage - 1).coerceAtLeast(0)
                    },
                    onNextPage = {
                        currentPage = (currentPage + 1).coerceAtMost(pageRanges.lastIndex)
                    },
                    onGoToPage = { page ->
                        currentPage = page.coerceIn(0, pageRanges.lastIndex)
                    },
                    onToggleTheme = {
                        themeToggleCount += 1
                    },
                    onPageRangesUpdated = { _, _ -> },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val themeToggle = composeRule.onNodeWithTag("theme-toggle-button")
        composeRule.waitForIdle()

        themeToggle.performClick()
        assertEquals(1, themeToggleCount)

        repeat(20) { index ->
            composeRule.runOnUiThread {
                currentPage = (currentPage + 1).coerceAtMost(pageRanges.lastIndex)
            }
            composeRule.waitForIdle()
            if (index == 9) {
                currentPage = 0
                composeRule.waitForIdle()
            }
        }

        repeat(5) {
            themeToggle.performClick()
            composeRule.waitForIdle()
        }

        assertEquals(6, themeToggleCount)

        composeRule.runOnUiThread {
            currentPage = pageRanges.lastIndex
        }
        composeRule.waitForIdle()

        repeat(3) {
            themeToggle.performClick()
            composeRule.waitForIdle()
        }

        assertEquals(9, themeToggleCount)
    }
}
