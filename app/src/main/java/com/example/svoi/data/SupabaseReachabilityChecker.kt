package com.example.svoi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL

enum class ServerConnectionState {
    CHECKING,
    ONLINE,
    DEGRADED,
    OFFLINE
}

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
 * Initial state is pessimistic until the first probe or confirmed API success. This keeps
 * startup from launching a burst of Supabase requests before DNS is ready.
 */
class SupabaseReachabilityChecker(
    context: Context,
    private val probeUrl: String,
    private val anonKey: String
) {

    companion object {
        private const val TAG = "SupabaseChecker"
        private const val PROBE_TIMEOUT_MS = 4_000
        private const val PROBE_COOLDOWN_MS = 30_000L
        private const val FORCE_PROBE_MIN_INTERVAL_MS = 3_000L
        private const val OFFLINE_CONFIRMATION_DELAY_MS = 4_000L
        private const val STARTUP_WARMUP_MS = 12_000L
        private const val STARTUP_OFFLINE_CONFIRMATION_DELAY_MS = 15_000L
        private const val NETWORK_RETRY_DELAY_MS = 1_500L
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isReachable = MutableStateFlow(false)
    private val _connectionState = MutableStateFlow(ServerConnectionState.CHECKING)
    private val _shouldShowOfflineBanner = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var offlineConfirmationJob: Job? = null
    private val probeMutex = Mutex()

    /** True when Supabase is reachable. Starts false until probe/API confirms it. */
    val isReachable: StateFlow<Boolean> = _isReachable
    val connectionState: StateFlow<ServerConnectionState> = _connectionState
    val shouldShowOfflineBanner: StateFlow<Boolean> = _shouldShowOfflineBanner

    @Volatile private var lastProbeTime = 0L
    @Volatile private var isAppInForeground = true
    @Volatile private var onlineGeneration = 0L
    @Volatile private var foregroundStartedAtMs = System.currentTimeMillis()
    @Volatile private var networkRetryJob: Job? = null
    // Last time a real Supabase API call confirmed reachability (not the probe).
    // Used to ignore probe failures that arrive AFTER API calls already succeeded.
    @Volatile private var lastApiSuccessTime = 0L

    /**
     * Returns current reachability, running a fresh probe if the cached result is stale
     * (older than [PROBE_COOLDOWN_MS]) or if [force] = true.
     */
    suspend fun checkNow(force: Boolean = false): Boolean {
        if (!isAppInForeground) {
            Log.d(TAG, "checkNow: skipped because app background")
            return _isReachable.value
        }
        if (probeMutex.isLocked) {
            Log.d(TAG, "probe skipped, already in flight")
            return _isReachable.value
        }
        return probeMutex.withLock {
            if (!isAppInForeground) {
                Log.d(TAG, "checkNow: skipped because app background")
                return@withLock _isReachable.value
            }
            val now = System.currentTimeMillis()
            if (_isReachable.value && now - lastProbeTime < FORCE_PROBE_MIN_INTERVAL_MS) {
                Log.d(TAG, "probe skipped, cooldown")
                return@withLock true
            }
            val cacheValid = !force && now - lastProbeTime < PROBE_COOLDOWN_MS && _isReachable.value
            if (cacheValid) return@withLock true
            if (shouldWaitForValidatedNetwork()) {
                val readiness = networkReadiness()
                Log.d(TAG, "waiting for validated network (internet=${readiness.hasInternet}, validated=${readiness.isValidated})")
                _isReachable.value = false
                setConnectionState(ServerConnectionState.CHECKING)
                scheduleNetworkRetry()
                scheduleOfflineConfirmation("network not validated during startup")
                return@withLock false
            }
            if (force && !_isReachable.value) {
                offlineConfirmationJob?.cancel()
                setConnectionState(ServerConnectionState.CHECKING)
            }
            probe()
        }
    }

    /**
     * Called by SvoiApp when the OS reports no network. Immediately marks as unreachable
     * without spending a probe attempt.
     */
    fun markOffline() {
        if (!isAppInForeground) {
            _isReachable.value = false
            setConnectionState(ServerConnectionState.DEGRADED)
            lastProbeTime = System.currentTimeMillis()
            Log.d(TAG, "markOffline: app background, banner suppressed")
            return
        }
        if (isStartupWarmupActive()) {
            _isReachable.value = false
            setConnectionState(ServerConnectionState.CHECKING)
            lastProbeTime = System.currentTimeMillis()
            scheduleOfflineConfirmation("OS network unavailable during startup")
            Log.d(TAG, "startup warmup active: suppressing OS offline")
            return
        }
        _isReachable.value = false
        setConnectionState(ServerConnectionState.OFFLINE)
        lastProbeTime = System.currentTimeMillis()
        Log.d(TAG, "markOffline: isReachable = false")
    }

    /**
     * Call this after a successful Supabase API response to skip the next probe and
     * immediately flip the state to reachable.
     */
    fun markReachable(reason: String = "api") {
        if (offlineConfirmationJob != null) {
            Log.d(TAG, "offline debounce cancelled by $reason")
        }
        offlineConfirmationJob?.cancel()
        offlineConfirmationJob = null
        networkRetryJob?.cancel()
        networkRetryJob = null
        onlineGeneration++
        _isReachable.value = true
        setConnectionState(ServerConnectionState.ONLINE)
        lastProbeTime = System.currentTimeMillis()
        lastApiSuccessTime = System.currentTimeMillis()
        Log.d(TAG, "markReachable reason=$reason")
    }

    fun markRealtimeConnected() {
        markReachable(reason = "realtime connected")
    }

    /**
     * Called when a Supabase API request times out. The probe may have reported reachable
     * (CDN/edge responds quickly), but the actual backend is too slow. Mark as unreachable
     * and reset the cooldown so the next checkNow() runs a fresh probe immediately.
     */
    fun notifyTimeout() {
        markTemporarilyUnreachable("API request timed out")
    }

    fun notifyNetworkFailure(error: Throwable) {
        if (!isTransientNetworkFailure(error)) return
        if (!isAppInForeground) {
            Log.d(TAG, "ignoring transient failure while background (${error.javaClass.simpleName}: ${error.message})")
            return
        }
        markTemporarilyUnreachable("${error.javaClass.simpleName}: ${error.message}")
    }

    fun setAppInForeground(foreground: Boolean) {
        isAppInForeground = foreground
        if (!foreground) {
            offlineConfirmationJob?.cancel()
            offlineConfirmationJob = null
            networkRetryJob?.cancel()
            networkRetryJob = null
            _shouldShowOfflineBanner.value = false
            Log.d(TAG, "offline debounce cancelled because app background")
        } else if (!_isReachable.value) {
            foregroundStartedAtMs = System.currentTimeMillis()
            onlineGeneration++
            offlineConfirmationJob?.cancel()
            offlineConfirmationJob = null
            setConnectionState(ServerConnectionState.CHECKING)
            Log.d(TAG, "startup warmup active for ${STARTUP_WARMUP_MS}ms")
        }
    }

    fun isTransientNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is UnknownHostException || current is SocketTimeoutException) return true
            val name = current.javaClass.simpleName
            val message = current.message.orEmpty()
            if (name.contains("Timeout", ignoreCase = true) ||
                name.contains("UnknownHost", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("No address associated with hostname", ignoreCase = true) ||
                message.contains("Software caused connection abort", ignoreCase = true) ||
                message.contains("connection abort", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ||
                message.contains("stream was reset", ignoreCase = true) ||
                message.contains("failed to connect", ignoreCase = true) ||
                message.contains("network is unreachable", ignoreCase = true)
            ) return true
            current = current.cause
        }
        return false
    }

    private fun markTemporarilyUnreachable(reason: String) {
        if (!isAppInForeground) {
            Log.d(TAG, "ignoring transient failure while background ($reason)")
            return
        }
        if (_connectionState.value == ServerConnectionState.OFFLINE) return
        if (isStartupWarmupActive()) {
            _isReachable.value = false
            setConnectionState(ServerConnectionState.CHECKING)
            lastProbeTime = 0L
            scheduleNetworkRetry()
            scheduleOfflineConfirmation(reason)
            Log.d(TAG, "startup warmup: ignoring transient DNS failure ($reason)")
            return
        }
        _isReachable.value = false
        setConnectionState(ServerConnectionState.DEGRADED)
        lastProbeTime = 0L  // force re-probe on next checkNow()
        scheduleOfflineConfirmation(reason)
        Log.w(TAG, "temporary network failure ($reason) - connection degraded")
    }

    private fun scheduleOfflineConfirmation(reason: String) {
        if (offlineConfirmationJob?.isActive == true) return
        val generation = onlineGeneration
        val delayMs = if (isStartupWarmupActive()) {
            STARTUP_OFFLINE_CONFIRMATION_DELAY_MS
        } else {
            OFFLINE_CONFIRMATION_DELAY_MS
        }
        if (delayMs == STARTUP_OFFLINE_CONFIRMATION_DELAY_MS) {
            Log.d(TAG, "startup offline debounce scheduled ${STARTUP_OFFLINE_CONFIRMATION_DELAY_MS}ms")
        }
        offlineConfirmationJob = scope.launch {
            delay(delayMs)
            if (!isAppInForeground) {
                Log.d(TAG, "offline debounce cancelled because app background")
                return@launch
            }
            if (isStartupWarmupActive()) {
                Log.d(TAG, "offline debounce cancelled because startup warmup still active")
                return@launch
            }
            if (generation != onlineGeneration) {
                Log.d(TAG, "offline debounce cancelled because online generation changed")
                return@launch
            }
            val readiness = networkReadiness()
            val canConfirmOffline =
                _connectionState.value == ServerConnectionState.DEGRADED ||
                    !readiness.hasInternet ||
                    !readiness.isValidated
            if (!_isReachable.value && canConfirmOffline) {
                setConnectionState(ServerConnectionState.OFFLINE)
                Log.w(TAG, "offline confirmed after debounce ($reason)")
            }
        }
    }

    private fun setConnectionState(state: ServerConnectionState) {
        _connectionState.value = state
        _shouldShowOfflineBanner.value = state == ServerConnectionState.OFFLINE
    }

    fun isStartupNetworkUnstable(): Boolean {
        val readiness = networkReadiness()
        return isAppInForeground &&
            !_isReachable.value &&
            (isStartupWarmupActive() || _connectionState.value == ServerConnectionState.CHECKING) &&
            (!readiness.isValidated || !readiness.hasInternet)
    }

    private fun isStartupWarmupActive(): Boolean =
        isAppInForeground && System.currentTimeMillis() - foregroundStartedAtMs < STARTUP_WARMUP_MS

    private fun shouldWaitForValidatedNetwork(): Boolean {
        if (!isStartupWarmupActive()) return false
        val readiness = networkReadiness()
        return !readiness.hasInternet || !readiness.isValidated
    }

    private fun scheduleNetworkRetry() {
        if (networkRetryJob?.isActive == true) return
        networkRetryJob = scope.launch {
            delay(NETWORK_RETRY_DELAY_MS)
            if (isAppInForeground && !_isReachable.value) {
                checkNow(force = true)
            }
        }
    }

    private data class NetworkReadiness(
        val hasInternet: Boolean,
        val isValidated: Boolean
    )

    private fun networkReadiness(): NetworkReadiness {
        val network = connectivityManager.activeNetwork ?: return NetworkReadiness(
            hasInternet = false,
            isValidated = false
        )
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkReadiness(
            hasInternet = false,
            isValidated = false
        )
        return NetworkReadiness(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
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
            // Any real HTTP response means the server is reachable (even 4xx errors).
            val reachable = code in 100..499
            lastProbeTime = System.currentTimeMillis()
            if (reachable) {
                markReachable(reason = "probe/http")
            } else {
                markTemporarilyUnreachable("probe HTTP $code")
            }
            Log.d(TAG, "probe: HTTP $code -> reachable=$reachable")
            reachable
        } catch (e: Exception) {
            lastProbeTime = System.currentTimeMillis()
            // If a real API call confirmed reachability within the last 30s, the probe result
            // is stale (probe runs slower than actual requests on congested links). Trust the API.
            val sinceApiSuccess = System.currentTimeMillis() - lastApiSuccessTime
            if (sinceApiSuccess < 30_000L) {
                Log.d(TAG, "probe: failed but API confirmed reachable ${sinceApiSuccess}ms ago - ignoring probe failure")
                return@withContext true
            }
            markTemporarilyUnreachable("${e.javaClass.simpleName}: ${e.message}")
            Log.w(TAG, "probe: failed (${e.javaClass.simpleName}: ${e.message}) -> degraded")
            false
        }
    }
}
