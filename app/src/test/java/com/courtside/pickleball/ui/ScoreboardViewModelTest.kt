package com.courtside.pickleball.ui

import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.sync.ScoreboardStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardViewModelTest {
    @Test
    fun recordRallyWinnerUpdatesScoreAndEnablesUndo() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )

        viewModel.recordRallyWinner(Team.A)

        assertEquals(1, viewModel.state.value.teamAScore)
        assertTrue(viewModel.canUndo())
    }

    @Test
    fun undoRestoresPreviousGameState() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )
        val started = viewModel.state.value

        viewModel.recordRallyWinner(Team.A)
        viewModel.undo()

        assertEquals(started, viewModel.state.value)
        assertFalse(viewModel.canUndo())
    }

    @Test
    fun startMatchUsesSelectedStartingTeamAndClearsUndoHistory() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )
        viewModel.recordRallyWinner(Team.A)

        viewModel.startMatch(
            teamAName = "Chang & Fung",
            teamBName = "Lee & Wong",
            teamAPlayer1 = "Chang",
            teamAPlayer2 = "Fung",
            teamBPlayer1 = "Lee",
            teamBPlayer2 = "Wong",
            startingTeam = Team.B
        )

        assertEquals(0, viewModel.state.value.teamAScore)
        assertEquals(0, viewModel.state.value.teamBScore)
        assertEquals(Team.B, viewModel.state.value.servingTeam)
        assertEquals(ServerNumber.Two, viewModel.state.value.serverNumber)
        assertEquals("Chang & Fung", viewModel.state.value.settings.teamAName)
        assertEquals("Lee & Wong", viewModel.state.value.settings.teamBName)
        assertFalse(viewModel.canUndo())
        assertTrue(viewModel.matchActive.value)
    }

    @Test
    fun rallyInputBeforeMatchStartIsIgnored() {
        val viewModel = newViewModel()

        viewModel.recordRallyWinner(Team.A)

        assertEquals(GameState(), viewModel.state.value)
        assertFalse(viewModel.canUndo())
        assertFalse(viewModel.matchActive.value)
    }

    @Test
    fun endMatchDisablesRallyInputAndUndo() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )
        viewModel.recordRallyWinner(Team.A)

        viewModel.endMatch()
        viewModel.recordRallyWinner(Team.A)

        assertEquals(1, viewModel.state.value.teamAScore)
        assertFalse(viewModel.canUndo())
        assertFalse(viewModel.matchActive.value)
    }

    @Test
    fun receivingTeamRallyWinUsesSharedSideOutRules() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )

        viewModel.recordRallyWinner(Team.B)

        assertEquals(0, viewModel.state.value.teamAScore)
        assertEquals(0, viewModel.state.value.teamBScore)
        assertEquals(Team.B, viewModel.state.value.servingTeam)
        assertEquals(ServerNumber.One, viewModel.state.value.serverNumber)
        assertTrue(viewModel.canUndo())
    }

    @Test
    fun undoRestoresServerTransitionAndSideOutState() {
        val viewModel = newViewModel()
        viewModel.startMatch(
            teamAName = "Team A",
            teamBName = "Team B",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            startingTeam = Team.A
        )
        val beforeRally = viewModel.state.value

        viewModel.recordRallyWinner(Team.B)
        viewModel.undo()

        assertEquals(beforeRally, viewModel.state.value)
        assertFalse(viewModel.canUndo())
    }

    @Test
    fun resetClearsHistoryAndPreservesSettings() {
        val viewModel = newViewModel()
        val settings = GameSettings(
            teamAName = "Kitchen",
            teamBName = "Baseline",
            targetScore = 15
        )

        viewModel.reset(settings)
        viewModel.recordRallyWinner(Team.A)
        viewModel.reset()

        assertEquals(GameState(settings = settings), viewModel.state.value)
        assertFalse(viewModel.canUndo())
    }

    @Test
    fun rallyInputContinuesPastTargetScore() {
        val viewModel = newViewModel()
        viewModel.reset(GameSettings(targetScore = 1, winBy = 1))
        viewModel.recordRallyWinner(Team.A)

        viewModel.recordRallyWinner(Team.A)

        assertEquals(2, viewModel.state.value.teamAScore)
        assertTrue(viewModel.canUndo())
    }

    @Test
    fun voiceAnnouncementModeCanBeUpdated() {
        val viewModel = newViewModel()

        viewModel.setVoiceAnnouncementMode(VoiceAnnouncementMode.WatchThenPhone)

        assertEquals(VoiceAnnouncementMode.WatchThenPhone, viewModel.voiceAnnouncementMode.value)
        viewModel.setVoiceAnnouncementMode(VoiceAnnouncementMode.PhoneOnly)
    }

    private fun newViewModel(): ScoreboardViewModel =
        ScoreboardViewModel(ScoreboardStore())
}
