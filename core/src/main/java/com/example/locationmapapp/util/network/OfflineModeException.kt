/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util.network

import java.io.IOException

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module OfflineModeException.kt"

/**
 * Thrown by [OfflineModeInterceptor] when the V1 offline-only flag is on and
 * a call site attempted to make an outbound HTTP request. Not a network error —
 * it is a build-time posture: V1 ships fully offline, and any network call that
 * leaks through the ViewModel gates lands here as a hard backstop.
 *
 * Subclasses [IOException] so call sites that already catch IOException handle
 * this transparently. Recognized by
 * [com.example.locationmapapp.util.DebugLogger.e] as a known degraded condition
 * and compact-logged (one line, no stack trace).
 */
class OfflineModeException(host: String) : IOException(
    "offline-mode: outbound network disabled for V1 (host=$host)"
)
