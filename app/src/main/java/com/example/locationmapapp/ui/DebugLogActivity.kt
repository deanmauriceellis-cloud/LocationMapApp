/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.locationmapapp.util.DebugLogger

class DebugLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            textSize  = 10f
            typeface  = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            text = DebugLogger.getAll().joinToString("\n")
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        setContentView(scroll)
        title = "Debug Log (${DebugLogger.getAll().size} entries)"

        // Scroll to bottom
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
