-- ============================================================
-- Svoi Messenger — RLS policies for chats UPDATE / DELETE
-- Запускать в Supabase Dashboard → SQL Editor
-- ============================================================

-- Позволяет администратору чата переименовывать группу
CREATE POLICY "chats: admin can update"
  ON chats FOR UPDATE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM chat_members
      WHERE chat_members.chat_id = chats.id
        AND chat_members.user_id = auth.uid()
        AND chat_members.role = 'admin'
    )
  );

-- Позволяет администратору чата удалять группу
CREATE POLICY "chats: admin can delete"
  ON chats FOR DELETE
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM chat_members
      WHERE chat_members.chat_id = chats.id
        AND chat_members.user_id = auth.uid()
        AND chat_members.role = 'admin'
    )
  );

-- Позволяет администратору удалять участников из chat_members
CREATE POLICY "chat_members: admin can delete members"
  ON chat_members FOR DELETE
  TO authenticated
  USING (
    -- сам себя может удалить (выйти из чата)
    user_id = auth.uid()
    OR
    -- или это делает администратор чата
    EXISTS (
      SELECT 1 FROM chat_members AS cm
      WHERE cm.chat_id = chat_members.chat_id
        AND cm.user_id = auth.uid()
        AND cm.role = 'admin'
    )
  );
