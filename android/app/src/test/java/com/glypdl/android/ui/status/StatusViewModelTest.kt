/*
 * Copyright (c) 2026. Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.glypdl.android.ui.status

import android.content.Context
import android.net.Uri
import com.glypdl.android.data.repository.StatusRepository
import com.glypdl.android.data.status.StatusScanResult
import com.glypdl.android.data.status.model.StatusFilter
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.data.status.model.StatusTab
import com.glypdl.android.ui.status.components.StatusEmptyStateType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var statusRepository: StatusRepository

    private val sampleItems = listOf(
        StatusMedia(
            id = "content://statuses/1.jpg",
            uri = mockk(),
            displayName = "1.jpg",
            mimeType = "image/jpeg",
            mediaType = StatusMediaType.IMAGE,
            sizeBytes = 1024L,
            modifiedAt = 1000L
        ),
        StatusMedia(
            id = "content://statuses/2.mp4",
            uri = mockk(),
            displayName = "2.mp4",
            mimeType = "video/mp4",
            mediaType = StatusMediaType.VIDEO,
            sizeBytes = 2048L,
            modifiedAt = 2000L
        )
    )

    private lateinit var mockUri: Uri

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockUri = mockk(relaxed = true)
        every { mockUri.toString() } returns "content://tree/whatsapp"

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockUri

        context = mockk(relaxed = true)
        statusRepository = mockk(relaxed = true)

        every { statusRepository.getSavedStatuses() } returns flowOf(emptyList())
        every { statusRepository.configuredFolderUri } returns flowOf(null)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows FolderNotConfigured when no folder uri is saved`() = runTest(testDispatcher) {
        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StatusEmptyStateType.FolderNotConfigured, state.emptyStateType)
        assertFalse(state.isScanning)
    }

    @Test
    fun `initial state scans folder when folder uri is saved`() = runTest(testDispatcher) {
        val folderUri = "content://tree/whatsapp"
        every { statusRepository.configuredFolderUri } returns flowOf(folderUri)
        coEvery { statusRepository.scanStatuses(any()) } returns StatusScanResult.Success(sampleItems)

        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.availableStatuses.size)
        assertFalse(state.isScanning)
    }

    @Test
    fun `setTab updates currentTab and clears selection`() = runTest(testDispatcher) {
        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        viewModel.setTab(StatusTab.SAVED)
        assertEquals(StatusTab.SAVED, viewModel.uiState.value.currentTab)
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `setFilter filters items by media type`() = runTest(testDispatcher) {
        val folderUri = "content://tree/whatsapp"
        every { statusRepository.configuredFolderUri } returns flowOf(folderUri)
        coEvery { statusRepository.scanStatuses(any()) } returns StatusScanResult.Success(sampleItems)

        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        // Default ALL
        assertEquals(2, viewModel.uiState.value.filteredAvailable.size)

        // Filter IMAGES
        viewModel.setFilter(StatusFilter.IMAGES)
        assertEquals(1, viewModel.uiState.value.filteredAvailable.size)
        assertEquals(StatusMediaType.IMAGE, viewModel.uiState.value.filteredAvailable.first().mediaType)

        // Filter VIDEOS
        viewModel.setFilter(StatusFilter.VIDEOS)
        assertEquals(1, viewModel.uiState.value.filteredAvailable.size)
        assertEquals(StatusMediaType.VIDEO, viewModel.uiState.value.filteredAvailable.first().mediaType)
    }

    @Test
    fun `toggleSelection updates selected items and toggles selection mode`() = runTest(testDispatcher) {
        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        viewModel.toggleSelection("item_1")
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedIds.contains("item_1"))

        viewModel.toggleSelection("item_1")
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertFalse(viewModel.uiState.value.selectedIds.contains("item_1"))
    }

    @Test
    fun `setPreviewItem sets activePreviewMedia on statusRepository`() = runTest(testDispatcher) {
        val viewModel = StatusViewModel(context, statusRepository)
        advanceUntilIdle()

        val item = sampleItems.first()
        viewModel.setPreviewItem(item)
        io.mockk.verify { statusRepository.activePreviewMedia = item }
    }
}
