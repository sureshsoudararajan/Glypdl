/*
 * Copyright (C) 2026 The Glypdl Authors
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
package com.glypdl.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDownloadOptionsTest {

    @Test
    fun defaultOptions_haveSubtitlesDisabled() {
        val options = SubtitleDownloadOptions()
        assertEquals(SubtitleDownloadMode.MEDIA_ONLY, options.mode)
        assertFalse(options.isEnabled)
        assertFalse(options.isSubtitlesOnly)
        assertFalse(options.shouldEmbed)
        assertTrue(options.selectedLanguages.isEmpty())
        assertFalse(options.downloadAllLanguages)
        assertEquals(SubtitleFormatPreference.BEST, options.preferredFormat)
    }

    @Test
    fun subsOnlyMode_enablesSubtitlesOnly() {
        val options = SubtitleDownloadOptions(
            mode = SubtitleDownloadMode.SUBS_ONLY,
            selectedLanguages = setOf("en", "es"),
            preferredFormat = SubtitleFormatPreference.SRT
        )
        assertTrue(options.isEnabled)
        assertTrue(options.isSubtitlesOnly)
        assertFalse(options.shouldEmbed)
        assertEquals(2, options.selectedLanguages.size)
        assertEquals("srt", options.preferredFormat.format)
    }

    @Test
    fun embedSubsMode_enablesEmbedding() {
        val options = SubtitleDownloadOptions(
            mode = SubtitleDownloadMode.EMBED_SUBS,
            selectedLanguages = setOf("en"),
            embedInVideo = true
        )
        assertTrue(options.isEnabled)
        assertFalse(options.isSubtitlesOnly)
        assertTrue(options.shouldEmbed)
    }

    @Test
    fun mediaWithSubsMode_enablesSubtitlesWithoutEmbedding() {
        val options = SubtitleDownloadOptions(
            mode = SubtitleDownloadMode.MEDIA_WITH_SUBS,
            selectedLanguages = setOf("fr"),
            downloadAllLanguages = false
        )
        assertTrue(options.isEnabled)
        assertFalse(options.isSubtitlesOnly)
        assertFalse(options.shouldEmbed)
        assertTrue(options.hasSelections)
    }

    @Test
    fun formatPreference_fromFormat_resolvesProperly() {
        assertEquals(SubtitleFormatPreference.SRT, SubtitleFormatPreference.fromFormat("srt"))
        assertEquals(SubtitleFormatPreference.SRT, SubtitleFormatPreference.fromFormat(".SRT"))
        assertEquals(SubtitleFormatPreference.VTT, SubtitleFormatPreference.fromFormat("vtt"))
        assertEquals(SubtitleFormatPreference.ASS, SubtitleFormatPreference.fromFormat("ass"))
        assertEquals(SubtitleFormatPreference.SSA, SubtitleFormatPreference.fromFormat("ssa"))
        assertEquals(SubtitleFormatPreference.LRC, SubtitleFormatPreference.fromFormat("lrc"))
        assertEquals(SubtitleFormatPreference.BEST, SubtitleFormatPreference.fromFormat("unknown"))
        assertEquals(SubtitleFormatPreference.BEST, SubtitleFormatPreference.fromFormat("best"))
    }
}
