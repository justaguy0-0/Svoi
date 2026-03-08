-- ============================================================
-- Svoi Messenger — Enable Realtime
-- Запускать ПОСЛЕ 03_rls.sql
-- ============================================================
-- Включить Realtime для таблиц, на которые подписывается клиент

ALTER PUBLICATION supabase_realtime ADD TABLE messages;
ALTER PUBLICATION supabase_realtime ADD TABLE message_reads;
ALTER PUBLICATION supabase_realtime ADD TABLE user_presence;
ALTER PUBLICATION supabase_realtime ADD TABLE chat_members;
ALTER PUBLICATION supabase_realtime ADD TABLE pinned_messages;
