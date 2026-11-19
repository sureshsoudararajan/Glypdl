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

import android.app.PendingIntent
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
 * On Android 10+ clipboard access is restricted from background services,
 * so we pass the tile's own Context (which runs in the foreground UI context
 * during the tile click callback) to access the clipboard.
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

        // On Android 10+, background services/TileService cannot access clipboard data
        // while SystemUI has focus. Therefore, we do NOT abort here if url is null.
        // Instead, we launch MainActivity with EXTRA_AUTO_ANALYZE = true.
        // Once MainActivity is launched and gains window focus, it reads the clipboard
        // and navigates directly to the Analyze screen.
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!url.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, url)
            }
            putExtra(EXTRA_AUTO_ANALYZE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: startActivityAndCollapse(PendingIntent) is the only non-deprecated path.
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun readUrlFromClipboard(): String? {
        return try {
            // Use `this` (the TileService Context) — during the onClick() callback the
            // system grants foreground clipboard access on Android 10+.
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null
            if (!clipboard.hasPrimaryClip()) return null
            val clip = clipboard.primaryClip ?: return null
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
