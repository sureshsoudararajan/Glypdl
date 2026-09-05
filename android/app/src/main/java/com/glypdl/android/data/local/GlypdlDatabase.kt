/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.glypdl.android.data.local.dao.DownloadDao
import com.glypdl.android.data.local.dao.HistoryDao
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.local.entity.HistoryEntity

@Database(entities = [DownloadEntity::class, HistoryEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GlypdlDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
}
