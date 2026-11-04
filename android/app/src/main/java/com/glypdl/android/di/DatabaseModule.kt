/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glypdl.android.data.local.GlypdlDatabase
import com.glypdl.android.data.local.dao.DownloadDao
import com.glypdl.android.data.local.dao.HistoryDao
import com.glypdl.android.data.local.dao.SavedStatusDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_completedAt` ON `history` (`completedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_downloadId` ON `history` (`downloadId`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `saved_statuses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `originalUri` TEXT NOT NULL,
                    `savedUri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `savedAt` INTEGER NOT NULL,
                    `contentHash` TEXT,
                    `sourcePackage` TEXT NOT NULL DEFAULT 'com.whatsapp'
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_statuses_originalUri` ON `saved_statuses` (`originalUri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_statuses_savedAt` ON `saved_statuses` (`savedAt`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlypdlDatabase {
        return Room.databaseBuilder(
            context,
            GlypdlDatabase::class.java,
            "glypdl_database"
        )
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: GlypdlDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: GlypdlDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideSavedStatusDao(database: GlypdlDatabase): SavedStatusDao {
        return database.savedStatusDao()
    }
}
