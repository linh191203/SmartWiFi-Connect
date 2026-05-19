package com.smartwificonnect.wifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Connects the device to a Wi-Fi network using a hybrid strategy:
 *
 * 1) Adds the network as a [WifiNetworkSuggestion] so the OS can auto-connect
 *    in the future without any user interaction (after one-time approval).
 *    This persists across app restarts and reboots.
 *
 * 2) For the immediate session, requests a [WifiNetworkSpecifier] connection,
 *    which shows the system "Connect to ___?" dialog. User taps Connect once.
 *
 * 3) Skips step 2 entirely if the device is already on the requested SSID.
 *
 * Important: we DO NOT call [ConnectivityManager.bindProcessToNetwork] on the
 * resulting network. On Vivo Android 12 (FuntouchOS/OriginOS) that triggers an
 * "Đã xảy ra lỗi. Ứng dụng đã hủy yêu cầu chọn thiết bị" system dialog and
 * causes the WiFi connection to drop 2-3 seconds later.
 */
class WifiConnector(
    context: Context,
    private val createSpecifier: (ssid: String, password: String?, security: String?) -> WifiNetworkSpecifier = ::buildWifiSpecifier,
    private val createRequest: (WifiNetworkSpecifier) -> NetworkRequest = ::buildWifiNetworkRequest,
    private val internetValidationGraceMillis: Long = 3_000L,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    /**
     * Callback that succeeded in connecting to a WiFi. We deliberately keep it
     * registered so the OS does not drop the WiFi when the user navigates away
     * from the screen or the ViewModel is cleared. It is only released when:
     *  - the user explicitly disconnects via [disconnect],
     *  - a new connect attempt to a different SSID supersedes it.
     */
    private var keepAliveCallback: ConnectivityManager.NetworkCallback? = null
    private var keepAliveSsid: String? = null
    private var trackedNetwork: Network? = null
    /** Monotonically increasing session ID. Each connectViaSpecifier() increments
     *  this. Callbacks captured with a stale session are ignored. */
    private var currentSessionId: Long = 0L

    /**
     * Vivo Android 12 (FuntouchOS/OriginOS) has a firmware bug where ANY
     * use of WifiNetworkSpecifier triggers the system error dialog
     * "Đã xảy ra lỗi. Ứng dụng đã hủy yêu cầu chọn thiết bị" — even on
     * normal completion. The only reliable fix is to skip Specifier entirely
     * on Vivo and use Suggestion + WifiManager.disconnect() instead.
     */
    private val isVivoDevice: Boolean = Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
        Build.BRAND.equals("vivo", ignoreCase = true) ||
        Build.MANUFACTURER.equals("iqoo", ignoreCase = true)

    suspend fun connect(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiConnectResult {
        val normalizedSsid = ssid.trim()
        val normalizedPassword = password?.trim().orEmpty()

        // ── Connection request log ──
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "▶ WiFi CONNECT REQUEST")
        Log.d(TAG, "  Android version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        Log.d(TAG, "  Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "  SSID: '$normalizedSsid'")
        Log.d(TAG, "  Password length: ${normalizedPassword.length}")
        Log.d(TAG, "  Security: ${security ?: "<auto>"}")
        logPermissionStatus()
        logCurrentWifiState()
        Log.d(TAG, "════════════════════════════════════════════════════════════")

        // ── Validate inputs (cheap checks first) ──
        if (normalizedSsid.isEmpty()) {
            Log.e(TAG, "✗ SSID empty")
            return WifiConnectResult.Failed(WifiConnectFailureReason.INVALID_INPUT, "SSID is empty")
        }
        validateSecurityMode(security)?.let {
            Log.e(TAG, "✗ Unsupported security: $it")
            return WifiConnectResult.Failed(WifiConnectFailureReason.INVALID_INPUT, it)
        }
        if (normalizedPassword.isNotEmpty() && normalizedPassword.length !in 8..63) {
            Log.e(TAG, "✗ Password length invalid: ${normalizedPassword.length}")
            return WifiConnectResult.Failed(
                WifiConnectFailureReason.INVALID_INPUT,
                "Mật khẩu WPA/WPA2 phải có từ 8 đến 63 ký tự.",
            )
        }
        if (normalizedPassword.isNotEmpty() && !normalizedPassword.isPrintableAsciiPassphrase()) {
            Log.e(TAG, "✗ Password contains non-ASCII characters")
            return WifiConnectResult.Failed(
                WifiConnectFailureReason.INVALID_INPUT,
                "Mật khẩu có ký tự không được Android hỗ trợ. Vui lòng kiểm tra lại.",
            )
        }

        // ── Step 1: Add as Suggestion (for future auto-connect) ──
        // Best-effort, non-blocking. Failures here don't abort the main flow.
        addSuggestionForFutureAutoConnect(
            normalizedSsid,
            normalizedPassword.takeIf { it.isNotEmpty() },
            security,
        )

        // ── Step 2a: Skip if already kept-alive for the same SSID AND
        //              the device is REALLY on that SSID right now ──
        // Without the actual-SSID check, we'd return a stale Success when the
        // OS has rolled back to a different network (e.g. previous saved WiFi)
        // even though our keep-alive cache still says SSID B.
        if (keepAliveSsid != null && keepAliveSsid.equals(normalizedSsid, ignoreCase = true)) {
            val deviceCurrentSsid = getActualConnectedSsid()
            val deviceMatches = deviceCurrentSsid != null &&
                deviceCurrentSsid.equals(normalizedSsid, ignoreCase = true)
            if (deviceMatches) {
                Log.d(TAG, "  ✓ Already keep-alive on '$normalizedSsid' AND device confirms — skipping reconnect")
                return WifiConnectResult.Success(network = trackedNetwork, ssid = normalizedSsid)
            } else {
                Log.w(TAG, "  ⚠ Keep-alive cache says '$normalizedSsid' but device is on '$deviceCurrentSsid' — clearing stale cache and reconnecting")
                // Drop the stale cache so we re-attempt the real connect.
                keepAliveSsid = null
                trackedNetwork = null
                // Don't unregister keepAliveCallback here (Vivo dialog risk);
                // leave it dangling and let the new connect supersede it.
                keepAliveCallback = null
            }
        }

        // ── Step 2b: Skip Specifier if device is already on target SSID ──
        val currentSsid = getActualConnectedSsid()
        val hasDifferentKeepAlive =
            keepAliveSsid != null && !keepAliveSsid.equals(normalizedSsid, ignoreCase = true)
        val hasPendingSpecifier = activeCallback != null
        if (currentSsid != null && currentSsid.equals(normalizedSsid, ignoreCase = true)) {
            if (hasDifferentKeepAlive || hasPendingSpecifier) {
                Log.d(
                    TAG,
                    "  ▸ OS already reports '$normalizedSsid', but app state still points to keepAlive='$keepAliveSsid' / active=$hasPendingSpecifier — releasing stale app request",
                )
                releaseForNetworkSwitch()
                keepAliveSsid = normalizedSsid
                trackedNetwork = null
            }
            Log.d(TAG, "  ✓ Device already connected to '$normalizedSsid' — skipping Specifier")
            return WifiConnectResult.Success(normalizedSsid)
        }

        // ── User wants to switch from currentSsid → normalizedSsid ──
        // Honor user choice: clear all prior state (old callback / request /
        // tracked network / keep-alive) BEFORE any silent-auto-connect poll
        // so a stronger nearby WiFi cannot opportunistically take over.
        //
        // Treat any of these as "switching from another network":
        //   - currentSsid is non-null AND different from target (the obvious case)
        //   - keepAliveSsid is non-null (we own a prior session, even if OS
        //     hides SSID via "<unknown ssid>")
        //   - WiFi is enabled and there's an existing connection state we should
        //     wipe (defensive fallback for OEMs that mask the SSID)
        val isSwitchingNetwork =
            (currentSsid != null && !currentSsid.equals(normalizedSsid, ignoreCase = true)) ||
                hasDifferentKeepAlive ||
                hasPendingSpecifier
        if (isSwitchingNetwork || keepAliveSsid != null) {
            // ── Detailed switching debug snapshot per UX requirements ──
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            Log.d(TAG, "▶ NETWORK SWITCH — clearing prior state before new attempt")
            Log.d(TAG, "  Current OS SSID (before): '$currentSsid'")
            Log.d(TAG, "  Target SSID (user pick):  '$normalizedSsid'")
            Log.d(TAG, "  Prior keep-alive SSID:    '$keepAliveSsid'")
            Log.d(TAG, "  Prior active callback:    ${activeCallback != null}")
            Log.d(TAG, "  Prior keep-alive callback:${keepAliveCallback != null}")
            Log.d(TAG, "  Prior tracked network:    $trackedNetwork")
            Log.d(TAG, "  Session ID (before):      $currentSessionId")
            releaseForNetworkSwitch()
            // After releaseForNetworkSwitch(), session is incremented and
            // app-side callbacks/state are cleared. Log post-state.
            Log.d(TAG, "  ✓ Cleared old callback/request/keep-alive/state")
            Log.d(TAG, "  Session ID (after):       $currentSessionId")
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            // Give the OS a small window to actually drop the prior network
            // before we issue requestNetwork() for the new SSID. Without this,
            // on Vivo/Xiaomi the next callback may briefly fire with the OLD
            // network, causing a false "Success" report against WiFi A while
            // the user wanted WiFi B.
            kotlinx.coroutines.delay(400)
        }

        // ── Step 2c: Try Suggestion-based switch (no dialog, Vivo-safe) ──
        // After adding the suggestion for WiFi B and disconnecting WiFi A,
        // the OS should auto-associate to B within ~3s. This avoids the
        // WifiNetworkSpecifier entirely — which is critical on Vivo where
        // Specifier triggers the "Ứng dụng đã hủy yêu cầu" error dialog.
        //
        // Strategy: call WifiManager.disconnect() to break current association,
        // then wait event-driven for the OS to pick up our suggestion for
        // the new SSID.
        if (isSwitchingNetwork) {
            Log.d(TAG, "  ▸ Switching: calling WifiManager.disconnect() to break current association")
            @Suppress("DEPRECATION")
            runCatching { wifiManager?.disconnect() }
            // Vivo needs longer (suggestion can take 6-8s on first time after
            // disconnect). Event-driven wait so we resume the instant the
            // status-bar WiFi icon flips.
            val maxWaitMillis = if (isVivoDevice) 8_000L else 4_000L
            Log.d(TAG, "  ▸ Awaiting suggestion-based switch to '$normalizedSsid' (max ${maxWaitMillis}ms)...")
            val joined = awaitSsidAssociation(normalizedSsid, maxWaitMillis)
            if (joined != null) {
                Log.d(TAG, "  ✓ Suggestion-based switch succeeded — now on '$normalizedSsid' (no Specifier needed)")
                keepAliveSsid = normalizedSsid
                // Device is associated to the correct SSID. Skip HTTP internet
                // check — DNS/routing takes 1-3s to stabilize on real devices.
                // Checking too early causes false "connected but no internet".
                // The OS will validate internet on its own; user sees WiFi icon.
                return WifiConnectResult.Success(normalizedSsid)
            }
            Log.d(TAG, "  ▸ Suggestion-based switch did not happen in ${maxWaitMillis}ms — falling through")
        } else {
            // Not switching — try silent auto-connect via Suggestion (event-driven 2-4s)
            val maxWaitMillis = if (isVivoDevice) 4_000L else 2_000L
            Log.d(TAG, "  ▸ Awaiting silent auto-connect via Suggestion (max ${maxWaitMillis}ms)...")
            val joined = awaitSsidAssociation(normalizedSsid, maxWaitMillis)
            if (joined != null) {
                Log.d(TAG, "  ✓ Silent auto-connect succeeded via Suggestion — no dialog shown")
                keepAliveSsid = normalizedSsid
                return WifiConnectResult.Success(normalizedSsid)
            }
            Log.d(TAG, "  ▸ Silent auto-connect did not happen — falling through to event-driven long wait")
        }

        // ── Step 3: Connect via the right path for THIS device ──
        //
        // Two paths exist and we pick based on manufacturer:
        //
        // A) Specifier path — WifiNetworkSpecifier + ConnectivityManager.
        //    Standard Android API. Shows the system "Connect to '<SSID>'?"
        //    dialog ONCE (Android-required for 3rd-party apps); user taps
        //    Connect, OS associates, callback fires Success. This is the
        //    correct, standards-compliant flow on AOSP / Pixel / Samsung /
        //    Xiaomi / OPPO / OnePlus etc.
        //
        // B) Suggestion-only path — for Vivo / iQOO where Specifier triggers
        //    the OEM error dialog "Đã xảy ra lỗi. Ứng dụng đã hủy yêu cầu
        //    chọn thiết bị". On those devices we rely on the suggestion
        //    we added in Step 1 plus an event-driven wait, and ask the user
        //    to confirm via the system Wi-Fi panel as a last resort.
        //
        // Picking the right path is what makes "Connecting..." actually
        // turn into a real connection — Specifier on AOSP-class devices
        // is the only API that can SWITCH from Wi-Fi A to Wi-Fi B without
        // forcing user to leave the app.
        if (isVivoDevice) {
            // ── Vivo path: Suggestion only, event-driven ──
            val finalTimeoutMillis = 20_000L
            Log.d(
                TAG,
                "  ▸ Vivo device — Suggestion-only path (event-driven wait for '$normalizedSsid', timeout ${finalTimeoutMillis}ms)",
            )
            val joinedSsid = awaitSsidAssociation(
                targetSsid = normalizedSsid,
                timeoutMillis = finalTimeoutMillis,
            )
            if (joinedSsid != null) {
                keepAliveSsid = normalizedSsid
                return WifiConnectResult.Success(normalizedSsid)
            }
            Log.w(TAG, "  ⚠ Vivo: Suggestion did not land within ${finalTimeoutMillis}ms")
            return WifiConnectResult.Failed(
                reason = WifiConnectFailureReason.TIMEOUT,
                message = "Đang chờ hệ thống kết nối tới '$normalizedSsid'...",
            )
        }

        // ── Non-Vivo: Specifier path (standard, works on AOSP/Samsung/Xiaomi/OPPO) ──
        Log.d(TAG, "  ▸ Non-Vivo device — using WifiNetworkSpecifier (standard Android dialog)")
        Log.d(TAG, "  ▸ Releasing all prior WiFi state before connecting to '$normalizedSsid'")
        releaseAll()
        return connectViaSpecifier(normalizedSsid, normalizedPassword, security)
    }

    /**
     * Opens the system Wi-Fi quick panel (Settings.Panel.ACTION_WIFI on
     * Android 10+, falls back to ACTION_WIFI_SETTINGS on older versions).
     *
     * NOTE: kept for emergency / debug use, currently NOT invoked from
     * the connect flow — the standard Specifier dialog is preferred so the
     * user never has to leave the app.
     *
     * @return true if an Activity was successfully started, false otherwise.
     */
    private fun openSystemWifiPanel(): Boolean = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            @Suppress("DEPRECATION")
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        Log.d(TAG, "  ✓ Opened system Wi-Fi panel — user can tap target SSID")
        true
    }.getOrElse { error ->
        Log.w(TAG, "  ⚠ Failed to open system Wi-Fi panel: ${error.message}")
        false
    }

    /**
     * Event-driven wait for the device to associate with [targetSsid].
     *
     * The previous implementation polled `getActualConnectedSsid()` every 500ms.
     * That added up to 250ms average lag between "OS shows WiFi icon" and
     * "app reports connected". Worst case ~500ms.
     *
     * This version registers a NetworkCallback on TRANSPORT_WIFI and resumes
     * the coroutine the instant `onAvailable` fires for a network whose SSID
     * matches the target. Polling is kept as a 250ms safety net (some OEMs
     * deliver onCapabilitiesChanged but skip onAvailable on resumed sessions).
     *
     * Importantly we do NOT use WifiNetworkSpecifier here — we register a
     * plain "any WiFi" callback. No system dialog is triggered.
     *
     * @param onPanelHint optional callback invoked once when [panelHintAfterMillis]
     * has elapsed without association — used to nudge the user with the system
     * Wi-Fi panel when switching networks.
     * @return the actual SSID joined, or null on timeout.
     */
    private suspend fun awaitSsidAssociation(
        targetSsid: String,
        timeoutMillis: Long,
        panelHintAfterMillis: Long = Long.MAX_VALUE,
        onPanelHint: (() -> Unit)? = null,
    ): String? = coroutineScope {
        val manager = connectivityManager ?: return@coroutineScope null
        val startedAt = System.currentTimeMillis()

        // Fast pre-check: maybe we already are on the target SSID.
        getActualConnectedSsid()?.let { current ->
            if (current.equals(targetSsid, ignoreCase = true)) {
                Log.d(TAG, "  ✓ awaitSsidAssociation: already on '$current' (0ms)")
                return@coroutineScope current
            }
        }

        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<String?> { continuation ->
            var resolved = false
            var pollJob: Job? = null
            var hintJob: Job? = null
            var observerCallback: ConnectivityManager.NetworkCallback? = null

            fun cleanup() {
                pollJob?.cancel()
                hintJob?.cancel()
                observerCallback?.let { cb ->
                    runCatching { manager.unregisterNetworkCallback(cb) }
                }
            }

            fun finishWith(ssid: String?) {
                if (resolved) return
                resolved = true
                cleanup()
                if (continuation.isActive) continuation.resume(ssid)
            }

            fun checkAndMaybeFinish(source: String) {
                if (resolved) return
                val current = getActualConnectedSsid()
                if (current != null && current.equals(targetSsid, ignoreCase = true)) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    Log.d(TAG, "  ✓ awaitSsidAssociation: '$current' confirmed via $source (${elapsed}ms)")
                    finishWith(current)
                }
            }

            // ── 1) Event-driven: any WiFi network change wakes us ──
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    checkAndMaybeFinish("onAvailable")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    val info = networkCapabilities.transportInfo as? WifiInfo
                    val ssid = info?.ssid?.normalizeWifiSsid()
                    if (ssid != null && ssid.equals(targetSsid, ignoreCase = true)) {
                        val elapsed = System.currentTimeMillis() - startedAt
                        Log.d(TAG, "  ✓ awaitSsidAssociation: '$ssid' confirmed via onCapabilitiesChanged (${elapsed}ms)")
                        finishWith(ssid)
                    } else {
                        checkAndMaybeFinish("onCapabilitiesChanged")
                    }
                }
            }
            observerCallback = cb
            try {
                val observerRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                manager.registerNetworkCallback(observerRequest, cb)
            } catch (t: Throwable) {
                Log.w(TAG, "  ⚠ Failed to register observer callback (will rely on polling): ${t.message}")
                observerCallback = null
            }

            // ── 2) Backup polling: 250ms cadence ──
            // Half the previous interval so worst-case lag is ~250ms instead
            // of ~500ms when the callback path misses an event.
            pollJob = launch {
                // Periodic status logs (per user requirement: log SSID at 1s/3s/5s).
                val statusMilestones = mutableSetOf(1_000L, 3_000L, 5_000L, 10_000L)
                while (!resolved) {
                    delay(250)
                    if (resolved) return@launch
                    val elapsed = System.currentTimeMillis() - startedAt
                    val crossed = statusMilestones.firstOrNull { it <= elapsed }
                    if (crossed != null) {
                        statusMilestones.remove(crossed)
                        val nowSsid = getActualConnectedSsid()
                        Log.d(
                            TAG,
                            "  ⏱ awaitSsidAssociation status @${crossed}ms: target='$targetSsid', actual='$nowSsid'",
                        )
                    }
                    checkAndMaybeFinish("poll")
                }
            }

            // ── 3) Optional one-shot panel nudge ──
            if (panelHintAfterMillis < timeoutMillis && onPanelHint != null) {
                hintJob = launch {
                    delay(panelHintAfterMillis)
                    if (resolved) return@launch
                    val current = getActualConnectedSsid()
                    if (current == null || !current.equals(targetSsid, ignoreCase = true)) {
                        runCatching { onPanelHint() }
                    }
                }
            }

            continuation.invokeOnCancellation { cleanup() }
            }
        }.also { joined ->
            if (joined == null) {
                Log.w(TAG, "  ⚠ awaitSsidAssociation: timeout after ${timeoutMillis}ms (still on '${getActualConnectedSsid()}')")
            }
        }
    }

    /**
     * Adds a [WifiNetworkSuggestion] to the system for future auto-connect.
     * The OS will display a one-time notification asking the user to approve
     * the app to suggest networks. Once approved, the device will auto-connect
     * to this WiFi in the future when in range, without app interaction.
     *
     * Best-effort: any failure is logged but does not abort the main connect flow.
     */
    private fun addSuggestionForFutureAutoConnect(
        ssid: String,
        password: String?,
        security: String?,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.d(TAG, "  ▸ Suggestion skipped: Android < 10 doesn't support WifiNetworkSuggestion")
                return@runCatching
            }
            val mgr = wifiManager
            if (mgr == null) {
                Log.w(TAG, "  ▸ Suggestion skipped: WifiManager unavailable")
                return@runCatching
            }

            val suggestion = buildSuggestion(ssid, password, security)

            // Remove any prior suggestion for the same SSID first (in case password changed).
            // removeNetworkSuggestions matches by SSID + credentials so this is safe.
            runCatching { mgr.removeNetworkSuggestions(listOf(suggestion)) }

            val status = mgr.addNetworkSuggestions(listOf(suggestion))
            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ->
                    Log.d(TAG, "  ▸ ✓ Suggestion added: '$ssid' (will auto-connect next time)")
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE ->
                    Log.d(TAG, "  ▸ Suggestion already exists for '$ssid' — OK")
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED ->
                    Log.w(TAG, "  ▸ ⚠ App not allowed to suggest networks (user previously denied)")
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP ->
                    Log.w(TAG, "  ▸ ⚠ Reached max suggestions per app (50). Consider removing old ones.")
                else ->
                    Log.w(TAG, "  ▸ ⚠ Suggestion add status: $status")
            }
        }.onFailure {
            Log.w(TAG, "  ▸ Suggestion add failed (non-fatal): ${it.message}")
        }
    }

    private fun buildSuggestion(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (!password.isNullOrEmpty()) {
            val useWpa3 = security.orEmpty().contains("WPA3", ignoreCase = true)
            if (useWpa3) {
                builder.setWpa3Passphrase(password)
            } else {
                builder.setWpa2Passphrase(password)
            }
        }
        // Hint to OS that this network is preferred when available.
        // Note: setIsAppInteractionRequired(false) means the app does not need
        // to be running for the OS to use this suggestion.
        runCatching { builder.setIsAppInteractionRequired(false) }
        return builder.build()
    }

    /**
     * Immediate connection via [WifiNetworkSpecifier] + [ConnectivityManager.requestNetwork].
     * Shows a system dialog "Connect to <SSID>?" — user taps Connect once.
     *
     * IMPORTANT: We do NOT call bindProcessToNetwork() on the resulting network.
     * On Vivo Android 12, that causes the system to cancel the request 2-3s
     * later with the dialog "Đã xảy ra lỗi. Ứng dụng đã hủy yêu cầu chọn thiết bị".
     */
    private suspend fun connectViaSpecifier(
        normalizedSsid: String,
        normalizedPassword: String,
        security: String?,
    ): WifiConnectResult = coroutineScope {
        var internetFallbackJob: Job? = null
        var joinedWithoutInternet: WifiConnectResult.ConnectedWithoutInternet? = null
        var associationWatcherJob: Job? = null

        // Increment session so any stale callbacks from prior attempts are ignored.
        val mySession = ++currentSessionId
        Log.d(TAG, "  ▸ New session=$mySession for '$normalizedSsid'")

        suspendCancellableCoroutine { continuation ->
            val manager = connectivityManager
                ?: return@suspendCancellableCoroutine continuation.resume(
                    WifiConnectResult.Failed(
                        WifiConnectFailureReason.UNKNOWN,
                        "ConnectivityManager unavailable",
                    ).also { Log.e(TAG, "✗ ConnectivityManager null") },
                )

            cancelPendingRequest()

            fun isStaleSession(): Boolean = currentSessionId != mySession

            val specifier = createSpecifier(
                normalizedSsid,
                normalizedPassword.takeIf { it.isNotEmpty() },
                security,
            )
            val request = createRequest(specifier)
            Log.d(TAG, "  ▸ Specifier built. Calling requestNetwork()...")
            Log.d(TAG, "  ⚠ Android will show a system dialog. User must tap Connect.")

            fun cancelInternetFallback() {
                internetFallbackJob?.cancel()
                internetFallbackJob = null
            }

            fun cancelAssociationWatcher() {
                associationWatcherJob?.cancel()
                associationWatcherJob = null
            }

            fun finish(result: WifiConnectResult, releaseRequest: Boolean) {
                cancelInternetFallback()
                cancelAssociationWatcher()
                val joinedTargetNetwork = when (result) {
                    is WifiConnectResult.Success -> result.network
                    is WifiConnectResult.ConnectedWithoutInternet -> result.network
                    is WifiConnectResult.Failed -> null
                }
                val joinedTargetSsid = when (result) {
                    is WifiConnectResult.Success -> result.ssid
                    is WifiConnectResult.ConnectedWithoutInternet -> result.ssid
                    is WifiConnectResult.Failed -> null
                }
                if (joinedTargetSsid != null) {
                    trackedNetwork = joinedTargetNetwork
                    keepAliveSsid = joinedTargetSsid

                    // CRITICAL: KEEP the Specifier callback registered as keep-alive.
                    // We previously released it after 3s thinking the Suggestion
                    // would take over at system level — but on MIUI/Xiaomi/Vivo
                    // that causes the OS to revert to the PREVIOUSLY saved
                    // WiFi (which has higher priority than a freshly-added
                    // Suggestion). Result: app says "Connected to B" but device
                    // actually rolls back to A within 3-5s.
                    //
                    // Trade-off: while the callback is held, the WiFi is
                    // "app-bound" — other apps go through cellular/old WiFi.
                    // This is acceptable because: (1) the user is currently
                    // using OUR app, (2) the keep-alive is released the moment
                    // they start a new connect() to a different SSID, and
                    // (3) when the app process dies, the OS frees everything
                    // and the Suggestion picks up.
                    val currentActive = activeCallback
                    if (currentActive != null) {
                        // Drop any prior keep-alive WITHOUT unregistering
                        // (some OEMs raise a cancel dialog on unregister).
                        keepAliveCallback = currentActive
                        activeCallback = null
                        Log.d(TAG, "  ✓ Specifier callback promoted to permanent keep-alive for '$joinedTargetSsid' (NOT released to prevent OS rollback to previous WiFi)")
                    }
                }
                when (result) {
                    is WifiConnectResult.Success ->
                        Log.d(TAG, "══ RESULT: ✓ SUCCESS — Connected to '${result.ssid}' ══")
                    is WifiConnectResult.ConnectedWithoutInternet ->
                        Log.w(TAG, "══ RESULT: ⚠ NO INTERNET — '${result.ssid}', captive=${result.isCaptivePortal} ══")
                    is WifiConnectResult.Failed ->
                        Log.e(TAG, "══ RESULT: ✗ FAILED — ${result.reason}: ${result.message} ══")
                }
                complete(
                    continuation = continuation,
                    result = result,
                    releaseRequest = result is WifiConnectResult.Failed && releaseRequest,
                )
            }

            fun rememberJoinedWithoutInternet(result: WifiConnectResult.ConnectedWithoutInternet) {
                if (!continuation.isActive) return
                cancelAssociationWatcher()
                joinedWithoutInternet = result
                Log.d(TAG, "  ⏳ Joined WiFi '${result.ssid}' — checking SSID match before reporting...")

                // SSID-match override: if the OS-level SSID matches the user's
                // target, we accept this as Success. The "no internet" signal
                // from capabilities is unreliable on fresh app-bound networks
                // because:
                //   - DNS hasn't resolved yet (~50-200ms typical, up to 2s)
                //   - OS internet probe runs against the bound network which
                //     may not have full routing yet
                //   - WifiNetworkSpecifier networks often never get the
                //     INTERNET / VALIDATED capabilities at all
                //   - Personal hotspots (iPhone, Android) frequently never get
                //     NET_CAPABILITY_VALIDATED because the OS validates the
                //     UPSTREAM (cellular) link separately. Internet IS available.
                // Trusting SSID match avoids the confusing "connected but no
                // internet" UI that flashes briefly even when WiFi is fine.
                val actualSsid = getActualConnectedSsid()
                if (actualSsid != null && actualSsid.equals(result.ssid, ignoreCase = true)) {
                    Log.d(TAG, "  ✓ SSID '$actualSsid' matches target — reporting Success directly")
                    finish(
                        result = WifiConnectResult.Success(
                            network = result.network,
                            ssid = result.ssid,
                        ),
                        releaseRequest = false,
                    )
                    return
                }

                if (internetFallbackJob?.isActive == true) return
                Log.d(TAG, "  ⏳ SSID not yet matching, falling back to ${internetValidationGraceMillis}ms grace check + real HTTP probe...")

                internetFallbackJob = launch {
                    delay(internetValidationGraceMillis.coerceAtLeast(0L))
                    val fallback = joinedWithoutInternet ?: return@launch
                    if (!continuation.isActive) return@launch

                    // Re-check SSID after the grace window — by now the OS
                    // should have committed the association.
                    val laterActualSsid = getActualConnectedSsid()
                    if (laterActualSsid != null && laterActualSsid.equals(fallback.ssid, ignoreCase = true)) {
                        Log.d(TAG, "  ✓ Late SSID match — Upgrading to Success")
                        finish(
                            result = WifiConnectResult.Success(
                                network = fallback.network,
                                ssid = fallback.ssid,
                            ),
                            releaseRequest = false,
                        )
                        return@launch
                    }

                    // SSID still not matching → could be Vivo location-mask OR
                    // a hotspot where SSID isn't reflected at WifiManager level.
                    // Last resort: do a REAL HTTP probe through the bound
                    // network. If we can reach generate_204, internet works
                    // regardless of what NET_CAPABILITY_VALIDATED says — this
                    // is the definitive answer for personal hotspots.
                    Log.d(TAG, "  ⏳ Trying real HTTP probe through bound network for hotspot/masked-SSID case...")
                    val realInternetCheck = performRealInternetCheck(fallback.network)
                    if (realInternetCheck) {
                        Log.d(TAG, "  ✓ Real HTTP probe succeeded — Upgrading to Success (hotspot with internet)")
                        finish(
                            result = WifiConnectResult.Success(
                                network = fallback.network,
                                ssid = fallback.ssid,
                            ),
                            releaseRequest = false,
                        )
                        return@launch
                    }

                    Log.w(TAG, "  ⏰ Grace expired, SSID not matching, HTTP probe failed — reporting joined-without-internet")
                    finish(result = fallback, releaseRequest = false)
                }
            }

            // Smart SSID verification: prevents the callback from claiming
            // "Success" while the device is still on the OLD WiFi network.
            //  1) WifiManager reports requested SSID → ACCEPT
            //  2) WifiManager reports a DIFFERENT real SSID → REJECT (still on old WiFi)
            //  3) WifiManager reports null/<unknown> (Vivo location-off case)
            //     → trust the Specifier, since the OS only fires callbacks for
            //       the Specifier-bound SSID anyway.
            fun callbackMatchesRequestedSsid(source: String): Boolean {
                val actualSsid = getActualConnectedSsid()
                if (actualSsid.isNullOrBlank()) {
                    Log.d(
                        TAG,
                        "  ▸ $source — WifiManager SSID unavailable, trusting Specifier match for '$normalizedSsid'",
                    )
                    return true
                }
                val matches = actualSsid.equals(normalizedSsid, ignoreCase = true)
                if (!matches) {
                    Log.w(
                        TAG,
                        "  ▸ $source IGNORED — WifiManager reports '$actualSsid' but user requested '$normalizedSsid' (waiting for OS to switch)",
                    )
                }
                return matches
            }

            fun startAssociationWatcher() {
                if (associationWatcherJob?.isActive == true) return
                associationWatcherJob = launch {
                    while (continuation.isActive && !isStaleSession()) {
                        delay(500L)
                        val actualSsid = getActualConnectedSsid()
                        if (actualSsid != null && actualSsid.equals(normalizedSsid, ignoreCase = true)) {
                            Log.d(
                                TAG,
                                "  ✓ Actual SSID is now '$actualSsid' — accepting as Success for '$normalizedSsid'",
                            )
                            // CRITICAL: capture the live WiFi Network handle so
                            // the post-success internet probe can route through
                            // it (Network.openConnection). Without this the
                            // probe falls back to system default, and on a
                            // freshly-bound WiFi the default may still be the
                            // OLD network (or cellular) — causing DNS lookups
                            // to fail with UnknownHostException.
                            val liveNetwork = findActiveWifiNetwork()
                            Log.d(TAG, "  ▸ Captured live WiFi network for probe: $liveNetwork")
                            finish(
                                result = WifiConnectResult.Success(
                                    ssid = normalizedSsid,
                                    network = liveNetwork,
                                ),
                                releaseRequest = false,
                            )
                            return@launch
                        }
                    }
                }
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isStaleSession()) { Log.d(TAG, "  ▸ onAvailable IGNORED (stale session)"); return }
                    Log.d(TAG, "  ▸ onAvailable() network=$network")
                    val capabilities = manager.getNetworkCapabilities(network)
                    logNetworkCapabilities(capabilities, "onAvailable")
                    if (!callbackMatchesRequestedSsid("onAvailable")) return
                    val observed = capabilities?.toWifiConnectionResult(network, normalizedSsid)
                        ?: WifiConnectResult.ConnectedWithoutInternet(
                            network = network,
                            ssid = normalizedSsid,
                            hasInternetCapability = false,
                            isCaptivePortal = false,
                        )
                    when (observed) {
                        is WifiConnectResult.Success -> finish(observed, releaseRequest = false)
                        is WifiConnectResult.ConnectedWithoutInternet -> rememberJoinedWithoutInternet(observed)
                        is WifiConnectResult.Failed -> finish(observed, releaseRequest = true)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (isStaleSession()) return
                    if (!continuation.isActive) return
                    logNetworkCapabilities(networkCapabilities, "onCapabilitiesChanged")
                    if (!callbackMatchesRequestedSsid("onCapabilitiesChanged")) return
                    val observed = networkCapabilities.toWifiConnectionResult(network, normalizedSsid)
                        ?: return
                    when (observed) {
                        is WifiConnectResult.Success -> finish(observed, releaseRequest = false)
                        is WifiConnectResult.ConnectedWithoutInternet -> rememberJoinedWithoutInternet(observed)
                        is WifiConnectResult.Failed -> finish(observed, releaseRequest = true)
                    }
                }

                override fun onLost(network: Network) {
                    Log.e(TAG, "  ▸ onLost() session=$mySession current=$currentSessionId")
                    if (keepAliveCallback === this) {
                        Log.d(TAG, "  ▸ onLost on keep-alive — clearing keep-alive state")
                        keepAliveCallback = null
                        keepAliveSsid = null
                        trackedNetwork = null
                    }
                    if (isStaleSession()) { Log.d(TAG, "  ▸ onLost IGNORED (stale session)"); return }
                    if (!continuation.isActive) return
                    // If the device is not (yet) on the requested SSID, this
                    // onLost may belong to the OLD network we just released.
                    // Don't fail the whole connect attempt — let the watcher
                    // pick up the actual association when it lands.
                    val actualSsid = getActualConnectedSsid()
                    if (!actualSsid.isNullOrBlank() &&
                        !actualSsid.equals(normalizedSsid, ignoreCase = true)
                    ) {
                        Log.w(
                            TAG,
                            "  ▸ onLost IGNORED — WifiManager still on '$actualSsid', waiting for switch to '$normalizedSsid'",
                        )
                        return
                    }
                    finish(
                        result = WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                            message = "Network lost before verification",
                        ),
                        releaseRequest = true,
                    )
                }

                override fun onUnavailable() {
                    if (isStaleSession()) { Log.d(TAG, "  ▸ onUnavailable IGNORED (stale session)"); return }
                    Log.e(TAG, "  ▸ onUnavailable() — user rejected dialog or wrong password")
                    finish(
                        result = WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.WRONG_PASSWORD_OR_REJECTED,
                            message = "Không thể kết nối. Hãy bấm 'Kết nối' trên hộp thoại hệ thống hoặc kiểm tra mật khẩu.",
                        ),
                        releaseRequest = true,
                    )
                }
            }

            activeCallback = callback
            continuation.invokeOnCancellation {
                Log.d(TAG, "  ▸ Coroutine cancelled — cleaning up")
                cancelInternetFallback()
                cancelAssociationWatcher()
                cancelPendingRequest()
            }

            try {
                manager.requestNetwork(request, callback)
                startAssociationWatcher()
                Log.d(TAG, "  ✓ requestNetwork() called. Waiting for system dialog & callback...")
            } catch (security: SecurityException) {
                Log.e(TAG, "  ✗ SecurityException: ${security.message}", security)
                finish(
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.PERMISSION_DENIED,
                        message = security.message,
                    ),
                    releaseRequest = true,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "  ✗ Exception: ${t.message}", t)
                finish(
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.UNKNOWN,
                        message = t.message,
                    ),
                    releaseRequest = true,
                )
            }
        }
    }

    /**
     * Scan all known networks for the one currently bound to WiFi transport.
     * Used as a fallback when the Specifier's `onAvailable(network)` did not
     * fire (or fired stale) but the device has clearly associated. Without
     * the WiFi Network handle, post-success internet probes fall back to
     * system default which may still be the OLD network → UnknownHostException
     * on DNS, even though the new WiFi is fine.
     */
    private fun findActiveWifiNetwork(): Network? {
        val cm = connectivityManager ?: return null
        return runCatching {
            cm.allNetworks.firstOrNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }.getOrNull()
    }

    /**
     * Public wrapper for the internal HTTP-based internet check. Use this in
     * MainViewModel as a final "is internet really available?" probe before
     * showing the user a "no internet" message — covers personal hotspots
     * (iPhone, Android) where the OS often does NOT set NET_CAPABILITY_VALIDATED
     * even though internet works fine.
     *
     * Single attempt — for retry logic with explicit timing windows, use
     * [checkRealInternetWithRetries] instead.
     */
    suspend fun checkRealInternet(): Boolean = performRealInternetCheck(trackedNetwork ?: findActiveWifiNetwork())

    /**
     * Robust internet probe with retries at 1s, 3s, 5s. Use this for
     * personal hotspots (iPhone tethering, Android tethering) where the
     * upstream auth handshake can take several seconds after the WiFi
     * association completes.
     *
     * Retry timing matches the user-visible UX: short and predictable.
     * The probe always goes through THIS connector's [trackedNetwork] when
     * available — never the system default — so we don't get a false
     * positive from cellular when WiFi has no internet, and never a false
     * negative when WiFi works but the OS is still validating.
     *
     * Returns [InternetCheckOutcome] with `success = true` if any retry
     * succeeded, plus a diagnostic reason string for logging.
     */
    suspend fun checkRealInternetWithRetries(targetSsid: String): InternetCheckOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "────────────────────────────────────────────────────────────")
        Log.d(TAG, "▶ INTERNET PROBE with retries (target='$targetSsid')")
        // Snapshot the network we'll probe against. Prefer trackedNetwork from
        // the latest Specifier callback so the probe goes through the WIFI
        // we just joined — not cellular, not the previous WiFi.
        // If trackedNetwork is null (watcher path skipped capture), fall back
        // to scanning ConnectivityManager.allNetworks for the WiFi transport.
        val probeNetwork = trackedNetwork ?: findActiveWifiNetwork()
        val probeSsid = getActualConnectedSsid()
        Log.d(TAG, "  Target SSID:           '$targetSsid'")
        Log.d(TAG, "  Currently associated:  '$probeSsid'")
        Log.d(TAG, "  Probing via network:   ${probeNetwork ?: "<system default>"}")

        // 6 retries at +1s, +3s, +5s, +8s, +10s, +12s (total budget ~12s).
        // Matches the time Android typically needs to validate hotspot
        // upstream (4G/5G handshake + captive-portal probe).
        val schedule = listOf(
            1_000L to "@1s",
            2_000L to "@3s",
            2_000L to "@5s",
            3_000L to "@8s",
            2_000L to "@10s",
            2_000L to "@12s",
        )
        var lastReason = "no attempts"
        schedule.forEach { (sleepMs, attemptLabel) ->
            kotlinx.coroutines.delay(sleepMs)
            // Re-snapshot the WiFi Network on each retry. Between attempts the
            // OS may have replaced the bound network handle (e.g. moved from
            // app-bound Specifier to system Suggestion). Order:
            //   1) trackedNetwork  — set by Specifier onAvailable
            //   2) findActiveWifiNetwork() — scans ConnectivityManager
            //   3) initial probeNetwork snapshot
            val livenetwork = trackedNetwork ?: findActiveWifiNetwork() ?: probeNetwork

            // Capability snapshot for diagnostic — log INTERNET / VALIDATED on the
            // bound network so we can correlate with HTTP-probe results.
            val capsString = describeCapabilities(livenetwork)
            Log.d(TAG, "  $attemptLabel caps=$capsString associatedSsid='${getActualConnectedSsid()}'")

            // PRIMARY: real HTTP probe through the bound network.
            val (httpOk, httpReason) = tryInternetProbeOnce(livenetwork)
            Log.d(TAG, "  $attemptLabel HTTP probe: success=$httpOk ${if (httpOk) "" else "(reason: $httpReason)"}")
            if (httpOk) {
                Log.d(TAG, "  ✓ Internet probe succeeded $attemptLabel")
                Log.d(TAG, "────────────────────────────────────────────────────────────")
                return@withContext InternetCheckOutcome(
                    success = true,
                    reason = "http-ok-$attemptLabel ($httpReason)",
                    probedNetwork = livenetwork?.toString(),
                    associatedSsid = getActualConnectedSsid(),
                )
            }

            // FALLBACK: VALIDATED capability flip from OS. If the OS flipped
            // NET_CAPABILITY_VALIDATED on the bound network, accept that as
            // proof of internet — covers cases where direct HTTP from the
            // process is firewalled but Android's own probe succeeded.
            val caps = livenetwork?.let { connectivityManager?.getNetworkCapabilities(it) }
            if (caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            ) {
                Log.d(TAG, "  ✓ NET_CAPABILITY_VALIDATED on bound network $attemptLabel — accepting")
                Log.d(TAG, "────────────────────────────────────────────────────────────")
                return@withContext InternetCheckOutcome(
                    success = true,
                    reason = "os-validated-$attemptLabel",
                    probedNetwork = livenetwork.toString(),
                    associatedSsid = getActualConnectedSsid(),
                )
            }

            lastReason = httpReason
        }
        Log.w(TAG, "  ✗ All 6 internet probes failed. Last reason: $lastReason")
        Log.d(TAG, "────────────────────────────────────────────────────────────")
        InternetCheckOutcome(
            success = false,
            reason = lastReason,
            probedNetwork = probeNetwork?.toString(),
            associatedSsid = getActualConnectedSsid(),
        )
    }

    private fun describeCapabilities(network: Network?): String {
        val cm = connectivityManager ?: return "cm=null"
        val caps = network?.let { cm.getNetworkCapabilities(it) } ?: return "caps=null"
        val transports = listOfNotNull(
            "WIFI".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
            "CELL".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
            "VPN".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
        ).joinToString("|").ifEmpty { "<none>" }
        val flags = listOfNotNull(
            "INTERNET".takeIf { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) },
            "VALIDATED".takeIf { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) },
            "CAPTIVE".takeIf { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) },
            "TRUSTED".takeIf { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) },
            "NOT_RESTRICTED".takeIf { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) },
        ).joinToString("|").ifEmpty { "<none>" }
        return "transports=$transports flags=$flags"
    }

    private suspend fun tryInternetProbeOnce(network: Network?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // Lightweight HTTP/204 endpoints. We only need ONE to succeed.
        val targets = listOf(
            "https://connectivitycheck.gstatic.com/generate_204" to 204,
            "https://www.google.com/generate_204" to 204,
            "https://clients3.google.com/generate_204" to 204,
        )
        var lastErr = "none"
        for ((url, expectedCode) in targets) {
            try {
                val connection = if (network != null) {
                    network.openConnection(URL(url)) as HttpURLConnection
                } else {
                    URL(url).openConnection() as HttpURLConnection
                }
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                val code = connection.responseCode
                connection.disconnect()
                if (code == expectedCode) return@withContext true to "ok($url=$code)"
                lastErr = "$url returned $code (expected $expectedCode)"
            } catch (e: Exception) {
                lastErr = "$url: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        false to lastErr
    }

    /**
     * Performs a real HTTP request to verify internet connectivity.
     * If [network] is provided, the request goes through that network specifically
     * (without binding the process — uses [Network.openConnection]).
     */
    private suspend fun performRealInternetCheck(network: Network?): Boolean = withContext(Dispatchers.IO) {
        val targets = listOf(
            "https://connectivitycheck.gstatic.com/generate_204" to 204,
            "https://www.google.com/generate_204" to 204,
            "https://clients3.google.com/generate_204" to 204,
        )
        for ((url, expectedCode) in targets) {
            try {
                val connection = if (network != null) {
                    network.openConnection(URL(url)) as HttpURLConnection
                } else {
                    URL(url).openConnection() as HttpURLConnection
                }
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                val code = connection.responseCode
                connection.disconnect()
                Log.d(TAG, "  Internet check: $url → HTTP $code (expected $expectedCode)")
                if (code == expectedCode) return@withContext true
            } catch (e: Exception) {
                Log.d(TAG, "  Internet check failed for $url: ${e.message}")
            }
        }
        false
    }

    /** Returns the SSID the device is currently associated with, or null. */
    fun getActualConnectedSsid(): String? {
        val mgr = wifiManager ?: return null
        @Suppress("DEPRECATION")
        val info = runCatching { mgr.connectionInfo }.getOrNull() ?: return null
        val ssid = info.ssid?.normalizeWifiSsid()
        Log.d(TAG, "  Device WiFi state: SSID='$ssid', BSSID='${info.bssid}', netId=${info.networkId}")
        return ssid?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
    }

    /** Full post-connection verification: SSID match + real internet check. */
    suspend fun verifyConnection(expectedSsid: String): WifiConnectionVerification {
        Log.d(TAG, "────────────────────────────────────────────────────────────")
        Log.d(TAG, "▶ POST-CONNECTION VERIFICATION for '$expectedSsid'")

        val actualSsid = getActualConnectedSsid()
        val ssidMatches = actualSsid != null && actualSsid.equals(expectedSsid, ignoreCase = true)
        Log.d(TAG, "  Actual='$actualSsid', Expected='$expectedSsid', Match=$ssidMatches")

        if (!ssidMatches) {
            Log.e(TAG, "  ✗ SSID MISMATCH — device is NOT on expected WiFi")
            Log.d(TAG, "────────────────────────────────────────────────────────────")
            return WifiConnectionVerification(
                actualSsid = actualSsid,
                expectedSsid = expectedSsid,
                ssidMatches = false,
                hasRealInternet = false,
                status = ConnectionVerificationStatus.SSID_MISMATCH,
            )
        }

        val hasInternet = performRealInternetCheck(trackedNetwork)
        val status = if (hasInternet) {
            Log.d(TAG, "  ✓ VERIFIED: Connected to '$actualSsid' WITH internet")
            ConnectionVerificationStatus.FULLY_CONNECTED
        } else {
            Log.w(TAG, "  ⚠ Connected to '$actualSsid' but NO real internet")
            ConnectionVerificationStatus.CONNECTED_NO_INTERNET
        }
        Log.d(TAG, "────────────────────────────────────────────────────────────")
        return WifiConnectionVerification(
            actualSsid = actualSsid,
            expectedSsid = expectedSsid,
            ssidMatches = true,
            hasRealInternet = hasInternet,
            status = status,
        )
    }

    fun cancelPendingRequest() {
        val manager = connectivityManager ?: return
        val callback = activeCallback ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
        activeCallback = null
    }

    /**
     * Disconnect current WiFi — release callbacks and network slot.
     * Does NOT remove suggestions (so user can reconnect later by tapping).
     * Call this when switching to a different WiFi.
     *
     * VIVO SAFE: On Vivo Android 12, calling unregisterNetworkCallback on a
     * specifier callback triggers the system error dialog. We use a safe
     * variant that catches and suppresses the error.
     */
    fun releaseAll() {
        val manager = connectivityManager ?: return
        Log.d(TAG, "▶ releaseAll() — disconnecting (keeping suggestions)")
        // On Vivo, we deliberately AVOID unregistering specifier callbacks
        // because that itself triggers the "Đã xảy ra lỗi" dialog.
        // The callback will be cleaned up when the app process dies (which
        // is acceptable since they hold no resources beyond the WiFi binding).
        if (!isVivoDevice) {
            activeCallback?.let { safeUnregister(manager, it) }
            keepAliveCallback?.let { safeUnregister(manager, it) }
        } else {
            Log.d(TAG, "  ▸ Vivo: skipping unregisterNetworkCallback to avoid system error dialog")
        }
        activeCallback = null
        keepAliveCallback = null
        keepAliveSsid = null
        trackedNetwork = null
        currentSessionId++
    }

    /**
     * Clear app-side state before a new SSID request. A completed keep-alive
     * callback for the previous SSID must be released here; otherwise Android
     * can keep pulling the radio back to the old Wi-Fi when the user switches
     * B -> A. Pending callbacks are only marked stale so duplicate taps don't
     * cancel the currently visible system chooser.
     */
    private fun releaseForNetworkSwitch() {
        Log.d(TAG, "▶ releaseForNetworkSwitch() — clearing stale state for SSID switch")
        val manager = connectivityManager
        if (manager != null && !isVivoDevice) {
            keepAliveCallback?.let { safeUnregister(manager, it) }
        } else if (keepAliveCallback != null) {
            Log.d(TAG, "  ▸ Skipping keep-alive unregister on Vivo-class device")
        }
        activeCallback = null
        keepAliveCallback = null
        keepAliveSsid = null
        trackedNetwork = null
        currentSessionId++
    }

    /**
     * Safely unregister a network callback. On Vivo Android 12, this can
     * throw or trigger the "Ứng dụng đã hủy yêu cầu" dialog. We catch
     * all exceptions and log them as non-fatal.
     */
    private fun safeUnregister(manager: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "  ⚠ unregisterNetworkCallback failed (Vivo quirk): ${e.message}")
        }
    }

    /**
     * Explicitly disconnect. Same as releaseAll — just ngắt, không xoá.
     */
    fun disconnect() {
        releaseAll()
    }

    /**
     * PERMANENTLY forget a WiFi: disconnect + remove suggestion + clear everything.
     * Call when user DELETES a WiFi from history. After this, OS won't
     * auto-connect to this SSID anymore.
     *
     * Per-SSID safety: only disconnects the live WiFi if its SSID matches the
     * one being forgotten. A different active connection is preserved.
     * removeNetworkSuggestions() also matches strictly by SSID + credentials,
     * so suggestions for OTHER SSIDs are untouched.
     */
    fun forgetNetwork(ssid: String) {
        Log.d(TAG, "▶ forgetNetwork('$ssid') — full clear: disconnect + remove suggestion")
        // Disconnect ONLY if the live keep-alive matches this SSID.
        if (keepAliveSsid != null && keepAliveSsid.equals(ssid, ignoreCase = true)) {
            Log.d(TAG, "  ▸ '$ssid' is the active WiFi — releasing callbacks + request")
            releaseAll()
        } else {
            Log.d(TAG, "  ▸ '$ssid' is NOT the active WiFi (active='$keepAliveSsid') — keeping current connection intact")
        }
        // Remove suggestions for THIS SSID only. All known security variants
        // (open/WPA2/WPA3) are tried so the OS drops every saved variant —
        // but each Builder().setSsid(ssid) ensures we never touch suggestions
        // for any other SSID.
        runCatching {
            val mgr = wifiManager ?: return@runCatching
            val variants = mutableListOf<WifiNetworkSuggestion>()
            // Open
            variants += WifiNetworkSuggestion.Builder().setSsid(ssid).build()
            // WPA2
            variants += WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase("placeholder")
                .build()
            // WPA3 (best-effort; fails silently if the build doesn't support it)
            runCatching {
                variants += WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa3Passphrase("placeholder")
                    .build()
            }
            mgr.removeNetworkSuggestions(variants)
            Log.d(TAG, "  ✓ Suggestions removed for '$ssid' (open/WPA2/WPA3 variants)")
        }.onFailure {
            Log.w(TAG, "  ⚠ Failed to remove suggestion: ${it.message}")
        }
    }

    /** Returns the SSID currently kept alive by the connector (null if none). */
    fun getKeptAliveSsid(): String? = keepAliveSsid

    private fun complete(
        continuation: kotlinx.coroutines.CancellableContinuation<WifiConnectResult>,
        result: WifiConnectResult,
        releaseRequest: Boolean,
    ) {
        if (continuation.isActive) continuation.resume(result)
        if (releaseRequest) cancelPendingRequest()
    }

    // ── Debug helpers ──

    private fun logPermissionStatus() {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        val changeWifi = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CHANGE_WIFI_STATE)
        val accessWifi = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_WIFI_STATE)
        val changeNet = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CHANGE_NETWORK_STATE)
        Log.d(TAG, "  Permissions: FINE=${fine == PackageManager.PERMISSION_GRANTED}, COARSE=${coarse == PackageManager.PERMISSION_GRANTED}, CHANGE_WIFI=${changeWifi == PackageManager.PERMISSION_GRANTED}, ACCESS_WIFI=${accessWifi == PackageManager.PERMISSION_GRANTED}, CHANGE_NET=${changeNet == PackageManager.PERMISSION_GRANTED}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES)
            Log.d(TAG, "    NEARBY_WIFI_DEVICES: ${nearby == PackageManager.PERMISSION_GRANTED}")
        }
    }

    @Suppress("DEPRECATION")
    private fun logCurrentWifiState() {
        val mgr = wifiManager ?: return
        Log.d(TAG, "  WiFi state: enabled=${mgr.isWifiEnabled}")
        runCatching {
            val info = mgr.connectionInfo
            Log.d(TAG, "    Current SSID='${info?.ssid?.normalizeWifiSsid()}', BSSID='${info?.bssid}', netId=${info?.networkId}, RSSI=${info?.rssi}")
        }
    }

    private fun logNetworkCapabilities(capabilities: NetworkCapabilities?, source: String) {
        if (capabilities == null) {
            Log.d(TAG, "    [$source] capabilities=null")
            return
        }
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        val wifiInfo = capabilities.transportInfo as? WifiInfo
        Log.d(TAG, "    [$source] WIFI=$hasWifi INET=$hasInternet VAL=$isValidated CAPTIVE=$isCaptive ssid='${wifiInfo?.ssid?.normalizeWifiSsid()}' bssid='${wifiInfo?.bssid}'")
    }

    companion object {
        private const val TAG = "WifiConnector"
    }
}

