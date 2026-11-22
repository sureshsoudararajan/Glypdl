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
package com.glypdl.android.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val label: String) {
    CMD("CMD"),
    INFO("INFO"),
    STDOUT("STDOUT"),
    STDERR("STDERR"),
    WARN("WARN"),
    ERROR("ERROR")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun toFormattedString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestamp))
        return "[$timeStr] [${level.label}] [$tag] $message"
    }
}

object DiagnosticLogger {
    private const val RETENTION_MILLIS = 24L * 60 * 60 * 1000L // 24 hours
    private const val MAX_MEMORY_ENTRIES = 1200

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logFile: File? = null
    private var appContext: Context? = null

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var entryCounter = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        val logsDir = File(context.filesDir, "diagnostics")
        if (!logsDir.exists()) logsDir.mkdirs()
        logFile = File(logsDir, "app_diagnostics.log")

        scope.launch {
            loadAndPruneLogs()
            log("System", "Diagnostic logger initialized. Auto-retention: 24h.", LogLevel.INFO)
        }
    }

    private val SENSITIVE_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("(?i)(authorization|cookie|set-cookie|x-csrftoken|x-ig-[a-z-]+)(\\s*[:=]\\s*)[^\\n]*") to "$1$2[REDACTED]",
        Regex("(?i)(--cookies|--username|--password|--video-password|--add-header)(\\s+)\\S+") to "$1$2[REDACTED]",
        Regex("(?i)([?&;\\s\"'](?:access_token|token|sessionid|csrftoken|sig|signature|key|password|auth)=)[^&\\s\"']+") to "$1[REDACTED]"
    )

    /** Masks credentials, cookies and tokens so exported diagnostics are safe to share. */
    internal fun redact(message: String): String =
        SENSITIVE_PATTERNS.fold(message) { acc, (regex, repl) -> regex.replace(acc, repl) }

    fun log(tag: String, rawMessage: String, level: LogLevel = LogLevel.INFO) {
        val message = redact(rawMessage)
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            id = ++entryCounter,
            timestamp = now,
            level = level,
            tag = tag,
            message = message
        )

        // Native Android Logcat output (guarded for JVM unit tests)
        try {
            when (level) {
                LogLevel.ERROR -> Log.e(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.CMD -> Log.i(tag, "[CMD] $message")
                LogLevel.STDOUT, LogLevel.INFO -> Log.i(tag, message)
                LogLevel.STDERR -> Log.w(tag, "[STDERR] $message")
            }
        } catch (_: Throwable) {
            // Android Logcat not available in JVM unit tests
        }

        scope.launch {
            mutex.withLock {
                val current = _logs.value.toMutableList()
                current.add(entry)
                // Enforce max memory cap
                val cutoff = now - RETENTION_MILLIS
                val pruned = current.filter { it.timestamp >= cutoff }.takeLast(MAX_MEMORY_ENTRIES)
                _logs.value = pruned

                // Append to disk file
                try {
                    logFile?.let { file ->
                        FileOutputStream(file, true).bufferedWriter().use { writer ->
                            writer.write("${entry.timestamp}|${entry.level.name}|${entry.tag}|${entry.message.replace("\n", " ⏎ ")}\n")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore write failures
                }
            }
        }
    }

    fun logCmd(cmd: String) {
        log("yt-dlp", cmd, LogLevel.CMD)
    }

    fun logStdout(tag: String, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val level = if (trimmed.contains("warning", ignoreCase = true)) {
            LogLevel.WARN
        } else if (trimmed.contains("error", ignoreCase = true)) {
            LogLevel.ERROR
        } else {
            LogLevel.STDOUT
        }
        log(tag, trimmed, level)
    }

    fun logStderr(tag: String, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        log(tag, trimmed, LogLevel.STDERR)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(tag, fullMsg, LogLevel.ERROR)
    }

    private suspend fun loadAndPruneLogs() {
        mutex.withLock {
            val file = logFile ?: return
            if (!file.exists()) return

            val now = System.currentTimeMillis()
            val cutoff = now - RETENTION_MILLIS
            val validEntries = mutableListOf<LogEntry>()

            try {
                file.forEachLine { line ->
                    val parts = line.split("|", limit = 4)
                    if (parts.size >= 4) {
                        val ts = parts[0].toLongOrNull() ?: 0L
                        if (ts >= cutoff) {
                            val lvl = try { LogLevel.valueOf(parts[1]) } catch (_: Exception) { LogLevel.INFO }
                            val tag = parts[2]
                            val msg = parts[3].replace(" ⏎ ", "\n")
                            validEntries.add(
                                LogEntry(
                                    id = ++entryCounter,
                                    timestamp = ts,
                                    level = lvl,
                                    tag = tag,
                                    message = msg
                                )
                            )
                        }
                    }
                }

                // Rewrite file without purged lines
                file.bufferedWriter().use { writer ->
                    for (entry in validEntries) {
                        writer.write("${entry.timestamp}|${entry.level.name}|${entry.tag}|${entry.message.replace("\n", " ⏎ ")}\n")
                    }
                }

                _logs.value = validEntries.takeLast(MAX_MEMORY_ENTRIES)
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    fun getFormattedLogs(): String {
        val entries = _logs.value
        if (entries.isEmpty()) {
            return "--- No diagnostic logs recorded yet ---"
        }
        val sb = StringBuilder()
        sb.append("==================================================\n")
        sb.append(" Glypdl Diagnostic Logs (Last 24 Hours)\n")
        sb.append(" Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append(" Total Entries: ${entries.size}\n")
        sb.append("==================================================\n\n")

        for (entry in entries) {
            sb.append(entry.toFormattedString()).append("\n")
        }
        return sb.toString()
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                _logs.value = emptyList()
                try {
                    logFile?.delete()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            log("System", "Diagnostic logs manually cleared.", LogLevel.INFO)
        }
    }

    fun exportLogsToFile(context: Context): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(exportDir, "glypdl-diagnostics-$timestamp.txt")
        exportFile.writeText(getFormattedLogs())
        return exportFile
    }

    fun createShareIntent(context: Context): Intent? {
        return try {
            val file = exportLogsToFile(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Glypdl Diagnostic Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }
}
