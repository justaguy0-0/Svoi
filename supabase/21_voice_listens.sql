-- Voice listens: tracks which users have listened to which voice messages
-- Similar to message_reads but specifically for voice playback

CREATE TABLE IF NOT EXISTS voice_listens (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(message_id, user_id)
);

ALTER TABLE voice_listens ENABLE ROW LEVEL SECURITY;

-- Any chat member can see listens for messages in that chat
CREATE POLICY "voice_listens_select" ON voice_listens
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM messages m
            JOIN chat_members cm ON cm.chat_id = m.chat_id
            WHERE m.id = message_id
              AND cm.user_id = auth.uid()
              AND cm.left_at IS NULL
        )
    );

-- Users can only insert their own listens
CREATE POLICY "voice_listens_insert" ON voice_listens
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Enable Realtime for voice_listens
ALTER PUBLICATION supabase_realtime ADD TABLE voice_listens;
