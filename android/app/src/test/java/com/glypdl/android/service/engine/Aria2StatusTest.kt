/*
 * Copyright (C) 2026 The Glypdl Authors
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.service.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Aria2StatusTest {

    @Test
    fun computesProgressPercentageCorrectly() {
        val status = Aria2Status(
            gid = "test-gid-1",
            status = "active",
            totalLength = 1000L,
            completedLength = 500L,
            downloadSpeed = 100L,
            uploadSpeed = 0L,
            connections = 4,
            numSeeders = 0,
            numPieces = 10,
            pieceLength = 100L,
            bitfield = null,
            files = emptyList(),
            errorCode = null,
            errorMessage = null,
            dir = "/tmp",
            followedBy = null,
            infoHash = null,
            bittorrent = null
        )

        assertEquals(50f, status.progressPercent, 0.01f)
        assertTrue(status.isActive)
        assertFalse(status.isComplete)
        assertFalse(status.isPaused)
        assertFalse(status.isError)
    }

    @Test
    fun handlesZeroTotalLengthGracefully() {
        val status = Aria2Status(
            gid = "test-gid-2",
            status = "waiting",
            totalLength = 0L,
            completedLength = 0L,
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            connections = 0,
            numSeeders = 0,
            numPieces = 0,
            pieceLength = 0L,
            bitfield = null,
            files = emptyList(),
            errorCode = null,
            errorMessage = null,
            dir = "/tmp",
            followedBy = null,
            infoHash = null,
            bittorrent = null
        )

        assertEquals(0f, status.progressPercent, 0.01f)
        assertTrue(status.isWaiting)
    }

    @Test
    fun identifiesCompletedAndErrorStates() {
        val completed = Aria2Status(
            gid = "test-gid-3",
            status = "complete",
            totalLength = 2000L,
            completedLength = 2000L,
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            connections = 0,
            numSeeders = 0,
            numPieces = 20,
            pieceLength = 100L,
            bitfield = null,
            files = emptyList(),
            errorCode = null,
            errorMessage = null,
            dir = "/tmp",
            followedBy = null,
            infoHash = null,
            bittorrent = null
        )
        assertTrue(completed.isComplete)
        assertEquals(100f, completed.progressPercent, 0.01f)

        val error = completed.copy(status = "error", errorCode = "1", errorMessage = "Network timeout")
        assertTrue(error.isError)

        val activeSeeding = completed.copy(status = "active", downloadSpeed = 0L, uploadSpeed = 500L)
        assertTrue(activeSeeding.isComplete)
        assertEquals(100f, activeSeeding.progressPercent, 0.01f)
    }
}
