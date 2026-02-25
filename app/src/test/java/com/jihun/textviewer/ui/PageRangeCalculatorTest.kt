package com.jihun.textviewer.ui

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.ui.theme.TextViewerTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageRangeCalculatorTest {

    @Test
    fun calculatePageRanges_coversAllTextWithoutGapOrLoss() {
        val content = """
            Page calculation is based on screen height and should never skip characters when moving.
            On devices with narrow content area, line wrapping and page boundaries must stay contiguous.
            If this test fails, next/previous movement will show missing middle text.
        """.trimIndent().repeat(40)

        val ranges = calculatePageRanges(
            text = content,
            textStyle = TextViewerTypography.bodyLarge,
            textMeasurer = createTestMeasurer(),
            availableWidthPx = 900,
            availableHeightPx = 1540,
        )

        assertPageRangesAreContinuousAndLossless(content, ranges)
    }

    @Test
    fun calculatePageRanges_handlesWideRangeLayoutsWithoutCoverageGap() {
        val content = ("Galaxy S24 기준으로 긴 텍스트를 반복 출력합니다. ".repeat(200))

        val singleLineRanges = calculatePageRanges(
            text = content,
            textStyle = TextViewerTypography.bodyLarge,
            textMeasurer = createTestMeasurer(),
            availableWidthPx = 320,
            availableHeightPx = 1700,
        )
        val wideRanges = calculatePageRanges(
            text = content,
            textStyle = TextViewerTypography.bodyLarge,
            textMeasurer = createTestMeasurer(),
            availableWidthPx = 1080,
            availableHeightPx = 1700,
        )

        assertPageRangesAreContinuousAndLossless(content, singleLineRanges)
        assertPageRangesAreContinuousAndLossless(content, wideRanges)
        assertTrue(wideRanges.size >= 1)
        assertTrue(wideRanges.size <= singleLineRanges.size)
    }

    private fun assertPageRangesAreContinuousAndLossless(
        content: String,
        ranges: List<TextPageRange>,
    ) {
        assertTrue(ranges.isNotEmpty())
        assertEquals(0, ranges.first().startOffset)
        assertEquals(content.length, ranges.last().endOffset)

        ranges.forEachIndexed { _, range ->
            assertTrue(range.startOffset <= range.endOffset)
            assertTrue(range.startOffset >= 0)
            assertTrue(range.endOffset <= content.length)
        }

        for (index in 0 until ranges.size - 1) {
            val current = ranges[index]
            val next = ranges[index + 1]
            assertEquals(
                "page boundaries must be contiguous",
                current.endOffset,
                next.startOffset,
            )
        }

        val reconstructed = ranges.joinToString("") {
            content.substring(it.startOffset, it.endOffset)
        }
        assertEquals(content, reconstructed)
    }

    private fun createTestMeasurer(): TextMeasurer {
        val context = RuntimeEnvironment.getApplication() as Context
        val metrics = context.resources.displayMetrics
        return TextMeasurer(
            fallbackFontFamilyResolver = createFontFamilyResolver(context),
            fallbackDensity = Density(metrics.density, metrics.scaledDensity),
            fallbackLayoutDirection = LayoutDirection.Ltr,
        )
    }
}
