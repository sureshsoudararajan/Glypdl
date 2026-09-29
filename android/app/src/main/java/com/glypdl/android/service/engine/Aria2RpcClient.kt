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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

class Aria2RpcException(val code: Int, message: String) : Exception(message)

@Singleton
class Aria2RpcClient @Inject constructor() {
    private var rpcSecret: String = ""
    private var rpcUrl = "http://127.0.0.1:6800/jsonrpc"
    private var requestIdCounter = AtomicLong(0)

    fun setSecret(secret: String) {
        rpcSecret = secret
    }

    fun setPort(port: Int) {
        rpcUrl = "http://127.0.0.1:$port/jsonrpc"
    }

    suspend fun addUri(uris: List<String>, options: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val result = call("aria2.addUri", listOf(uris, options))
        result.getString("result")
    }

    suspend fun addTorrent(torrentBase64: String, uris: List<String> = emptyList(), options: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val result = call("aria2.addTorrent", listOf(torrentBase64, uris, options))
        result.getString("result")
    }

    suspend fun tellStatus(gid: String, keys: List<String>? = null): Aria2Status = withContext(Dispatchers.IO) {
        val params = if (keys != null) listOf(gid, keys) else listOf(gid)
        val result = call("aria2.tellStatus", params)
        parseStatus(result.getJSONObject("result"))
    }

    suspend fun pause(gid: String): String = withContext(Dispatchers.IO) {
        val result = call("aria2.pause", listOf(gid))
        result.getString("result")
    }

    suspend fun unpause(gid: String): String = withContext(Dispatchers.IO) {
        val result = call("aria2.unpause", listOf(gid))
        result.getString("result")
    }

    suspend fun remove(gid: String): String = withContext(Dispatchers.IO) {
        val result = call("aria2.remove", listOf(gid))
        result.getString("result")
    }

    suspend fun forceRemove(gid: String): String = withContext(Dispatchers.IO) {
        val result = call("aria2.forceRemove", listOf(gid))
        result.getString("result")
    }

