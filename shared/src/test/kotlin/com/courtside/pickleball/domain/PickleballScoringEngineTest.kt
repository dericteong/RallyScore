package com.courtside.pickleball.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        assertTrue(next.status is GameStatus.InProgress)
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

        assertTrue(next.status is GameStatus.InProgress)
        assertEquals(15, next.teamAScore)
    }

    @Test
    fun completedGameIgnoresFurtherRallyInput() {
        val complete = GameState(
            teamAScore = 11,
            teamBScore = 8,
            status = GameStatus.Complete(Team.A)
        )

        val next = engine.recordRallyWinner(complete, Team.A)

        assertEquals(complete, next)
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
