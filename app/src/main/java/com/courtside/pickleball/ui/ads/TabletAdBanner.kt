package com.courtside.pickleball.ui.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Anchored adaptive banner for the bottom of the tablet scoreboard.
 *
 * No-op unless [ADS_ENABLED]. The slot reserves the banner's height up front so the scoreboard
 * above it does not reflow when the ad loads. Callers gate this to the tablet layout and keep a
 * clear gap from the score tap targets - AdMob prohibits ads placed where they cause mis-taps.
 */
@Composable
internal fun TabletAdBanner(modifier: Modifier = Modifier) {
    if (!ADS_ENABLED) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val adView = remember { AdView(context) }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    // Full-width anchored adaptive banner. App is landscape-locked, so this width is stable for the
    // life of the banner. Note: Google's TEST creative is a fixed 728x90 image and gets letterboxed
    // with black bars inside a wider adaptive slot on a large tablet - that's a test-ad artifact
    // only. Real ads fill the full adaptive width, so no bars in production.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        val reservedHeight = with(density) { adSize.getHeightInPixels(context).toDp() }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(reservedHeight),
            factory = {
                adView.apply {
                    setAdSize(adSize)
                    adUnitId = BANNER_AD_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
