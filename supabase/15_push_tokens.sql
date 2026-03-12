-- FCM push tokens for each device
CREATE TABLE IF NOT EXISTS push_tokens (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token   TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, token)
);

-- Only the owner can read/write their tokens
ALTER TABLE push_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own tokens"
    ON push_tokens FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Edge Function needs service role to read tokens — no extra policy needed
-- (service role bypasses RLS)
