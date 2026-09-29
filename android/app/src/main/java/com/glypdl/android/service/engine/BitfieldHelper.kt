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

object BitfieldHelper {
    /**
     * Decodes an aria2c hex bitfield string into a boolean array of piece availability.
     * @param hexBitfield Hexadecimal string representing bits (e.g. "f0", "ff00").
     * @param totalPieces Optional total number of pieces to clamp the returned array to.
     */
    fun decodeHex(hexBitfield: String?, totalPieces: Int = 0): BooleanArray {
        if (hexBitfield.isNullOrBlank()) {
            return BooleanArray(0)
        }
        
        val bitsList = mutableListOf<Boolean>()
        for (char in hexBitfield) {
            val num = char.digitToIntOrNull(16) ?: continue
            bitsList.add((num and 8) != 0)
            bitsList.add((num and 4) != 0)
            bitsList.add((num and 2) != 0)
            bitsList.add((num and 1) != 0)
        }
        
        return if (totalPieces > 0 && totalPieces < bitsList.size) {
            bitsList.take(totalPieces).toBooleanArray()
        } else {
            bitsList.toBooleanArray()
        }
    }
    
    /**
     * Returns percentage of completed pieces (0f..100f).
     */
    fun calculateCompletionPercent(bitfield: BooleanArray): Float {
        if (bitfield.isEmpty()) return 0f
        val completed = bitfield.count { it }
        return (completed.toFloat() / bitfield.size) * 100f
    }
}
