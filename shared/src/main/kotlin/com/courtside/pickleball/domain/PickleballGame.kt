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

fun GameState.spokenScoreCall(): String =
    "${servingScore.spokenNumber()} ${receivingScore.spokenNumber()} ${serverNumber.spokenNumber()}"

private fun ServerNumber.spokenNumber(): String = when (this) {
    ServerNumber.One -> "one"
    ServerNumber.Two -> "two"
}

private fun Int.spokenNumber(): String = when (this) {
    0 -> "zero"
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    11 -> "eleven"
    12 -> "twelve"
    13 -> "thirteen"
    14 -> "fourteen"
    15 -> "fifteen"
    16 -> "sixteen"
    17 -> "seventeen"
    18 -> "eighteen"
    19 -> "nineteen"
    20 -> "twenty"
    else -> toString()
}
