package com.jihun.textviewer.domain.viewmodel

import android.net.Uri
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import com.jihun.textviewer.domain.repository.TextFileRepository
import com.jihun.textviewer.domain.util.PaginationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TextViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openFile_persistsInitialProgressForNewFile() = runTest {
        val content = "Large file sample for opening test ".repeat(45_000) // 1,125,000자
        val document = createDocument(content = content, uri = "content://doc/large.txt")
        val textFileRepository = FakeTextFileRepository { Result.success(document) }
        val historyRepository = FakeReadingHistoryRepository()
        val viewModel = TextViewerViewModel(textFileRepository, historyRepository)
        val uri = Uri.Builder()
            .scheme("content")
            .authority("doc")
            .appendPath("large.txt")
            .build()

        val effect = async {
            viewModel.effect.first { it is TextViewerEffect.FileOpened }
        }

        viewModel.onAction(TextViewerAction.OpenFile(uri))

        advanceUntilIdle()

        assertEquals(document, viewModel.state.value.currentDocument)
        assertEquals(document.totalPages, viewModel.state.value.totalPages)
        assertNull(viewModel.state.value.pendingRestoreRatio)
        assertEquals("첫 페이지 저장 이력이 기록되어야 합니다", 1, historyRepository.saved.size)
        assertEquals(0, historyRepository.saved.last().currentPage)
        assertEquals(document.totalPages, historyRepository.saved.last().totalPages)
        assertEquals(document.uri, viewModel.state.value.currentDocument?.uri)
        assertEquals(document.fileName, viewModel.state.value.currentDocument?.fileName)

        val emittedEffect = effect.await()
        assertTrue(emittedEffect is TextViewerEffect.FileOpened)
    }

    @Test
    fun openHistoryEntry_restoresProgressAndReplacesWithVisualPageCount() = runTest {
        val content = "Resume check line ".repeat(45_000)
        val document = createDocument(content = content, uri = "content://doc/large.txt")
        val textFileRepository = FakeTextFileRepository { Result.success(document) }
        val historyRepository = FakeReadingHistoryRepository()
        val viewModel = TextViewerViewModel(textFileRepository, historyRepository)
        val uriString = document.uri

        val effect = async {
            viewModel.effect.first { it is TextViewerEffect.Resumed }
        }

        viewModel.onAction(
            TextViewerAction.OpenHistoryEntry(
                fileUri = uriString,
                page = 4,
                totalPages = 10,
                pageSize = 1800,
            ),
        )

        advanceUntilIdle()

        val expectedRatio = (4 + 0.5f) / 10f
        val expectedPage = ResumeProgressCalculator.resolvePageFromRatio(
            ratio = expectedRatio,
            totalPages = document.totalPages,
        )
        assertNull(viewModel.state.value.pendingRestoreRatio)
        assertEquals(expectedPage, viewModel.state.value.currentPage)
        assertEquals(document.uri, viewModel.state.value.currentDocument?.uri)
        assertEquals(expectedPage, historyRepository.saved.last().currentPage)
        viewModel.onAction(TextViewerAction.SetVisualPageCount(21))
        advanceUntilIdle()
        assertEquals(expectedPage, viewModel.state.value.currentPage)
        assertTrue(effect.await() is TextViewerEffect.Resumed)
    }

    private fun createDocument(content: String, uri: String): TextDocument {
        return TextDocument(
            uri = uri,
            fileName = "large.txt",
            content = content,
            pages = PaginationUtil.paginate(content = content, pageCharLimit = 1_800),
        )
    }

    private class FakeTextFileRepository(
        private val onLoad: suspend (Uri) -> Result<TextDocument>,
    ) : TextFileRepository {
        override suspend fun loadTextFile(uri: Uri): Result<TextDocument> = onLoad(uri)
        override fun isTxtFile(uri: Uri): Boolean = true
    }

    private class FakeReadingHistoryRepository : ReadingHistoryRepository {
        private val store = mutableMapOf<String, ReadingHistory>()
        val saved = mutableListOf<ReadingHistory>()

        override suspend fun saveHistory(history: ReadingHistory) {
            store[history.fileUri] = history
            saved.add(history)
        }

        override suspend fun getHistory(fileUri: String): ReadingHistory? = store[fileUri]

        override suspend fun getLatestHistory(): ReadingHistory? = store.values.lastOrNull()

        override suspend fun getAllHistory(): List<ReadingHistory> = store.values.toList()

        override suspend fun clearHistory(fileUri: String) {
            store.remove(fileUri)
        }
    }
}
