-- ============================================================
-- Svoi Messenger — Helper Functions, Triggers
-- Запускать ПОСЛЕ 01_schema.sql
-- ============================================================

-- ── Helper: проверка членства в чате ────────────────────────
CREATE OR REPLACE FUNCTION is_chat_member(p_chat_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM chat_members
    WHERE chat_id = p_chat_id AND user_id = auth.uid()
  );
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ── Helper: проверка роли admin в чате ──────────────────────
CREATE OR REPLACE FUNCTION is_chat_admin(p_chat_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM chat_members
    WHERE chat_id = p_chat_id AND user_id = auth.uid() AND role = 'admin'
  );
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- ── Trigger: создать profile + presence при регистрации ─────
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO profiles (id, display_name, emoji, bg_color)
  VALUES (NEW.id, '', '😊', '#6200EE')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO user_presence (user_id, online, last_seen)
  VALUES (NEW.id, FALSE, NOW())
  ON CONFLICT (user_id) DO NOTHING;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- ── Trigger: обновлять updated_at в profiles ─────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_updated_at
  BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER messages_updated_at
  BEFORE UPDATE ON messages
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();
