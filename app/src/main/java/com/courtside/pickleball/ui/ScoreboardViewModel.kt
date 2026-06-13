package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.sync.ScoreboardStore
import kotlinx.coroutines.flow.StateFlow

class ScoreboardViewModel(
    private val store: ScoreboardStore = RallyScorePhoneHub.store
) : ViewModel() {
    val state: StateFlow<GameState> = store.state
    val matchActive: StateFlow<Boolean> = store.matchActive
    val watchConnected: StateFlow<Boolean> = RallyScorePhoneHub.watchConnected

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

    fun canUndo(): Boolean = store.canUndo()
}
