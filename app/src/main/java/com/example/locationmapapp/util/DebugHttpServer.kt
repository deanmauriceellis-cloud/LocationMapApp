package com.example.locationmapapp.util

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * DebugHttpServer — embedded HTTP server for programmatic control via
 *   adb forward tcp:8085 tcp:8085 && curl localhost:8085/state
 *
 * Minimal HTTP/1.0 parser. All responses are Connection: close.
 * Runs accept loop on Dispatchers.IO. Call start() from MainActivity.onCreate(),
 * stop() from onDestroy(). Safe to call start() multiple times.
 */
object DebugHttpServer {

    private const val PORT = 8085
    private const val TAG  = "DebugHttp"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile var endpoints: DebugEndpoints? = null

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) {
            DebugLogger.i(TAG, "Already running on port $PORT, skipping start()")
            return
        }
        // Clean up any leftover socket from a previous run
        serverSocket?.runCatching { close() }
        serverSocket = null

        job = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                DebugLogger.i(TAG, "Listening on port $PORT")
                while (isActive) {
                    val socket = serverSocket!!.accept()
                    launch { handleConnection(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}", e)
                DebugLogger.e(TAG, "Server error: ${e.message}", e)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        serverSocket?.runCatching { close() }
        serverSocket = null
        DebugLogger.i(TAG, "Stopped")
    }

    private suspend fun handleConnection(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5_000
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                // Consume headers (we don't need them)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    sendResponse(s.getOutputStream(), 400, "text/plain", "Bad request")
                    return
                }
                val method = parts[0]
                val rawPath = parts[1]

                // Parse path and query params
                val qIdx = rawPath.indexOf('?')
                val path = if (qIdx >= 0) rawPath.substring(0, qIdx) else rawPath
                val params = if (qIdx >= 0) parseQuery(rawPath.substring(qIdx + 1)) else emptyMap()

                DebugLogger.d(TAG, "$method $path params=$params")

                val ep = endpoints
                if (ep == null) {
                    sendJson(s.getOutputStream(), 503, """{"error":"Activity not active"}""")
                    return
                }

                val result = ep.handle(method, path, params)
                if (result.contentType == "image/png") {
                    sendResponse(s.getOutputStream(), result.status, result.contentType, result.bodyBytes!!)
                } else {
                    sendResponse(s.getOutputStream(), result.status, result.contentType, result.body)
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Request error: ${e.message}", e)
                runCatching { sendJson(s.getOutputStream(), 500, """{"error":"${e.message}"}""") }
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                map[java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8")] =
                    java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            } else if (pair.isNotEmpty()) {
                map[java.net.URLDecoder.decode(pair, "UTF-8")] = ""
            }
        }
        return map
    }

    private fun sendJson(out: OutputStream, status: Int, json: String) {
        sendResponse(out, status, "application/json", json)
    }

    private fun sendResponse(out: OutputStream, status: Int, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        sendResponse(out, status, contentType, bodyBytes)
    }

    private fun sendResponse(out: OutputStream, status: Int, contentType: String, bodyBytes: ByteArray) {
        val statusText = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"
            500 -> "Internal Server Error"; 503 -> "Service Unavailable"
            else -> "OK"
        }
        val header = "HTTP/1.0 $status $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }
}
