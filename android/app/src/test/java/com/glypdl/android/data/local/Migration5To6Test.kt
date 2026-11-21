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

class Migration5To6Test {

    @Test
    fun migration_5_6_executesRequiredSql() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        assertEquals(5, DatabaseModule.MIGRATION_5_6.startVersion)
        assertEquals(6, DatabaseModule.MIGRATION_5_6.endVersion)

        DatabaseModule.MIGRATION_5_6.migrate(db)

        verify {
            db.execSQL(match { it.contains("ALTER TABLE `downloads` ADD COLUMN `headersJson`") })
            db.execSQL(match { it.contains("ALTER TABLE `downloads` ADD COLUMN `sourceKind`") })
            db.execSQL(match { it.contains("ALTER TABLE `downloads` ADD COLUMN `pageUrl`") })
        }
    }
}
