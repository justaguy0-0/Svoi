-- Fix v3: SECURITY DEFINER функция для сохранения FCM токена.
--
-- Проблема: ON CONFLICT (token) DO UPDATE проверяет USING-политику против
-- СУЩЕСТВУЮЩЕЙ строки. Если токен принадлежал другому user_id — RLS блокирует
-- даже при USING (true) из-за особенностей PostgREST/Supabase.
--
-- Решение: функция с SECURITY DEFINER обходит RLS полностью и выполняется
-- с правами владельца (postgres). auth.uid() внутри SECURITY DEFINER работает.

CREATE OR REPLACE FUNCTION save_push_token(p_token text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO push_tokens (user_id, token, created_at)
  VALUES (auth.uid(), p_token, now())
  ON CONFLICT (token)
  DO UPDATE SET user_id = auth.uid(), created_at = now();
END;
$$;

-- Только аутентифицированные пользователи могут вызывать функцию
REVOKE ALL ON FUNCTION save_push_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION save_push_token(text) TO authenticated;
