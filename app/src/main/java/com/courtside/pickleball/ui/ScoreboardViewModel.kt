package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.sync.ScoreboardStore
import com.courtside.pickleball.sync.TabletDisplayState
import com.courtside.pickleball.sync.TabletDisplaySync
import kotlinx.coroutines.flow.StateFlow

class ScoreboardViewModel(
    private val store: ScoreboardStore = RallyScorePhoneHub.store
) : ViewModel() {
    val state: StateFlow<GameState> = store.state
    val matchActive: StateFlow<Boolean> = store.matchActive
    val watchConnected: StateFlow<Boolean> = RallyScorePhoneHub.watchConnected
    val voiceAnnouncementMode: StateFlow<VoiceAnnouncementMode> = RallyScorePhoneHub.voiceAnnouncementMode
    val remoteTabletDisplayState: StateFlow<TabletDisplayState?> = TabletDisplaySync.remoteDisplayState

    fun startMatch(teamAName: String, teamBName: String, startingTeam: Team) {
        store.startMatch(teamAName, teamBName, startingTeam)
    }

    fun recordRallyWinner(team: Team): GameState {
        return store.recordRallyWinner(team)
    }

    fun undo() {
        store.undo()
    }

    fun reset(
        settings: GameSettings = state.value.settings,
        startingTeam: Team = Team.A
    ) {
        store.reset(settings, startingTeam)
    }

    fun endMatch() {
        store.endMatch()
    }

    fun setVoiceAnnouncementMode(mode: VoiceAnnouncementMode) {
        RallyScorePhoneHub.setVoiceAnnouncementMode(mode)
    }

    fun refreshWatchConnection() {
        RallyScorePhoneHub.refreshConnectedNodes()
    }

    fun canUndo(): Boolean = store.canUndo()
}
