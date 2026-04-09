-- 31_global_search.sql
-- SECURITY DEFINER function for global message search across all chats the user is a member of.
-- Handles personal chat names (other user's display_name) and group chat names separately.
-- Respects history_from so users don't see messages from before they joined.

CREATE OR REPLACE FUNCTION search_messages(query TEXT, lim INT DEFAULT 20, off INT DEFAULT 0)
RETURNS TABLE(
    message_id  UUID,
    chat_id     UUID,
    chat_type   TEXT,
    chat_name   TEXT,
    content     TEXT,
    sender_name TEXT,
    created_at  TIMESTAMPTZ,
    emoji       TEXT,
    bg_color    TEXT,
    is_own      BOOLEAN
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT
        m.id                                                        AS message_id,
        m.chat_id,
        c.type                                                      AS chat_type,
        CASE
            WHEN c.type = 'group' THEN COALESCE(c.name, 'Группа')
            ELSE COALESCE(other_p.display_name, 'Пользователь')
        END                                                         AS chat_name,
        m.content,
        COALESCE(sender_p.display_name, 'Пользователь')            AS sender_name,
        m.created_at,
        CASE
            WHEN c.type = 'group' THEN '👥'
            ELSE COALESCE(other_p.emoji, '😊')
        END                                                         AS emoji,
        CASE
            WHEN c.type = 'group' THEN '#455A64'
            ELSE COALESCE(other_p.bg_color, '#5C6BC0')
        END                                                         AS bg_color,
        (m.sender_id = auth.uid())                                  AS is_own
    FROM messages m
    -- User must be an active member of the chat
    INNER JOIN chat_members cm_self
        ON  cm_self.chat_id = m.chat_id
        AND cm_self.user_id = auth.uid()
        AND cm_self.left_at IS NULL
    INNER JOIN chats c ON c.id = m.chat_id
    -- For personal chats: get the other member's profile for avatar + name
    LEFT JOIN chat_members cm_other
        ON  cm_other.chat_id = m.chat_id
        AND cm_other.user_id != auth.uid()
        AND c.type = 'personal'
    LEFT JOIN profiles other_p  ON other_p.id  = cm_other.user_id
    LEFT JOIN profiles sender_p ON sender_p.id = m.sender_id
    WHERE
        m.content ILIKE '%' || query || '%'
        AND m.deleted_for_all = false
        AND m.type NOT IN ('system')
        -- Respect history_from: don't show messages sent before the user joined
        AND (cm_self.history_from IS NULL OR m.created_at >= cm_self.history_from)
    ORDER BY m.created_at DESC
    LIMIT lim OFFSET off;
$$;

REVOKE EXECUTE ON FUNCTION search_messages(TEXT, INT, INT) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION search_messages(TEXT, INT, INT) TO authenticated;
