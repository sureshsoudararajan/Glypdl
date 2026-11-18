/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.glypdl.android.MainActivity

/**
 * Quick Settings tile that opens the WhatsApp Status Saver screen directly.
 *
 * Requires Android 7.0 (API 24) or higher, enforced via the manifest
 * <uses-sdk> attribute and the tile registration.
 */
@RequiresApi(Build.VERSION_CODES.N)
class WhatsAppSaverTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_NAVIGATE_TO_ROUTE, ROUTE_STATUS_SAVER)
        }

        // On API 34+ startActivityAndCollapse(PendingIntent) is preferred, but for broad
        // compatibility we use the deprecated single-argument overload with an Intent.
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    companion object {
        const val EXTRA_NAVIGATE_TO_ROUTE = "com.glypdl.android.extra.NAVIGATE_TO_ROUTE"
        const val ROUTE_STATUS_SAVER = "status_saver"
    }
}
