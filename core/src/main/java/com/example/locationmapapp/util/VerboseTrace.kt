/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.util

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module VerboseTrace.kt"

/**
 * VerboseTrace — uniform, grep-friendly gate-decision logging for the
 * verbose-debug effort introduced in S243.
 *
 * All emissions go through [DebugLogger], so they ride the same TCP +
 * file + logcat sinks already wired. Callers should still wrap usage
 * with `if (BuildConfig.DEBUG) VerboseTrace.gate(...)` for R8 to dead-
 * code-eliminate the call entirely in release builds — this object only
 * formats the line, it does not gate by itself.
 *
 * Output line format (one shape, grep-able):
 *     gate:<name> allowed=<true|false> reason=<reason> [extras]
 *
 * Example call site:
 *     if (BuildConfig.DEBUG) VerboseTrace.gate("isHistoricalQualified",
 *         allowed = false, reason = "tour-mode-not-eligible",
 *         tag = "NARR-GATE", extras = mapOf("poi" to point.id))
 */
object VerboseTrace {

    fun gate(
        name: String,
        allowed: Boolean,
        reason: String,
        tag: String = "Gate",
        extras: Map<String, Any?> = emptyMap(),
    ) {
        val extra = if (extras.isEmpty()) "" else
            extras.entries.joinToString(" ", prefix = " ") { "${it.key}=${it.value}" }
        DebugLogger.d(tag, "gate:$name allowed=$allowed reason=$reason$extra")
    }
}
