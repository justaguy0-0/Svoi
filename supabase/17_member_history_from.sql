-- Add history_from column to chat_members
-- NULL = see all history
-- timestamp = see only messages created at or after this timestamp

ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS history_from TIMESTAMPTZ;
