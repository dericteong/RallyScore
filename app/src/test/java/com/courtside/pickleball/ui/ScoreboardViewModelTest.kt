package com.courtside.pickleball.ui

import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardViewModelTest {
    @Test
    fun recordRallyWinnerUpdatesScoreAndEnablesUndo() {
        val viewModel = ScoreboardViewModel()

        viewModel.recordRallyWinner(Team.A)

        assertEquals(1, viewModel.state.value.teamAScore)
        assertTrue(viewModel.canUndo())
    }

    @Test
    fun undoRestoresPreviousGameState() {
        val viewModel = ScoreboardViewModel()

        viewModel.recordRallyWinner(Team.A)
        viewModel.undo()

        assertEquals(GameState(), viewModel.state.value)
        assertFalse(viewModel.canUndo())
    }

    @Test
    fun resetClearsHistoryAndPreservesSettings() {
        val viewModel = ScoreboardViewModel()
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
        val viewModel = ScoreboardViewModel()
        viewModel.reset(GameSettings(targetScore = 1, winBy = 1))
        viewModel.recordRallyWinner(Team.A)

        viewModel.recordRallyWinner(Team.A)

        assertEquals(2, viewModel.state.value.teamAScore)
        assertTrue(viewModel.canUndo())
    }
}
