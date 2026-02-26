package com.jihun.textviewer.domain.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import com.jihun.textviewer.domain.repository.TextFileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TextViewerViewModel(
    private val textFileRepository: TextFileRepository,
    private val historyRepository: ReadingHistoryRepository,
    private val pageCharLimit: Int = DEFAULT_PAGE_CHAR_LIMIT,
) : ViewModel() {
    private val _state = MutableStateFlow(TextViewerState())
    val state: StateFlow<TextViewerState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<TextViewerEffect>()
    val effect: SharedFlow<TextViewerEffect> = _effect.asSharedFlow()

    private var pendingRestoreOffset: Int? = null

    fun onAction(action: TextViewerAction) {
        when (action) {
            is TextViewerAction.OpenFile -> openFile(
                uri = action.uri,
                preferredPage = null,
                previousPageCount = null,
                previousPageSize = null,
                preferredOffset = null,
                emitResumed = false,
            )
            is TextViewerAction.OpenHistoryEntry -> openFile(
                uri = Uri.parse(action.fileUri),
                preferredPage = action.page,
                previousPageCount = action.totalPages,
                previousPageSize = action.pageSize,
                preferredOffset = action.currentOffset.takeIf { it >= 0 },
                emitResumed = true,
            )
            TextViewerAction.LoadHistory -> loadHistory()
            TextViewerAction.ResumeLastSession -> resumeLastSession()
            TextViewerAction.CloseDocument -> closeDocument()
            is TextViewerAction.SetPageRanges -> applyPageRanges(
                documentUri = action.documentUri,
                ranges = action.ranges,
            )
            is TextViewerAction.GoToPage -> setPage(action.page)
            TextViewerAction.NextPage -> movePage(1)
            TextViewerAction.PreviousPage -> movePage(-1)
        }
    }

    private fun closeDocument() {
        pendingRestoreOffset = null
        _state.update {
            it.copy(
                isLoading = false,
                currentDocument = null,
                currentPage = 0,
                currentOffset = 0,
                pageRanges = emptyList(),
                pageContent = "",
                totalPages = 0,
                errorMessage = null,
            )
        }
    }

    private fun openFile(
        uri: Uri,
        preferredPage: Int?,
        previousPageCount: Int? = null,
        previousPageSize: Int? = null,
        preferredOffset: Int? = null,
        emitResumed: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            textFileRepository.loadTextFile(uri)
                .onSuccess { document ->
                    if (document.content.isBlank()) {
                        val message = "선택한 파일에서 텍스트를 읽을 수 없습니다."
                        _state.update {
                            it.copy(
                                isLoading = false,
                                currentDocument = null,
                                currentPage = 0,
                                currentOffset = 0,
                                pageRanges = emptyList(),
                                pageContent = "",
                                totalPages = 0,
                                errorMessage = message,
                            )
                        }
                        _effect.emit(TextViewerEffect.ShowError(message))
                        return@onSuccess
                    }

                    val initialOffset = resolveRestoreOffset(
                        contentLength = document.content.length,
                        preferredOffset = preferredOffset,
                        preferredPage = preferredPage,
                        previousPageCount = previousPageCount,
                        previousPageSize = previousPageSize,
                    )
                    pendingRestoreOffset = initialOffset

                    _state.update {
                        it.copy(
                            isLoading = false,
                            currentDocument = document,
                            currentPage = 0,
                            currentOffset = 0,
                            pageRanges = emptyList(),
                            pageContent = "",
                            totalPages = 0,
                            errorMessage = null,
                        )
                    }

                    if (emitResumed) {
                        _effect.emit(TextViewerEffect.Resumed)
                    } else {
                        _effect.emit(TextViewerEffect.FileOpened)
                    }
                }
                .onFailure { throwable ->
                    val message = formatOpenFileError(throwable)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            currentDocument = null,
                            currentPage = 0,
                            currentOffset = 0,
                            pageRanges = emptyList(),
                            pageContent = "",
                            totalPages = 0,
                            errorMessage = message,
                        )
                    }
                    _effect.emit(TextViewerEffect.ShowError(message))
                }
        }
    }

    private fun resolveRestoreOffset(
        contentLength: Int,
        preferredOffset: Int?,
        preferredPage: Int?,
        previousPageCount: Int?,
        previousPageSize: Int?,
    ): Int {
        if (contentLength <= 0) return 0

        preferredOffset?.takeIf { it >= 0 }?.let {
            return it.coerceIn(0, contentLength - 1)
        }

        val ratio = ResumeProgressCalculator.computeRestoreRatio(
            preferredPage = preferredPage,
            previousPageCount = previousPageCount,
            previousPageSize = previousPageSize,
        ) ?: return 0

        return (ratio * contentLength).toInt().coerceIn(0, contentLength - 1)
    }

    private fun applyPageRanges(documentUri: String, ranges: List<TextPageRange>) {
        val current = _state.value
        val document = current.currentDocument ?: return
        if (document.uri != documentUri) return
        if (document.content.isEmpty()) return

        val normalizedRanges = normalizeRanges(ranges, document.content.length)
        if (normalizedRanges.isEmpty()) return

        val anchorOffset = pendingRestoreOffset ?: current.currentOffset
        val safeAnchorOffset = anchorOffset.coerceIn(0, document.content.length - 1)
        val page = pageIndexForOffset(normalizedRanges, safeAnchorOffset)
        val selectedRange = normalizedRanges[page]

        _state.update {
            it.copy(
                currentPage = page,
                currentOffset = selectedRange.startOffset,
                pageRanges = normalizedRanges,
                totalPages = normalizedRanges.size,
                pageContent = document.content.substring(selectedRange.startOffset, selectedRange.endOffset),
            )
        }
        pendingRestoreOffset = null

        viewModelScope.launch {
            persistCurrentProgress(document, page, selectedRange.startOffset)
        }
    }

    private fun movePage(delta: Int) {
        val current = _state.value
        if (current.currentDocument == null) return
        val ranges = current.pageRanges
        if (ranges.isEmpty() || delta == 0) return

        if (ranges.isEmpty()) return

        val currentPage = current.currentPage.coerceIn(0, ranges.size - 1)
        val targetPage = (currentPage + delta).coerceIn(0, ranges.lastIndex)
        if (targetPage == currentPage) return

        setPage(targetPage)
    }

    private fun normalizeRanges(
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

        if (sortedRanges.isEmpty()) {
            return listOf(TextPageRange(startOffset = 0, endOffset = contentLength))
        }

        val normalized = mutableListOf<TextPageRange>()
        var cursor = 0

        sortedRanges.forEach { range ->
            val start = range.startOffset.coerceAtLeast(cursor)
            val end = range.endOffset.coerceAtLeast(start).coerceAtMost(contentLength)

            if (end <= cursor) return@forEach

            if (start > cursor) {
                normalized += TextPageRange(startOffset = cursor, endOffset = start)
            }

            normalized += TextPageRange(startOffset = start, endOffset = end)
            cursor = end
            if (cursor >= contentLength) return@forEach
        }

        if (cursor < contentLength) {
            normalized += TextPageRange(startOffset = cursor, endOffset = contentLength)
        }

        return normalized
    }

    private fun pageIndexForOffset(
        ranges: List<TextPageRange>,
        offset: Int,
        preferNextOnBoundary: Boolean = false,
    ): Int {
        if (ranges.isEmpty()) return 0

        val safeOffset = offset.coerceIn(0, ranges.last().endOffset)
        val directMatch = ranges.indexOfFirst { safeOffset >= it.startOffset && safeOffset < it.endOffset }
        if (directMatch >= 0) return directMatch

        if (safeOffset <= ranges.first().startOffset) return 0
        if (safeOffset >= ranges.last().endOffset) return ranges.lastIndex

        val boundaryMatch = ranges.indexOfLast { it.endOffset == safeOffset || it.endOffset < safeOffset }
        if (boundaryMatch < 0) return 0
        if (
            preferNextOnBoundary &&
            ranges[boundaryMatch].endOffset == safeOffset &&
            boundaryMatch + 1 < ranges.size
        ) return boundaryMatch + 1

        return boundaryMatch
    }

    private fun formatOpenFileError(throwable: Throwable): String {
        val source = throwable.message?.trim().orEmpty()
        return when {
            source.contains("Read permission denied") ->
                "파일 읽기 권한이 없습니다. 파일 앱에서 다시 선택해 주세요."
            source.contains("Input stream is unavailable") ->
                "선택한 파일의 접근이 불가능합니다. 다른 파일 또는 다시 선택해 주세요."
            source.contains("Timed out while reading file") ->
                "파일 읽기 시간이 초과되었습니다. 네트워크/용량이 큰 파일은 잠시 뒤 다시 시도해 주세요."
            source.contains("파일이 너무 큽니다") ->
                "텍스트 파일 용량이 커서 읽지 못했습니다. 용량이 작은 파일로 다시 시도해 주세요."
            source.contains("지원되지 않는 파일 형식") ->
                "텍스트 파일이 아니거나 지원되지 않는 형식입니다."
            source.contains("바이너리 데이터로 감지") ->
                "바이너리 파일로 판단되어 읽기에서 제외되었습니다."
            source.contains("Failed to open text file") ->
                "파일 열기에 실패했습니다."
            source.isBlank() ->
                "텍스트 파일을 여는 중 알 수 없는 오류가 발생했습니다."
            else -> source
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = historyRepository.getAllHistory()
            _state.update { it.copy(history = history) }
        }
    }

    private fun resumeLastSession() {
        viewModelScope.launch {
            val latest = historyRepository.getLatestHistory()
            if (latest == null) {
                _effect.emit(TextViewerEffect.ShowError("No reading history found"))
                return@launch
            }
            openFile(
                uri = Uri.parse(latest.fileUri),
                preferredPage = latest.currentPage,
                previousPageCount = latest.totalPages,
                previousPageSize = latest.pageSize,
                preferredOffset = latest.currentOffset.takeIf { it >= 0 },
                emitResumed = true,
            )
        }
    }

    private fun setPage(requestedPage: Int) {
        val current = _state.value
        val document = current.currentDocument ?: return
        val ranges = current.pageRanges
        if (ranges.isEmpty()) return

        val safePage = requestedPage.coerceIn(0, ranges.size - 1)
        if (safePage == current.currentPage) return

        val range = ranges[safePage]

        _state.update {
            it.copy(
                currentPage = safePage,
                currentOffset = range.startOffset,
                pageContent = document.content.substring(range.startOffset, range.endOffset),
            )
        }
        viewModelScope.launch {
            persistCurrentProgress(document, safePage, range.startOffset)
        }
    }

    private suspend fun persistCurrentProgress(document: TextDocument, page: Int, offset: Int) {
        val safeOffset = if (document.content.isEmpty()) 0 else offset.coerceIn(0, document.content.length - 1)
        historyRepository.saveHistory(
            ReadingHistory(
                fileUri = document.uri,
                fileName = document.fileName,
                currentPage = page,
                currentOffset = safeOffset,
                pageSize = pageCharLimit,
                totalPages = _state.value.totalPages,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        val history = historyRepository.getAllHistory()
        _state.update { it.copy(history = history) }
    }

    companion object {
        private const val DEFAULT_PAGE_CHAR_LIMIT = 1800
    }
}
