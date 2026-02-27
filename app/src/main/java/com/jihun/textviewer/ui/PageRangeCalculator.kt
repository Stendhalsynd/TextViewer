package com.jihun.textviewer.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.jihun.textviewer.domain.model.TextPageRange
import kotlin.math.max
import java.util.LinkedHashMap

private const val PAGE_RANGE_CACHE_MAX_ENTRIES = 12
private const val MINIMUM_ESTIMATE_CHARS_PER_PAGE = 220
private const val MAX_EXACT_CALCULATION_CHARS = 300_000
private const val EXACT_CALCULATION_CHAR_GROWTH_BASE = 256
private const val PAGE_LAYOUT_BUCKET_PX = 8

private data class PageRangeCacheKey(
    val textHash: Int,
    val contentLength: Int,
    val widthPx: Int,
    val heightPx: Int,
    val textStyleSignature: Int,
)

private object PageRangeCache {
    private val cache: LinkedHashMap<PageRangeCacheKey, List<TextPageRange>> =
        object : LinkedHashMap<PageRangeCacheKey, List<TextPageRange>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<PageRangeCacheKey, List<TextPageRange>>?,
            ): Boolean {
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
    skipEstimateStage: Boolean = false,
    estimatedRanges: List<TextPageRange>? = null,
): List<TextPageRange> {
    if (text.isEmpty()) {
        return listOf(TextPageRange(0, 0))
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)
    val bucketedWidth = (width / PAGE_LAYOUT_BUCKET_PX).coerceAtLeast(1) * PAGE_LAYOUT_BUCKET_PX
    val bucketedHeight = (height / PAGE_LAYOUT_BUCKET_PX).coerceAtLeast(1) * PAGE_LAYOUT_BUCKET_PX
    val key = PageRangeCacheKey(
        textHash = text.hashCode(),
        contentLength = text.length,
        widthPx = bucketedWidth,
        heightPx = bucketedHeight,
        textStyleSignature = textStyleSignature(textStyle),
    )
    PageRangeCache.get(key)?.let { return it }

    val estimate = estimatedRanges ?: runCatching {
        estimatePageRanges(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            availableWidthPx = bucketedWidth,
            availableHeightPx = bucketedHeight,
        )
    }.getOrElse { listOf(TextPageRange(0, text.length)) }

    val ranges = if (text.length > MAX_EXACT_CALCULATION_CHARS && !skipEstimateStage) {
        estimate
    } else {
        runCatching {
            calculatePageRangesExact(
                text = text,
                textStyle = textStyle,
                textMeasurer = textMeasurer,
                availableWidthPx = bucketedWidth,
                availableHeightPx = bucketedHeight,
                estimatedRanges = estimate,
            )
        }.getOrElse { estimate }
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

    if (availableWidthPx <= 0 || availableHeightPx <= 0) {
        return listOf(TextPageRange(0, text.length))
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)
    val approximateCharsPerPage = estimateCharsPerPage(
        text = text,
        textStyle = textStyle,
        textMeasurer = textMeasurer,
        availableWidthPx = width,
        availableHeightPx = height,
    )

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
    estimatedRanges: List<TextPageRange>?,
): List<TextPageRange> {
    if (text.isEmpty()) {
        return listOf(TextPageRange(0, 0))
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)

    val estimatedCharsPerPage = estimateCharsPerPage(
        text = text,
        textStyle = textStyle,
        textMeasurer = textMeasurer,
        availableWidthPx = width,
        availableHeightPx = height,
        estimatedRangesHint = estimatedRanges,
    ).coerceAtLeast(MINIMUM_ESTIMATE_CHARS_PER_PAGE)

    val ranges = mutableListOf<TextPageRange>()
    var startOffset = 0

    while (startOffset < text.length) {
        val endOffset = resolvePageEndOffset(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            startOffset = startOffset,
            availableWidthPx = width,
            availableHeightPx = height,
            initialGuessChars = estimatedCharsPerPage,
        )
        val safeEnd = endOffset.coerceIn(startOffset + 1, text.length)
        ranges += TextPageRange(startOffset = startOffset, endOffset = safeEnd)
        startOffset = safeEnd
        if (safeEnd <= 0) {
            break
        }
    }

    return normalizePageRanges(
        ranges = ranges,
        contentLength = text.length,
    )
}

private fun resolvePageEndOffset(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    startOffset: Int,
    availableWidthPx: Int,
    availableHeightPx: Int,
    initialGuessChars: Int,
): Int {
    val contentLength = text.length
    if (startOffset >= contentLength - 1) return contentLength

    val minEnd = startOffset + 1
    if (startOffset >= contentLength) return contentLength
    if (!canFitRange(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            startOffset = startOffset,
            endOffset = minEnd,
            availableWidthPx = availableWidthPx,
            availableHeightPx = availableHeightPx,
        )
    ) {
        return minEnd
    }

    var fitEnd = minEnd
    var probeEnd = (startOffset + initialGuessChars).coerceAtMost(contentLength).coerceAtLeast(minEnd)

    while (true) {
        if (probeEnd >= contentLength) {
            return if (canFitRange(
                    text = text,
                    textStyle = textStyle,
                    textMeasurer = textMeasurer,
                    startOffset = startOffset,
                    endOffset = contentLength,
                    availableWidthPx = availableWidthPx,
                    availableHeightPx = availableHeightPx,
                )
            ) {
                contentLength
            } else {
                contentLength - 1
            }
        }

        val canFitProbe = canFitRange(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            startOffset = startOffset,
            endOffset = probeEnd,
            availableWidthPx = availableWidthPx,
            availableHeightPx = availableHeightPx,
        )
        if (canFitProbe) {
            fitEnd = probeEnd
            val growth = max((probeEnd - startOffset) / 2 + EXACT_CALCULATION_CHAR_GROWTH_BASE, EXACT_CALCULATION_CHAR_GROWTH_BASE)
            probeEnd = (probeEnd + growth).coerceAtMost(contentLength)
            continue
        }

        val failEnd = probeEnd
        return binarySearchPageEnd(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            startOffset = startOffset,
            availableWidthPx = availableWidthPx,
            availableHeightPx = availableHeightPx,
            low = fitEnd,
            high = failEnd,
        )
    }
}

private fun binarySearchPageEnd(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    startOffset: Int,
    availableWidthPx: Int,
    availableHeightPx: Int,
    low: Int,
    high: Int,
): Int {
    if (high <= low + 1) return low

    var knownGood = low
    var knownBad = high
    while (knownGood + 1 < knownBad) {
        val mid = (knownGood + knownBad) / 2 + ((knownGood + knownBad) % 2)
        val canFit = canFitRange(
            text = text,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            startOffset = startOffset,
            endOffset = mid,
            availableWidthPx = availableWidthPx,
            availableHeightPx = availableHeightPx,
        )
        if (canFit) {
            knownGood = mid
        } else {
            knownBad = mid
        }
    }
    return knownGood
}

private fun canFitRange(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    startOffset: Int,
    endOffset: Int,
    availableWidthPx: Int,
    availableHeightPx: Int,
): Boolean {
    if (endOffset <= startOffset) return false
    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)
    val candidate = text.substring(startOffset, endOffset)
    if (candidate.isEmpty()) return false

    return runCatching {
        textMeasurer.measure(
            text = AnnotatedString(candidate),
            style = textStyle,
            constraints = Constraints(
                maxWidth = width,
                maxHeight = height,
            ),
            softWrap = true,
            overflow = TextOverflow.Clip,
        ).size.height <= height
    }.getOrDefault(false)
}

