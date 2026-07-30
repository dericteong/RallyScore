package com.courtside.pickleball.ui.ads

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the "Remove Ads" entitlement.
 *
 * The purchase itself is a STUB for now: [purchaseRemoveAds] grants the entitlement locally so the
 * button and the layout-revert behaviour can be built and tested before the `remove_ads` in-app
 * product exists in Play Console. When Play Billing is added, replace only the body of
 * [purchaseRemoveAds] with the real purchase/acknowledge flow and query the entitlement on launch
 * from BillingClient - callers and UI stay the same.
 */
object RemoveAdsManager {
    private const val PREFS_NAME = "rallyscore_ads"
    private const val KEY_ADS_REMOVED = "ads_removed"

    private val _hasRemovedAds = MutableStateFlow(false)
    val hasRemovedAds: StateFlow<Boolean> = _hasRemovedAds.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        _hasRemovedAds.value = ctx
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADS_REMOVED, false)
    }

    /**
     * STUB entitlement grant. Persists so the revert survives restarts, matching how a real
     * purchase would behave. To re-test with ads back on, clear the app's storage.
     *
     * TODO(billing): replace with the Play Billing purchase flow for the `remove_ads` product;
     * set the entitlement only after the purchase is confirmed and acknowledged.
     */
    fun purchaseRemoveAds() {
        _hasRemovedAds.value = true
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_ADS_REMOVED, true)
            ?.apply()
    }
}

/**
 * Whether ads are currently shown, provided at the tablet scoreboard root. Composables that adapt
 * to the ad layout (call bar height/text, score number size) read this instead of the compile-time
 * [ADS_ENABLED], so buying Remove Ads reverts them to their original no-ads sizing at runtime.
 */
val LocalAdsShown = staticCompositionLocalOf { false }
