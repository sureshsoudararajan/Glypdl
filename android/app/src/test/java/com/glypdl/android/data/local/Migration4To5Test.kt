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

class Migration4To5Test {

    @Test
    fun migration_4_5_executesRequiredSql() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        assertEquals(4, DatabaseModule.MIGRATION_4_5.startVersion)
        assertEquals(5, DatabaseModule.MIGRATION_4_5.endVersion)

        DatabaseModule.MIGRATION_4_5.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `subtitle_files`") })
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_subtitle_files_downloadId`") })
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_subtitle_files_languageCode`") })
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_subtitle_files_createdAt`") })
            db.execSQL(match { it.contains("ALTER TABLE `downloads` ADD COLUMN `subtitleOptionsJson`") })
        }
    }
}
