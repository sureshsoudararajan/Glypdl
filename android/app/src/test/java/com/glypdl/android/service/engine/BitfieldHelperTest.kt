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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BitfieldHelperTest {

    @Test
    fun decodeHex_validHex_decodesCorrectly() {
        val result = BitfieldHelper.decodeHex("f0")
        assertArrayEquals(
            booleanArrayOf(true, true, true, true, false, false, false, false),
            result
        )
    }

    @Test
    fun decodeHex_totalPiecesClamp_clampsCorrectly() {
        val result = BitfieldHelper.decodeHex("f0", 5)
        assertArrayEquals(
            booleanArrayOf(true, true, true, true, false),
            result
        )
    }

    @Test
    fun decodeHex_nullOrBlank_returnsEmptyArray() {
        assertArrayEquals(BooleanArray(0), BitfieldHelper.decodeHex(null))
        assertArrayEquals(BooleanArray(0), BitfieldHelper.decodeHex(""))
        assertArrayEquals(BooleanArray(0), BitfieldHelper.decodeHex("   "))
    }

    @Test
    fun calculateCompletionPercent_calculatesCorrectly() {
        val bitfield = booleanArrayOf(true, true, false, false)
        assertEquals(50f, BitfieldHelper.calculateCompletionPercent(bitfield), 0.01f)
        
        val emptyBitfield = BooleanArray(0)
        assertEquals(0f, BitfieldHelper.calculateCompletionPercent(emptyBitfield), 0.01f)
    }
}
