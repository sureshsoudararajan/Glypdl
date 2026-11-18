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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.glypdl.android.MainActivity

/**
 * Quick Settings tile that reads the latest URL from the clipboard and
 * immediately sends it to Glypdl's Analyze screen.
 *
 * If the clipboard contains no recognisable URL the user is shown a brief
 * Toast so they know to copy a link first.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ClipboardAnalyzeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val url = readUrlFromClipboard()
        if (url.isNullOrBlank()) {
            Toast.makeText(applicationContext, "No link found in clipboard. Copy a URL first.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(EXTRA_AUTO_ANALYZE, true)
        }

        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    private fun readUrlFromClipboard(): String? {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return null
            // Minimal URL validation — same heuristic used by UrlValidator
            if (text.startsWith("http://") || text.startsWith("https://")) text else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_AUTO_ANALYZE = "com.glypdl.android.extra.AUTO_ANALYZE"
    }
}
