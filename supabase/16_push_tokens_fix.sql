-- Fix: make token the sole primary key so one device token can only belong to one user.
-- Previously PRIMARY KEY (user_id, token) allowed the same physical device token
-- to be stored for multiple accounts simultaneously, causing double notifications.
-- Now upsert on token will automatically reassign ownership to the new user.

ALTER TABLE push_tokens DROP CONSTRAINT push_tokens_pkey;
ALTER TABLE push_tokens ADD CONSTRAINT push_tokens_pkey PRIMARY KEY (token);

-- Keep fast lookup by user_id for the Edge Function's IN query
CREATE INDEX IF NOT EXISTS push_tokens_user_id_idx ON push_tokens(user_id);
