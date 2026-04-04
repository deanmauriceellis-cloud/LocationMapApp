import { useState, useCallback } from 'react'
import { authFetch } from '@/config/api'
import type { PoiComment, CommentsResponse } from '@/lib/types'

export function useComments() {
  const [comments, setComments] = useState<PoiComment[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)

  const loadComments = useCallback(async (osmType: string, osmId: string | number) => {
    setLoading(true)
    try {
      const data = await authFetch<CommentsResponse>(`/comments/${osmType}/${osmId}`)
      setComments(data.comments)
      setTotal(data.total)
    } catch {
      // If not authed, try unauthenticated fetch
      try {
        const res = await fetch(`${import.meta.env.VITE_API_URL || 'http://10.0.0.4:4300'}/comments/${osmType}/${osmId}`)
        if (res.ok) {
          const data = await res.json()
          setComments(data.comments)
          setTotal(data.total)
        }
      } catch {
        setComments([])
        setTotal(0)
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const postComment = useCallback(async (osmType: string, osmId: string | number, content: string, rating?: number) => {
    await authFetch('/comments', {
      method: 'POST',
      body: JSON.stringify({ osmType, osmId: String(osmId), content, rating: rating || null }),
    })
    await loadComments(osmType, osmId)
  }, [loadComments])

  const voteOnComment = useCallback(async (commentId: number, vote: 1 | -1) => {
    const result = await authFetch<{ upvotes: number; downvotes: number }>(`/comments/${commentId}/vote`, {
      method: 'POST',
      body: JSON.stringify({ vote }),
    })
    setComments(prev => prev.map(c =>
      c.id === commentId ? { ...c, upvotes: result.upvotes, downvotes: result.downvotes, viewerVote: vote } : c
    ))
  }, [])

  const deleteComment = useCallback(async (commentId: number, osmType: string, osmId: string | number) => {
    await authFetch(`/comments/${commentId}`, { method: 'DELETE' })
    await loadComments(osmType, osmId)
  }, [loadComments])

  const clearComments = useCallback(() => {
    setComments([])
    setTotal(0)
  }, [])

  return { comments, total, loading, loadComments, postComment, voteOnComment, deleteComment, clearComments }
}
