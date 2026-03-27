-- Fix v2: правильная RLS для push_tokens при upsert с ON CONFLICT (token).
--
-- Проблема: PostgreSQL при FOR UPDATE без USING использует WITH CHECK как USING,
-- поэтому предыдущий фикс (25_...) не помог — поведение осталось прежним.
--
-- Корень: ON CONFLICT DO UPDATE проверяет USING против СУЩЕСТВУЮЩЕЙ строки.
-- Если токен принадлежит другому user_id — USING (auth.uid() = user_id) падает.
--
-- Решение: USING (true) разрешает UPDATE любой строки (нужно для перехвата
-- токена при смене аккаунта на устройстве). WITH CHECK по-прежнему гарантирует,
-- что результирующая строка принадлежит текущему пользователю.

-- Убираем все политики (и старые, и из 25_...)
DROP POLICY IF EXISTS "Users manage own tokens"  ON push_tokens;
DROP POLICY IF EXISTS "push_tokens_select"        ON push_tokens;
DROP POLICY IF EXISTS "push_tokens_insert"        ON push_tokens;
DROP POLICY IF EXISTS "push_tokens_update"        ON push_tokens;
DROP POLICY IF EXISTS "push_tokens_delete"        ON push_tokens;

-- SELECT: только свои токены
CREATE POLICY "push_tokens_select"
    ON push_tokens FOR SELECT
    USING (auth.uid() = user_id);

-- INSERT: новая строка должна принадлежать текущему пользователю
CREATE POLICY "push_tokens_insert"
    ON push_tokens FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- UPDATE: USING (true) — любая строка может быть перезаписана (нужно для upsert
-- когда токен уже существует с другим user_id). WITH CHECK гарантирует,
-- что в результате user_id = текущий пользователь.
CREATE POLICY "push_tokens_update"
    ON push_tokens FOR UPDATE
    USING (true)
    WITH CHECK (auth.uid() = user_id);

-- DELETE: только свои токены
CREATE POLICY "push_tokens_delete"
    ON push_tokens FOR DELETE
    USING (auth.uid() = user_id);
