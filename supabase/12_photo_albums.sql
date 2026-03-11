-- Add support for album (multi-photo) messages

-- 1. Add photo_urls column (array of remote URLs for album messages)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS photo_urls TEXT[];

-- 2. Update the type CHECK constraint to include 'album'
ALTER TABLE messages
  DROP CONSTRAINT IF EXISTS messages_type_check;

ALTER TABLE messages
  ADD CONSTRAINT messages_type_check
  CHECK (type IN ('text', 'photo', 'file', 'system', 'album'));
