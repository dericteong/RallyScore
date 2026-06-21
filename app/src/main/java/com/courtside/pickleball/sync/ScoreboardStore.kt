package com.courtside.pickleball.sync

import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScoreboardStore(
    private val scoringEngine: PickleballScoringEngine = PickleballScoringEngine()
) {
    private val history = mutableListOf<GameState>()
    private val _state = MutableStateFlow(GameState())
    private val _matchActive = MutableStateFlow(false)

    val state: StateFlow<GameState> = _state.asStateFlow()
    val matchActive: StateFlow<Boolean> = _matchActive.asStateFlow()

    fun startMatch(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        startingTeam: Team
    ): GameState {
        history.clear()
        val next = GameState(
            servingTeam = startingTeam,
            settings = GameSettings(
                teamAName = teamAName.trim().ifEmpty { "Team A" },
                teamBName = teamBName.trim().ifEmpty { "Team B" },
                teamAPlayer1 = teamAPlayer1.trim().ifEmpty { "P1" },
                teamAPlayer2 = teamAPlayer2.trim().ifEmpty { "P2" },
                teamBPlayer1 = teamBPlayer1.trim().ifEmpty { "P3" },
                teamBPlayer2 = teamBPlayer2.trim().ifEmpty { "P4" }
            )
        )
        _state.value = next
        _matchActive.value = true
        return next
    }

    fun recordRallyWinner(team: Team): GameState {
        if (!_matchActive.value) return _state.value

        val current = _state.value
        val next = scoringEngine.recordRallyWinner(current, team)
        if (next == current) return current

        history += current
        _state.value = next
        return next
    }

    fun undo(): GameState {
        if (!_matchActive.value) return _state.value

        val previous = history.removeLastOrNull() ?: return _state.value
        _state.value = previous
        return previous
    }

    fun updateTeamNames(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String
    ) {
        if (!_matchActive.value) return
        _state.value = _state.value.copy(
            settings = _state.value.settings.copy(
                teamAName = teamAName.trim().ifEmpty { "Team A" },
                teamBName = teamBName.trim().ifEmpty { "Team B" },
                teamAPlayer1 = teamAPlayer1.trim().ifEmpty { "P1" },
                teamAPlayer2 = teamAPlayer2.trim().ifEmpty { "P2" },
                teamBPlayer1 = teamBPlayer1.trim().ifEmpty { "P3" },
                teamBPlayer2 = teamBPlayer2.trim().ifEmpty { "P4" }
            )
        )
    }

    fun reset(
        settings: GameSettings = _state.value.settings,
        startingTeam: Team = Team.A
    ): GameState {
        history.clear()
        val next = GameState(settings = settings, servingTeam = startingTeam)
        _state.value = next
        _matchActive.value = true
        return next
    }

    fun endMatch(): GameState {
        history.clear()
        _matchActive.value = false
        return _state.value
    }

    fun canUndo(): Boolean = _matchActive.value && history.isNotEmpty()

    fun restore(state: GameState, matchActive: Boolean) {
        history.clear()
        _state.value = state
        _matchActive.value = matchActive
    }
}
