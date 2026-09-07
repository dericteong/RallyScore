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

/** Phone and tablet host activity for PickleCast. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerRepository.initialize(applicationContext)
        RallyScorePhoneHub.initialize(applicationContext)
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
