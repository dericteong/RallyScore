package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScoreboardViewModel(
    private val scoringEngine: PickleballScoringEngine = PickleballScoringEngine()
) : ViewModel() {
    private val history = mutableListOf<GameState>()
    private val _state = MutableStateFlow(GameState())

    val state: StateFlow<GameState> = _state.asStateFlow()

    fun startMatch(teamAName: String, teamBName: String, startingTeam: Team) {
        history.clear()
        _state.value = GameState(
            servingTeam = startingTeam,
            settings = GameSettings(
                teamAName = teamAName.trim().ifEmpty { "Team A" },
                teamBName = teamBName.trim().ifEmpty { "Team B" }
            )
        )
    }

    fun recordRallyWinner(team: Team): GameState {
        val current = _state.value
        val next = scoringEngine.recordRallyWinner(current, team)
        if (next == current) return current

        history += current
        _state.value = next
        return next
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _state.value = previous
    }

    fun reset(
        settings: GameSettings = _state.value.settings,
        startingTeam: Team = Team.A
    ) {
        history.clear()
        _state.value = GameState(settings = settings, servingTeam = startingTeam)
    }

    fun canUndo(): Boolean = history.isNotEmpty()
}
