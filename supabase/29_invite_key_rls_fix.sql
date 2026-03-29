-- Fix: invite_keys RLS vulnerability
-- Previously anon could read the entire invite_keys table (used=TRUE for all rows).
-- Now: direct table access is removed for anon; validation goes through a
-- SECURITY DEFINER function that returns only a boolean.

-- 1. Drop the overly broad SELECT policy
DROP POLICY IF EXISTS "invite_keys: anon can read" ON invite_keys;

-- 2. SECURITY DEFINER function — anon can call it but never touches the table directly.
--    Returns true only if the key exists AND has not been used yet.
CREATE OR REPLACE FUNCTION validate_invite_key(p_key text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM invite_keys
    WHERE key = p_key AND used = false
  );
$$;

GRANT EXECUTE ON FUNCTION validate_invite_key(text) TO anon, authenticated;

-- 3. Allow authenticated users to claim (mark as used) exactly their own key.
--    USING: the row must currently be unused.
--    WITH CHECK: they can only set used=true and used_by=their own uid.
CREATE POLICY "invite_keys: authenticated can claim unused key"
  ON invite_keys FOR UPDATE
  TO authenticated
  USING (used = false)
  WITH CHECK (used = true AND used_by = auth.uid());
