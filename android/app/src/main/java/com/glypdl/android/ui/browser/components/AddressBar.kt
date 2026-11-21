/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
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

package com.glypdl.android.ui.browser.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddressBar(
    url: String,
    isLoading: Boolean,
    isSecure: Boolean,
    isDesktopMode: Boolean,
    onUrlChange: (String) -> Unit,
    onSubmitUrl: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToggleDesktop: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onCopyUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                placeholder = {
                    Text(
                        "Search or type URL",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    if (isSecure) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure Connection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Insecure Connection",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFocused && url.isNotEmpty()) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear text",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (isLoading) {
                            IconButton(onClick = onStop) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Stop loading",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(onClick = onReload) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        onSubmitUrl(url)
                    }
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Browser options")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Desktop site")
                                Switch(
                                    checked = isDesktopMode,
                                    onCheckedChange = {
                                        onToggleDesktop()
                                        menuExpanded = false
                                    }
                                )
                            }
                        },
                        onClick = {
                            onToggleDesktop()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DesktopWindows, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        onClick = {
                            onCopyUrl()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share link") },
                        onClick = {
                            onShare()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in external browser") },
                        onClick = {
                            onOpenExternal()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}
