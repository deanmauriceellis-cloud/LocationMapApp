/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module chat.js';

const jwt = require('jsonwebtoken');

module.exports = function(app, deps) {
  const { pgPool, requirePg, requireAuth, sanitizeText, sanitizeMultiline,
          roomCreateLimiter, JWT_SECRET, io } = deps;

  // Ensure "Global" room exists
  async function ensureGlobalRoom() {
    if (!pgPool) return;
    try {
      const existing = await pgPool.query("SELECT id FROM chat_rooms WHERE name = 'Global'");
      if (existing.rows.length === 0) {
        await pgPool.query(
          "INSERT INTO chat_rooms (room_type, name, description) VALUES ('public', 'Global', 'Global chat room for all users')"
        );
        console.log('[Chat] Created Global room');
      }
    } catch (err) {
      console.error('[Chat] Failed to ensure Global room:', err.message);
    }
  }

  app.get('/chat/rooms', requirePg, async (req, res) => {
    try {
      // Optionally include membership info for authed user
      let viewerUserId = null;
      const authHeader = req.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        try {
          const decoded = jwt.verify(authHeader.slice(7), JWT_SECRET);
          viewerUserId = decoded.sub;
        } catch (_) {}
      }

      let query;
      if (viewerUserId) {
        query = `
          SELECT r.id, r.room_type, r.name, r.description, r.member_count,
                 r.last_message_at, r.created_at,
                 CASE WHEN rm.user_id IS NOT NULL THEN TRUE ELSE FALSE END AS is_member,
                 rm.role AS member_role
          FROM chat_rooms r
          LEFT JOIN room_memberships rm ON rm.room_id = r.id AND rm.user_id = $1
          ORDER BY r.last_message_at DESC NULLS LAST, r.created_at DESC`;
        const result = await pgPool.query(query, [viewerUserId]);
        res.json(result.rows.map(r => ({
          id: r.id, roomType: r.room_type, name: r.name, description: r.description,
          memberCount: r.member_count, lastMessageAt: r.last_message_at,
          createdAt: r.created_at, isMember: r.is_member, memberRole: r.member_role
        })));
      } else {
        query = `SELECT id, room_type, name, description, member_count, last_message_at, created_at
                 FROM chat_rooms ORDER BY last_message_at DESC NULLS LAST, created_at DESC`;
        const result = await pgPool.query(query);
        res.json(result.rows.map(r => ({
          id: r.id, roomType: r.room_type, name: r.name, description: r.description,
          memberCount: r.member_count, lastMessageAt: r.last_message_at, createdAt: r.created_at
        })));
      }
    } catch (err) {
      console.error('[Chat] GET rooms error:', err.message);
      res.status(500).json({ error: 'Failed to fetch rooms' });
    }
  });

  app.post('/chat/rooms', requirePg, requireAuth, roomCreateLimiter, async (req, res) => {
    try {
      const { name: rawName, description: rawDesc, roomType } = req.body;
      const name = sanitizeText(rawName, 100);
      if (!name || name.length < 2) {
        return res.status(400).json({ error: 'name must be 2-100 characters' });
      }
      const description = rawDesc ? sanitizeText(rawDesc, 255) : null;
      const type = roomType || 'public';
      if (!['public', 'private'].includes(type)) {
        return res.status(400).json({ error: 'roomType must be public or private' });
      }

      const result = await pgPool.query(
        `INSERT INTO chat_rooms (room_type, name, description, created_by, member_count)
         VALUES ($1, $2, $3, $4, 1) RETURNING id, created_at`,
        [type, name, description, req.user.id]
      );
      const room = result.rows[0];

      // Auto-join creator as admin
      await pgPool.query(
        'INSERT INTO room_memberships (room_id, user_id, role) VALUES ($1, $2, $3)',
        [room.id, req.user.id, 'admin']
      );
      console.log(`[Chat] Room created: "${name}" by ${req.user.displayName}`);
      res.status(201).json({ id: room.id, name, createdAt: room.created_at });
    } catch (err) {
      console.error('[Chat] POST room error:', err.message);
      res.status(500).json({ error: 'Failed to create room' });
    }
  });

  app.get('/chat/rooms/:id/messages', requirePg, async (req, res) => {
    try {
      const roomId = req.params.id;
      const before = req.query.before;
      const limit = Math.min(parseInt(req.query.limit) || 50, 100);

      let query, params;
      if (before) {
        query = `
          SELECT m.id, m.room_id, m.user_id, m.content, m.reply_to_id, m.is_deleted, m.sent_at,
                 u.display_name AS author_name
          FROM messages m JOIN users u ON m.user_id = u.id
          WHERE m.room_id = $1 AND m.id < $2
          ORDER BY m.sent_at DESC LIMIT $3`;
        params = [roomId, before, limit];
      } else {
        query = `
          SELECT m.id, m.room_id, m.user_id, m.content, m.reply_to_id, m.is_deleted, m.sent_at,
                 u.display_name AS author_name
          FROM messages m JOIN users u ON m.user_id = u.id
          WHERE m.room_id = $1
          ORDER BY m.sent_at DESC LIMIT $2`;
        params = [roomId, limit];
      }

      const result = await pgPool.query(query, params);
      // Return in chronological order (oldest first)
      res.json(result.rows.reverse().map(r => ({
        id: r.id, roomId: r.room_id, userId: r.user_id,
        content: r.is_deleted ? '[deleted]' : r.content,
        replyToId: r.reply_to_id, isDeleted: r.is_deleted,
        sentAt: r.sent_at, authorName: r.author_name
      })));
    } catch (err) {
      console.error('[Chat] GET messages error:', err.message);
      res.status(500).json({ error: 'Failed to fetch messages' });
    }
  });

  // ── Socket.IO Chat ──────────────────────────────────────────────────────────

  io.use((socket, next) => {
    const token = socket.handshake.auth?.token;
    if (!token) return next(new Error('Authentication required'));
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      socket.user = { id: decoded.sub, displayName: decoded.name, role: decoded.role };
      next();
    } catch (err) {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket) => {
    console.log(`[Socket.IO] Connected: ${socket.user.displayName} (${socket.id})`);

    // Per-socket message rate limiting: 30 messages per 60 seconds
    const msgTimestamps = [];
    const SOCKET_MSG_WINDOW = 60 * 1000;
    const SOCKET_MSG_MAX = 30;

    socket.on('join_room', async (roomId) => {
      if (!pgPool) return;
      socket.join(roomId);

      // Ensure membership exists
      try {
        await pgPool.query(
          `INSERT INTO room_memberships (room_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
          [roomId, socket.user.id]
        );
        // Update member count
        const countResult = await pgPool.query(
          'SELECT COUNT(*) FROM room_memberships WHERE room_id = $1', [roomId]
        );
        await pgPool.query(
          'UPDATE chat_rooms SET member_count = $1 WHERE id = $2',
          [countResult.rows[0].count, roomId]
        );
      } catch (err) {
        console.error('[Socket.IO] join_room DB error:', err.message);
      }

      console.log(`[Socket.IO] ${socket.user.displayName} joined room ${roomId}`);
    });

    socket.on('leave_room', (roomId) => {
      socket.leave(roomId);
      console.log(`[Socket.IO] ${socket.user.displayName} left room ${roomId}`);
    });

    socket.on('send_message', async (data) => {
      if (!pgPool) return;
      const { roomId, content: rawContent, replyToId } = data;

      // Rate limit check
      const now = Date.now();
      while (msgTimestamps.length > 0 && now - msgTimestamps[0] > SOCKET_MSG_WINDOW) msgTimestamps.shift();
      if (msgTimestamps.length >= SOCKET_MSG_MAX) {
        socket.emit('error_message', { error: 'Rate limit exceeded — slow down' });
        return;
      }
      msgTimestamps.push(now);

      const content = sanitizeMultiline(rawContent, 1000);
      if (!roomId || !content) return;

      try {
        const result = await pgPool.query(
          `INSERT INTO messages (room_id, user_id, content, reply_to_id)
           VALUES ($1, $2, $3, $4) RETURNING id, sent_at`,
          [roomId, socket.user.id, content, replyToId || null]
        );
        const msg = result.rows[0];

        await pgPool.query(
          'UPDATE chat_rooms SET last_message_at = $1 WHERE id = $2',
          [msg.sent_at, roomId]
        );

        const outMsg = {
          id: msg.id,
          roomId,
          userId: socket.user.id,
          authorName: socket.user.displayName,
          content,
          replyToId: replyToId || null,
          sentAt: msg.sent_at
        };
        io.to(roomId).emit('new_message', outMsg);
      } catch (err) {
        console.error('[Socket.IO] send_message error:', err.message);
      }
    });

    socket.on('typing', (roomId) => {
      socket.to(roomId).emit('user_typing', {
        userId: socket.user.id,
        displayName: socket.user.displayName
      });
    });

    socket.on('disconnect', () => {
      console.log(`[Socket.IO] Disconnected: ${socket.user.displayName}`);
    });
  });

  return { ensureGlobalRoom };
};
