-- Add status column to typing_status
-- 'typing' = user is typing
-- 'uploading_media' = user is uploading photo/video
ALTER TABLE typing_status
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'typing';
