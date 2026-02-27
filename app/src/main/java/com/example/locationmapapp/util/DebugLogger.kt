package com.example.locationmapapp.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * DebugLogger — in-memory ring buffer + logcat bridge.
 * Captures up to MAX_ENTRIES log lines with sequence numbers and timestamps.
 * Viewable in DebugLogActivity; streamed over TCP if TcpLogStreamer is active.
 */
object DebugLogger {

    private const val MAX_ENTRIES = 1_000
    private val seq = AtomicInteger(0)
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    var tcpStreamer: ((String) -> Unit)? = null   // set by TcpLogStreamer

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        log("E", tag, msg)
        t?.let { log("E", tag, formatThrowable(it)) }
    }

    private fun log(level: String, tag: String, msg: String) {
        val line = "[${seq.incrementAndGet()}] ${sdf.format(Date())} $level/$tag: $msg"
        when (level) {
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> Log.w(tag, msg)
            "E" -> Log.e(tag, msg)
        }
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(line)
        }
        tcpStreamer?.invoke(line)
    }

    fun getAll(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun formatThrowable(t: Throwable): String {
        val sb = StringBuilder()
        var cause: Throwable? = t
        while (cause != null) {
            sb.append("  ${cause.javaClass.simpleName}: ${cause.message}\n")
            cause.stackTrace.take(5).forEach { sb.append("    at $it\n") }
            cause = cause.cause
        }
        return sb.toString()
    }
}
