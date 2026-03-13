-- Fix: make token the sole primary key so one device token can only belong to one user.
-- Step 1: remove duplicate tokens, keeping the most recently created row per token.
DELETE FROM push_tokens
WHERE ctid NOT IN (
    SELECT DISTINCT ON (token) ctid
    FROM push_tokens
    ORDER BY token, created_at DESC
);

-- Step 2: change primary key from (user_id, token) to (token).
ALTER TABLE push_tokens DROP CONSTRAINT push_tokens_pkey;
ALTER TABLE push_tokens ADD CONSTRAINT push_tokens_pkey PRIMARY KEY (token);

-- Step 3: keep fast lookup by user_id for the Edge Function's IN query.
CREATE INDEX IF NOT EXISTS push_tokens_user_id_idx ON push_tokens(user_id);
