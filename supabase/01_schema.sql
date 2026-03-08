-- ============================================================
-- Svoi Messenger — Database Schema
-- Запускать в Supabase Dashboard → SQL Editor
-- ============================================================

-- ── profiles ────────────────────────────────────────────────
CREATE TABLE profiles (
  id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL DEFAULT '',
  status_text  TEXT NOT NULL DEFAULT '',
  emoji        TEXT NOT NULL DEFAULT '😊',
  bg_color     TEXT NOT NULL DEFAULT '#6200EE',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── invite_keys ──────────────────────────────────────────────
CREATE TABLE invite_keys (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key        TEXT UNIQUE NOT NULL,
  used       BOOLEAN NOT NULL DEFAULT FALSE,
  used_by    UUID REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── chats ────────────────────────────────────────────────────
CREATE TABLE chats (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type       TEXT NOT NULL CHECK (type IN ('personal', 'group')),
  name       TEXT,  -- только для группы
  created_by UUID REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── chat_members ─────────────────────────────────────────────
CREATE TABLE chat_members (
  chat_id   UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  user_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role      TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('member', 'admin')),
  muted     BOOLEAN NOT NULL DEFAULT FALSE,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (chat_id, user_id)
);

-- ── messages ─────────────────────────────────────────────────
CREATE TABLE messages (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_id             UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  sender_id           UUID REFERENCES auth.users(id),
  content             TEXT,
  type                TEXT NOT NULL DEFAULT 'text' CHECK (type IN ('text', 'photo', 'file')),
  file_url            TEXT,
  file_name           TEXT,
  file_size           BIGINT,
  reply_to_id         UUID REFERENCES messages(id),
  forwarded_from_id   UUID REFERENCES messages(id),
  edited_at           TIMESTAMPTZ,
  deleted_for_all     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── message_reads ────────────────────────────────────────────
CREATE TABLE message_reads (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  read_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (message_id, user_id)
);

-- ── pinned_messages ──────────────────────────────────────────
CREATE TABLE pinned_messages (
  chat_id    UUID PRIMARY KEY REFERENCES chats(id) ON DELETE CASCADE,
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  pinned_by  UUID REFERENCES auth.users(id),
  pinned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── user_presence ────────────────────────────────────────────
CREATE TABLE user_presence (
  user_id   UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  online    BOOLEAN NOT NULL DEFAULT FALSE,
  last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── fcm_tokens ───────────────────────────────────────────────
CREATE TABLE fcm_tokens (
  user_id    UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  token      TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_messages_chat_id_created_at ON messages(chat_id, created_at DESC);
CREATE INDEX idx_chat_members_user_id        ON chat_members(user_id);
CREATE INDEX idx_message_reads_user_id       ON message_reads(user_id);
