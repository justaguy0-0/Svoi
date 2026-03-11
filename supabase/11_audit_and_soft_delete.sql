-- ============================================================
-- Svoi Messenger — Audit log + Soft delete for chats
-- Запускать в Supabase Dashboard → SQL Editor
-- ============================================================

-- ── 1. Таблица архива удалённых чатов ────────────────────────

CREATE TABLE IF NOT EXISTS deleted_chats (
    id          uuid PRIMARY KEY,
    type        text,
    name        text,
    created_by  uuid REFERENCES auth.users ON DELETE SET NULL,
    created_at  timestamptz,
    deleted_by  uuid REFERENCES auth.users ON DELETE SET NULL,
    deleted_at  timestamptz DEFAULT now()
);

-- Доступ только через service_role (Dashboard / бэкенд)
ALTER TABLE deleted_chats ENABLE ROW LEVEL SECURITY;
-- Нет политик → обычные пользователи не видят и не пишут

-- ── 2. Таблица журнала аудита ──────────────────────────────────

CREATE TABLE IF NOT EXISTS audit_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    action          text        NOT NULL,  -- 'group_create' | 'group_rename' | 'group_delete'
                                           -- | 'member_add' | 'member_remove'
    actor_id        uuid        REFERENCES auth.users ON DELETE SET NULL,
    chat_id         uuid,       -- намеренно без FK — чат может быть удалён
    target_user_id  uuid        REFERENCES auth.users ON DELETE SET NULL,
    meta            jsonb,      -- доп. данные (старое/новое название и т.д.)
    created_at      timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS audit_log_chat_id_idx    ON audit_log (chat_id);
CREATE INDEX IF NOT EXISTS audit_log_actor_id_idx   ON audit_log (actor_id);
CREATE INDEX IF NOT EXISTS audit_log_created_at_idx ON audit_log (created_at DESC);

-- Аутентифицированный пользователь может писать только свои события
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY "audit_log: authenticated can insert own"
    ON audit_log FOR INSERT
    TO authenticated
    WITH CHECK (actor_id = auth.uid());

-- Читать журнал — только через service_role (Dashboard / администратор)
-- (никакой SELECT-политики для обычных пользователей)

-- ── 3. Триггер: группа создана ────────────────────────────────

CREATE OR REPLACE FUNCTION _audit_chat_create()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.type = 'group' THEN
        INSERT INTO audit_log (action, actor_id, chat_id, meta)
        VALUES (
            'group_create',
            auth.uid(),
            NEW.id,
            jsonb_build_object('name', NEW.name)
        );
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_chat_create ON chats;
CREATE TRIGGER trg_audit_chat_create
    AFTER INSERT ON chats
    FOR EACH ROW EXECUTE FUNCTION _audit_chat_create();

-- ── 4. Триггер: группа переименована ─────────────────────────

CREATE OR REPLACE FUNCTION _audit_chat_rename()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.type = 'group' AND OLD.name IS DISTINCT FROM NEW.name THEN
        INSERT INTO audit_log (action, actor_id, chat_id, meta)
        VALUES (
            'group_rename',
            auth.uid(),
            NEW.id,
            jsonb_build_object('old_name', OLD.name, 'new_name', NEW.name)
        );
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_chat_rename ON chats;
CREATE TRIGGER trg_audit_chat_rename
    AFTER UPDATE ON chats
    FOR EACH ROW EXECUTE FUNCTION _audit_chat_rename();

-- ── 5. Триггер: участник добавлен ─────────────────────────────

CREATE OR REPLACE FUNCTION _audit_member_add()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO audit_log (action, actor_id, chat_id, target_user_id)
    VALUES ('member_add', auth.uid(), NEW.chat_id, NEW.user_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_member_add ON chat_members;
CREATE TRIGGER trg_audit_member_add
    AFTER INSERT ON chat_members
    FOR EACH ROW EXECUTE FUNCTION _audit_member_add();

-- ── 6. Триггер: участник удалён ───────────────────────────────

CREATE OR REPLACE FUNCTION _audit_member_remove()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO audit_log (action, actor_id, chat_id, target_user_id)
    VALUES ('member_remove', auth.uid(), OLD.chat_id, OLD.user_id);
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_member_remove ON chat_members;
CREATE TRIGGER trg_audit_member_remove
    AFTER DELETE ON chat_members
    FOR EACH ROW EXECUTE FUNCTION _audit_member_remove();

-- ── 7. Функция мягкого удаления чата (RPC) ───────────────────
--  Атомарно: копирует в deleted_chats → пишет в audit_log → удаляет из chats.
--  Доступна только члену чата с ролью admin.

CREATE OR REPLACE FUNCTION soft_delete_chat(p_chat_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_actor uuid := auth.uid();
BEGIN
    -- Проверка прав: только admin чата может удалить
    IF NOT EXISTS (
        SELECT 1 FROM chat_members
        WHERE chat_id = p_chat_id
          AND user_id = v_actor
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'permission denied: not an admin of this chat';
    END IF;

    -- Архивируем чат
    INSERT INTO deleted_chats (id, type, name, created_by, created_at, deleted_by)
    SELECT id, type, name, created_by, created_at, v_actor
    FROM chats
    WHERE id = p_chat_id;

    -- Пишем в журнал вручную (триггер не сработает из-за SECURITY DEFINER)
    INSERT INTO audit_log (action, actor_id, chat_id)
    VALUES ('group_delete', v_actor, p_chat_id);

    -- Удаляем чат (каскад на chat_members, pinned_messages и т.д. — если настроен FK)
    DELETE FROM chats WHERE id = p_chat_id;
END;
$$;

-- Только аутентифицированные пользователи могут вызывать функцию
REVOKE ALL ON FUNCTION soft_delete_chat(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION soft_delete_chat(uuid) TO authenticated;

-- ── 8. Подсказка по FK-каскадам ──────────────────────────────
-- Если хочешь, чтобы при удалении чата автоматически чистились
-- chat_members, messages, pinned_messages — добавь каскадные FK:
--
-- ALTER TABLE chat_members
--   DROP CONSTRAINT chat_members_chat_id_fkey,
--   ADD  CONSTRAINT chat_members_chat_id_fkey
--        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE;
--
-- ALTER TABLE messages
--   DROP CONSTRAINT messages_chat_id_fkey,
--   ADD  CONSTRAINT messages_chat_id_fkey
--        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE;
--
-- ALTER TABLE pinned_messages
--   DROP CONSTRAINT pinned_messages_chat_id_fkey,
--   ADD  CONSTRAINT pinned_messages_chat_id_fkey
--        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE;
