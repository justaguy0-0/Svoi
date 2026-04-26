package com.example.svoi.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actively probes whether the Supabase backend is reachable, independent of OS-level
 * internet connectivity. This is needed because in some regions the cellular connection
 * remains "up" at the OS level (NET_CAPABILITY_INTERNET = true) while specific services
 * are blocked at the network level (DPI / routing restrictions).
 *
 * Strategy: HTTP HEAD to /rest/v1/ with a 4-second timeout.
 * Any HTTP response (even 4xx) = reachable.
 * IOException / SocketTimeoutException = blocked.
 *
 * The result is cached for [PROBE_COOLDOWN_MS] to avoid hammering the probe endpoint.
 * Initial state is optimistic (true) so a fresh install works normally.
 */
class SupabaseReachabilityChecker(
    private val probeUrl: String,
    private val anonKey: String
) {

    companion object {
        private const val TAG = "SupabaseChecker"
        private const val PROBE_TIMEOUT_MS = 4_000
        private const val PROBE_COOLDOWN_MS = 30_000L
    }

    private val _isReachable = MutableStateFlow(true)

    /** True when Supabase is reachable. Starts optimistic (true). */
    val isReachable: StateFlow<Boolean> = _isReachable

    @Volatile private var lastProbeTime = 0L
    // Last time a real Supabase API call confirmed reachability (not the probe).
    // Used to ignore probe failures that arrive AFTER API calls already succeeded.
    @Volatile private var lastApiSuccessTime = 0L

    /**
     * Returns current reachability, running a fresh probe if the cached result is stale
     * (older than [PROBE_COOLDOWN_MS]) or if [force] = true.
     */
    suspend fun checkNow(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val cacheValid = !force && now - lastProbeTime < PROBE_COOLDOWN_MS && _isReachable.value
        if (cacheValid) return true
        return probe()
    }

    /**
     * Called by SvoiApp when the OS reports no network — immediately marks as unreachable
     * without spending a probe attempt.
     */
    fun markOffline() {
        _isReachable.value = false
        lastProbeTime = System.currentTimeMillis()
        Log.d(TAG, "markOffline: isReachable = false")
    }

    /**
     * Call this after a successful Supabase API response to skip the next probe and
     * immediately flip the state to reachable.
     */
    fun markReachable() {
        _isReachable.value = true
        lastProbeTime = System.currentTimeMillis()
        lastApiSuccessTime = System.currentTimeMillis()
        Log.d(TAG, "markReachable: isReachable = true (server confirmed)")
    }

    /**
     * Called when a Supabase API request times out. The probe may have reported reachable
     * (CDN/edge responds quickly), but the actual backend is too slow — mark as unreachable
     * and reset the cooldown so the next checkNow() runs a fresh probe immediately.
     */
    fun notifyTimeout() {
        if (!_isReachable.value) return  // already unreachable, avoid log spam
        _isReachable.value = false
        lastProbeTime = 0L  // force re-probe on next checkNow()
        Log.w(TAG, "notifyTimeout: API request timed out — marking unreachable, will re-probe on next check")
    }

    private suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "probe: checking $probeUrl ...")
        try {
            val conn = URL(probeUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = PROBE_TIMEOUT_MS
            conn.readTimeout = PROBE_TIMEOUT_MS
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("apikey", anonKey)
            conn.instanceFollowRedirects = false
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            // Any real HTTP response means the server is reachable (even 4xx errors)
            val reachable = code in 100..499
            _isReachable.value = reachable
            lastProbeTime = System.currentTimeMillis()
            Log.d(TAG, "probe: HTTP $code → reachable=$reachable")
            reachable
        } catch (e: Exception) {
            lastProbeTime = System.currentTimeMillis()
            // If a real API call confirmed reachability within the last 30s, the probe result
            // is stale (probe runs slower than actual requests on congested links). Trust the API.
            val sinceApiSuccess = System.currentTimeMillis() - lastApiSuccessTime
            if (sinceApiSuccess < 30_000L) {
                Log.d(TAG, "probe: failed but API confirmed reachable ${sinceApiSuccess}ms ago — ignoring probe failure")
                return@withContext true
            }
            _isReachable.value = false
            Log.w(TAG, "probe: failed (${e.javaClass.simpleName}: ${e.message}) → blocked")
            false
        }
    }
}
