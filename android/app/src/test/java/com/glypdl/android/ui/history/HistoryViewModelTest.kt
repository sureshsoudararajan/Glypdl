/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.ui.history

import com.glypdl.android.data.local.entity.HistoryEntity
import com.glypdl.android.data.repository.HistoryRepository
import com.glypdl.android.domain.usecase.GetDownloadHistoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var getHistoryUseCase: GetDownloadHistoryUseCase
    private lateinit var historyRepository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

    private val sampleHistory = listOf(
        HistoryEntity(
            id = 1L,
            downloadId = "dl-1",
            url = "https://example.com/video1",
            title = "Suresh Tutorial Video",
            thumbnailUrl = null,
            format = "1080p MP4",
            filePath = "/path/video1.mp4",
            fileUri = null,
            fileSize = 1024L,
            duration = 120L,
            completedAt = System.currentTimeMillis()
        ),
        HistoryEntity(
            id = 2L,
            downloadId = "dl-2",
            url = "https://example.com/video2",
            title = "Another Clip",
            thumbnailUrl = null,
            format = "720p MP4",
            filePath = "/path/video2.mp4",
            fileUri = null,
            fileSize = 2048L,
            duration = 60L,
            completedAt = System.currentTimeMillis()
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getHistoryUseCase = mockk()
        historyRepository = mockk(relaxed = true)

        every { getHistoryUseCase(null) } returns flowOf(sampleHistory)
        every { getHistoryUseCase("Suresh") } returns flowOf(listOf(sampleHistory[0]))
        every { getHistoryUseCase("") } returns flowOf(sampleHistory)

        viewModel = HistoryViewModel(
            getHistoryUseCase = getHistoryUseCase,
            historyRepository = historyRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty search query`() {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `typing Suresh character by character updates searchQuery faithfully without corruption`() {
        val target = "Suresh"
        val typedSteps = mutableListOf<String>()

        var current = ""
        for (char in target) {
            current += char
            viewModel.onSearchQueryChange(current)
            typedSteps.add(viewModel.searchQuery.value)
        }

        assertEquals(
            listOf("S", "Su", "Sur", "Sure", "Sures", "Suresh"),
            typedSteps
        )
        assertEquals("Suresh", viewModel.searchQuery.value)
    }

    @Test
    fun `rapid typing Suresh does not lose characters or reorder keystrokes`() = runTest(testDispatcher) {
        // Simulates rapid user typing where multiple keystrokes occur before coroutine switches
        val keystrokes = listOf("S", "Su", "Sur", "Sure", "Sures", "Suresh")

        keystrokes.forEach { stroke ->
            viewModel.onSearchQueryChange(stroke)
            assertEquals(stroke, viewModel.searchQuery.value)
        }

        assertEquals("Suresh", viewModel.searchQuery.value)
    }

    @Test
    fun `search query triggers debounced history query`() = runTest(testDispatcher) {
        val collectedItems = mutableListOf<List<HistoryEntity>>()
        val collectJob = launch(testDispatcher) {
            viewModel.historyItems.collect { items ->
                collectedItems.add(items)
            }
        }

        advanceUntilIdle()

        viewModel.onSearchQueryChange("Suresh")
        // Before debounce timeout (150ms), getHistoryUseCase("Suresh") should not be called yet
        advanceTimeBy(100L)
        coVerify(exactly = 0) { getHistoryUseCase("Suresh") }

        // After debounce timeout, getHistoryUseCase should be invoked with trimmed query
        advanceTimeBy(100L)
        advanceUntilIdle()

        coVerify(atLeast = 1) { getHistoryUseCase("Suresh") }

        collectJob.cancel()
    }

    @Test
    fun `clearing search query resets searchQuery to empty`() {
        viewModel.onSearchQueryChange("Suresh")
        assertEquals("Suresh", viewModel.searchQuery.value)

        viewModel.onSearchQueryChange("")
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `deleteHistoryItem delegates to historyRepository`() = runTest(testDispatcher) {
        val itemToDelete = sampleHistory[0]
        viewModel.deleteHistoryItem(itemToDelete)
        advanceUntilIdle()

        coVerify(exactly = 1) { historyRepository.deleteHistory(itemToDelete) }
    }

    @Test
    fun `clearHistory delegates to historyRepository`() = runTest(testDispatcher) {
        viewModel.clearHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { historyRepository.clearAll() }
    }
}
