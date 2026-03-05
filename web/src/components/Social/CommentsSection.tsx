import { useState } from 'react'
import { StarRating } from './StarRating'
import { relativeTime } from '@/lib/timeFormat'
import type { PoiComment } from '@/lib/types'

interface Props {
  osmType: string
  osmId: string | number
  comments: PoiComment[]
  total: number
  loading: boolean
  userId: number | null
  userRole: string | null
  isLoggedIn: boolean
  onPost: (osmType: string, osmId: string | number, content: string, rating?: number) => Promise<void>
  onVote: (commentId: number, vote: 1 | -1) => void
  onDelete: (commentId: number, osmType: string, osmId: string | number) => void
  onLoginRequired: () => void
}

export function CommentsSection({ osmType, osmId, comments, total, loading, userId, userRole, isLoggedIn, onPost, onVote, onDelete, onLoginRequired }: Props) {
  const [showForm, setShowForm] = useState(false)
  const [content, setContent] = useState('')
  const [rating, setRating] = useState(0)
  const [posting, setPosting] = useState(false)

  const handlePost = async () => {
    if (!content.trim()) return
    setPosting(true)
    try {
      await onPost(osmType, osmId, content.trim(), rating || undefined)
      setContent('')
      setRating(0)
      setShowForm(false)
    } catch {
      // error handled upstream
    } finally {
      setPosting(false)
    }
  }

  const canDelete = (c: PoiComment) =>
    userId === c.userId || userRole === 'owner' || userRole === 'support'

  return (
    <div className="border-t border-gray-200 dark:border-gray-700 p-4 space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
          Comments {total > 0 && `(${total})`}
        </h3>
        {isLoggedIn ? (
          <button
            onClick={() => setShowForm(!showForm)}
            className="text-xs text-teal-600 dark:text-teal-400 hover:underline"
          >
            {showForm ? 'Cancel' : '+ Add'}
          </button>
        ) : (
          <button
            onClick={onLoginRequired}
            className="text-xs text-teal-600 dark:text-teal-400 hover:underline"
          >
            Sign in to comment
          </button>
        )}
      </div>

      {/* Add comment form */}
      {showForm && (
        <div className="space-y-2 bg-gray-50 dark:bg-gray-800/50 rounded-lg p-3">
          <StarRating rating={rating} interactive onRate={setRating} size={20} />
          <textarea
            value={content}
            onChange={e => setContent(e.target.value.slice(0, 1000))}
            placeholder="Write a comment..."
            rows={3}
            className="w-full text-sm p-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 resize-none focus:ring-2 focus:ring-teal-500 outline-none"
          />
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-gray-400">{content.length}/1000</span>
            <button
              onClick={handlePost}
              disabled={posting || !content.trim()}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50 transition-colors"
            >
              {posting ? 'Posting...' : 'Post'}
            </button>
          </div>
        </div>
      )}

      {/* Loading */}
      {loading && <p className="text-xs text-gray-400">Loading comments...</p>}

      {/* Comment list */}
      {comments.map(c => (
        <div key={c.id} className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-teal-600 dark:text-teal-400">{c.authorName}</span>
            <span className="text-[10px] text-gray-400">{relativeTime(c.createdAt)}</span>
          </div>
          {c.rating && <StarRating rating={c.rating} size={12} />}
          <p className="text-sm text-gray-800 dark:text-gray-200 whitespace-pre-wrap">{c.content}</p>
          <div className="flex items-center gap-3">
            {/* Upvote */}
            <button
              onClick={() => isLoggedIn ? onVote(c.id, 1) : onLoginRequired()}
              className={`flex items-center gap-0.5 text-xs ${c.viewerVote === 1 ? 'text-teal-600 dark:text-teal-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'}`}
            >
              <svg viewBox="0 0 24 24" className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M7 10l5-5 5 5" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M12 5v14" strokeLinecap="round" />
              </svg>
              {c.upvotes > 0 && c.upvotes}
            </button>
            {/* Downvote */}
            <button
              onClick={() => isLoggedIn ? onVote(c.id, -1) : onLoginRequired()}
              className={`flex items-center gap-0.5 text-xs ${c.viewerVote === -1 ? 'text-red-500' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'}`}
            >
              <svg viewBox="0 0 24 24" className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M7 14l5 5 5-5" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M12 19V5" strokeLinecap="round" />
              </svg>
              {c.downvotes > 0 && c.downvotes}
            </button>
            {/* Delete */}
            {canDelete(c) && !c.isDeleted && (
              <button
                onClick={() => onDelete(c.id, osmType, osmId)}
                className="text-xs text-gray-400 hover:text-red-500 ml-auto"
              >
                Delete
              </button>
            )}
          </div>
        </div>
      ))}

      {!loading && comments.length === 0 && (
        <p className="text-xs text-gray-400">No comments yet.</p>
      )}
    </div>
  )
}
