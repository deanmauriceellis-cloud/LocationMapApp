#!/bin/bash
# LocationMapApp v1.5
# Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
#
# This source code is proprietary and confidential.
# Unauthorized copying, modification, or distribution is
# strictly prohibited.
MODULE_ID="(C) Dean Maurice Ellis, 2026 - Module create-social-tables.sh"
# LocationMapApp v1.5.45 — Social Layer DDL
# Creates the 8 tables needed for auth, comments, and chat
# Must run as: sudo -u postgres bash create-social-tables.sh

set -euo pipefail

DB="locationmapapp"
APP_USER="witchdoctor"

echo "=== LocationMapApp Social Layer DDL ==="
echo "Database: $DB"
echo "Granting to: $APP_USER"
echo ""

psql -d "$DB" -v ON_ERROR_STOP=1 <<'SQL'

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name    VARCHAR(50) NOT NULL,
    email           TEXT,
    password_hash   TEXT,
    platform_role   VARCHAR(20) NOT NULL DEFAULT 'user',
    is_banned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Auth Lookup (login by email hash)
CREATE TABLE IF NOT EXISTS auth_lookup (
    email_hash  VARCHAR(64) PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Refresh Tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id);

-- 4. POI Comments
CREATE TABLE IF NOT EXISTS poi_comments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    osm_type    VARCHAR(10) NOT NULL,
    osm_id      BIGINT NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id),
    parent_id   BIGINT REFERENCES poi_comments(id),
    content     TEXT NOT NULL CHECK (length(content) <= 2000),
    rating      SMALLINT CHECK (rating BETWEEN 1 AND 5),
    upvotes     INTEGER NOT NULL DEFAULT 0,
    downvotes   INTEGER NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comments_poi ON poi_comments (osm_type, osm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_user ON poi_comments (user_id);

-- 5. Comment Votes
CREATE TABLE IF NOT EXISTS comment_votes (
    comment_id  BIGINT NOT NULL REFERENCES poi_comments(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    vote        SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);

-- 6. Chat Rooms
CREATE TABLE IF NOT EXISTS chat_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type       VARCHAR(20) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    created_by      UUID REFERENCES users(id),
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    member_count    INTEGER NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Room Memberships
CREATE TABLE IF NOT EXISTS room_memberships (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted_until TIMESTAMPTZ,
    PRIMARY KEY (room_id, user_id)
);

-- 8. Messages (simple table — partitioning deferred)
CREATE TABLE IF NOT EXISTS messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id     UUID NOT NULL REFERENCES chat_rooms(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,
    reply_to_id BIGINT,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by  UUID,
    deleted_at  TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_room ON messages (room_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_user ON messages (user_id);

-- Grant all to app user
GRANT ALL ON users, auth_lookup, refresh_tokens,
    poi_comments, comment_votes,
    chat_rooms, room_memberships, messages
TO witchdoctor;

-- Grant sequence usage (for IDENTITY columns)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO witchdoctor;

SQL

echo ""
echo "=== Done! 8 tables created ==="
echo ""
psql -d "$DB" -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename IN ('users','auth_lookup','refresh_tokens','poi_comments','comment_votes','chat_rooms','room_memberships','messages') ORDER BY tablename;"
