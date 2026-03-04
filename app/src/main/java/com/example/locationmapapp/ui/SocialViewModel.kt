/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.AuthUser
import com.example.locationmapapp.data.model.ChatMessage
import com.example.locationmapapp.data.model.ChatRoom
import com.example.locationmapapp.data.model.PoiComment
import com.example.locationmapapp.data.repository.AuthRepository
import com.example.locationmapapp.data.repository.ChatRepository
import com.example.locationmapapp.data.repository.CommentRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SocialViewModel.kt"

@HiltViewModel
class SocialViewModel @Inject constructor(
    internal val authRepository: AuthRepository,
    internal val commentRepository: CommentRepository,
    internal val chatRepository: ChatRepository
) : ViewModel() {

    private val TAG = "SocialVM"

    // ── Auth ─────────────────────────────────────────────────────────────────

    private val _authUser = MutableLiveData<AuthUser?>(authRepository.currentUser())
    val authUser: LiveData<AuthUser?> = _authUser

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun register(displayName: String, email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = authRepository.register(displayName, email, password)
                _authUser.value = resp.user
                onResult(true, null)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Register error: ${e.message}")
                onResult(false, e.message)
            }
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = authRepository.login(email, password)
                _authUser.value = resp.user
                onResult(true, null)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Login error: ${e.message}")
                onResult(false, e.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authUser.value = null
        }
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    private val _poiComments = MutableLiveData<List<PoiComment>>()
    val poiComments: LiveData<List<PoiComment>> = _poiComments

    fun loadComments(osmType: String, osmId: Long) {
        viewModelScope.launch {
            try {
                val resp = commentRepository.fetchComments(osmType, osmId)
                _poiComments.value = resp.comments
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Load comments error: ${e.message}")
                _poiComments.value = emptyList()
            }
        }
    }

    fun postComment(osmType: String, osmId: Long, content: String, rating: Int?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = commentRepository.postComment(osmType, osmId, content, rating)
            if (id != null) {
                loadComments(osmType, osmId)
            }
            onResult(id != null)
        }
    }

    fun voteOnComment(commentId: Long, vote: Int, onResult: (Pair<Int, Int>?) -> Unit) {
        viewModelScope.launch {
            val result = commentRepository.voteOnComment(commentId, vote)
            onResult(result)
        }
    }

    fun deleteComment(commentId: Long, osmType: String, osmId: Long) {
        viewModelScope.launch {
            if (commentRepository.deleteComment(commentId)) {
                loadComments(osmType, osmId)
            }
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    private val _chatRooms = MutableLiveData<List<ChatRoom>>()
    val chatRooms: LiveData<List<ChatRoom>> = _chatRooms

    private val _chatMessages = MutableLiveData<List<ChatMessage>>()
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages

    private val _currentRoomId = MutableLiveData<String?>()

    fun loadChatRooms() {
        viewModelScope.launch {
            try {
                val rooms = chatRepository.fetchRooms()
                _chatRooms.value = rooms
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Load rooms error: ${e.message}")
            }
        }
    }

    fun connectChat() {
        viewModelScope.launch {
            chatRepository.connect()
            chatRepository.setOnMessageListener { msg ->
                // Add to live list if in same room
                if (msg.roomId == _currentRoomId.value) {
                    val current = _chatMessages.value?.toMutableList() ?: mutableListOf()
                    current.add(msg)
                    _chatMessages.postValue(current)
                }
            }
        }
    }

    fun disconnectChat() {
        chatRepository.disconnect()
    }

    fun joinRoom(roomId: String) {
        _currentRoomId.value = roomId
        viewModelScope.launch {
            // Ensure socket is connected before joining
            if (!chatRepository.isConnected()) {
                chatRepository.connect()
                chatRepository.setOnMessageListener { msg ->
                    if (msg.roomId == _currentRoomId.value) {
                        val current = _chatMessages.value?.toMutableList() ?: mutableListOf()
                        current.add(msg)
                        _chatMessages.postValue(current)
                    }
                }
            }
            chatRepository.joinRoom(roomId)
            val messages = chatRepository.fetchMessages(roomId)
            _chatMessages.value = messages
        }
    }

    fun leaveRoom(roomId: String) {
        chatRepository.leaveRoom(roomId)
        if (_currentRoomId.value == roomId) {
            _currentRoomId.value = null
            _chatMessages.value = emptyList()
        }
    }

    fun sendChatMessage(roomId: String, content: String) {
        chatRepository.sendMessage(roomId, content)
    }

    fun createRoom(name: String, description: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val id = chatRepository.createRoom(name, description)
            if (id != null) loadChatRooms()
            onResult(id)
        }
    }
}
