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

package com.glypdl.android.service.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2ProcessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rpcClient: Aria2RpcClient
) {
    private val tag = "Aria2ProcessManager"
    private val rpcSecret = UUID.randomUUID().toString()
    private var rpcPort: Int = 6800
    private var isInitialized = false
    private val mutex = Mutex()
    private var process: Process? = null
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            try {
                com.yausername.aria2c.Aria2c.getInstance().init(context)
                isInitialized = true
                Log.i(tag, "Aria2c native libraries initialized successfully")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize Aria2c via yausername package", e)
            }
        }
    }

    suspend fun ensureRunning() {
        if (!isRunning()) {
            mutex.withLock {
                if (!isRunning()) {
                    initialize()
                    startDaemon()
                }
            }
        }
    }

    suspend fun isRunning(): Boolean {
        val proc = process
        if (proc != null && !proc.isAlive) {
            return false
        }
        return try {
            rpcClient.getVersion()
            true
        } catch (e: Exception) {
            Log.v(tag, "RPC check exception: ${e.message}")
            false
        }
    }

    fun getExecutableFile(): File {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val libAria2c = File(nativeDir, "libaria2c.so")
        if (libAria2c.exists()) {
            return libAria2c
        }

        val noBackupPackages = File(context.noBackupFilesDir, "youtubedl-android/packages/aria2c")
        val candidates = listOf(
            File(noBackupPackages, "aria2c"),
            File(noBackupPackages, "bin/aria2c"),
            File(noBackupPackages, "usr/bin/aria2c"),
            File(context.filesDir, "aria2c"),
            File(context.filesDir, "libaria2c.so")
        )
        for (candidate in candidates) {
            if (candidate.exists()) {
                try { candidate.setExecutable(true, false) } catch (e: Exception) {}
                return candidate
            }
        }

        val systemAria2c = File("/system/bin/aria2c")
        if (systemAria2c.exists()) {
            return systemAria2c
        }

        return libAria2c
    }

    private fun findAvailablePort(defaultPort: Int = 6800): Int {
        return try {
            ServerSocket(defaultPort).use { it.localPort }
        } catch (e: Exception) {
            try {
                ServerSocket(0).use { it.localPort }
            } catch (e2: Exception) {
                defaultPort
            }
        }
    }

    private suspend fun startDaemon() = withContext(Dispatchers.IO) {
        val executableFile = getExecutableFile()
        if (!executableFile.exists()) {
            throw IllegalStateException("Aria2 executable not found at ${executableFile.absolutePath}. Ensure aria2c native library is bundled.")
        }

        val stagingDir = getStagingDir()
        val sessionFile = getSessionFile()

        rpcPort = findAvailablePort(6800)
        rpcClient.setPort(rpcPort)
        rpcClient.setSecret(rpcSecret)

        val cmd = mutableListOf(
            executableFile.absolutePath,
            "--enable-rpc=true",
            "--rpc-listen-port=$rpcPort",
            "--rpc-listen-all=false",
            "--rpc-secret=$rpcSecret",
            "--dir=${stagingDir.absolutePath}",
            "--allow-overwrite=true",
            "--auto-file-renaming=true",
            "--max-concurrent-downloads=8",
            "--max-connection-per-server=8",
            "--split=8",
            "--min-split-size=1M",
            "--continue=true",
            "--daemon=false",
            "--check-certificate=false",
            "--bt-save-metadata=true",
            "--save-session=${sessionFile.absolutePath}",
            "--file-allocation=none",
            "--summary-interval=0",
            "--disable-ipv6=true",
            "--seed-time=0",
            "--seed-ratio=0.0",
            "--enable-dht=true",
            "--dht-file-path=${File(stagingDir, "dht.dat").absolutePath}",
            "--dht-entry-point=dht.transmissionbt.com:6881",
            "--dht-entry-point=router.bittorrent.com:6881",
            "--dht-entry-point=router.utorrent.com:6881",
            "--bt-tracker-connect-timeout=5",
            "--bt-tracker-timeout=5",
            "--bt-max-peers=60",
            "--async-dns=false"
        )
        
        if (sessionFile.exists() && sessionFile.length() > 0) {
            cmd.add("--input-file=${sessionFile.absolutePath}")
        }

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)

        val env = pb.environment()
        val packagesDir = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val aria2cLibDir = File(packagesDir, "aria2c/usr/lib")
        val pythonLibDir = File(packagesDir, "python/usr/lib")
        val ffmpegLibDir = File(packagesDir, "ffmpeg/usr/lib")
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

        val ldPaths = listOfNotNull(
            aria2cLibDir.takeIf { it.exists() }?.absolutePath,
            pythonLibDir.takeIf { it.exists() }?.absolutePath,
            ffmpegLibDir.takeIf { it.exists() }?.absolutePath,
            nativeLibDir.takeIf { it.exists() }?.absolutePath,
            System.getenv("LD_LIBRARY_PATH")?.takeIf { it.isNotBlank() }
        )
        env["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")
        env["PATH"] = "${nativeLibDir.absolutePath}:" + (System.getenv("PATH") ?: "/system/bin")

        val certFile = File(packagesDir, "python/usr/etc/tls/cert.pem")
        if (certFile.exists()) {
            env["SSL_CERT_FILE"] = certFile.absolutePath
        }

        try {
            val proc = pb.start()
            process = proc

            val recentOutput = java.util.Collections.synchronizedList(mutableListOf<String>())
            processScope.launch {
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (recentOutput.size >= 25) {
                                recentOutput.removeAt(0)
                            }
                            recentOutput.add(line)
                            Log.d("aria2c", line)
                        }
                    }
                } catch (e: Exception) {
                    // Process stream closed
                }
            }

            var ready = false
            for (i in 0 until 25) { // 5 seconds wait
                if (!proc.isAlive) {
                    delay(50)
                    val exitCode = proc.exitValue()
                    val details = recentOutput.joinToString("\n")
                    throw IllegalStateException("aria2c process exited prematurely with code $exitCode. Output:\n$details")
                }
                if (isRunning()) {
                    ready = true
                    break
                }
                delay(200)
            }
            
            if (!ready) {
                proc.destroy()
                process = null
                val details = recentOutput.joinToString("\n")
                throw IllegalStateException("Failed to connect to aria2c RPC on port $rpcPort after 5s. Output:\n$details")
            }
            Log.i(tag, "aria2c daemon started successfully on port $rpcPort")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start aria2c daemon", e)
            process?.destroy()
            process = null
            throw e
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (isRunning()) {
            try {
                rpcClient.shutdown()
                delay(500)
            } catch (e: Exception) {
                // Ignore errors during shutdown
            }
        }
        process?.destroy()
        process = null
    }

    fun getStagingDir(): File {
        val dir = File(context.externalCacheDir, "aria2_staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSessionFile(): File {
        val file = File(context.filesDir, "aria2_session.txt")
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }
}
