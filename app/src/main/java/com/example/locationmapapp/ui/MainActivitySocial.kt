/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.R
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.launch
import android.content.Context

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivitySocial.kt"

// =========================================================================
// AUTH / SOCIAL DIALOGS
// =========================================================================

internal fun MainActivity.showAuthDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Register"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(closeBtn)
    }

    // ── Form Fields ──
    val displayNameField = android.widget.EditText(this).apply {
        hint = "Display Name"
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#80FFFFFF"))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        filters = arrayOf(android.text.InputFilter.LengthFilter(50))
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    val emailField = android.widget.EditText(this).apply {
        hint = "Email"
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#80FFFFFF"))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        filters = arrayOf(android.text.InputFilter.LengthFilter(255))
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    val passwordField = android.widget.EditText(this).apply {
        hint = "Password"
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#80FFFFFF"))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(128))
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
    }

    val errorText = TextView(this).apply {
        setTextColor(Color.parseColor("#FF6B6B"))
        textSize = 13f
        visibility = View.GONE
        setPadding(0, 0, 0, dp(8))
    }

    val submitBtn = TextView(this).apply {
        text = "Register"
        textSize = 16f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#1E88E5"))
        gravity = android.view.Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
    }

    val infoText = TextView(this).apply {
        text = "Your account is bonded to this device"
        textSize = 12f
        setTextColor(Color.parseColor("#80FFFFFF"))
        gravity = android.view.Gravity.CENTER
    }

    // ── Submit action ──
    submitBtn.setOnClickListener {
        val displayName = displayNameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        if (displayName.length < 2) {
            errorText.text = "Display name must be at least 2 characters"
            errorText.visibility = View.VISIBLE
            return@setOnClickListener
        }
        if (email.isEmpty() || password.isEmpty()) {
            errorText.text = "Email and password are required"
            errorText.visibility = View.VISIBLE
            return@setOnClickListener
        }
        if (!email.contains("@") || !email.contains(".")) {
            errorText.text = "Invalid email format"
            errorText.visibility = View.VISIBLE
            return@setOnClickListener
        }
        if (password.length < 8) {
            errorText.text = "Password must be at least 8 characters"
            errorText.visibility = View.VISIBLE
            return@setOnClickListener
        }
        submitBtn.isEnabled = false
        submitBtn.text = "Registering..."
        socialViewModel.register(displayName, email, password) { success, err ->
            runOnUiThread {
                submitBtn.isEnabled = true
                if (success) {
                    toast("Welcome, ${displayName}!")
                    dialog.dismiss()
                } else {
                    errorText.text = err ?: "Registration failed"
                    errorText.visibility = View.VISIBLE
                    submitBtn.text = "Register"
                }
            }
        }
    }

    // ── Layout ──
    val formLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(16), dp(24), dp(24))
        addView(displayNameField)
        addView(emailField)
        addView(passwordField)
        addView(errorText)
        addView(submitBtn)
        addView(infoText)
    }

    val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        addView(header)
        addView(View(this@showAuthDialog).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })
        addView(formLayout)
    }

    dialog.setContentView(rootLayout)
    dialog.show()
}

internal fun MainActivity.showChatDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Chat Rooms"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val newRoomBtn = TextView(this).apply {
        text = "+ New"
        textSize = 14f
        setTextColor(Color.parseColor("#64B5F6"))
        setPadding(dp(8), 0, dp(8), 0)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(newRoomBtn)
        addView(closeBtn)
    }

    // ── Room list ──
    val roomList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(16))
    }
    val loadingText = TextView(this).apply {
        text = "Loading rooms..."
        textSize = 13f
        setTextColor(Color.parseColor("#80FFFFFF"))
    }
    roomList.addView(loadingText)

    fun renderRooms(rooms: List<com.example.locationmapapp.data.model.ChatRoom>) {
        roomList.removeAllViews()
        if (rooms.isEmpty()) {
            roomList.addView(TextView(this).apply {
                text = "No rooms yet"
                textSize = 13f
                setTextColor(Color.parseColor("#80FFFFFF"))
            })
            return
        }
        for (room in rooms) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    showChatRoomDialog(room.id, room.name)
                }
            }
            // Room name + member count
            val nameRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            nameRow.addView(TextView(this).apply {
                text = room.name
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            nameRow.addView(TextView(this).apply {
                text = "${room.memberCount} ${if (room.memberCount == 1) "member" else "members"}"
                textSize = 11f
                setTextColor(Color.parseColor("#666666"))
            })
            card.addView(nameRow)

            if (room.description != null) {
                card.addView(TextView(this).apply {
                    text = room.description
                    textSize = 12f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    maxLines = 2
                    setPadding(0, dp(2), 0, 0)
                })
            }
            roomList.addView(card)
        }
    }

    socialViewModel.loadChatRooms()
    socialViewModel.chatRooms.observe(this) { rooms ->
        renderRooms(rooms ?: emptyList())
    }

    // New room dialog
    newRoomBtn.setOnClickListener {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        content.addView(TextView(this).apply {
            text = "New Chat Room"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })
        val nameInput = android.widget.EditText(this).apply {
            hint = "Room name"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        val descInput = android.widget.EditText(this).apply {
            hint = "Description (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            filters = arrayOf(android.text.InputFilter.LengthFilter(255))
        }
        content.addView(nameInput)
        content.addView(descInput)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        btnRow.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { dlg.dismiss() }
        })
        btnRow.addView(TextView(this).apply {
            text = "Create"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E88E5"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.length < 2) return@setOnClickListener
                socialViewModel.createRoom(name, descInput.text.toString().trim().ifEmpty { null }) { id ->
                    runOnUiThread {
                        dlg.dismiss()
                        if (id != null) toast("Room created") else toast("Failed to create room")
                    }
                }
            }
        })
        content.addView(btnRow)
        dlg.setContentView(content)
        dlg.window?.let { w ->
            val dm = resources.displayMetrics
            w.setLayout((dm.widthPixels * 0.85).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            w.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dlg.show()
    }

    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(roomList)
    }
    val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        addView(header)
        addView(View(this@showChatDialog).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })
        addView(scrollView)
    }
    dialog.setContentView(rootLayout)
    dialog.show()
}

