-- Мягкий выход из личного чата: помечаем запись вместо удаления,
-- чтобы второй пользователь мог видеть имя и аватар вышедшего.

ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS left_at TIMESTAMPTZ DEFAULT NULL;

-- Разрешаем участнику обновлять свою строку (чтобы проставить left_at)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE tablename = 'chat_members' AND policyname = 'Members can mark themselves as left'
  ) THEN
    EXECUTE '
      CREATE POLICY "Members can mark themselves as left"
      ON chat_members FOR UPDATE
      USING (auth.uid() = user_id)
      WITH CHECK (auth.uid() = user_id)
    ';
  END IF;
END;
$$;
