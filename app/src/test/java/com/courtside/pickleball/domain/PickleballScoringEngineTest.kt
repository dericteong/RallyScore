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
    fun gameCompletesAtElevenWithTwoPointMargin() {
        val state = GameState(
            teamAScore = 10,
            teamBScore = 8,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertEquals(GameStatus.Complete(Team.A), next.status)
    }

    @Test
    fun gameDoesNotCompleteWithoutTwoPointMargin() {
        val state = GameState(
            teamAScore = 10,
            teamBScore = 10,
            servingTeam = Team.A,
            serverNumber = ServerNumber.One,
            isFirstServerException = false
        )

        val next = engine.recordRallyWinner(state, Team.A)

        assertTrue(next.status is GameStatus.InProgress)
        assertEquals(11, next.teamAScore)
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
}
