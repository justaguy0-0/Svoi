-- Add mentioned_user_ids column to messages
-- Stores UUIDs of users explicitly @-mentioned in the message.
-- Used by the Edge Function to send push notifications bypassing mute settings.
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS mentioned_user_ids UUID[] NOT NULL DEFAULT '{}';
