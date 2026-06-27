package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.sync.ScoreboardStore
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletCommand
import com.courtside.pickleball.sync.TabletDisplayState
import com.courtside.pickleball.sync.TabletDisplaySync
import kotlinx.coroutines.flow.StateFlow

class ScoreboardViewModel(
    private val store: ScoreboardStore = RallyScorePhoneHub.store
) : ViewModel() {
    private val usesPhoneHub: Boolean = store === RallyScorePhoneHub.store

    val state: StateFlow<GameState> = store.state
    val matchActive: StateFlow<Boolean> = store.matchActive
    val watchConnected: StateFlow<Boolean> = RallyScorePhoneHub.watchConnected
    val voiceAnnouncementMode: StateFlow<VoiceAnnouncementMode> = RallyScorePhoneHub.voiceAnnouncementMode
    val remoteTabletDisplayState: StateFlow<TabletDisplayState?> = TabletDisplaySync.remoteDisplayState
    val tabletConnectionState: StateFlow<TabletConnectionState> = TabletDisplaySync.connectionState

    fun startMatch(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        startingTeam: Team
    ) {
        if (usesPhoneHub) {
            RallyScorePhoneHub.startMatch(
                teamAName,
                teamBName,
                teamAPlayer1,
                teamAPlayer2,
                teamBPlayer1,
                teamBPlayer2,
                startingTeam
            )
        } else {
            store.startMatch(
                teamAName,
                teamBName,
                teamAPlayer1,
                teamAPlayer2,
                teamBPlayer1,
                teamBPlayer2,
                startingTeam
            )
        }
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
        if (usesPhoneHub) {
            RallyScorePhoneHub.reset(settings, startingTeam)
        } else {
            store.reset(settings, startingTeam)
        }
    }

    fun endMatch() {
        if (usesPhoneHub) {
            RallyScorePhoneHub.endMatch()
        } else {
            store.endMatch()
        }
    }

    fun updateTeamNames(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String
    ) {
        store.updateTeamNames(teamAName, teamBName, teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2)
    }

    fun sendTabletCommand(command: TabletCommand) {
        TabletDisplaySync.sendTabletCommand(command)
    }

    fun forgetPairedTabletPhone() {
        TabletDisplaySync.forgetPairedPhone()
    }

    fun setVoiceAnnouncementMode(mode: VoiceAnnouncementMode) {
        RallyScorePhoneHub.setVoiceAnnouncementMode(mode)
    }

    fun refreshWatchConnection() {
        RallyScorePhoneHub.refreshConnectedNodes()
    }

    fun canUndo(): Boolean = store.canUndo()
}