    suspend fun removeDownloadResult(gid: String): String = withContext(Dispatchers.IO) {
        val result = call("aria2.removeDownloadResult", listOf(gid))
        result.getString("result")
    }

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        val result = call("aria2.getVersion", emptyList())
        result.getJSONObject("result").getString("version")
    }

    suspend fun tellActive(keys: List<String>? = null): List<Aria2Status> = withContext(Dispatchers.IO) {
        val params = if (keys != null) listOf(keys) else emptyList()
        val result = call("aria2.tellActive", params)
        val arr = result.getJSONArray("result")
        val list = mutableListOf<Aria2Status>()
        for (i in 0 until arr.length()) {
            list.add(parseStatus(arr.getJSONObject(i)))
        }
        list
    }

    suspend fun tellWaiting(offset: Int, num: Int, keys: List<String>? = null): List<Aria2Status> = withContext(Dispatchers.IO) {
        val params = if (keys != null) listOf(offset, num, keys) else listOf(offset, num)
        val result = call("aria2.tellWaiting", params)
        val arr = result.getJSONArray("result")
        val list = mutableListOf<Aria2Status>()
        for (i in 0 until arr.length()) {
            list.add(parseStatus(arr.getJSONObject(i)))
        }
        list
    }

    suspend fun tellStopped(offset: Int, num: Int, keys: List<String>? = null): List<Aria2Status> = withContext(Dispatchers.IO) {
        val params = if (keys != null) listOf(offset, num, keys) else listOf(offset, num)
        val result = call("aria2.tellStopped", params)
        val arr = result.getJSONArray("result")
        val list = mutableListOf<Aria2Status>()
        for (i in 0 until arr.length()) {
            list.add(parseStatus(arr.getJSONObject(i)))
        }
        list
    }

    suspend fun shutdown(): Unit = withContext(Dispatchers.IO) {
        call("aria2.shutdown", emptyList())
    }

    suspend fun forceShutdown(): Unit = withContext(Dispatchers.IO) {
        call("aria2.forceShutdown", emptyList())
    }

    suspend fun saveSession(): Unit = withContext(Dispatchers.IO) {
        call("aria2.saveSession", emptyList())
    }

    suspend fun changeOption(gid: String, options: Map<String, String>): String = withContext(Dispatchers.IO) {
        val result = call("aria2.changeOption", listOf(gid, options))
        result.getString("result")
    }

    suspend fun getFiles(gid: String): List<Aria2File> = withContext(Dispatchers.IO) {
        val result = call("aria2.getFiles", listOf(gid))
        val filesArray = result.getJSONArray("result")
        val files = mutableListOf<Aria2File>()
        for (i in 0 until filesArray.length()) {
            val fObj = filesArray.getJSONObject(i)
            val urisArray = fObj.optJSONArray("uris")
            val uris = mutableListOf<Aria2Uri>()
            if (urisArray != null) {
                for (j in 0 until urisArray.length()) {
                    val uObj = urisArray.getJSONObject(j)
                    uris.add(Aria2Uri(uObj.optString("uri"), uObj.optString("status")))
                }
            }
            files.add(
                Aria2File(
                    index = fObj.optInt("index", 1),
                    path = fObj.optString("path", ""),
                    length = fObj.optLong("length", 0L),
                    completedLength = fObj.optLong("completedLength", 0L),
                    selected = fObj.optString("selected") == "true",
                    uris = uris
                )
            )
        }
        files
    }

    suspend fun getPeers(gid: String): List<Aria2Peer> = withContext(Dispatchers.IO) {
        val result = call("aria2.getPeers", listOf(gid))
        val arr = result.getJSONArray("result")
        val list = mutableListOf<Aria2Peer>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Aria2Peer(
                    peerId = obj.optString("peerId", ""),
                    ip = obj.optString("ip", ""),
                    port = obj.optInt("port", 0),
                    downloadSpeed = obj.optLong("downloadSpeed", 0L),
                    uploadSpeed = obj.optLong("uploadSpeed", 0L),
                    isSeeder = obj.optString("seeder") == "true"
                )
            )
        }
        list
    }

    private suspend fun call(method: String, params: List<Any>): JSONObject = withContext(Dispatchers.IO) {
        val id = requestIdCounter.incrementAndGet().toString()
        val reqJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            
            val jsonParams = JSONArray()
            jsonParams.put("token:$rpcSecret")
            params.forEach { param ->
                when (param) {
                    is List<*> -> jsonParams.put(JSONArray(param))
                    is Map<*, *> -> jsonParams.put(JSONObject(param))
                    else -> jsonParams.put(param)
                }
            }
            put("params", jsonParams)
        }

        val url = URL(rpcUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.doOutput = true

        try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(reqJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val responseString = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseString.isBlank()) {
                throw Aria2RpcException(responseCode, "Empty response from aria2c (HTTP $responseCode)")
            }

            val resJson = JSONObject(responseString)
            if (resJson.has("error")) {
                val errorObj = resJson.getJSONObject("error")
                val code = errorObj.optInt("code", -1)
                val msg = errorObj.optString("message", "Unknown error")
                throw Aria2RpcException(code, msg)
            }
            resJson
        } finally {
            conn.disconnect()
        }
    }

    private fun parseStatus(json: JSONObject): Aria2Status {
        val filesArray = json.optJSONArray("files")
        val files = mutableListOf<Aria2File>()
        if (filesArray != null) {
            for (i in 0 until filesArray.length()) {
                val fObj = filesArray.getJSONObject(i)
                
                val urisArray = fObj.optJSONArray("uris")
                val uris = mutableListOf<Aria2Uri>()
                if (urisArray != null) {
                    for (j in 0 until urisArray.length()) {
                        val uObj = urisArray.getJSONObject(j)
                        uris.add(Aria2Uri(uObj.optString("uri"), uObj.optString("status")))
                    }
                }
                
                files.add(
                    Aria2File(
                        index = fObj.optInt("index", 1),
                        path = fObj.optString("path", ""),
                        length = fObj.optLong("length", 0L),
                        completedLength = fObj.optLong("completedLength", 0L),
                        selected = fObj.optString("selected") == "true",
                        uris = uris
                    )
                )
            }
        }

        var bittorrent: Aria2BtInfo? = null
        if (json.has("bittorrent")) {
            val btObj = json.getJSONObject("bittorrent")
            
            var announceList: MutableList<List<String>>? = null
            if (btObj.has("announceList")) {
                announceList = mutableListOf()
                val annArray = btObj.getJSONArray("announceList")
                for (i in 0 until annArray.length()) {
                    val innerArray = annArray.getJSONArray(i)
                    val innerList = mutableListOf<String>()
                    for (j in 0 until innerArray.length()) {
                        innerList.add(innerArray.getString(j))
                    }
                    announceList.add(innerList)
                }
            }
            
            var mode: String? = null
            if (btObj.has("info")) {
                val infoObj = btObj.getJSONObject("info")
                mode = infoObj.optString("mode")
            }
            
            bittorrent = Aria2BtInfo(
                announceList = announceList,
                comment = btObj.optString("comment").takeIf { it.isNotBlank() },
                creationDate = btObj.optLong("creationDate").takeIf { it > 0 },
                mode = btObj.optString("mode").takeIf { it.isNotBlank() } ?: mode,
                name = btObj.optString("info", "").let { 
                    if (btObj.has("info")) btObj.getJSONObject("info").optString("name") else null
                }
            )
        }

        val followedByArray = json.optJSONArray("followedBy")
        val followedBy = if (followedByArray != null) {
            val list = mutableListOf<String>()
            for (i in 0 until followedByArray.length()) {
                list.add(followedByArray.getString(i))
            }
            list
        } else null

        return Aria2Status(
            gid = json.optString("gid", ""),
            status = json.optString("status", ""),
            totalLength = json.optLong("totalLength", 0L),
            completedLength = json.optLong("completedLength", 0L),
            downloadSpeed = json.optLong("downloadSpeed", 0L),
            uploadSpeed = json.optLong("uploadSpeed", 0L),
            connections = json.optInt("connections", 0),
            numSeeders = json.optInt("numSeeders", 0),
            numPieces = json.optInt("numPieces", 0),
            pieceLength = json.optLong("pieceLength", 0L),
            bitfield = json.optString("bitfield").takeIf { it.isNotBlank() },
            files = files,
            errorCode = json.optString("errorCode").takeIf { it.isNotBlank() },
            errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() },
            dir = json.optString("dir", ""),
            followedBy = followedBy,
            infoHash = json.optString("infoHash").takeIf { it.isNotBlank() },
            bittorrent = bittorrent
        )
    }
}

data class Aria2Peer(
    val peerId: String,
    val ip: String,
    val port: Int,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val isSeeder: Boolean
)
