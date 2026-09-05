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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.ui.analyze.AnalyzeScreen
import com.glypdl.android.ui.downloads.DownloadDetailScreen
import com.glypdl.android.ui.downloads.DownloadsScreen
import com.glypdl.android.ui.history.HistoryScreen
import com.glypdl.android.ui.home.HomeScreen
import com.glypdl.android.ui.settings.SettingsScreen
import com.glypdl.android.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun GlypdlRootApp(
    navController: NavHostController,
    startDestination: String,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Welcome.route

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                fun navigateTab(route: String) {
                    if (currentRoute == route) return
                    val popped = navController.popBackStack(route, inclusive = false)
                    if (!popped) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                val isHome = currentRoute == Screen.Home.route || currentRoute?.startsWith("analyze") == true
                val isDownloads = currentRoute == Screen.Downloads.route || currentRoute?.startsWith("download_detail") == true
                val isHistory = currentRoute == Screen.History.route
                val isSettings = currentRoute == Screen.Settings.route

                NavigationBar {
                    NavigationBarItem(
                        selected = isHome,
                        onClick = { navigateTab(Screen.Home.route) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = isDownloads,
                        onClick = { navigateTab(Screen.Downloads.route) },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = isHistory,
                        onClick = { navigateTab(Screen.History.route) },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = isSettings,
                        onClick = { navigateTab(Screen.Settings.route) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        GlypdlNavHost(
            navController = navController,
            startDestination = startDestination,
            settingsRepository = settingsRepository,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun GlypdlNavHost(
    navController: NavHostController,
    startDestination: String,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Welcome.route) {
            val scope = rememberCoroutineScope()
            WelcomeScreen(
                onComplete = {
                    scope.launch {
                        settingsRepository.setFirstRunComplete(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAnalyze = { url ->
                    navController.navigate(Screen.Analyze.createRoute(url))
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToDownloadDetail = { downloadId ->
                    navController.navigate(Screen.DownloadDetail.createRoute(downloadId))
                }
            )
        }

        composable(
            route = Screen.Analyze.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) {
            AnalyzeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onNavigateToDownloadDetail = { downloadId ->
                    navController.navigate(Screen.DownloadDetail.createRoute(downloadId))
                }
            )
        }

        composable(
            route = Screen.DownloadDetail.route,
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType })
        ) {
            DownloadDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateToAnalyze = { url ->
                    navController.navigate(Screen.Analyze.createRoute(url))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
