-- 30_hidden_from_search.sql
-- Adds the ability for users to hide their profile from search results.
-- The filtering is enforced server-side via a SECURITY DEFINER function,
-- so it cannot be bypassed by crafting direct API requests.

-- Step 1: Add column to profiles
ALTER TABLE profiles
  ADD COLUMN IF NOT EXISTS hidden_from_search BOOLEAN NOT NULL DEFAULT false;

-- Step 2: Replace the client-side search with a server-enforced RPC function.
-- SECURITY DEFINER means it runs as the function owner (bypassing RLS),
-- but we manually enforce hidden_from_search = false inside the body,
-- making it impossible to bypass regardless of client-side filters.
CREATE OR REPLACE FUNCTION search_users(query TEXT)
RETURNS SETOF profiles
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT *
  FROM profiles
  WHERE
    hidden_from_search = false
    AND id != auth.uid()
    AND display_name ILIKE '%' || query || '%'
  ORDER BY display_name
  LIMIT 20;
$$;

-- Grant execute to authenticated users only
REVOKE EXECUTE ON FUNCTION search_users(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION search_users(TEXT) TO authenticated;
