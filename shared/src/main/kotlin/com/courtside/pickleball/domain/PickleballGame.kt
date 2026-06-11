package com.courtside.pickleball.domain

enum class Team {
    A,
    B;

    fun opponent(): Team = if (this == A) B else A
}

enum class ServerNumber {
    One,
    Two
}

sealed interface GameStatus {
    data object InProgress : GameStatus
    data class Complete(val winner: Team) : GameStatus
}

data class GameSettings(
    val teamAName: String = "Team A",
    val teamBName: String = "Team B",
    val targetScore: Int = 11,
    val winBy: Int = 2
)

data class GameState(
    val teamAScore: Int = 0,
    val teamBScore: Int = 0,
    val servingTeam: Team = Team.A,
    val serverNumber: ServerNumber = ServerNumber.Two,
    val isFirstServerException: Boolean = true,
    val settings: GameSettings = GameSettings(),
    val status: GameStatus = GameStatus.InProgress
) {
    val servingScore: Int
        get() = scoreFor(servingTeam)

    val receivingScore: Int
        get() = scoreFor(servingTeam.opponent())

    val scoreCall: String
        get() = "$servingScore - $receivingScore - ${serverNumber.displayValue}"

    fun scoreFor(team: Team): Int = when (team) {
        Team.A -> teamAScore
        Team.B -> teamBScore
    }

    fun teamName(team: Team): String = when (team) {
        Team.A -> settings.teamAName
        Team.B -> settings.teamBName
    }

    fun withScore(team: Team, score: Int): GameState = when (team) {
        Team.A -> copy(teamAScore = score)
        Team.B -> copy(teamBScore = score)
    }
}

val ServerNumber.displayValue: Int
    get() = when (this) {
        ServerNumber.One -> 1
        ServerNumber.Two -> 2
    }
