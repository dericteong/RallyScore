package com.courtside.pickleball.domain

class PickleballScoringEngine {
    fun recordRallyWinner(state: GameState, rallyWinner: Team): GameState {
        if (state.status is GameStatus.Complete) return state

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
        val nextScore = state.scoreFor(state.servingTeam) + 1
        return state.withScore(state.servingTeam, nextScore)
    }

    private fun scorePointFor(state: GameState, team: Team): GameState {
        val nextScore = state.scoreFor(team) + 1
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
