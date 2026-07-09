package com.courtside.pickleball.ui

import androidx.lifecycle.ViewModel
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.player.Player
import com.courtside.pickleball.player.PlayerRepository
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.sync.PhoneUiSyncRequest
import com.courtside.pickleball.sync.ScoreboardStore
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletCommand
import com.courtside.pickleball.sync.TabletDisplayState
import com.courtside.pickleball.sync.TabletDisplaySync
import com.courtside.pickleball.sync.TabletPhoneCandidate
import com.courtside.pickleball.sync.TabletSetupPayload
import com.courtside.pickleball.sync.WatchTabletFallbackSync
import kotlinx.coroutines.flow.StateFlow

/** ViewModel façade for RallyScore phone and tablet screens. */
class ScoreboardViewModel(
    private val store: ScoreboardStore,
    private val phoneHub: RallyScorePhoneHub,
    private val tabletDisplaySync: TabletDisplaySync,
    private val watchTabletFallbackSync: WatchTabletFallbackSync,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    private val usesPhoneHub: Boolean = store === phoneHub.store

    val state: StateFlow<GameState> = store.state
    val matchActive: StateFlow<Boolean> = store.matchActive
    val watchConnected: StateFlow<Boolean> = phoneHub.watchConnected
    val tabletWatchConnected: StateFlow<Boolean> = watchTabletFallbackSync.watchConnected
    val voiceAnnouncementMode: StateFlow<VoiceAnnouncementMode> = phoneHub.voiceAnnouncementMode
    val remoteTabletDisplayState: StateFlow<TabletDisplayState?> = tabletDisplaySync.remoteDisplayState
    val tabletConnectionState: StateFlow<TabletConnectionState> = tabletDisplaySync.clientConnectionState
    val tabletHostConnectionState: StateFlow<TabletConnectionState> = tabletDisplaySync.hostConnectionState
    val discoveredTabletPhones: StateFlow<List<TabletPhoneCandidate>> = tabletDisplaySync.discoveredPhones
    val pairedTabletPhoneHost: StateFlow<String?> = tabletDisplaySync.pairedPhoneHost
    val phoneUiSyncRequest: StateFlow<PhoneUiSyncRequest?> = phoneHub.phoneUiSyncRequest
    val players: StateFlow<List<Player>> = playerRepository.players

    /** Starts a new local or phone-owned match from setup values. */
    fun startMatch(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat,
        startingTeam: Team
    ) {
        if (usesPhoneHub) {
            phoneHub.startMatch(
                teamAName,
                teamBName,
                teamAPlayer1,
                teamAPlayer2,
                teamBPlayer1,
                teamBPlayer2,
                scoringFormat,
                startingTeam
            )
        } else {
            store.startMatch(
                teamAName,
                teamBName,
                teamAPlayer1,
                teamAPlayer2,
                teamBPlayer1,
                teamBPlayer2,
                scoringFormat,
                startingTeam
            )
        }
    }

    /** Records a rally winner against the currently active source-of-truth match. */
    fun recordRallyWinner(team: Team): GameState {
        return store.recordRallyWinner(team)
    }

    /** Requests an undo on the active source-of-truth match. */
    fun undo() {
        store.undo()
    }

    /** Applies a score correction to the active match. */
    fun adjustScore(team: Team, delta: Int) {
        store.adjustScore(team, delta)
    }

    /** Applies a serving-state correction to the active match. */
    fun adjustServeState(servingTeam: Team, serverNumber: ServerNumber) {
        store.adjustServeState(servingTeam, serverNumber)
    }

    /** Resets the active match while preserving the provided settings. */
    fun reset(
        settings: GameSettings = state.value.settings,
        startingTeam: Team = Team.A
    ) {
        if (usesPhoneHub) {
            phoneHub.reset(settings, startingTeam)
        } else {
            store.reset(settings, startingTeam)
        }
    }

    /** Ends the active match. */
    fun endMatch() {
        if (usesPhoneHub) {
            phoneHub.endMatch()
        } else {
            store.endMatch()
        }
    }

    /** Updates setup labels for the active match without changing the live score. */
    fun updateTeamNames(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat = state.value.settings.scoringFormat
    ) {
        store.updateTeamNames(
            teamAName,
            teamBName,
            teamAPlayer1,
            teamAPlayer2,
            teamBPlayer1,
            teamBPlayer2,
            scoringFormat
        )
    }

    /** Sends a connected-tablet command through the current sync transport. */
    fun sendTabletCommand(command: TabletCommand) {
        tabletDisplaySync.sendTabletCommand(command)
    }

    /** Sends setup intent from a connected tablet to the paired phone host. */
    fun sendTabletSetupCommand(
        hostId: String?,
        command: TabletCommand,
        payload: TabletSetupPayload
    ): Boolean = tabletDisplaySync.sendTabletSetupCommand(hostId, command, payload)

    /** Clears the currently paired phone host on tablet-sized devices. */
    fun forgetPairedTabletPhone() {
        tabletDisplaySync.forgetPairedPhone()
    }

    /** Pairs a tablet client with a discovered phone host. */
    fun pairTabletToPhone(hostId: String): Boolean =
        tabletDisplaySync.pairToDiscoveredPhone(hostId)

    /** Exposes the local court code used for pairing and court selection. */
    fun localCourtCode(): String = phoneHub.courtCode()

    /** Updates the shared connected voice-announcement mode. */
    fun setVoiceAnnouncementMode(mode: VoiceAnnouncementMode) {
        phoneHub.setVoiceAnnouncementMode(mode)
    }

    /** Refreshes the current Wear connection state from Google Play Services. */
    fun refreshWatchConnection() {
        phoneHub.refreshConnectedNodes()
    }

    /** Indicates whether undo is currently available for the active match. */
    fun canUndo(): Boolean = store.canUndo()

    fun addPlayer(name: String) {
        playerRepository.addPlayer(name)
    }

    fun renamePlayer(id: String, name: String) {
        playerRepository.renamePlayer(id, name)
    }

    fun deletePlayer(id: String) {
        playerRepository.deletePlayer(id)
    }

    fun deleteAllPlayers() {
        playerRepository.deleteAllPlayers()
    }

    fun saveMatchPlayers(names: List<String>) {
        playerRepository.markPlayersPlayed(names)
    }
}
