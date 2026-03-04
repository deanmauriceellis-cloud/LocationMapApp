/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.widget.Toast

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityHelpers.kt"

internal fun MainActivity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

internal fun MainActivity.showAboutDialog() {
    val message = """
        |LocationMapApp v1.5.55
        |Copyright (c) 2026 Dean Maurice Ellis
        |All rights reserved.
        |
        |Website: DestructiveAIGurus.com
        |Email: Questions@DestructiveAIGurus.com
        |
        |This application is proprietary software.
        |Unauthorized copying, modification, or
        |distribution is strictly prohibited.
    """.trimMargin()

    android.app.AlertDialog.Builder(this)
        .setTitle("About LocationMapApp")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

