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
package com.glypdl.android.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateUrlUseCaseTest {

    private lateinit var validateUrlUseCase: ValidateUrlUseCase

    @Before
    fun setup() {
        validateUrlUseCase = ValidateUrlUseCase()
    }

    @Test
    fun `valid https URL returns success with cleaned URL`() {
        val url = "  https://example.com/video  "
        val result = validateUrlUseCase(url)
        
        assertTrue(result.isSuccess)
        assertEquals("https://example.com/video", result.getOrNull())
    }

    @Test
    fun `valid http URL returns success`() {
        val url = "http://example.com/video"
        val result = validateUrlUseCase(url)
        
        assertTrue(result.isSuccess)
        assertEquals("http://example.com/video", result.getOrNull())
    }

    @Test
    fun `empty URL returns failure`() {
        val result = validateUrlUseCase("")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `no scheme returns failure`() {
        val result = validateUrlUseCase("example.com")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invalid URL returns failure`() {
        val result = validateUrlUseCase("ftp://example.com")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
