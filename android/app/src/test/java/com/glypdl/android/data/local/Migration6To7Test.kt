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
package com.glypdl.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.glypdl.android.di.DatabaseModule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration6To7Test {

    @Test
    fun migration_6_7_executesRequiredSql() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        assertEquals(6, DatabaseModule.MIGRATION_6_7.startVersion)
        assertEquals(7, DatabaseModule.MIGRATION_6_7.endVersion)

        DatabaseModule.MIGRATION_6_7.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `bookmarks`") })
            db.execSQL(match { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url`") })
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `browser_history`") })
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_browser_history_url`") })
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_browser_history_visitedAt`") })
        }
    }
}
