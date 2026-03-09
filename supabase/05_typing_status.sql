-- Typing status table for "User is typing..." indicators
-- Run this in Supabase Dashboard → SQL Editor

CREATE TABLE IF NOT EXISTS typing_status (
    chat_id UUID NOT NULL,
    user_id UUID NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (chat_id, user_id)
);

ALTER TABLE typing_status ENABLE ROW LEVEL SECURITY;

-- Members of a chat can see who's typing
CREATE POLICY "typing_select" ON typing_status FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM chat_members
            WHERE chat_id = typing_status.chat_id AND user_id = auth.uid()
        )
    );

CREATE POLICY "typing_insert" ON typing_status FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "typing_update" ON typing_status FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "typing_delete" ON typing_status FOR DELETE
    USING (auth.uid() = user_id);
