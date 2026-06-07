package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScoreboardViewModel(
    private val scoringEngine: PickleballScoringEngine = PickleballScoringEngine()
) : ViewModel() {
    private val history = mutableListOf<GameState>()
    private val _state = MutableStateFlow(GameState())

    val state: StateFlow<GameState> = _state.asStateFlow()

    fun recordRallyWinner(team: Team) {
        val current = _state.value
        history += current
        _state.value = scoringEngine.recordRallyWinner(current, team)
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _state.value = previous
    }

    fun reset(settings: GameSettings = _state.value.settings) {
        history.clear()
        _state.value = GameState(settings = settings)
    }

    fun canUndo(): Boolean = history.isNotEmpty()
}
