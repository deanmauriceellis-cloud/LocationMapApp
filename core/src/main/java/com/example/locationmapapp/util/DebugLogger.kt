/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module DebugLogger.kt"

/**
 * DebugLogger — in-memory ring buffer + logcat bridge + optional file sink.
 *
 * Captures up to MAX_ENTRIES log lines with sequence numbers and timestamps.
 * Viewable in DebugLogActivity; streamed over TCP if TcpLogStreamer is active;
 * mirrored to a daily-rotated file if `initFileSink(context)` has been called.
 *
 * S110: file sink added so logs survive process death — the in-memory ring
 * buffer is gone the moment the process dies (e.g., today's drive bug at 16:38),
 * but a flushed file on external storage survives. Pull with:
 *   adb pull /sdcard/Android/data/com.example.wickedsalemwitchcitytour/files/logs/
 */
object DebugLogger {

    private const val MAX_ENTRIES = 1_000
    private val seq = AtomicInteger(0)
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    /**
     * S110: process ID stamped into every log line. The in-memory `seq`
     * counter resets to 0 on every fresh process start, so when multiple
     * process lifetimes get appended to the same daily log file, sequence
     * numbers like `[53]` collide across process instances. Including the PID
     * in the prefix disambiguates them with a single grep:
     *   grep '|26950]' debug-20260409.log
     */
    private val pid: Int by lazy { android.os.Process.myPid() }

    var tcpStreamer: ((String) -> Unit)? = null   // set by TcpLogStreamer

    /** Lazily-constructed file sink. Null until `initFileSink(context)` runs. */
    @Volatile private var fileSink: FileLogSink? = null

    /**
     * Wire up the file sink. Idempotent — safe to call from
     * Application.onCreate(). Logs go to:
     *   <externalFilesDir>/logs/debug-YYYYMMDD.log
     * with daily rotation and a 7-day retention window.
     *
     * On the first call, also writes a clear process-start banner so multiple
     * process lifetimes appended to the same file are easy to navigate.
     */
    fun initFileSink(context: Context) {
        if (fileSink != null) return
        try {
            val baseDir = context.getExternalFilesDir(null)
                ?: context.filesDir // fallback to internal storage
            val logsDir = File(baseDir, "logs").apply {
                if (!exists()) mkdirs()
            }
            fileSink = FileLogSink(logsDir)
            // Banner first — easy grep target for "where did this process start?"
            i("DebugLogger", "════════ process started PID=$pid ════════")
            i("DebugLogger", "File sink initialized at ${logsDir.absolutePath}")
        } catch (e: Exception) {
            Log.e("DebugLogger", "initFileSink failed", e)
        }
    }

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null && isExpectedNetworkException(t)) {
            // S110: detect "expected" network conditions (server down, DNS
            // failure, TCP timeout, circuit breaker open). Log ONE line
            // total — the call site's message already contains the exception
            // text via `${e.message}`, so a follow-up line would be pure
            // duplication. Demoted to WARNING since this is a known
            // degraded condition, not a bug.
            log("W", tag, msg)
            return
        }
        log("E", tag, msg)
        if (t != null) {
            log("E", tag, formatThrowable(t))
        }
    }

    /**
     * S110: network exception classes that we treat as "known degraded
     * condition, no need for a stack trace."
     *
     * Walks the cause chain so wrapped exceptions (e.g. Retrofit's
     * `HttpException` wrapping a `ConnectException`) are also recognized.
     */
    private fun isExpectedNetworkException(t: Throwable): Boolean {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            when (cur) {
                is com.example.locationmapapp.util.network.CircuitOpenException,
                is com.example.locationmapapp.util.network.OfflineModeException,
                is ConnectException,
                is SocketTimeoutException,
                is UnknownHostException -> return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun log(level: String, tag: String, msg: String) {
        val line = "[${seq.incrementAndGet()}|$pid] ${sdf.format(Date())} $level/$tag: $msg"
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
        fileSink?.write(line)
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

/**
 * Append-only file sink for DebugLogger. Single background thread, line-buffered,
 * `flush()` after every line so logs survive process death (the bug-recovery use case).
 *
 * - Daily rotation: switches files when the calendar date changes.
 * - Retention: prunes log files older than 7 days, run once per rotation.
 * - Thread-safe: writes go through a single-thread executor; the call site
 *   never blocks on file I/O.
 * - Failure-tolerant: any exception during write is logged to logcat and
 *   swallowed; the in-memory ring buffer and logcat bridge keep working.
 */
private class FileLogSink(private val logsDir: File) {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DebugLogger-FileSink").apply { isDaemon = true }
    }
    private val sdfDate = SimpleDateFormat("yyyyMMdd", Locale.US)
    private var currentDate: String = ""
    private var writer: BufferedWriter? = null
    private val retentionMs = 7L * 24L * 60L * 60L * 1000L

    fun write(line: String) {
        executor.execute {
            try {
                val today = sdfDate.format(Date())
                if (today != currentDate || writer == null) {
                    rotate(today)
                }
                writer?.apply {
                    write(line)
                    newLine()
                    flush()
                }
            } catch (e: Exception) {
                Log.e("FileLogSink", "Write failed", e)
                try { writer?.close() } catch (_: Exception) {}
                writer = null
            }
        }
    }

    private fun rotate(today: String) {
        try { writer?.close() } catch (_: Exception) {}
        currentDate = today
        val file = File(logsDir, "debug-$today.log")
        writer = BufferedWriter(FileWriter(file, /* append = */ true))
        // Header line so a fresh file always shows when it was opened
        writer?.write(
            "===== log opened ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} ====="
        )
        writer?.newLine()
        writer?.flush()
        pruneOldLogs()
    }

    private fun pruneOldLogs() {
        try {
            val cutoff = System.currentTimeMillis() - retentionMs
            logsDir.listFiles { f ->
                f.isFile && f.name.startsWith("debug-") && f.name.endsWith(".log")
            }?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e("FileLogSink", "pruneOldLogs failed", e)
        }
    }
}
