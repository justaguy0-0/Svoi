-- Message reactions: emoji reactions on messages (like Telegram)

CREATE TABLE IF NOT EXISTS message_reactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(message_id, user_id, emoji)
);

CREATE INDEX idx_message_reactions_message_id ON message_reactions(message_id);

ALTER TABLE message_reactions ENABLE ROW LEVEL SECURITY;

-- Any chat member can see reactions
CREATE POLICY "message_reactions_select" ON message_reactions
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM messages m
            JOIN chat_members cm ON cm.chat_id = m.chat_id
            WHERE m.id = message_id
              AND cm.user_id = auth.uid()
              AND cm.left_at IS NULL
        )
    );

-- Users can only insert their own reactions
CREATE POLICY "message_reactions_insert" ON message_reactions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Users can only delete their own reactions
CREATE POLICY "message_reactions_delete" ON message_reactions
    FOR DELETE USING (auth.uid() = user_id);

-- Enable Realtime
ALTER PUBLICATION supabase_realtime ADD TABLE message_reactions;
