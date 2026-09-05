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

package com.glypdl.android.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun UrlInputField(
    url: String,
    onUrlChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    isValid: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter URL") },
            singleLine = true,
            isError = url.isNotEmpty() && !isValid,
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { onUrlChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                    }
                } else {
                    IconButton(onClick = {
                        val clipData = clipboardManager.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val pasteData = clipData.getItemAt(0).text?.toString() ?: ""
                            onUrlChange(pasteData)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                    }
                }
            }
        )
        
        if (url.isNotEmpty() && !isValid) {
            Text(
                text = "Please enter a valid supported URL",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Button(
            onClick = onAnalyzeClick,
            enabled = isValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            }
            Text("Analyze URL")
        }
    }
}
