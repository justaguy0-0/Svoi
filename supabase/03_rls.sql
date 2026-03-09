-- ============================================================
-- Svoi Messenger — Row Level Security Policies
-- Запускать ПОСЛЕ 02_functions_triggers.sql
-- ============================================================

-- ── profiles ────────────────────────────────────────────────
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles: authenticated can read all"
  ON profiles FOR SELECT
  TO authenticated
  USING (TRUE);

CREATE POLICY "profiles: own row update"
  ON profiles FOR UPDATE
  TO authenticated
  USING (id = auth.uid());

-- ── invite_keys ──────────────────────────────────────────────
ALTER TABLE invite_keys ENABLE ROW LEVEL SECURITY;

-- Анонимный пользователь может читать (только для валидации ключа при регистрации)
CREATE POLICY "invite_keys: anon can read"
  ON invite_keys FOR SELECT
  TO anon, authenticated
  USING (TRUE);

-- Вставку и обновление делает только service_role (admin через Supabase Dashboard)

-- ── chats ────────────────────────────────────────────────────
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;

CREATE POLICY "chats: member can read"
  ON chats FOR SELECT
  TO authenticated
  USING (is_chat_member(id));

CREATE POLICY "chats: authenticated can create"
  ON chats FOR INSERT
  TO authenticated
  WITH CHECK (created_by = auth.uid());

-- ── chat_members ─────────────────────────────────────────────
ALTER TABLE chat_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY "chat_members: member of same chat can read"
  ON chat_members FOR SELECT
  TO authenticated
  USING (is_chat_member(chat_id));

CREATE POLICY "chat_members: authenticated can insert"
  ON chat_members FOR INSERT
  TO authenticated
  WITH CHECK (TRUE);

CREATE POLICY "chat_members: own row update (muted)"
  ON chat_members FOR UPDATE
  TO authenticated
  USING (user_id = auth.uid());

CREATE POLICY "chat_members: admin can delete"
  ON chat_members FOR DELETE
  TO authenticated
  USING (is_chat_admin(chat_id));

-- ── messages ─────────────────────────────────────────────────
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "messages: member can read"
  ON messages FOR SELECT
  TO authenticated
  USING (is_chat_member(chat_id));

CREATE POLICY "messages: member can send"
  ON messages FOR INSERT
  TO authenticated
  WITH CHECK (is_chat_member(chat_id) AND sender_id = auth.uid());

-- Редактировать может отправитель (в течение 24 часов) или admin чата
CREATE POLICY "messages: sender can edit within 24h or admin"
  ON messages FOR UPDATE
  TO authenticated
  USING (
    (sender_id = auth.uid() AND created_at > NOW() - INTERVAL '24 hours')
    OR is_chat_admin(chat_id)
  );

-- Удалять (deleted_for_all) может отправитель или admin чата
CREATE POLICY "messages: sender or admin can delete"
  ON messages FOR DELETE
  TO authenticated
  USING (sender_id = auth.uid() OR is_chat_admin(chat_id));

-- ── message_reads ────────────────────────────────────────────
ALTER TABLE message_reads ENABLE ROW LEVEL SECURITY;

CREATE POLICY "message_reads: member can read"
  ON message_reads FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM messages m
      WHERE m.id = message_id AND is_chat_member(m.chat_id)
    )
  );

CREATE POLICY "message_reads: own insert"
  ON message_reads FOR INSERT
  TO authenticated
  WITH CHECK (user_id = auth.uid());

-- ── pinned_messages ──────────────────────────────────────────
ALTER TABLE pinned_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "pinned_messages: member can read"
  ON pinned_messages FOR SELECT
  TO authenticated
  USING (is_chat_member(chat_id));

CREATE POLICY "pinned_messages: member can insert"
  ON pinned_messages FOR INSERT
  TO authenticated
  WITH CHECK (is_chat_member(chat_id));

CREATE POLICY "pinned_messages: member can update"
  ON pinned_messages FOR UPDATE
  TO authenticated
  USING (is_chat_member(chat_id));

CREATE POLICY "pinned_messages: member can delete"
  ON pinned_messages FOR DELETE
  TO authenticated
  USING (is_chat_member(chat_id));

-- ── user_presence ────────────────────────────────────────────
ALTER TABLE user_presence ENABLE ROW LEVEL SECURITY;

CREATE POLICY "user_presence: authenticated can read"
  ON user_presence FOR SELECT
  TO authenticated
  USING (TRUE);

CREATE POLICY "user_presence: own upsert"
  ON user_presence FOR INSERT
  TO authenticated
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "user_presence: own update"
  ON user_presence FOR UPDATE
  TO authenticated
  USING (user_id = auth.uid());

-- ── fcm_tokens ───────────────────────────────────────────────
ALTER TABLE fcm_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY "fcm_tokens: own read"
  ON fcm_tokens FOR SELECT
  TO authenticated
  USING (user_id = auth.uid());

CREATE POLICY "fcm_tokens: own insert"
  ON fcm_tokens FOR INSERT
  TO authenticated
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "fcm_tokens: own update"
  ON fcm_tokens FOR UPDATE
  TO authenticated
  USING (user_id = auth.uid());
