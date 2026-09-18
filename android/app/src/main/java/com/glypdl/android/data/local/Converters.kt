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

import androidx.room.TypeConverter
import com.glypdl.android.data.model.DownloadStatus

/**
 * Room [TypeConverter] definitions for converting complex Kotlin domain types
 * into primitive representations supported by SQLite, and vice-versa.
 */
class Converters {

    /**
     * Converts a [DownloadStatus] enum into its [String] name for database persistence.
     *
     * @param status The status enum value to persist.
     * @return The string representation of the enum (e.g., "DOWNLOADING", "COMPLETED").
     */
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    /**
     * Converts a stored [String] back into its matching [DownloadStatus] enum.
     * Gracefully falls back to [DownloadStatus.QUEUED] if the stored value is unrecognized
     * or corrupted, preventing database read crashes during app upgrades.
     *
     * @param status The string stored in the SQLite database column.
     * @return The matching [DownloadStatus] enum, or [DownloadStatus.QUEUED] if invalid.
     */
    @TypeConverter
    fun toDownloadStatus(status: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            DownloadStatus.QUEUED
        }
    }
}
