-- Add 'voice', 'album', 'video' to message type constraint + duration column

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_type_check;

ALTER TABLE messages
    ADD CONSTRAINT messages_type_check
    CHECK (type IN ('text', 'photo', 'file', 'system', 'album', 'video', 'voice'));

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS duration INT; -- voice message duration in seconds
