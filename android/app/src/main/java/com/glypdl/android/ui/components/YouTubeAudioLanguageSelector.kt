/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.model.AudioTrackClassification
import com.glypdl.android.data.model.YouTubeAudioLanguage
import com.glypdl.android.data.model.YouTubeAudioTrack

/**
 * Prominent card displayed on [AnalyzeScreen] when a YouTube video exposes multiple audio languages.
 *
 * Displays current language choice with classification badge and quick action to open the full selector.
 */
@Composable
fun YouTubeAudioLanguageCard(
    selectedLanguage: YouTubeAudioLanguage?,
    selectedTrack: YouTubeAudioTrack?,
    availableLanguagesCount: Int,
    onOpenSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenSelector() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDIO LANGUAGE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "$availableLanguagesCount available",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedLanguage != null && selectedTrack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedLanguage.languageName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ClassificationBadge(classification = selectedLanguage.classification)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val details = buildList {
                            if (selectedTrack.displayCodec.isNotBlank()) add(selectedTrack.displayCodec)
                            if (selectedTrack.displayBitrate.isNotBlank()) add(selectedTrack.displayBitrate)
                            if (selectedTrack.displayExtension.isNotBlank()) add(selectedTrack.displayExtension)
                            if (selectedTrack.displaySize.isNotBlank()) add(selectedTrack.displaySize)
                        }.joinToString(" · ")

                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onOpenSelector) {
                        Text("Change", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Text(
                    text = "Tap to choose audio language",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bottom sheet modal displaying all available YouTube audio tracks and languages.
 *
 * Supports searching languages, choosing Original or Dubbed tracks, and switching quality tiers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAudioLanguageBottomSheet(
    videoTitle: String,
    languages: List<YouTubeAudioLanguage>,
    selectedLanguage: YouTubeAudioLanguage?,
    selectedTrack: YouTubeAudioTrack?,
    onLanguageSelected: (YouTubeAudioLanguage) -> Unit,
    onTrackSelected: (YouTubeAudioTrack) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(languages, searchQuery) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter { lang ->
                lang.languageName.contains(searchQuery, ignoreCase = true) ||
                        lang.languageCode.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val originalLanguages = remember(filteredLanguages) {
        filteredLanguages.filter { it.isOriginal }
    }

    val otherLanguages = remember(filteredLanguages) {
        filteredLanguages.filter { !it.isOriginal }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Audio Language",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${languages.size} Tracks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search language (e.g. Tamil, Hindi, Spanish)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Languages List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Original Language Track(s)
                if (originalLanguages.isNotEmpty()) {
                    item {
                        Text(
                            text = "ORIGINAL AUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }

                    items(originalLanguages, key = { it.languageCode }) { origLang ->
                        val isLangSelected = selectedLanguage?.languageCode.equals(origLang.languageCode, ignoreCase = true)
                        AudioLanguageItem(
                            language = origLang,
                            isSelected = isLangSelected,
                            currentTrack = if (isLangSelected) selectedTrack ?: origLang.selectedTrack else origLang.selectedTrack,
                            onLanguageClick = {
                                onLanguageSelected(origLang)
                            },
                            onTrackSelect = onTrackSelected
                        )
                    }
                }

                // Section 2: Other Available Languages (Dubbed / Alternative)
                if (otherLanguages.isNotEmpty()) {
                    item {
                        Text(
                            text = if (originalLanguages.isNotEmpty()) "OTHER LANGUAGES (DUBBED / ALTERNATIVE)" else "AVAILABLE LANGUAGES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(otherLanguages, key = { it.languageCode }) { lang ->
                        val isLangSelected = selectedLanguage?.languageCode.equals(lang.languageCode, ignoreCase = true)
                        AudioLanguageItem(
                            language = lang,
                            isSelected = isLangSelected,
                            currentTrack = if (isLangSelected) selectedTrack ?: lang.selectedTrack else lang.selectedTrack,
                            onLanguageClick = {
                                onLanguageSelected(lang)
                            },
                            onTrackSelect = onTrackSelected
                        )
                    }
                }

                if (filteredLanguages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No audio language matched \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Individual item card representing an audio language option.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioLanguageItem(
    language: YouTubeAudioLanguage,
    isSelected: Boolean,
    currentTrack: YouTubeAudioTrack,
    onLanguageClick: () -> Unit,
    onTrackSelect: (YouTubeAudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        if (language.isOriginal) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    OutlinedCard(
        onClick = onLanguageClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onLanguageClick
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = language.languageName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        Text(
                            text = "[${language.languageCode}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ClassificationBadge(classification = language.classification)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val details = buildList {
                        if (currentTrack.displayCodec.isNotBlank()) add(currentTrack.displayCodec)
                        if (currentTrack.displayBitrate.isNotBlank()) add(currentTrack.displayBitrate)
                        if (currentTrack.displayExtension.isNotBlank()) add(currentTrack.displayExtension)
                        if (currentTrack.displaySize.isNotBlank()) add(currentTrack.displaySize)
                    }.joinToString(" · ")

                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // If selected and multiple quality variants exist for this language, allow switching
            if (isSelected && language.tracks.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AVAILABLE QUALITY VARIANTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    language.tracks.forEach { track ->
                        val isTrackActive = currentTrack.formatId == track.formatId
                        val chipLabel = buildString {
                            if (track.displayBitrate.isNotBlank()) append(track.displayBitrate)
                            if (track.displayCodec.isNotBlank()) append(" (${track.displayCodec})")
                            if (track.displayExtension.isNotBlank()) append(" · ${track.displayExtension}")
                        }

                        FilterChip(
                            selected = isTrackActive,
                            onClick = { onTrackSelect(track) },
                            label = {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isTrackActive) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isTrackActive) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual badge for audio classification ([AudioTrackClassification.ORIGINAL], [AudioTrackClassification.DUBBED], [AudioTrackClassification.UNKNOWN]).
 */
@Composable
fun ClassificationBadge(
    classification: AudioTrackClassification,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor) = when (classification) {
        AudioTrackClassification.ORIGINAL -> Triple(
            "Original",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        AudioTrackClassification.DUBBED -> Triple(
            "Dubbed",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        AudioTrackClassification.UNKNOWN -> Triple(
            "Unknown",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (classification == AudioTrackClassification.ORIGINAL) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
