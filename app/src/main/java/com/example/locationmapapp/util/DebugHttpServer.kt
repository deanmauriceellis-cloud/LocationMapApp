/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module DebugHttpServer.kt"

/**
 * DebugHttpServer — HTTP listener disabled for V1 (S209).
 *
 * The embedded HTTP server (port 4303) was a dev/QA hook for programmatic
 * control via `adb forward`. It is hard-disabled in V1 because the app
 * must make zero outside contact (memory rule: feedback_v1_no_external_contact).
 * `start()` and `stop()` are intentional no-ops; no `ServerSocket` is bound.
 *
 * The `endpoints` field is retained as an in-process holder so existing
 * non-HTTP callers keep working.
 */
object DebugHttpServer {
    @Volatile var endpoints: DebugEndpoints? = null

    fun start() {}
    fun stop() {}
}
