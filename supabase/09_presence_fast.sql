-- =============================================================================
-- 09_presence_fast.sql
--
-- Fast presence detection: heartbeat every 3s, TTL = 10s.
-- Gives 3x safety margin (one missed heartbeat = still online).
--
-- How it works:
--   Online:  online=true AND last_seen < 10 seconds ago
--   Offline: online=false (explicit) OR last_seen >= 10 seconds ago (TTL/crash)
--
-- Latency:
--   Normal close / phone sleep: ~1s (explicit setOnline(false) via Realtime)
--   App crash / kill:           up to 15s (TTL 10s + observer poll 5s)
-- =============================================================================

CREATE OR REPLACE VIEW user_presence_view AS
SELECT
    user_id,
    online,
    last_seen,
    (online = true AND last_seen > NOW() - INTERVAL '10 seconds') AS is_truly_online
FROM user_presence;

GRANT SELECT ON user_presence_view TO authenticated;
