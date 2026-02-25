package com.jihun.textviewer.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import com.jihun.textviewer.domain.model.TextPageRange

internal fun calculatePageRanges(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    availableWidthPx: Int,
    availableHeightPx: Int,
): List<TextPageRange> {
    if (text.isEmpty()) {
        return listOf(TextPageRange(0, 0))
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)

    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle,
        constraints = Constraints(maxWidth = width),
        softWrap = true,
        overflow = TextOverflow.Clip,
    )

    if (layout.lineCount <= 0) {
        return listOf(TextPageRange(0, text.length))
    }

    val rawRanges = mutableListOf<TextPageRange>()
    var startLine = 0
    val viewportHeight = height.toFloat()

    while (startLine < layout.lineCount) {
        val pageTop = layout.getLineTop(startLine)
        var endLine = startLine

        while (endLine + 1 < layout.lineCount) {
            if (layout.getLineBottom(endLine + 1) - pageTop <= viewportHeight) {
                endLine += 1
            } else {
                break
            }
        }

        val startOffset = layout.getLineStart(startLine)
        val lineEnd = layout.getLineEnd(endLine, true)
        val clampedEnd = lineEnd.coerceIn(startOffset, text.length)
        val endOffset = if (clampedEnd > startOffset) {
            clampedEnd
        } else {
            (startOffset + 1).coerceAtMost(text.length)
        }
        rawRanges += TextPageRange(startOffset = startOffset, endOffset = endOffset)

        startLine = endLine + 1
    }

    return normalizePageRanges(
        ranges = rawRanges,
        contentLength = text.length,
    )
}

private fun normalizePageRanges(
    ranges: List<TextPageRange>,
    contentLength: Int,
): List<TextPageRange> {
    if (contentLength <= 0) return listOf(TextPageRange(startOffset = 0, endOffset = 0))
    if (ranges.isEmpty()) return listOf(TextPageRange(startOffset = 0, endOffset = contentLength))

    val sortedRanges = ranges
        .asSequence()
        .map { range ->
            val start = range.startOffset.coerceIn(0, contentLength)
            val end = range.endOffset.coerceIn(start, contentLength)
            TextPageRange(startOffset = start, endOffset = end)
        }
        .filter { it.endOffset > it.startOffset }
        .sortedBy { it.startOffset }
        .toList()

    if (sortedRanges.isEmpty()) return listOf(TextPageRange(startOffset = 0, endOffset = contentLength))

    val normalized = mutableListOf<TextPageRange>()
    var cursor = 0

    sortedRanges.forEach { range ->
        if (cursor >= contentLength) return@forEach

        val clampedStart = range.startOffset.coerceIn(0, contentLength)
        val clampedEnd = range.endOffset.coerceIn(clampedStart, contentLength)
        if (clampedEnd <= cursor) return@forEach

        if (clampedStart > cursor) {
            normalized += TextPageRange(startOffset = cursor, endOffset = clampedStart)
            cursor = clampedStart
        }

        if (normalized.isNotEmpty() && normalized.last().endOffset >= clampedStart) {
            val last = normalized.removeAt(normalized.lastIndex)
            val mergedEnd = max(last.endOffset, clampedEnd)
            normalized += TextPageRange(startOffset = last.startOffset, endOffset = mergedEnd)
        } else {
            normalized += TextPageRange(startOffset = clampedStart, endOffset = clampedEnd)
        }

        cursor = normalized.last().endOffset
    }

    if (cursor < contentLength) {
        normalized += TextPageRange(startOffset = cursor, endOffset = contentLength)
    }

    return normalized
}
