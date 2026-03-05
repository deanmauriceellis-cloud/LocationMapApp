import { useState, useRef, useEffect } from 'react'
import { relativeTime } from '@/lib/timeFormat'
import type { AuthUser, ChatRoom, ChatMessage } from '@/lib/types'

interface ChatHook {
  rooms: ChatRoom[]
  messages: ChatMessage[]
  currentRoomId: number | null
  connected: boolean
  typingUser: string | null
  roomsLoading: boolean
  messagesLoading: boolean
  loadRooms: () => void
  connect: () => void
  disconnect: () => void
  joinRoom: (id: number) => void
  leaveRoom: () => void
  sendMessage: (content: string) => void
  sendTyping: () => void
  createRoom: (name: string, description?: string) => Promise<void>
}

interface Props {
  open: boolean
  user: AuthUser | null
  chat: ChatHook
  onLoginRequired: () => void
  onClose: () => void
}

export function ChatPanel({ open, user, chat, onLoginRequired, onClose }: Props) {
  const [view, setView] = useState<'rooms' | 'chat'>('rooms')
  const [input, setInput] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [newRoomName, setNewRoomName] = useState('')
  const [newRoomDesc, setNewRoomDesc] = useState('')
  const [creating, setCreating] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  // Scroll to bottom on new messages
  useEffect(() => {
    if (view === 'chat') {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [chat.messages.length, view])

  if (!open) return null

  if (!user) {
    return (
      <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl">
        <PanelHeader title="Chat" onClose={onClose} />
        <div className="flex-1 flex flex-col items-center justify-center gap-3 p-6">
          <p className="text-sm text-gray-500 dark:text-gray-400 text-center">Sign in to chat with other users.</p>
          <button
            onClick={onLoginRequired}
            className="px-4 py-2 rounded-lg bg-teal-600 text-white text-sm font-medium hover:bg-teal-700 transition-colors"
          >
            Sign In
          </button>
        </div>
      </div>
    )
  }

  const handleSend = () => {
    if (!input.trim()) return
    chat.sendMessage(input)
    setInput('')
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    } else {
      chat.sendTyping()
    }
  }

  const handleCreateRoom = async () => {
    if (!newRoomName.trim()) return
    setCreating(true)
    try {
      await chat.createRoom(newRoomName.trim(), newRoomDesc.trim() || undefined)
      setNewRoomName('')
      setNewRoomDesc('')
      setShowCreate(false)
    } catch {
      // error
    } finally {
      setCreating(false)
    }
  }

  const handleJoinRoom = (room: ChatRoom) => {
    chat.joinRoom(room.id)
    setView('chat')
  }

  const handleBackToRooms = () => {
    chat.leaveRoom()
    setView('rooms')
  }

  const currentRoom = chat.rooms.find(r => r.id === chat.currentRoomId)

  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl">
      {view === 'rooms' ? (
        <>
          {/* Room list header */}
          <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
            <h2 className="flex-1 font-semibold text-sm text-gray-900 dark:text-gray-100">Chat</h2>
            <button
              onClick={() => setShowCreate(!showCreate)}
              className="text-xs text-teal-600 dark:text-teal-400 hover:underline"
            >
              {showCreate ? 'Cancel' : '+ New'}
            </button>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Create room form */}
          {showCreate && (
            <div className="p-3 border-b border-gray-200 dark:border-gray-700 space-y-2">
              <input
                value={newRoomName}
                onChange={e => setNewRoomName(e.target.value.slice(0, 100))}
                placeholder="Room name"
                className="w-full px-3 py-1.5 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 outline-none focus:ring-2 focus:ring-teal-500"
              />
              <input
                value={newRoomDesc}
                onChange={e => setNewRoomDesc(e.target.value.slice(0, 255))}
                placeholder="Description (optional)"
                className="w-full px-3 py-1.5 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 outline-none focus:ring-2 focus:ring-teal-500"
              />
              <button
                onClick={handleCreateRoom}
                disabled={creating || !newRoomName.trim()}
                className="w-full py-1.5 text-xs font-medium rounded-lg bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50 transition-colors"
              >
                {creating ? 'Creating...' : 'Create Room'}
              </button>
            </div>
          )}

          {/* Room list */}
          <div className="flex-1 overflow-y-auto">
            {chat.roomsLoading && <p className="p-4 text-xs text-gray-400">Loading rooms...</p>}
            {chat.rooms.map(room => (
              <button
                key={room.id}
                onClick={() => handleJoinRoom(room)}
                className="w-full text-left px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800 border-b border-gray-100 dark:border-gray-800 transition-colors"
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{room.name}</span>
                  <span className="text-[10px] text-gray-400">
                    {room.memberCount} {room.memberCount === 1 ? 'member' : 'members'}
                  </span>
                </div>
                {room.description && (
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 truncate">{room.description}</p>
                )}
                {room.lastMessageAt && (
                  <p className="text-[10px] text-gray-400 mt-0.5">{relativeTime(room.lastMessageAt)}</p>
                )}
              </button>
            ))}
          </div>
        </>
      ) : (
        <>
          {/* Chat room header */}
          <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
            <button onClick={handleBackToRooms} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </button>
            <h2 className="flex-1 font-semibold text-sm text-gray-900 dark:text-gray-100 truncate">
              {currentRoom?.name || 'Chat'}
            </h2>
            {!chat.connected && (
              <span className="text-[10px] text-red-400">disconnected</span>
            )}
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {chat.messagesLoading && <p className="text-xs text-gray-400 text-center">Loading messages...</p>}
            {chat.messages.map(msg => {
              const isOwn = msg.userId === user.id
              return (
                <div key={msg.id} className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[75%] rounded-xl px-3 py-2 ${
                    isOwn
                      ? 'bg-teal-600 text-white'
                      : 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100'
                  }`}>
                    {!isOwn && (
                      <p className="text-[10px] font-medium text-teal-600 dark:text-teal-400 mb-0.5">{msg.authorName}</p>
                    )}
                    <p className="text-sm whitespace-pre-wrap break-words">{msg.content}</p>
                    <p className={`text-[10px] mt-1 ${isOwn ? 'text-teal-200' : 'text-gray-400'}`}>
                      {relativeTime(msg.sentAt)}
                    </p>
                  </div>
                </div>
              )
            })}
            {chat.typingUser && (
              <p className="text-xs text-gray-400 italic">{chat.typingUser} is typing...</p>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Send bar */}
          <div className="flex items-center gap-2 p-3 border-t border-gray-200 dark:border-gray-700">
            <input
              value={input}
              onChange={e => setInput(e.target.value.slice(0, 1000))}
              onKeyDown={handleKeyDown}
              placeholder="Type a message..."
              className="flex-1 px-3 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 outline-none focus:ring-2 focus:ring-teal-500"
            />
            <button
              onClick={handleSend}
              disabled={!input.trim()}
              className="w-9 h-9 rounded-lg bg-teal-600 text-white flex items-center justify-center hover:bg-teal-700 disabled:opacity-50 transition-colors shrink-0"
            >
              <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 2L11 13" />
                <path d="M22 2l-7 20-4-9-9-4 20-7z" />
              </svg>
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function PanelHeader({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
      <h2 className="font-semibold text-sm text-gray-900 dark:text-gray-100">{title}</h2>
      <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
        <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 6 6 18M6 6l12 12" />
        </svg>
      </button>
    </div>
  )
}
