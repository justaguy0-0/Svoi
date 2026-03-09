-- =============================================================================
-- 06_server_time.sql
--
-- Fixes clock-skew problems between devices (emulator, different timezones).
-- All presence timestamps are now written and compared using server time only.
-- =============================================================================

-- 1. Trigger: overwrite last_seen with server NOW() on every insert/update.
--    Client-provided value is ignored — the server is always the source of truth.
CREATE OR REPLACE FUNCTION fn_set_presence_server_time()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.last_seen = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trig_presence_server_time ON user_presence;
CREATE TRIGGER trig_presence_server_time
    BEFORE INSERT OR UPDATE ON user_presence
    FOR EACH ROW EXECUTE FUNCTION fn_set_presence_server_time();

-- 2. View: adds server-computed is_truly_online.
--    Comparison uses DB NOW() so it's immune to client clock skew.
CREATE OR REPLACE VIEW user_presence_view AS
SELECT
    user_id,
    online,
    last_seen,
    (online = true AND last_seen > NOW() - INTERVAL '60 seconds') AS is_truly_online
FROM user_presence;

GRANT SELECT ON user_presence_view TO authenticated;
