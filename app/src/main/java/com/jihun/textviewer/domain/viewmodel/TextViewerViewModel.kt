package com.jihun.textviewer.domain.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import com.jihun.textviewer.domain.repository.TextFileRepository
import com.jihun.textviewer.domain.util.PaginationUtil
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

    fun onAction(action: TextViewerAction) {
        when (action) {
            is TextViewerAction.OpenFile -> openFile(
                uri = action.uri,
                preferredPage = null,
                emitResumed = false,
            )
            is TextViewerAction.OpenHistoryEntry -> openFile(
                uri = Uri.parse(action.fileUri),
                preferredPage = action.page,
                previousPageCount = action.totalPages,
                previousPageSize = action.pageSize,
                emitResumed = true,
            )
            TextViewerAction.LoadHistory -> loadHistory()
            TextViewerAction.ResumeLastSession -> resumeLastSession()
            TextViewerAction.CloseDocument -> closeDocument()
            is TextViewerAction.GoToPage -> setPage(action.page)
            is TextViewerAction.SetVisualPageCount -> setVisualTotalPages(action.totalPages)
            TextViewerAction.NextPage -> setPage(_state.value.currentPage + 1)
            TextViewerAction.PreviousPage -> setPage(_state.value.currentPage - 1)
        }
    }

    private fun closeDocument() {
        _state.update {
            it.copy(
                isLoading = false,
                currentDocument = null,
                currentPage = 0,
                pageContent = "",
                totalPages = 0,
                pendingRestoreRatio = null,
                errorMessage = null,
            )
        }
    }

    private fun openFile(
        uri: Uri,
        preferredPage: Int?,
        previousPageCount: Int? = null,
        previousPageSize: Int? = null,
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
                                pageContent = "",
                                totalPages = 0,
                                pendingRestoreRatio = null,
                                errorMessage = message,
                            )
                        }
                        _effect.emit(TextViewerEffect.ShowError(message))
                        return@onSuccess
                    }

                    val restoreRatio = ResumeProgressCalculator.computeRestoreRatio(
                        preferredPage = preferredPage,
                        previousPageCount = previousPageCount,
                        previousPageSize = previousPageSize,
                    )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            currentDocument = document,
                            currentPage = 0,
                            pageContent = document.content,
                            totalPages = 1,
                            pendingRestoreRatio = restoreRatio,
                            errorMessage = null,
                        )
                    }

                    if (restoreRatio == null) {
                        persistCurrentProgress(document, 0)
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
                            pageContent = "",
                            totalPages = 0,
                            pendingRestoreRatio = null,
                            errorMessage = message,
                        )
                    }
                    _effect.emit(TextViewerEffect.ShowError(message))
                }
        }
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
                emitResumed = true,
            )
        }
    }

    private fun setPage(requestedPage: Int) {
        val current = _state.value
        val document = current.currentDocument ?: return
        val safePage = PaginationUtil.clampPage(requestedPage, current.totalPages)
        if (safePage == current.currentPage) return

        _state.update {
            it.copy(
                currentPage = safePage,
            )
        }
        viewModelScope.launch {
            persistCurrentProgress(document, safePage)
        }
    }

    private fun setVisualTotalPages(estimatedTotalPages: Int) {
        val current = _state.value
        val safeTotalPages = estimatedTotalPages.coerceAtLeast(1)
        val restoredPage = current.pendingRestoreRatio?.let { ratio ->
            ResumeProgressCalculator.resolvePageFromRatio(
                ratio = ratio,
                totalPages = safeTotalPages,
            )
        }
        val nextPage = PaginationUtil.clampPage(restoredPage ?: current.currentPage, safeTotalPages)
        val shouldClearPendingRatio = current.pendingRestoreRatio != null &&
            (safeTotalPages > 1 || restoredPage != 0)

        if (nextPage == current.currentPage && safeTotalPages == current.totalPages && current.pendingRestoreRatio == null) return

        _state.update {
            it.copy(
                totalPages = safeTotalPages,
                currentPage = nextPage,
                pendingRestoreRatio = if (shouldClearPendingRatio) null else it.pendingRestoreRatio,
            )
        }

        if (nextPage != current.currentPage) {
            val document = current.currentDocument ?: return
            viewModelScope.launch {
                persistCurrentProgress(document, nextPage)
            }
        }
    }

    private suspend fun persistCurrentProgress(document: TextDocument, page: Int) {
        historyRepository.saveHistory(
            ReadingHistory(
                fileUri = document.uri,
                fileName = document.fileName,
                currentPage = page,
                pageSize = pageCharLimit,
                totalPages = document.totalPages,
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
