-- =============================================================================
-- 08_presence_ttl.sql
--
-- Increases presence TTL from 60s to 90s.
-- Heartbeat fires every 30s, so 90s gives a 3x safety margin.
-- This prevents false "offline" flickers when one heartbeat is delayed.
-- =============================================================================

CREATE OR REPLACE VIEW user_presence_view AS
SELECT
    user_id,
    online,
    last_seen,
    (online = true AND last_seen > NOW() - INTERVAL '90 seconds') AS is_truly_online
FROM user_presence;

GRANT SELECT ON user_presence_view TO authenticated;