internal fun MainActivity.showChatRoomDialog(roomId: String, roomName: String) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val backBtn = TextView(this).apply {
        text = "\u2190"
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(8), 0, dp(12), 0)
        setOnClickListener {
            socialViewModel.leaveRoom(roomId)
            dialog.dismiss()
            showChatDialog()
        }
    }
    val titleText = TextView(this).apply {
        text = roomName
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener {
            socialViewModel.leaveRoom(roomId)
            socialViewModel.disconnectChat()
            dialog.dismiss()
        }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), dp(10), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(backBtn)
        addView(titleText)
        addView(closeBtn)
    }

    // ── Messages area ──
    val messagesContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(messagesContainer)
        isFillViewport = true
    }

    val currentUserId = socialViewModel.authUser.value?.id

    fun renderMessages(messages: List<com.example.locationmapapp.data.model.ChatMessage>) {
        messagesContainer.removeAllViews()
        for (msg in messages) {
            val isOwn = msg.userId == currentUserId
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (isOwn) Color.parseColor("#1E88E5") else Color.parseColor("#2A2A2A"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = if (isOwn) android.view.Gravity.END else android.view.Gravity.START
                    bottomMargin = dp(4)
                    marginStart = if (isOwn) dp(48) else 0
                    marginEnd = if (isOwn) 0 else dp(48)
                }
            }
            if (!isOwn) {
                bubble.addView(TextView(this).apply {
                    text = msg.authorName
                    textSize = 11f
                    setTextColor(Color.parseColor("#4FC3F7"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            }
            bubble.addView(TextView(this).apply {
                text = msg.content
                textSize = 14f
                setTextColor(Color.WHITE)
            })
            val relTime = try {
                val instant = java.time.Instant.parse(msg.sentAt)
                val dur = java.time.Duration.between(instant, java.time.Instant.now())
                when {
                    dur.toMinutes() < 1 -> "just now"
                    dur.toMinutes() < 60 -> "${dur.toMinutes()}m"
                    dur.toHours() < 24 -> "${dur.toHours()}h"
                    else -> msg.sentAt.take(10)
                }
            } catch (_: Exception) { "" }
            bubble.addView(TextView(this).apply {
                text = relTime
                textSize = 10f
                setTextColor(Color.parseColor("#80FFFFFF"))
                gravity = android.view.Gravity.END
            })
            messagesContainer.addView(bubble)
        }
        // Scroll to bottom
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    // Connect and join
    socialViewModel.connectChat()
    socialViewModel.joinRoom(roomId)

    socialViewModel.chatMessages.observe(this) { messages ->
        renderMessages(messages ?: emptyList())
    }

    // ── Send bar ──
    val messageInput = android.widget.EditText(this).apply {
        hint = "Type a message..."
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#80FFFFFF"))
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        maxLines = 3
    }
    val sendBtn = TextView(this).apply {
        text = "Send"
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#1E88E5"))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        gravity = android.view.Gravity.CENTER
    }
    sendBtn.setOnClickListener {
        val text = messageInput.text.toString().trim()
        if (text.isNotEmpty()) {
            socialViewModel.sendChatMessage(roomId, text)
            messageInput.text.clear()
        }
    }
    val sendBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#252525"))
        addView(messageInput)
        addView(sendBtn)
    }

    // ── Root ──
    val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        fitsSystemWindows = true
        addView(header)
        addView(View(this@showChatRoomDialog).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })
        addView(scrollView)
        addView(sendBar)
    }

    dialog.setOnDismissListener {
        socialViewModel.leaveRoom(roomId)
    }

    dialog.setContentView(rootLayout)
    dialog.show()
}

