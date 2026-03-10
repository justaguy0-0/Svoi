-- =============================================================================
-- 09_presence_fast.sql
--
-- Fast presence detection: heartbeat every 5s, TTL = 15s.
-- Gives 3x safety margin (one missed heartbeat = still online).
--
-- How it works:
--   Online:  online=true AND last_seen < 15 seconds ago
--   Offline: online=false (explicit) OR last_seen >= 15 seconds ago (TTL/crash)
--
-- Latency:
--   Normal close / phone sleep: ~1s (explicit setOnline(false) via Realtime)
--   App crash / kill:           up to 25s (TTL 15s + observer poll 10s)
-- =============================================================================

CREATE OR REPLACE VIEW user_presence_view AS
SELECT
    user_id,
    online,
    last_seen,
    (online = true AND last_seen > NOW() - INTERVAL '15 seconds') AS is_truly_online
FROM user_presence;

GRANT SELECT ON user_presence_view TO authenticated;
