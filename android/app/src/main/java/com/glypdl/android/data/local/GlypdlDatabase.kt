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

/**
 * The primary Room SQLite database for Glypdl Android.
 *
 * Tables:
 * - [DownloadEntity] (`downloads`): Tracks active, queued, paused, failed, and completed download tasks.
 * - [HistoryEntity] (`history`): Stores permanent records of successfully completed downloads for quick access/replay.
 *
 * Schema versioning:
 * - Version 2 introduces support for enhanced status states (e.g. PROCESSING) and technical diagnostics logging.
 * - [TypeConverters] registered for serializing enum types such as [DownloadStatus].
 */
@Database(
    entities = [DownloadEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GlypdlDatabase : RoomDatabase() {

    /**
     * Provides access to database operations on the `downloads` table.
     */
    abstract fun downloadDao(): DownloadDao

    /**
     * Provides access to database operations on the `history` table.
     */
    abstract fun historyDao(): HistoryDao
}
