package com.example.locationmapapp.util

import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TcpLogStreamer — streams DebugLogger output to a PC running:
 *   nc -lk 9999
 *
 * Pre-connection buffer: lines logged before the socket opens are queued
 * and flushed immediately on first connect — nothing is lost during startup.
 *
 * Auto-reconnects on disconnect with 10s backoff.
 * Call start() once from MainActivity onCreate().
 *
 * NOTE: socket.isConnected() is unreliable for detecting remote close in Java.
 * We detect dead connections via PrintWriter.checkError() after every write.
 */
object TcpLogStreamer {

    private const val HOST = "10.0.0.4"
    private const val PORT = 3333
    private const val TAG  = "TcpLogStreamer"
    private const val PRE_CONNECT_BUFFER_SIZE = 500

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var writer: PrintWriter? = null
    @Volatile private var connected = false

    private val preConnectBuffer = ConcurrentLinkedQueue<String>()
    private val reconnectNeeded  = AtomicBoolean(false)

    fun start() {
        DebugLogger.tcpStreamer = { line -> send(line) }
        scope.launch { connectLoop() }
        DebugLogger.i(TAG, "TCP log streamer starting → $HOST:$PORT")
    }

    private fun send(line: String) {
        // Always queue — the IO coroutine drains the queue.
        // This avoids NetworkOnMainThreadException when DebugLogger
        // is called from the main thread (e.g. during onCreate).
        if (preConnectBuffer.size >= PRE_CONNECT_BUFFER_SIZE) preConnectBuffer.poll()
        preConnectBuffer.add(line)
    }

    private suspend fun connectLoop() {
        while (true) {
            try {
                val socket = Socket(HOST, PORT)
                // autoFlush=true so every println() goes out immediately
                val pw = PrintWriter(socket.getOutputStream(), true)

                writer    = pw
                connected = true
                reconnectNeeded.set(false)

                DebugLogger.i(TAG, "TCP connected to $HOST:$PORT")

                // Drain queue on IO thread — all writes happen here, never on main thread
                while (!reconnectNeeded.get()) {
                    var line = preConnectBuffer.poll()
                    if (line == null) {
                        delay(50)
                        continue
                    }
                    while (line != null) {
                        pw.println(line)
                        if (pw.checkError()) {
                            writer = null
                            connected = false
                            reconnectNeeded.set(true)
                            break
                        }
                        line = preConnectBuffer.poll()
                    }
                }

                runCatching { socket.close() }
                DebugLogger.w(TAG, "TCP connection lost — reconnecting in 5s")

            } catch (e: Exception) {
                writer    = null
                connected = false
                DebugLogger.w(TAG, "TCP connect failed: ${e.message} — retry in 10s")
                delay(10_000)
                continue
            }
            delay(5_000)
        }
    }
}
