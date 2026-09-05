/*
 * Copyright (c) 2026. Glypdl
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

package com.glypdl.android.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Home : Screen("home")
    object Downloads : Screen("downloads")
    object History : Screen("history")
    object Settings : Screen("settings")
    
    object Analyze : Screen("analyze/{url}") {
        fun createRoute(url: String): String {
            val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
            return "analyze/$encodedUrl"
        }
    }
    
    object DownloadDetail : Screen("download_detail/{downloadId}") {
        fun createRoute(downloadId: String): String {
            return "download_detail/$downloadId"
        }
    }
}
