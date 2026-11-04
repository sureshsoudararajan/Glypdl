/*
 * Copyright (c) 2026. Glypdl Contributors
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
 */
package com.glypdl.android.ui.status.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glypdl.android.ui.theme.WhatsAppGreen

sealed class StatusEmptyStateType {
    object FolderNotConfigured : StatusEmptyStateType()
    object PermissionRevoked : StatusEmptyStateType()
    data class FolderNotStatuses(val folderName: String) : StatusEmptyStateType()
    object NoStatuses : StatusEmptyStateType()
    object NoSavedStatuses : StatusEmptyStateType()
    data class GenericError(val message: String) : StatusEmptyStateType()
}

@Composable
fun StatusEmptyState(
    type: StatusEmptyStateType,
    onConfigureFolderClick: () -> Unit,
    onManualImportClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, description, primaryButtonText, showManualImport) = when (type) {
        is StatusEmptyStateType.FolderNotConfigured -> StateConfig(
            icon = Icons.Default.FolderOpen,
            title = "Set Up Status Access",
            description = "Select your WhatsApp Media folder to discover accessible status images and videos.\n\nTip: Navigate to Android > media > com.whatsapp > WhatsApp > Media",
            primaryButton = "Select WhatsApp Folder",
            showManualImport = true
        )
        is StatusEmptyStateType.PermissionRevoked -> StateConfig(
            icon = Icons.Default.Lock,
            title = "Permission Required",
            description = "Glypdl no longer has permission to access the previously selected folder. Please grant folder access again.",
            primaryButton = "Grant Folder Access",
            showManualImport = true
        )
        is StatusEmptyStateType.FolderNotStatuses -> StateConfig(
            icon = Icons.Default.FolderSpecial,
            title = "Select WhatsApp Media Folder",
            description = "The selected folder \"${type.folderName}\" doesn't contain accessible statuses.\n\nPlease select the WhatsApp Media folder (Android > media > com.whatsapp > WhatsApp > Media) or the .Statuses folder directly.",
            primaryButton = "Choose Different Folder",
            showManualImport = true
        )
        is StatusEmptyStateType.NoStatuses -> StateConfig(
            icon = Icons.Default.PhotoLibrary,
            title = "No Statuses Found",
            description = "No recent status images or videos were discovered. Open WhatsApp and view some statuses first, then pull down to refresh.",
            primaryButton = "Refresh",
            showManualImport = true
        )
        is StatusEmptyStateType.NoSavedStatuses -> StateConfig(
            icon = Icons.Default.BookmarkBorder,
            title = "No Saved Statuses Yet",
            description = "Statuses you save from the Available tab will appear here permanently in your gallery.",
            primaryButton = "Browse Available Statuses",
            showManualImport = false
        )
        is StatusEmptyStateType.GenericError -> StateConfig(
            icon = Icons.Default.ErrorOutline,
            title = "Unable to Load Statuses",
            description = type.message,
            primaryButton = "Try Again",
            showManualImport = true
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (type is StatusEmptyStateType.NoStatuses) {
                    onRefreshClick()
                } else {
                    onConfigureFolderClick()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = WhatsAppGreen
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(primaryButtonText)
        }

        if (showManualImport) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManualImportClick,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Files Manually")
            }
        }
    }
}

private data class StateConfig(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val primaryButton: String,
    val showManualImport: Boolean
)