private fun estimateCharsPerPage(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    availableWidthPx: Int,
    availableHeightPx: Int,
    estimatedRangesHint: List<TextPageRange>? = null,
): Int {
    if (!estimatedRangesHint.isNullOrEmpty()) {
        val hintCount = minOf(estimatedRangesHint.size, 3)
        var hintSum = 0
        for (index in 0 until hintCount) {
            val range = estimatedRangesHint[index]
            hintSum += range.endOffset - range.startOffset
        }
        val averageHint = if (hintCount > 0) hintSum / hintCount else 0
        if (averageHint > 0) {
            return averageHint
        }
    }

    val width = availableWidthPx.coerceAtLeast(1)
    val height = availableHeightPx.coerceAtLeast(1)

    val sampleText = "가나다라마바사".take(text.length.coerceAtMost(12))
    val sampleLayout = runCatching {
        textMeasurer.measure(
            text = AnnotatedString(sampleText),
            style = textStyle,
            constraints = Constraints(
                maxWidth = width,
                maxHeight = height,
            ),
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }.getOrElse { return MINIMUM_ESTIMATE_CHARS_PER_PAGE }

    val sampleLineHeight = sampleLayout.size.height.toFloat().coerceAtLeast(1f)
    val estimatedLinesPerPage = (height / sampleLineHeight).coerceAtLeast(1f)
    val averageCharWidth = sampleLayout.size.width.toFloat().coerceAtLeast(4f) / maxOf(sampleText.length, 1).toFloat()
    val approximateCharsPerLine = (width / averageCharWidth).toInt().coerceAtLeast(8)
    return (approximateCharsPerLine * estimatedLinesPerPage).toInt().coerceAtLeast(MINIMUM_ESTIMATE_CHARS_PER_PAGE)
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
