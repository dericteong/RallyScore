package com.courtside.pickleball.sync

import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardStoreTest {
    @Test
    fun updateTeamNamesIsIgnoredWhenMatchIsNotActive() {
        val store = ScoreboardStore()
        val initial = store.state.value

        store.updateTeamNames(
            teamAName = "Blue",
            teamBName = "Green",
            teamAPlayer1 = "A1",
            teamAPlayer2 = "A2",
            teamBPlayer1 = "B1",
            teamBPlayer2 = "B2"
        )

        assertEquals(initial, store.state.value)
        assertFalse(store.matchActive.value)
    }

    @Test
    fun updateTeamNamesCanAlsoUpdateScoringFormat() {
        val store = ScoreboardStore()
        store.startMatch(
            teamAName = "My Team",
            teamBName = "Opp Team",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            scoringFormat = ScoringFormat.Traditional,
            startingTeam = Team.A
        )

        store.updateTeamNames(
            teamAName = "Blue",
            teamBName = "Green",
            teamAPlayer1 = "A1",
            teamAPlayer2 = "A2",
            teamBPlayer1 = "B1",
            teamBPlayer2 = "B2",
            scoringFormat = ScoringFormat.Rally
        )

        assertEquals("Blue", store.state.value.settings.teamAName)
        assertEquals("Green", store.state.value.settings.teamBName)
        assertEquals(ScoringFormat.Rally, store.state.value.settings.scoringFormat)
    }

    @Test
    fun adjustScoreClampsAtZeroAndDoesNotCreateUndoWhenNothingChanges() {
        val store = ScoreboardStore()
        store.startMatch(
            teamAName = "My Team",
            teamBName = "Opp Team",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            scoringFormat = ScoringFormat.Traditional,
            startingTeam = Team.A
        )

        val unchanged = store.adjustScore(Team.A, -1)

        assertEquals(0, unchanged.teamAScore)
        assertFalse(store.canUndo())
    }

    @Test
    fun endMatchClearsUndoButKeepsLastVisibleState() {
        val store = ScoreboardStore()
        store.startMatch(
            teamAName = "My Team",
            teamBName = "Opp Team",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            scoringFormat = ScoringFormat.Traditional,
            startingTeam = Team.A
        )
        store.recordRallyWinner(Team.A)

        val ended = store.endMatch()

        assertEquals(1, ended.teamAScore)
        assertFalse(store.matchActive.value)
        assertFalse(store.canUndo())
        assertEquals(ended, store.state.value)
    }

    @Test
    fun restoreReplacesStateAndClearsOldUndoHistory() {
        val store = ScoreboardStore()
        store.startMatch(
            teamAName = "My Team",
            teamBName = "Opp Team",
            teamAPlayer1 = "P1",
            teamAPlayer2 = "P2",
            teamBPlayer1 = "P3",
            teamBPlayer2 = "P4",
            scoringFormat = ScoringFormat.Traditional,
            startingTeam = Team.A
        )
        store.recordRallyWinner(Team.A)

        val restored = GameState(
            teamAScore = 8,
            teamBScore = 7,
            servingTeam = Team.B,
            serverNumber = ServerNumber.One,
            isFirstServerException = false,
            settings = GameSettings(scoringFormat = ScoringFormat.Rally)
        )

        store.restore(restored, matchActive = true)

        assertEquals(restored, store.state.value)
        assertTrue(store.matchActive.value)
        assertFalse(store.canUndo())
    }
}
