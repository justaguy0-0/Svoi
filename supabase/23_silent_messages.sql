-- Add silent flag to messages (suppresses push notification)
ALTER TABLE messages ADD COLUMN silent BOOLEAN NOT NULL DEFAULT false;
