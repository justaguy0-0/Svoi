-- Add waveform_data column to messages for voice waveform visualization
-- Format: string of 40 digits 0-9, e.g. "3845671923487512836749201857630482915746"
-- Each digit represents normalized amplitude (0=silence, 9=max)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS waveform_data TEXT;
