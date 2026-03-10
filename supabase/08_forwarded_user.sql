-- Add forwarded_from_user_id to messages for "Переслано от" display
ALTER TABLE messages ADD COLUMN IF NOT EXISTS forwarded_from_user_id UUID REFERENCES auth.users(id);