// ── Free helpers ──

private fun validateSecurityMode(security: String?): String? {
    val n = security.orEmpty()
    return when {
        n.contains("enterprise", ignoreCase = true) ->
            "WPA/WPA2 Enterprise cần cấu hình tài khoản riêng trong cài đặt hệ thống."
        n.contains("wep", ignoreCase = true) ->
            "WEP không được hỗ trợ trong luồng kết nối tự động an toàn."
        else -> null
    }
}

private fun buildWifiSpecifier(
    ssid: String,
    password: String?,
    security: String?,
): WifiNetworkSpecifier {
    val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
    val pw = password.orEmpty()
    if (pw.isNotEmpty()) {
        val useWpa3 = security.orEmpty().contains("WPA3", ignoreCase = true)
        if (useWpa3) {
            Log.d("WifiConnector", "  Specifier using WPA3")
            builder.setWpa3Passphrase(pw)
        } else {
            Log.d("WifiConnector", "  Specifier using WPA2")
            builder.setWpa2Passphrase(pw)
        }
    } else {
        Log.d("WifiConnector", "  Specifier open network")
    }
    return builder.build()
}

private fun buildWifiNetworkRequest(specifier: WifiNetworkSpecifier): NetworkRequest {
    return NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(specifier)
        .build()
}

