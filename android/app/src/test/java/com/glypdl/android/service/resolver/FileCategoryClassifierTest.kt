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
package com.glypdl.android.service.resolver

import com.glypdl.android.data.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileCategoryClassifierTest {

    @Test
    fun classifiesArchiveExtensionsAccurately() {
        assertEquals(FileCategory.ARCHIVE, FileCategoryClassifier.fromExtension("zip"))
        assertEquals(FileCategory.ARCHIVE, FileCategoryClassifier.fromExtension("rar"))
        assertEquals(FileCategory.ARCHIVE, FileCategoryClassifier.fromExtension("7z"))
        assertEquals(FileCategory.ARCHIVE, FileCategoryClassifier.fromExtension("tar"))
        assertEquals(FileCategory.ARCHIVE, FileCategoryClassifier.fromExtension("gz"))
    }

    @Test
    fun classifiesDiskImageExtensionsAccurately() {
        assertEquals(FileCategory.DISK_IMAGE, FileCategoryClassifier.fromExtension("iso"))
        assertEquals(FileCategory.DISK_IMAGE, FileCategoryClassifier.fromExtension("img"))
        assertEquals(FileCategory.DISK_IMAGE, FileCategoryClassifier.fromExtension("vhd"))
    }

    @Test
    fun classifiesApplicationExtensionsAccurately() {
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("apk"))
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("exe"))
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("deb"))
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("rpm"))
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("msi"))
        assertEquals(FileCategory.APPLICATION, FileCategoryClassifier.fromExtension("AppImage"))
    }

    @Test
    fun classifiesDocumentExtensionsAccurately() {
        assertEquals(FileCategory.DOCUMENT, FileCategoryClassifier.fromExtension("pdf"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryClassifier.fromExtension("docx"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryClassifier.fromExtension("xlsx"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryClassifier.fromExtension("epub"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryClassifier.fromExtension("txt"))
    }

    @Test
    fun classifiesMediaExtensionsAccurately() {
        assertEquals(FileCategory.AUDIO, FileCategoryClassifier.fromExtension("mp3"))
        assertEquals(FileCategory.AUDIO, FileCategoryClassifier.fromExtension("flac"))
        assertEquals(FileCategory.AUDIO, FileCategoryClassifier.fromExtension("wav"))
        assertEquals(FileCategory.VIDEO, FileCategoryClassifier.fromExtension("mp4"))
        assertEquals(FileCategory.VIDEO, FileCategoryClassifier.fromExtension("mkv"))
        assertEquals(FileCategory.VIDEO, FileCategoryClassifier.fromExtension("webm"))
        assertEquals(FileCategory.IMAGE, FileCategoryClassifier.fromExtension("png"))
        assertEquals(FileCategory.IMAGE, FileCategoryClassifier.fromExtension("jpg"))
        assertEquals(FileCategory.IMAGE, FileCategoryClassifier.fromExtension("svg"))
    }

    @Test
    fun classifiesCodeAndDataExtensionsAccurately() {
        assertEquals(FileCategory.CODE, FileCategoryClassifier.fromExtension("kt"))
        assertEquals(FileCategory.CODE, FileCategoryClassifier.fromExtension("py"))
        assertEquals(FileCategory.CODE, FileCategoryClassifier.fromExtension("rs"))
        assertEquals(FileCategory.DATA, FileCategoryClassifier.fromExtension("json"))
        assertEquals(FileCategory.DATA, FileCategoryClassifier.fromExtension("csv"))
        assertEquals(FileCategory.DATA, FileCategoryClassifier.fromExtension("yaml"))
    }

    @Test
    fun classifiesFontExtensionsAccurately() {
        assertEquals(FileCategory.FONT, FileCategoryClassifier.fromExtension("ttf"))
        assertEquals(FileCategory.FONT, FileCategoryClassifier.fromExtension("woff2"))
    }

    @Test
    fun unknownExtensionReturnsUnknown() {
        assertEquals(FileCategory.UNKNOWN, FileCategoryClassifier.fromExtension("xyz123"))
        assertEquals(FileCategory.UNKNOWN, FileCategoryClassifier.fromExtension(""))
    }

    @Test
    fun extractsExtensionAndFilenameFromUrl() {
        assertEquals("zip", FileCategoryClassifier.extractExtensionFromUrl("https://example.com/files/archive.zip"))
        assertEquals("iso", FileCategoryClassifier.extractExtensionFromUrl("https://releases.ubuntu.com/24.04/ubuntu-desktop.iso?query=1#hash"))
        assertEquals("ubuntu-desktop.iso", FileCategoryClassifier.extractFileNameFromUrl("https://releases.ubuntu.com/24.04/ubuntu-desktop.iso?query=1#hash"))
        assertEquals("app.apk", FileCategoryClassifier.extractFileNameFromUrl("https://cdn.example.org/download/app.apk"))
    }

    @Test
    fun extractsFilenameFromContentDispositionHeader() {
        assertEquals("package.zip", FileCategoryClassifier.extractFileNameFromContentDisposition("attachment; filename=\"package.zip\""))
        assertEquals("document.pdf", FileCategoryClassifier.extractFileNameFromContentDisposition("inline; filename=document.pdf"))
        assertEquals("report.xlsx", FileCategoryClassifier.extractFileNameFromContentDisposition("attachment; filename*=UTF-8''report.xlsx"))
        assertNull(FileCategoryClassifier.extractFileNameFromContentDisposition("inline"))
    }
}
