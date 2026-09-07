package com.courtside.pickleball.domain

/**
 * Pure scoring engine for PickleCast match updates.
 *
 * The engine never auto-completes a match. It only applies the configured scoring format,
 * serve transitions, side outs, and first-server exception rules.
 */
class PickleballScoringEngine {
    /** Applies one rally result and returns the next immutable match state. */
    fun recordRallyWinner(state: GameState, rallyWinner: Team): GameState {
        return when (state.settings.scoringFormat) {
            ScoringFormat.Traditional -> {
                if (rallyWinner == state.servingTeam) {
                    scorePoint(state)
                } else {
                    loseServe(state)
                }
            }
            ScoringFormat.Rally -> {
                if (rallyWinner == state.servingTeam) {
                    scorePoint(state)
                } else {
                    loseServe(scorePointFor(state, rallyWinner))
                }
            }
        }
    }

    private fun scorePoint(state: GameState): GameState {
        val nextScore = (state.scoreFor(state.servingTeam) + 1).coerceAtMost(MAX_MATCH_SCORE)
        return state.withScore(state.servingTeam, nextScore)
    }

    private fun scorePointFor(state: GameState, team: Team): GameState {
        val nextScore = (state.scoreFor(team) + 1).coerceAtMost(MAX_MATCH_SCORE)
        return state.withScore(team, nextScore)
    }

    private fun loseServe(state: GameState): GameState {
        if (state.isFirstServerException) {
            return sideOut(state).copy(isFirstServerException = false)
        }

        return when (state.serverNumber) {
            ServerNumber.One -> state.copy(serverNumber = ServerNumber.Two)
            ServerNumber.Two -> sideOut(state)
        }
    }

    private fun sideOut(state: GameState): GameState =
        state.copy(
            servingTeam = state.servingTeam.opponent(),
            serverNumber = ServerNumber.One
        )

}
