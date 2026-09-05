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
import com.glypdl.android.data.local.GlypdlDatabase
import com.glypdl.android.data.local.dao.DownloadDao
import com.glypdl.android.data.local.dao.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlypdlDatabase {
        return Room.databaseBuilder(
            context,
            GlypdlDatabase::class.java,
            "glypdl_database"
        ).fallbackToDestructiveMigration().build()
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
}
