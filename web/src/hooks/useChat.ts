import { useState, useCallback, useRef } from 'react'
import { io, Socket } from 'socket.io-client'
import { authFetch, getStoredTokens, API_BASE } from '@/config/api'
import type { ChatRoom, ChatMessage } from '@/lib/types'

export function useChat() {
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [currentRoomId, setCurrentRoomId] = useState<number | null>(null)
  const [connected, setConnected] = useState(false)
  const [typingUser, setTypingUser] = useState<string | null>(null)
  const [roomsLoading, setRoomsLoading] = useState(false)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const socketRef = useRef<Socket | null>(null)
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const loadRooms = useCallback(async () => {
    setRoomsLoading(true)
    try {
      const data = await authFetch<ChatRoom[]>('/chat/rooms')
      setRooms(data)
    } catch {
      setRooms([])
    } finally {
      setRoomsLoading(false)
    }
  }, [])

  const connect = useCallback(() => {
    if (socketRef.current?.connected) return
    const tokens = getStoredTokens()
    if (!tokens?.accessToken) return

    const socket = io(API_BASE, {
      auth: { token: tokens.accessToken },
      transports: ['websocket', 'polling'],
    })

    socket.on('connect', () => setConnected(true))
    socket.on('disconnect', () => setConnected(false))

    socket.on('new_message', (msg: ChatMessage) => {
      setMessages(prev => [...prev, msg])
    })

    socket.on('user_typing', (data: { displayName: string }) => {
      setTypingUser(data.displayName)
      if (typingTimerRef.current) clearTimeout(typingTimerRef.current)
      typingTimerRef.current = setTimeout(() => setTypingUser(null), 3000)
    })

    socket.on('error_message', (data: { error: string }) => {
      console.warn('[Chat]', data.error)
    })

    socketRef.current = socket
  }, [])

  const disconnect = useCallback(() => {
    socketRef.current?.disconnect()
    socketRef.current = null
    setConnected(false)
    setCurrentRoomId(null)
    setMessages([])
    setTypingUser(null)
  }, [])

  const joinRoom = useCallback(async (roomId: number) => {
    setMessagesLoading(true)
    setCurrentRoomId(roomId)
    setMessages([])
    try {
      const msgs = await authFetch<ChatMessage[]>(`/chat/rooms/${roomId}/messages`)
      setMessages(msgs)
    } catch {
      // empty
    } finally {
      setMessagesLoading(false)
    }
    socketRef.current?.emit('join_room', roomId)
  }, [])

  const leaveRoom = useCallback(() => {
    if (currentRoomId) {
      socketRef.current?.emit('leave_room', currentRoomId)
    }
    setCurrentRoomId(null)
    setMessages([])
    setTypingUser(null)
  }, [currentRoomId])

  const sendMessage = useCallback((content: string) => {
    if (!currentRoomId || !content.trim()) return
    socketRef.current?.emit('send_message', { roomId: currentRoomId, content: content.trim() })
  }, [currentRoomId])

  const sendTyping = useCallback(() => {
    if (currentRoomId) {
      socketRef.current?.emit('typing', currentRoomId)
    }
  }, [currentRoomId])

  const createRoom = useCallback(async (name: string, description?: string) => {
    await authFetch('/chat/rooms', {
      method: 'POST',
      body: JSON.stringify({ name, description }),
    })
    await loadRooms()
  }, [loadRooms])

  return {
    rooms, messages, currentRoomId, connected, typingUser,
    roomsLoading, messagesLoading,
    loadRooms, connect, disconnect, joinRoom, leaveRoom,
    sendMessage, sendTyping, createRoom,
  }
}
