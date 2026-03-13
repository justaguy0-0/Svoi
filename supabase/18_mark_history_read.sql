-- RPC: mark all messages in a chat before a given timestamp as read for a specific user.
-- Runs as SECURITY DEFINER so it can insert message_reads on behalf of another user.
-- Called by the admin right after adding a member with history access.

CREATE OR REPLACE FUNCTION mark_history_read(
    p_chat_id    UUID,
    p_user_id    UUID,
    p_before_ts  TIMESTAMPTZ
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO message_reads (message_id, user_id)
    SELECT m.id, p_user_id
    FROM messages m
    WHERE m.chat_id    = p_chat_id
      AND m.created_at < p_before_ts
      AND m.deleted_for_all = FALSE
    ON CONFLICT (message_id, user_id) DO NOTHING;
END;
$$;
