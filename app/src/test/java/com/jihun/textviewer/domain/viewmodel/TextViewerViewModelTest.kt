package com.jihun.textviewer.domain.viewmodel

import android.net.Uri
import com.jihun.textviewer.domain.model.ReadingHistory
import com.jihun.textviewer.domain.model.TextDocument
import com.jihun.textviewer.domain.model.TextPageRange
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import com.jihun.textviewer.domain.repository.TextFileRepository
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
    fun openFile_persistsInitialProgressAfterLayout() = runTest {
        val content = "Large file sample for opening test ".repeat(45_000)
        val document = createDocument(content = content, uri = "content://doc/large.txt")
        val textFileRepository = FakeTextFileRepository { Result.success(document) }
        val historyRepository = FakeReadingHistoryRepository()
        val viewModel = TextViewerViewModel(textFileRepository, historyRepository)
        val uri = Uri.Builder()
            .scheme("content")
            .authority("doc")
            .appendPath("large.txt")
            .build()

        val expectedRanges = calculateRanges(document.content, 1_200)
        val effect = async {
            viewModel.effect.first { it is TextViewerEffect.FileOpened }
        }

        viewModel.onAction(TextViewerAction.OpenFile(uri))
        advanceUntilIdle()
        viewModel.onAction(
            TextViewerAction.SetPageRanges(
                documentUri = document.uri,
                ranges = expectedRanges,
            ),
        )
        advanceUntilIdle()

        assertEquals(document, viewModel.state.value.currentDocument)
        assertEquals(expectedRanges.size, viewModel.state.value.totalPages)
        assertEquals(1, historyRepository.saved.size)
        assertEquals(0, historyRepository.saved.last().currentPage)
        assertEquals(0, historyRepository.saved.last().currentOffset)
        assertEquals(document.uri, viewModel.state.value.currentDocument?.uri)
        assertEquals(document.fileName, viewModel.state.value.currentDocument?.fileName)

        val emittedEffect = effect.await()
        assertTrue(emittedEffect is TextViewerEffect.FileOpened)
    }

    @Test
    fun openHistoryEntry_restoresProgressWithOffsetMapping_whenLayoutReady() = runTest {
        val content = "Resume check line ".repeat(45_000)
        val document = createDocument(content = content, uri = "content://doc/large.txt")
        val textFileRepository = FakeTextFileRepository { Result.success(document) }
        val historyRepository = FakeReadingHistoryRepository()
        val viewModel = TextViewerViewModel(textFileRepository, historyRepository)
        val uriString = document.uri
        val ranges = calculateRanges(document.content, 1_200)
        val restoreFromPage = 4
        val expectedRatio = (restoreFromPage + 0.5f) / 10f
        val expectedOffset = (document.content.length * expectedRatio).toInt()
        val expectedPage = ranges.indexOfFirst { expectedOffset >= it.startOffset && expectedOffset < it.endOffset }
            .takeIf { it >= 0 } ?: 0
        val expectedOffsetToStore = ranges[expectedPage].startOffset

        val effect = async {
            viewModel.effect.first { it is TextViewerEffect.Resumed }
        }

        viewModel.onAction(
            TextViewerAction.OpenHistoryEntry(
                fileUri = uriString,
                page = restoreFromPage,
                totalPages = 10,
                pageSize = 1_800,
            ),
        )
        advanceUntilIdle()
        viewModel.onAction(TextViewerAction.SetPageRanges(documentUri = uriString, ranges = ranges))
        advanceUntilIdle()

        assertEquals(expectedPage, viewModel.state.value.currentPage)
        assertEquals(document.uri, viewModel.state.value.currentDocument?.uri)
        assertEquals(expectedOffsetToStore, viewModel.state.value.currentOffset)
        assertEquals(expectedPage, historyRepository.saved.last().currentPage)
        assertEquals(viewModel.state.value.currentOffset, historyRepository.saved.last().currentOffset)
        assertTrue(effect.await() is TextViewerEffect.Resumed)
    }

    private fun calculateRanges(content: String, chunkSize: Int): List<TextPageRange> {
        if (content.isEmpty()) return listOf(TextPageRange(0, 0))

        val ranges = mutableListOf<TextPageRange>()
        var start = 0
        while (start < content.length) {
            val end = (start + chunkSize).coerceAtMost(content.length)
            ranges += TextPageRange(startOffset = start, endOffset = end)
            start = end
        }
        return ranges
    }

    @Test
    fun nextPage_doesNotSkipText_whenRangesContainGapsOrUnorderedOffsets() = runTest {
        val content = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(20)
        val document = createDocument(content = content, uri = "content://doc/misaligned.txt")
        val textFileRepository = FakeTextFileRepository { Result.success(document) }
        val historyRepository = FakeReadingHistoryRepository()
        val viewModel = TextViewerViewModel(textFileRepository, historyRepository)
        val ranges = listOf(
            TextPageRange(startOffset = 0, endOffset = 17),
            TextPageRange(startOffset = 26, endOffset = 30),
            TextPageRange(startOffset = 17, endOffset = 26),
            TextPageRange(startOffset = 34, endOffset = content.length),
            TextPageRange(startOffset = 30, endOffset = 34),
        )

        viewModel.onAction(TextViewerAction.OpenFile(Uri.Builder().scheme("content").authority("doc").appendPath("misaligned.txt").build()))
        advanceUntilIdle()
        viewModel.onAction(TextViewerAction.SetPageRanges(documentUri = document.uri, ranges = ranges))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.totalPages > 1)

        val pageCount = state.totalPages
        val forward = StringBuilder()
        repeat(pageCount) { index ->
            forward.append(viewModel.state.value.pageContent)
            if (index < pageCount - 1) {
                viewModel.onAction(TextViewerAction.NextPage)
                advanceUntilIdle()
            }
        }
        assertEquals(content, forward.toString())

        val backward = StringBuilder()
        viewModel.onAction(TextViewerAction.GoToPage(pageCount - 1))
        advanceUntilIdle()
        repeat(pageCount) { index ->
            backward.insert(0, viewModel.state.value.pageContent)
            if (index < pageCount - 1) {
                viewModel.onAction(TextViewerAction.PreviousPage)
                advanceUntilIdle()
            }
        }
        assertEquals(content, backward.toString())
    }

    private fun createDocument(content: String, uri: String): TextDocument {
        return TextDocument(
            uri = uri,
            fileName = "large.txt",
            content = content,
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
