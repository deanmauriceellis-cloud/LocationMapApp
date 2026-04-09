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
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module CircuitOpenException.kt"

/**
 * Thrown by [LocalServerCircuitBreakerInterceptor] when the call site requested
 * a URL on a [LocalServerHealth.LOCAL_HOSTS] host while the circuit breaker is
 * in the OPEN state. This is NOT a network error — it's a fast-fail signal that
 * the local server has been confirmed down within the last minute, so we're
 * skipping the TCP connect entirely.
 *
 * Subclasses [IOException] so call sites that already catch IOException for
 * OkHttp transport failures handle this transparently. The exception type is
 * also recognized by [com.example.locationmapapp.util.DebugLogger.e] as a known
 * network condition and gets compact-logged (one line, no stack trace).
 */
class CircuitOpenException(message: String) : IOException(message)
