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
                    val restoreRatio = preferredPage?.let { preferred ->
                        if (
                            preferred >= 0 &&
                            previousPageCount != null &&
                            previousPageCount > 0 &&
                            (previousPageSize == null || previousPageSize > 0)
                        ) {
                            (preferred + 0.5f) / previousPageCount.toFloat()
                        } else {
                            null
                        }
                    }

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
                    val message = throwable.message ?: "Failed to open file"
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }
                    _effect.emit(TextViewerEffect.ShowError(message))
                }
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
            ((safeTotalPages - 1) * ratio).toInt().coerceIn(0, safeTotalPages - 1)
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
