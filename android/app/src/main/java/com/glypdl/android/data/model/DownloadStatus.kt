/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

/**
 * State machine representing all possible lifecycle phases of a download task.
 *
 * State flow transitions:
 * `QUEUED` -> `PREPARING` -> `DOWNLOADING` -> `PROCESSING` -> `COMPLETED`
 * At any active stage, failure transitions to `FAILED`, cancellation to `CANCELLED`,
 * and pause to `PAUSED`.
 */
enum class DownloadStatus {
    /** Task is waiting in the FIFO queue for a free concurrent download slot. */
    QUEUED,

    /** Engine is initializing, fetching stream manifests, or preparing storage files. */
    PREPARING,

    /** Actively receiving media stream chunks over the network. */
    DOWNLOADING,

    /** Download execution has been temporarily suspended by user or Wi-Fi constraint. */
    PAUSED,

    /** Download finished; FFmpeg is actively demuxing, merging, or transcoding streams. */
    PROCESSING,

    /** Media streams have been successfully merged, exported to permanent storage, and logged to history. */
    COMPLETED,

    /** The download encountered a fatal unrecoverable or retryable error. */
    FAILED,

    /** Download was aborted by the user before completion. */
    CANCELLED
}
