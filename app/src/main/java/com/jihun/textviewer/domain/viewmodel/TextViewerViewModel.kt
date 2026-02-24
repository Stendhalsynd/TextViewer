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
            is TextViewerAction.OpenFile -> openFile(action.uri, preferredPage = null, emitResumed = false)
            is TextViewerAction.OpenHistoryEntry -> openFile(
                uri = Uri.parse(action.fileUri),
                preferredPage = action.page,
                emitResumed = true,
            )
            TextViewerAction.LoadHistory -> loadHistory()
            TextViewerAction.ResumeLastSession -> resumeLastSession()
            is TextViewerAction.GoToPage -> setPage(action.page)
            TextViewerAction.NextPage -> setPage(_state.value.currentPage + 1)
            TextViewerAction.PreviousPage -> setPage(_state.value.currentPage - 1)
        }
    }

    private fun openFile(uri: Uri, preferredPage: Int?, emitResumed: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            textFileRepository.loadTextFile(uri)
                .onSuccess { document ->
                    val safePage = PaginationUtil.clampPage(preferredPage ?: 0, document.totalPages)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            currentDocument = document,
                            currentPage = safePage,
                            pageContent = document.pages.getOrElse(safePage) { "" },
                            totalPages = document.totalPages,
                            errorMessage = null,
                        )
                    }
                    persistCurrentProgress(document, safePage)
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
                emitResumed = true,
            )
        }
    }

    private fun setPage(requestedPage: Int) {
        val current = _state.value
        val document = current.currentDocument ?: return
        val safePage = PaginationUtil.clampPage(requestedPage, document.totalPages)
        _state.update {
            it.copy(
                currentPage = safePage,
                pageContent = document.pages.getOrElse(safePage) { "" },
            )
        }
        viewModelScope.launch {
            persistCurrentProgress(document, safePage)
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
