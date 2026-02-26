package com.jihun.textviewer.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.jihun.textviewer.domain.model.TextPageRange
import java.util.LinkedHashMap
import kotlin.math.max

private const val PAGE_RANGE_CACHE_MAX_ENTRIES = 8
private const val MINIMUM_ESTIMATE_CHARS_PER_PAGE = 220
private const val MAX_EXACT_CALCULATION_CHARS = 300_000

private data class PageRangeCacheKey(
    val textHash: Int,
    val contentLength: Int,
    val widthPx: Int,
    val heightPx: Int,
    val textStyleSignature: Int,
)

private object PageRangeCache {
    private val cache: LinkedHashMap<PageRangeCacheKey, List<TextPageRange>> = object : LinkedHashMap<PageRangeCacheKey, List<TextPageRange>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageRangeCacheKey, List<TextPageRange>>?): Boolean {
            return size > PAGE_RANGE_CACHE_MAX_ENTRIES
        }
    }

    fun get(key: PageRangeCacheKey): List<TextPageRange>? = synchronized(cache) {
        cache[key]
    }

    fun put(key: PageRangeCacheKey, ranges: List<TextPageRange>) {
        synchronized(cache) {
            cache[key] = ranges
        }
    }
}

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

    val key = PageRangeCacheKey(
        textHash = text.hashCode(),
        contentLength = text.length,
        widthPx = availableWidthPx.coerceAtLeast(1),
        heightPx = availableHeightPx.coerceAtLeast(1),
        textStyleSignature = textStyleSignature(textStyle),
    )
    PageRangeCache.get(key)?.let { return it }

    val ranges = if (text.length <= MAX_EXACT_CALCULATION_CHARS) {
        runCatching {
            calculatePageRangesExact(
                text = text,
                textStyle = textStyle,
                textMeasurer = textMeasurer,
                availableWidthPx = key.widthPx,
                availableHeightPx = key.heightPx,
            )
        }.getOrElse { estimatePageRanges(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            availableWidthPx = key.widthPx,
            availableHeightPx = key.heightPx,
        ) }
    } else {
        estimatePageRanges(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            availableWidthPx = key.widthPx,
            availableHeightPx = key.heightPx,
        )
    }

    val normalizedRanges = normalizePageRanges(
        ranges = ranges,
        contentLength = text.length,
    )
    PageRangeCache.put(key, normalizedRanges)
    return normalizedRanges
}

internal fun estimatePageRanges(
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

    val sample = textMeasurer.measure(
        text = AnnotatedString("가나다라마바사"),
        style = textStyle,
        constraints = Constraints(maxWidth = width),
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
    val averageCharWidth = sample.size.width.toFloat().coerceAtLeast(1f) / 7f
    val approximateLinesPerPage = (height / sample.size.height.coerceAtLeast(1)).coerceAtLeast(1)
    val approximateCharsPerLine = (width / averageCharWidth).toInt().coerceAtLeast(8)
    val approximateCharsPerPage = (approximateCharsPerLine * approximateLinesPerPage).coerceAtLeast(MINIMUM_ESTIMATE_CHARS_PER_PAGE)

    val ranges = mutableListOf<TextPageRange>()
    var start = 0
    while (start < text.length) {
        val candidateEnd = start + approximateCharsPerPage
        val end = candidateEnd.coerceAtMost(text.length).coerceAtLeast(start + 1)
        ranges += TextPageRange(startOffset = start, endOffset = end)
        start = end
    }

    return normalizePageRanges(
        ranges = ranges,
        contentLength = text.length,
    )
}

private fun calculatePageRangesExact(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    availableWidthPx: Int,
    availableHeightPx: Int,
): List<TextPageRange> {
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

private fun textStyleSignature(textStyle: TextStyle): Int {
    var signature = 17
    fun mix(value: Int) {
        signature = 31 * signature + value
    }

    mix(textStyle.fontSize.value.toBits().toInt())
    mix(textStyle.letterSpacing.value.toBits().toInt())
    mix(textStyle.lineHeight.value.toBits().toInt())
    mix(textStyle.fontWeight?.weight ?: 0)
    mix(textStyle.fontStyle?.hashCode() ?: 0)
    mix(textStyle.fontFamily?.hashCode() ?: 0)

    return signature
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
        .asSequence()
        .fold(mutableListOf<TextPageRange>()) { merged, candidate ->
            if (merged.isEmpty()) {
                merged += candidate
            } else {
                val last = merged.last()
                if (last.endOffset == candidate.startOffset || candidate.startOffset <= last.endOffset) {
                    merged[merged.lastIndex] = TextPageRange(
                        startOffset = minOf(last.startOffset, candidate.startOffset),
                        endOffset = max(last.endOffset, candidate.endOffset),
                    )
                } else {
                    merged += candidate
                }
            }
            merged
        }
}
