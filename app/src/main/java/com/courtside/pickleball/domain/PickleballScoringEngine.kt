package com.courtside.pickleball.domain

class PickleballScoringEngine {
    fun recordRallyWinner(state: GameState, rallyWinner: Team): GameState {
        if (state.status is GameStatus.Complete) return state

        return if (rallyWinner == state.servingTeam) {
            scorePoint(state)
        } else {
            loseServe(state)
        }
    }

    private fun scorePoint(state: GameState): GameState {
        val nextScore = state.scoreFor(state.servingTeam) + 1
        val nextState = state.withScore(state.servingTeam, nextScore)
        return nextState.copy(status = statusFor(nextState, state.servingTeam))
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

    private fun statusFor(state: GameState, scoringTeam: Team): GameStatus {
        val score = state.scoreFor(scoringTeam)
        val opponentScore = state.scoreFor(scoringTeam.opponent())
        return if (score >= state.settings.targetScore && score - opponentScore >= state.settings.winBy) {
            GameStatus.Complete(scoringTeam)
        } else {
            GameStatus.InProgress
        }
    }
}
