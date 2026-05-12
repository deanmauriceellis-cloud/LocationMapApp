/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.widget.Toast

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module MainActivityHelpers.kt"

internal fun SalemMainActivity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

internal fun SalemMainActivity.showAboutDialog() {
    val message = """
        |Katrina's Mystic Visitors Guide v1.5.55
        |Copyright (c) 2026 Destructive AI Gurus, LLC
        |All rights reserved.
        |
        |Website: DestructiveAIGurus.com
        |Email: contact@destructiveaigurus.com
        |
        |This application is proprietary software.
        |Unauthorized copying, modification, or
        |distribution is strictly prohibited.
    """.trimMargin()

    android.app.AlertDialog.Builder(this)
        .setTitle("About Katrina's Mystic Visitors Guide")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

