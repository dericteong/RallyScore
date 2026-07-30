package com.courtside.pickleball.ui.ads

/**
 * Master on/off switch for in-app ads.
 *
 * Flipping this to `false` removes every ad from the app in one place, without deleting the
 * integration - useful for a clean no-ads build, or to disable ads fast if AdMob ever flags the
 * account. Ads are additionally gated to the tablet layout at the call site.
 */
internal const val ADS_ENABLED = true

/**
 * Google's official test banner ad unit. ALWAYS use test ids during development: requesting the
 * real ad unit and clicking/viewing those ads yourself violates AdMob policy and can get the
 * account suspended.
 *
 * For a release build, point [BANNER_AD_UNIT_ID] at the real `ca-app-pub-.../...` banner unit
 * created under this app in the AdMob console. Keep [USING_TEST_ADS] in sync so it is obvious
 * which is live.
 */
internal const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

/** Set false and swap in the real banner unit id when going live. */
internal const val USING_TEST_ADS = true
internal const val BANNER_AD_UNIT_ID = TEST_BANNER_AD_UNIT_ID
