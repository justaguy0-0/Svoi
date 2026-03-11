-- Add mime_type column and video message type support

ALTER TABLE messages ADD COLUMN IF NOT EXISTS mime_type TEXT;

ALTER TABLE messages
  DROP CONSTRAINT IF EXISTS messages_type_check;

ALTER TABLE messages
  ADD CONSTRAINT messages_type_check
  CHECK (type IN ('text', 'photo', 'file', 'system', 'album', 'video'));