private fun String.isPrintableAsciiPassphrase(): Boolean = all { it.code in 32..126 }

private fun NetworkCapabilities.toWifiConnectionResult(
    network: Network,
    expectedSsid: String,
): WifiConnectResult? {
    if (!hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
    if (wifiSsidMismatch(expectedSsid)) {
        val wifiInfo = transportInfo as? WifiInfo
        val actual = wifiInfo?.ssid?.normalizeWifiSsid() ?: "<unknown>"
        Log.w("WifiConnector", "  ▸ Capabilities ignored — expected='$expectedSsid', actual='$actual'")
        return null
    }
    val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val isCaptive = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    // WifiNetworkSpecifier-bound networks may NOT carry NET_CAPABILITY_INTERNET
    // (that flag is for system-managed networks). NET_CAPABILITY_VALIDATED on
    // its own is the OS's affirmative signal that internet probe succeeded —
    // treat it as Success even without the INTERNET flag. This eliminates the
    // ~6s grace-period wait for a condition that will never come.
    if (isValidated && !isCaptive) return WifiConnectResult.Success(network = network, ssid = expectedSsid)
    return WifiConnectResult.ConnectedWithoutInternet(
        network = network,
        ssid = expectedSsid,
        hasInternetCapability = hasInternet,
        isCaptivePortal = isCaptive,
    )
}

private fun NetworkCapabilities.wifiSsidMismatch(expectedSsid: String): Boolean {
    val info = transportInfo as? WifiInfo ?: return false
    val actual = info.ssid.normalizeWifiSsid()
    return actual.isNotBlank() &&
        !actual.equals("<unknown ssid>", ignoreCase = true) &&
        !actual.equals(expectedSsid, ignoreCase = true)
}

private fun String.normalizeWifiSsid(): String = trim().removePrefix("\"").removeSuffix("\"")

/**
 * Diagnostic outcome of [WifiConnector.checkRealInternetWithRetries].
 * Carries enough context for MainViewModel to log a precise final state and
 * pick the right UI message.
 */
data class InternetCheckOutcome(
    val success: Boolean,
    val reason: String,
    val probedNetwork: String?,
    val associatedSsid: String?,
)

sealed class WifiConnectResult {
    data class Success(
        val ssid: String,
        val network: Network? = null,
    ) : WifiConnectResult()

    data class ConnectedWithoutInternet(
        val ssid: String,
        val network: Network? = null,
        val hasInternetCapability: Boolean = false,
        val isCaptivePortal: Boolean = false,
    ) : WifiConnectResult()

    data class Failed(
        val reason: WifiConnectFailureReason,
        val message: String? = null,
    ) : WifiConnectResult()
}

enum class WifiConnectFailureReason {
    INVALID_INPUT,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    UNSUPPORTED_DEVICE,
    SSID_NOT_FOUND,
    NETWORK_NOT_FOUND,
    WRONG_PASSWORD_OR_REJECTED,
    AUTHENTICATION_OR_UNAVAILABLE,
    NO_INTERNET,
    CAPTIVE_PORTAL,
    TIMEOUT,
    UNKNOWN,
}

data class WifiConnectionVerification(
    val actualSsid: String?,
    val expectedSsid: String,
    val ssidMatches: Boolean,
    val hasRealInternet: Boolean,
    val status: ConnectionVerificationStatus,
)

enum class ConnectionVerificationStatus {
    FULLY_CONNECTED,
    CONNECTED_NO_INTERNET,
    SSID_MISMATCH,
}
