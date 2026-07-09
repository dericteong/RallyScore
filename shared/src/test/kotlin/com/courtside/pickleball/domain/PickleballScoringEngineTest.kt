package com.courtside.pickleball.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PickleballScoringEngineTest {
    private val engine = PickleballScoringEngine()

    @Test
    fun initialStateStartsAtZeroZeroTwo() {
        val state = GameState()

        assertEquals(0, state.teamAScore)
        assertEquals(0, state.teamBScore)
        assertEquals(Team.A, state.servingTeam)
        assertEquals(ServerNumber.Two, state.serverNumber)
        assertEquals("0 - 0 - 2", state.scoreCall)
    }

    @Test
    fun servingTeamScoresWhenWinningRally() {
        val next = engine.recordRallyWinner(GameState(), Team.A)

        assertEquals(1, next.teamAScore)
        assertEquals(0, next.teamBScore)
        assertEquals(Team.A, next.servingTeam)
        assertEquals(ServerNumber.Two, next.serverNumber)
        assertEquals("1 - 0 - 2", next.scoreCall)
    }

    @Test
    fun firstServerExceptionSidesOutImmediately() {
        val next = engine.recordRallyWinner(GameState(), Team.B)

        assertEquals(Team.B, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals(false, next.isFirstServerException)
        assertEquals("0 - 0 - 1", next.scoreCall)
    }

    @Test
    fun normalServerOneLossMovesToServerTwo() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(8, next.teamAScore)
        assertEquals(6, next.teamBScore)
        assertEquals(Team.A, next.servingTeam)
        assertEquals(ServerNumber.Two, next.serverNumber)
        assertEquals("8 - 6 - 2", next.scoreCall)
    }

    @Test
    fun normalServerTwoLossSidesOut() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(Team.B, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals("6 - 8 - 1", next.scoreCall)
    }

    @Test
    fun scoreCallAlwaysUsesServingTeamFirst() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("6 - 8 - 1", state.scoreCall)
    }

    @Test
    fun spokenScoreCallUsesServingTeamFirst() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("six eight one", state.spokenScoreCall())
    }

    @Test
    fun spokenScoreCallUsesWordsAboveTwenty() {
        val state = GameState(
            teamAScore = 21,
            teamBScore = 20,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        assertEquals("twenty one twenty two", state.spokenScoreCall())
    }

    @Test
    fun gameKeepsGoingPastElevenForTimedPlay() {
        val state = GameState(
            teamAScore = 10,
            teamBScore = 8,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(11, next.teamAScore)
    }

    @Test
    fun scoreCanContinueAboveEleven() {
        val state = GameState(
            teamAScore = 14,
            teamBScore = 10,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(15, next.teamAScore)
    }

    @Test
    fun servingTeamScoreCapsAtNinetyNine() {
        val state = GameState(
            teamAScore = 99,
            teamBScore = 10,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(99, next.teamAScore)
    }

    @Test
    fun rallyModeReceivingTeamScoreCapsAtNinetyNine() {
        val state = GameState(
            teamAScore = 10,
            teamBScore = 99,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(99, next.teamBScore)
    }

    @Test
    fun rallyModeServingTeamScoresAndKeepsServe() {
        val state = GameState(
            teamAScore = 4,
            teamBScore = 3,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(5, next.teamAScore)
        assertEquals(3, next.teamBScore)
        assertEquals(Team.A, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals("5 - 3 - 1", next.scoreCall)
    }

    @Test
    fun rallyModeReceivingTeamScoresAndMovesServerOneToServerTwo() {
        val state = GameState(
            teamAScore = 7,
            teamBScore = 5,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(7, next.teamAScore)
        assertEquals(6, next.teamBScore)
        assertEquals(Team.A, next.servingTeam)
        assertEquals(ServerNumber.Two, next.serverNumber)
        assertEquals("7 - 6 - 2", next.scoreCall)
    }

    @Test
    fun rallyModeReceivingTeamScoresAndServerTwoSidesOut() {
        val state = GameState(
            teamAScore = 7,
            teamBScore = 5,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(7, next.teamAScore)
        assertEquals(6, next.teamBScore)
        assertEquals(Team.B, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals("6 - 7 - 1", next.scoreCall)
    }

    @Test
    fun rallyModeUsesThreeNumberSpokenScoreCall() {
        val state = GameState(
            teamAScore = 12,
            teamBScore = 9,
            servingTeam = Team.B,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        assertEquals("nine twelve two", state.spokenScoreCall())
    }

    @Test
    fun voiceAnnouncementModeRoundTripsThroughWireValues() {
        VoiceAnnouncementMode.values().forEach { mode ->
            assertEquals(mode, VoiceAnnouncementMode.fromWireValue(mode.wireValue))
        }
    }

    @Test
    fun unknownVoiceAnnouncementModeDefaultsToWatchThenPhone() {
        assertEquals(
            VoiceAnnouncementMode.WatchThenPhone,
            VoiceAnnouncementMode.fromWireValue("future-mode")
        )
    }

    @Test
    fun servingPlayerAtGameStartIsP1() {
        val state = GameState()

        assertEquals("P1", state.servingPlayerName())
    }

    @Test
    fun servingPlayerAfterTeamAScoresStillP1() {
        val state = GameState(
            teamAScore = 1,
            teamBScore = 0,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = true
        )

        assertEquals("P1", state.servingPlayerName())
    }

    @Test
    fun servingPlayerAfterSideOutToTeamBIsP4() {
        val state = GameState(
            teamAScore = 3,
            teamBScore = 4,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("P4", state.servingPlayerName())
    }

    @Test
    fun servingPlayerTeamBContinuesAfterScoring() {
        val state = GameState(
            teamAScore = 0,
            teamBScore = 1,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("P4", state.servingPlayerName())
    }

    @Test
    fun serverOneTeamAIsP1() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("P1", state.servingPlayerName())
    }

    @Test
    fun serverTwoTeamAIsP2() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        assertEquals("P2", state.servingPlayerName())
    }

    @Test
    fun serverOneTeamBIsP4() {
        val state = GameState(
            teamAScore = 6,
            teamBScore = 8,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        assertEquals("P4", state.servingPlayerName())
    }

    @Test
    fun serverTwoTeamBIsP3() {
        val state = GameState(
            teamAScore = 6,
            teamBScore = 8,
            servingTeam = Team.B,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        assertEquals("P3", state.servingPlayerName())
    }

    @Test
    fun sideOutToTeamBGivesP4AsServer1() {
        val state = GameState(
            teamAScore = 8,
            teamBScore = 6,
            servingTeam = Team.A,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.B)

        assertEquals(Team.B, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals("P4", next.servingPlayerName())
    }

    @Test
    fun sideOutToTeamAGivesP1AsServer1() {
        val state = GameState(
            teamAScore = 3,
            teamBScore = 4,
            servingTeam = Team.B,
            serverNumber = ServerNumber.Two,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(Team.A, next.servingTeam)
        assertEquals(ServerNumber.One, next.serverNumber)
        assertEquals("P1", next.servingPlayerName())
    }
}
