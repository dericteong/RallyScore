package com.courtside.pickleball.sync

import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory authoritative match store for phone-only and tablet-only ownership paths.
 *
 * The store keeps undo history local to the current match and delegates all scoring rules to the
 * shared [PickleballScoringEngine].
 */
class ScoreboardStore(
    private val scoringEngine: PickleballScoringEngine = PickleballScoringEngine()
) {
    private val history = mutableListOf<GameState>()
    private val _state = MutableStateFlow(GameState())
    private val _matchActive = MutableStateFlow(false)

    val state: StateFlow<GameState> = _state.asStateFlow()
    val matchActive: StateFlow<Boolean> = _matchActive.asStateFlow()

    /** Starts a new match and clears any undo history from the previous match. */
    fun startMatch(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat,
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
                teamBPlayer2 = teamBPlayer2.trim().ifEmpty { "P4" },
                scoringFormat = scoringFormat
            )
        )
        _matchActive.value = true
        _state.value = next
        return next
    }

    /** Records one rally winner through the shared scoring engine. */
    fun recordRallyWinner(team: Team): GameState {
        if (!_matchActive.value) return _state.value

        val current = _state.value
        val next = scoringEngine.recordRallyWinner(current, team)
        if (next == current) return current

        history += current
        _state.value = next
        return next
    }

    /** Restores the immediately previous in-memory match state when available. */
    fun undo(): GameState {
        if (!_matchActive.value) return _state.value

        val previous = history.removeLastOrNull() ?: return _state.value
        _state.value = previous
        return previous
    }

    /** Adjusts a team's score for correction flows while keeping the change undoable. */
    fun adjustScore(team: Team, delta: Int): GameState {
        if (!_matchActive.value || delta == 0) return _state.value

        val current = _state.value
        val nextScore = (current.scoreFor(team) + delta).coerceAtLeast(0)
        if (nextScore == current.scoreFor(team)) return current

        history += current
        _state.value = current.withScore(team, nextScore)
        return _state.value
    }

    /** Adjusts serving side or server number for correction flows while keeping the change undoable. */
    fun adjustServeState(servingTeam: Team, serverNumber: ServerNumber): GameState {
        if (!_matchActive.value) return _state.value

        val current = _state.value
        if (current.servingTeam == servingTeam && current.serverNumber == serverNumber) {
            return current
        }

        history += current
        _state.value = current.copy(
            servingTeam = servingTeam,
            serverNumber = serverNumber
        )
        return _state.value
    }

    /** Updates team labels without changing the current live score. */
    fun updateTeamNames(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat = _state.value.settings.scoringFormat
    ) {
        if (!_matchActive.value) return
        _state.value = _state.value.copy(
            settings = _state.value.settings.copy(
                teamAName = teamAName.trim().ifEmpty { "Team A" },
                teamBName = teamBName.trim().ifEmpty { "Team B" },
                teamAPlayer1 = teamAPlayer1.trim().ifEmpty { "P1" },
                teamAPlayer2 = teamAPlayer2.trim().ifEmpty { "P2" },
                teamBPlayer1 = teamBPlayer1.trim().ifEmpty { "P3" },
                teamBPlayer2 = teamBPlayer2.trim().ifEmpty { "P4" },
                scoringFormat = scoringFormat
            )
        )
    }

    /** Resets the live state to a fresh match using the supplied settings and starting team. */
    fun reset(
        settings: GameSettings = _state.value.settings,
        startingTeam: Team = Team.A
    ): GameState {
        history.clear()
        val next = GameState(settings = settings, servingTeam = startingTeam)
        _matchActive.value = true
        _state.value = next
        return next
    }

    /** Ends the active match and clears undo history while preserving the last visible score state. */
    fun endMatch(): GameState {
        history.clear()
        _matchActive.value = false
        return _state.value
    }

    /** Returns whether undo is currently available for the active match. */
    fun canUndo(): Boolean = _matchActive.value && history.isNotEmpty()

    /** Replaces the live state with a restored snapshot after process restart or remote sync. */
    fun restore(state: GameState, matchActive: Boolean) {
        history.clear()
        _matchActive.value = matchActive
        _state.value = state
    }
}