internal fun MainActivity.showAddCommentDialog(osmType: String, osmId: Long) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dlg = android.app.Dialog(this)
    dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        setPadding(dp(20), dp(16), dp(20), dp(16))
    }

    content.addView(TextView(this).apply {
        text = "Add Comment"
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    })

    // Star rating selector
    var selectedRating = 0
    val starsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(8))
    }
    val starViews = mutableListOf<TextView>()
    for (i in 1..5) {
        val starView = TextView(this).apply {
            text = "\u2606"
            textSize = 24f
            setTextColor(Color.parseColor("#FFB300"))
            setPadding(0, 0, dp(4), 0)
        }
        starView.setOnClickListener {
            selectedRating = if (selectedRating == i) 0 else i
            for (j in starViews.indices) {
                starViews[j].text = if (j < selectedRating) "\u2605" else "\u2606"
            }
        }
        starViews.add(starView)
        starsRow.addView(starView)
    }
    starsRow.addView(TextView(this).apply {
        text = "(optional)"
        textSize = 11f
        setTextColor(Color.parseColor("#666666"))
        setPadding(dp(8), dp(6), 0, 0)
    })
    content.addView(starsRow)

    // Comment text
    val commentInput = android.widget.EditText(this).apply {
        hint = "Write your comment..."
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#80FFFFFF"))
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minLines = 3
        maxLines = 6
        filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    content.addView(commentInput)

    val charCounter = TextView(this).apply {
        text = "0 / 1000"
        textSize = 11f
        setTextColor(Color.parseColor("#80FFFFFF"))
        gravity = android.view.Gravity.END
        setPadding(0, dp(2), 0, 0)
    }
    commentInput.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            charCounter.text = "${s?.length ?: 0} / 1000"
        }
    })
    content.addView(charCounter)

    val errorText = TextView(this).apply {
        setTextColor(Color.parseColor("#FF6B6B"))
        textSize = 12f
        visibility = View.GONE
        setPadding(0, dp(4), 0, 0)
    }
    content.addView(errorText)

    // Buttons row
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.END
        setPadding(0, dp(12), 0, 0)
    }
    btnRow.addView(TextView(this).apply {
        text = "Cancel"
        textSize = 14f
        setTextColor(Color.parseColor("#9E9E9E"))
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { dlg.dismiss() }
    })
    val submitBtn = TextView(this).apply {
        text = "Post"
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#1E88E5"))
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    submitBtn.setOnClickListener {
        val text = commentInput.text.toString().trim()
        if (text.isEmpty()) {
            errorText.text = "Comment cannot be empty"
            errorText.visibility = View.VISIBLE
            return@setOnClickListener
        }
        submitBtn.isEnabled = false
        submitBtn.text = "Posting..."
        socialViewModel.postComment(osmType, osmId, text, if (selectedRating > 0) selectedRating else null) { success ->
            runOnUiThread {
                if (success) {
                    toast("Comment posted")
                    dlg.dismiss()
                } else {
                    errorText.text = "Failed to post comment"
                    errorText.visibility = View.VISIBLE
                    submitBtn.isEnabled = true
                    submitBtn.text = "Post"
                }
            }
        }
    }
    btnRow.addView(submitBtn)
    content.addView(btnRow)

    dlg.setContentView(content)
    dlg.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.85).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        win.setBackgroundDrawableResource(android.R.color.transparent)
    }
    dlg.show()
}

internal fun MainActivity.showProfileDialog() {
    val user = socialViewModel.authUser.value
    if (user == null) {
        showAuthDialog()
        return
    }

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Profile"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(closeBtn)
    }

    // ── Profile info ──
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(16), dp(24), dp(24))
    }

    // Avatar circle with initial
    val avatarSize = dp(64)
    val avatarView = TextView(this).apply {
        text = user.displayName.firstOrNull()?.uppercase() ?: "?"
        textSize = 28f
        setTextColor(Color.WHITE)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(Color.parseColor("#1E88E5"))
        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        }
    }
    // Round it via outline
    avatarView.clipToOutline = true
    avatarView.outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    content.addView(avatarView)

    // Display name
    content.addView(TextView(this).apply {
        text = user.displayName
        textSize = 20f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, dp(4))
    })

    // Role badge
    content.addView(TextView(this).apply {
        text = user.role.uppercase()
        textSize = 11f
        setTextColor(Color.parseColor("#80FFFFFF"))
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, dp(24))
    })

    // User ID
    fun infoRow(label: String, value: String) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(this@showProfileDialog).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#9E9E9E"))
                layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@showProfileDialog).apply {
                text = value
                textSize = 13f
                setTextColor(Color.WHITE)
            })
        })
    }
    infoRow("User ID", user.id.take(8) + "...")
    infoRow("Role", user.role)

    // ── Root layout ──
    val rootLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        addView(header)
        addView(View(this@showProfileDialog).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })
        addView(content)
    }

    dialog.setContentView(rootLayout)
    dialog.show()
}

