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

enum class ScoringFormat {
    Traditional,
    Rally
}

sealed interface GameStatus {
    data object InProgress : GameStatus
    data class Complete(val winner: Team) : GameStatus
}

data class GameSettings(
    val teamAName: String = "Team A",
    val teamBName: String = "Team B",
    val teamAPlayer1: String = "P1",
    val teamAPlayer2: String = "P2",
    val teamBPlayer1: String = "P3",
    val teamBPlayer2: String = "P4",
    val scoringFormat: ScoringFormat = ScoringFormat.Traditional,
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
        get() = when (settings.scoringFormat) {
            ScoringFormat.Traditional,
            ScoringFormat.Rally -> "$servingScore - $receivingScore - ${serverNumber.displayValue}"
        }

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

    fun courtOrderedTeamName(team: Team): String {
        val isEvenScore = scoreFor(team) % 2 == 0
        return when (team) {
            Team.A -> if (isEvenScore) "${settings.teamAPlayer1} & ${settings.teamAPlayer2}"
                      else "${settings.teamAPlayer2} & ${settings.teamAPlayer1}"
            Team.B -> if (isEvenScore) "${settings.teamBPlayer1} & ${settings.teamBPlayer2}"
                      else "${settings.teamBPlayer2} & ${settings.teamBPlayer1}"
        }
    }

    fun servingPlayerName(): String {
        val effectiveServerIsOne = isFirstServerException || serverNumber == ServerNumber.One
        return when (servingTeam) {
            Team.A -> if (effectiveServerIsOne) settings.teamAPlayer1 else settings.teamAPlayer2
            Team.B -> if (effectiveServerIsOne) settings.teamBPlayer2 else settings.teamBPlayer1
        }
    }
}

val ServerNumber.displayValue: Int
    get() = when (this) {
        ServerNumber.One -> 1
        ServerNumber.Two -> 2
    }

fun GameState.spokenScoreCall(): String =
    when (settings.scoringFormat) {
        ScoringFormat.Traditional,
        ScoringFormat.Rally -> "${servingScore.spokenNumber()} ${receivingScore.spokenNumber()} ${serverNumber.spokenNumber()}"
    }

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
    else -> spokenNumberAbove20()
}

private fun Int.spokenNumberAbove20(): String {
    if (this < 100) {
        val tens = this / 10
        val ones = this % 10
        val tensWord = when (tens) {
            2 -> "twenty"
            3 -> "thirty"
            4 -> "forty"
            5 -> "fifty"
            6 -> "sixty"
            7 -> "seventy"
            8 -> "eighty"
            9 -> "ninety"
            else -> ""
        }
        return if (ones == 0) tensWord else "$tensWord ${ones.spokenNumber()}"
    }
    return toString().map { it }.joinToString(" ")
}
