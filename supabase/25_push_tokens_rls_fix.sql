-- Fix: upsert на push_tokens падал с "violates RLS (USING expression)"
-- Причина: FOR ALL политика с USING (auth.uid() = user_id) проверяет СУЩЕСТВУЮЩУЮ строку
-- при ON CONFLICT DO UPDATE. Если токен ранее принадлежал другому user_id — USING не проходит.
--
-- Решение: разбиваем на отдельные политики и убираем USING у UPDATE.
-- WITH CHECK по-прежнему гарантирует, что обновлённая строка принадлежит текущему пользователю.

DROP POLICY IF EXISTS "Users manage own tokens" ON push_tokens;

-- SELECT: только свои токены
CREATE POLICY "push_tokens_select"
  ON push_tokens FOR SELECT
  USING (auth.uid() = user_id);

-- INSERT: новая строка должна принадлежать текущему пользователю
CREATE POLICY "push_tokens_insert"
  ON push_tokens FOR INSERT
  WITH CHECK (auth.uid() = user_id);

-- UPDATE: без USING (любой токен можно перезаписать),
-- но результирующая строка обязана иметь user_id = текущий пользователь
CREATE POLICY "push_tokens_update"
  ON push_tokens FOR UPDATE
  WITH CHECK (auth.uid() = user_id);

-- DELETE: только свои токены
CREATE POLICY "push_tokens_delete"
  ON push_tokens FOR DELETE
  USING (auth.uid() = user_id);
