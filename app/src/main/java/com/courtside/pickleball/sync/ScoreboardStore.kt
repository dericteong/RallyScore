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

    fun startMatch(teamAName: String, teamBName: String, startingTeam: Team): GameState {
        history.clear()
        val next = GameState(
            servingTeam = startingTeam,
            settings = GameSettings(
                teamAName = teamAName.trim().ifEmpty { "Team A" },
                teamBName = teamBName.trim().ifEmpty { "Team B" }
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
