/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module comments.js';

const jwt = require('jsonwebtoken');

module.exports = function(app, deps) {
  const { pgPool, requirePg, requireAuth, sanitizeMultiline, commentLimiter, JWT_SECRET } = deps;

  app.get('/comments/:osm_type/:osm_id', requirePg, async (req, res) => {
    try {
      const { osm_type, osm_id } = req.params;
      // Optionally include viewer's votes
      let viewerUserId = null;
      const authHeader = req.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        try {
          const decoded = jwt.verify(authHeader.slice(7), JWT_SECRET);
          viewerUserId = decoded.sub;
        } catch (_) {}
      }

      let query, params;
      if (viewerUserId) {
        query = `
          SELECT c.id, c.osm_type, c.osm_id, c.user_id, c.parent_id, c.content,
                 c.rating, c.upvotes, c.downvotes, c.is_deleted, c.created_at,
                 u.display_name AS author_name,
                 cv.vote AS viewer_vote
          FROM poi_comments c
          JOIN users u ON c.user_id = u.id
          LEFT JOIN comment_votes cv ON cv.comment_id = c.id AND cv.user_id = $3
          WHERE c.osm_type = $1 AND c.osm_id = $2
          ORDER BY c.created_at DESC
          LIMIT 100`;
        params = [osm_type, osm_id, viewerUserId];
      } else {
        query = `
          SELECT c.id, c.osm_type, c.osm_id, c.user_id, c.parent_id, c.content,
                 c.rating, c.upvotes, c.downvotes, c.is_deleted, c.created_at,
                 u.display_name AS author_name
          FROM poi_comments c
          JOIN users u ON c.user_id = u.id
          WHERE c.osm_type = $1 AND c.osm_id = $2
          ORDER BY c.created_at DESC
          LIMIT 100`;
        params = [osm_type, osm_id];
      }

      const result = await pgPool.query(query, params);
      const comments = result.rows.map(r => ({
        id: r.id,
        osmType: r.osm_type,
        osmId: r.osm_id,
        userId: r.user_id,
        parentId: r.parent_id,
        content: r.is_deleted ? '[deleted]' : r.content,
        rating: r.rating,
        upvotes: r.upvotes,
        downvotes: r.downvotes,
        isDeleted: r.is_deleted,
        createdAt: r.created_at,
        authorName: r.author_name,
        viewerVote: r.viewer_vote || 0
      }));
      res.json({ comments, total: comments.length });
    } catch (err) {
      console.error('[Comments] GET error:', err.message);
      res.status(500).json({ error: 'Failed to fetch comments' });
    }
  });

  app.post('/comments', requirePg, requireAuth, commentLimiter, async (req, res) => {
    try {
      const { osmType, osmId, content: rawContent, rating, parentId } = req.body;
      if (!osmType || !osmId || !rawContent) {
        return res.status(400).json({ error: 'osmType, osmId, and content are required' });
      }
      if (!['node', 'way', 'relation'].includes(osmType)) {
        return res.status(400).json({ error: 'osmType must be node, way, or relation' });
      }
      const content = sanitizeMultiline(rawContent, 1000);
      if (!content) {
        return res.status(400).json({ error: 'Content cannot be empty' });
      }
      if (rating !== undefined && rating !== null && (!Number.isInteger(rating) || rating < 1 || rating > 5)) {
        return res.status(400).json({ error: 'Rating must be an integer between 1 and 5' });
      }

      const result = await pgPool.query(
        `INSERT INTO poi_comments (osm_type, osm_id, user_id, parent_id, content, rating)
         VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING id, created_at`,
        [osmType, osmId, req.user.id, parentId || null, content, rating || null]
      );
      console.log(`[Comments] New comment by ${req.user.displayName} on ${osmType}/${osmId}`);
      res.status(201).json({
        id: result.rows[0].id,
        createdAt: result.rows[0].created_at,
        authorName: req.user.displayName
      });
    } catch (err) {
      console.error('[Comments] POST error:', err.message);
      res.status(500).json({ error: 'Failed to post comment' });
    }
  });

  app.post('/comments/:id/vote', requirePg, requireAuth, commentLimiter, async (req, res) => {
    try {
      const commentId = parseInt(req.params.id);
      const { vote } = req.body;
      if (vote !== 1 && vote !== -1) {
        return res.status(400).json({ error: 'vote must be 1 or -1' });
      }

      // Upsert vote
      await pgPool.query(
        `INSERT INTO comment_votes (comment_id, user_id, vote)
         VALUES ($1, $2, $3)
         ON CONFLICT (comment_id, user_id) DO UPDATE SET vote = $3`,
        [commentId, req.user.id, vote]
      );

      // Recount aggregates
      const counts = await pgPool.query(
        `SELECT
           COALESCE(SUM(CASE WHEN vote = 1 THEN 1 ELSE 0 END), 0) AS upvotes,
           COALESCE(SUM(CASE WHEN vote = -1 THEN 1 ELSE 0 END), 0) AS downvotes
         FROM comment_votes WHERE comment_id = $1`,
        [commentId]
      );
      const { upvotes, downvotes } = counts.rows[0];
      await pgPool.query(
        'UPDATE poi_comments SET upvotes = $1, downvotes = $2 WHERE id = $3',
        [upvotes, downvotes, commentId]
      );
      res.json({ upvotes: parseInt(upvotes), downvotes: parseInt(downvotes) });
    } catch (err) {
      console.error('[Comments] Vote error:', err.message);
      res.status(500).json({ error: 'Failed to vote' });
    }
  });

  app.delete('/comments/:id', requirePg, requireAuth, async (req, res) => {
    try {
      const commentId = parseInt(req.params.id);
      // Only comment owner or platform support/owner can delete
      const result = await pgPool.query('SELECT user_id FROM poi_comments WHERE id = $1', [commentId]);
      if (result.rows.length === 0) {
        return res.status(404).json({ error: 'Comment not found' });
      }
      const isOwner = result.rows[0].user_id === req.user.id;
      const isAdmin = ['owner', 'support'].includes(req.user.role);
      if (!isOwner && !isAdmin) {
        return res.status(403).json({ error: 'Not authorized to delete this comment' });
      }
      await pgPool.query('UPDATE poi_comments SET is_deleted = TRUE WHERE id = $1', [commentId]);
      res.json({ ok: true });
    } catch (err) {
      console.error('[Comments] Delete error:', err.message);
      res.status(500).json({ error: 'Failed to delete comment' });
    }
  });
};
