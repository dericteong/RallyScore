package com.courtside.pickleball

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.courtside.pickleball.player.PlayerRepository
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.sync.TabletDisplaySync
import com.courtside.pickleball.sync.WatchTabletFallbackSync
import com.courtside.pickleball.ui.ScoreboardApp
import com.courtside.pickleball.ui.ScoreboardViewModel
import com.courtside.pickleball.ui.ads.ADS_ENABLED
import com.courtside.pickleball.ui.ads.RemoveAdsManager
import com.google.android.gms.ads.MobileAds

/** Phone and tablet host activity for RallyScore. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerRepository.initialize(applicationContext)
        RallyScorePhoneHub.initialize(applicationContext)
        if (ADS_ENABLED) {
            // One-time SDK init; safe to call again. Consent (UMP) is a separate follow-up before
            // serving real ads in EEA/UK.
            MobileAds.initialize(applicationContext)
            RemoveAdsManager.initialize(applicationContext)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val viewModel: ScoreboardViewModel = viewModel(factory = scoreboardViewModelFactory)
            ScoreboardApp(viewModel = viewModel)
        }
    }
}

/** Wires ScoreboardViewModel's dependencies explicitly at the composition root. */
private val scoreboardViewModelFactory = viewModelFactory {
    initializer {
        ScoreboardViewModel(
            store = RallyScorePhoneHub.store,
            phoneHub = RallyScorePhoneHub,
            tabletDisplaySync = TabletDisplaySync,
            watchTabletFallbackSync = WatchTabletFallbackSync,
            playerRepository = PlayerRepository
        )
    }
}
