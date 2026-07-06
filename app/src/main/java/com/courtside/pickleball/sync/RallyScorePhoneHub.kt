package com.courtside.pickleball.sync

import android.content.Context
import android.content.res.Configuration
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.player.PlayerRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneUiSyncRequest(
    val requestId: Long,
    val action: Action,
    val teamAPlayer1: String,
    val teamAPlayer2: String,
    val teamBPlayer1: String,
    val teamBPlayer2: String,
    val scoringFormat: ScoringFormat,
    val startingTeam: Team?,
    val myTeamOnTop: Boolean
) {
    enum class Action {
        StartMatch,
        ResumeMatch
    }
}

object RallyScorePhoneHub {
    private const val TAG = "RallyScorePhoneHub"
    private const val PREFS_NAME = "rallyscore_phone_match"
    private const val KEY_MATCH_ACTIVE = "match_active"
    private const val KEY_TEAM_A_NAME = "team_a_name"
    private const val KEY_TEAM_B_NAME = "team_b_name"
    private const val KEY_TEAM_A_PLAYER1 = "team_a_player1"
    private const val KEY_TEAM_A_PLAYER2 = "team_a_player2"
    private const val KEY_TEAM_B_PLAYER1 = "team_b_player1"
    private const val KEY_TEAM_B_PLAYER2 = "team_b_player2"
    private const val KEY_TEAM_A_SCORE = "team_a_score"
    private const val KEY_TEAM_B_SCORE = "team_b_score"
    private const val KEY_SERVING_TEAM = "serving_team"
    private const val KEY_SERVER_NUMBER = "server_number"
    private const val KEY_FIRST_SERVER_EXCEPTION = "first_server_exception"
    private const val KEY_SCORING_FORMAT = "scoring_format"
    private const val KEY_HOST_ID = "host_id"
    private const val KEY_SESSION_ID = "session_id"
    private const val MAX_NAME_FIELD_LENGTH = 60

    val store = ScoreboardStore()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _watchConnected = MutableStateFlow(false)
    private val _voiceAnnouncementMode = MutableStateFlow(VoiceAnnouncementMode.PhoneOnly)
    private val _phoneUiSyncRequest = MutableStateFlow<PhoneUiSyncRequest?>(null)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()
    val voiceAnnouncementMode: StateFlow<VoiceAnnouncementMode> = _voiceAnnouncementMode.asStateFlow()
    val phoneUiSyncRequest: StateFlow<PhoneUiSyncRequest?> = _phoneUiSyncRequest.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false
    @Volatile private var hostId: String = generateHostId()
    @Volatile private var sessionId: String = generateSessionId()
    @Volatile private var deviceRole: String = WearSyncContract.DEVICE_ROLE_PHONE

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        PlayerRepository.initialize(context.applicationContext)
        deviceRole = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            WearSyncContract.DEVICE_ROLE_TABLET
        } else {
            WearSyncContract.DEVICE_ROLE_PHONE
        }
        restoreIdentity(context.applicationContext)

        TabletDisplaySync.initialize(context.applicationContext)
        restorePersistedMatch(context.applicationContext)
        refreshConnectedNodes()
        if (deviceRole == WearSyncContract.DEVICE_ROLE_TABLET) {
            WatchTabletFallbackSync.initialize(context.applicationContext)
            WatchTabletFallbackSync.start(
                stateProvider = { store.state.value },
                matchActiveProvider = { store.matchActive.value },
                canUndoProvider = { store.canUndo() },
                voiceModeProvider = { _voiceAnnouncementMode.value },
                courtCodeProvider = { courtCode() },
                onWatchCommand = ::handleWatchCommand
            )
        } else {
            WatchTabletFallbackSync.stop()
        }
        TabletDisplaySync.startListener()
        TabletDisplaySync.startBroadcaster(
            stateProvider = { store.state.value },
            matchActiveProvider = { store.matchActive.value },
            canUndoProvider = { store.canUndo() },
            watchConnectedProvider = { _watchConnected.value },
            voiceModeProvider = { _voiceAnnouncementMode.value },
            hostIdProvider = { hostId },
            sessionIdProvider = { sessionId },
            onTabletCommand = { command -> handleTabletCommand(command) }
        )
        publishScoreState(store.state.value)

        scope.launch {
            store.state.collect { state ->
                persistMatch(context.applicationContext, state, store.matchActive.value)
                publishScoreState(state)
            }
        }
        scope.launch {
            store.matchActive.collect {
                persistMatch(context.applicationContext, store.state.value, it)
                publishScoreState(store.state.value)
            }
        }
        scope.launch {
            _voiceAnnouncementMode.collect {
                publishScoreState(store.state.value)
            }
        }
    }

    fun setVoiceAnnouncementMode(mode: VoiceAnnouncementMode) {
        _voiceAnnouncementMode.value = mode
    }

    fun courtCode(): String = hostId.toCourtCode()

    fun startMatch(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat,
        startingTeam: Team
    ): GameState {
        rotateSessionId()
        return store.startMatch(
            teamAName = teamAName,
            teamBName = teamBName,
            teamAPlayer1 = teamAPlayer1,
            teamAPlayer2 = teamAPlayer2,
            teamBPlayer1 = teamBPlayer1,
            teamBPlayer2 = teamBPlayer2,
            scoringFormat = scoringFormat,
            startingTeam = startingTeam
        )
    }

    fun reset(
        settings: GameSettings = store.state.value.settings,
        startingTeam: Team = Team.A
    ): GameState {
        rotateSessionId()
        return store.reset(settings = settings, startingTeam = startingTeam)
    }

    fun updateTeamNames(
        teamAName: String,
        teamBName: String,
        teamAPlayer1: String,
        teamAPlayer2: String,
        teamBPlayer1: String,
        teamBPlayer2: String,
        scoringFormat: ScoringFormat = store.state.value.settings.scoringFormat
    ) {
        store.updateTeamNames(
            teamAName = teamAName,
            teamBName = teamBName,
            teamAPlayer1 = teamAPlayer1,
            teamAPlayer2 = teamAPlayer2,
            teamBPlayer1 = teamBPlayer1,
            teamBPlayer2 = teamBPlayer2,
            scoringFormat = scoringFormat
        )
    }

    fun endMatch(): GameState {
        val current = store.endMatch()
        rotateSessionId()
        publishScoreState(current)
        return current
    }

    fun refreshConnectedNodes() {
        val context = appContext ?: return
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val connected = nodes.isNotEmpty()
                _watchConnected.value = connected
                SyncLog.debug(TAG) { "Connected Wear nodes refreshed: ${nodes.size}" }
            }
            .addOnFailureListener { error ->
                _watchConnected.value = false
                SyncLog.warn(TAG, "Unable to refresh Wear connection state", "Unable to refresh Wear nodes", error)
            }
    }

    fun handleWatchCommand(path: String) {
        // Callers arrive on binder/network threads (PhoneWearListenerService,
        // WatchTabletFallbackSync); hop to the main-immediate scope so watch commands
        // serialize with tablet commands and phone UI taps instead of racing them
        // inside ScoreboardStore.
        scope.launch {
            handleWatchCommandOnMain(path)
        }
    }

    private fun handleWatchCommandOnMain(path: String) {
        if (!store.matchActive.value &&
            path != WearSyncContract.COMMAND_START_MATCH_TEAM_A &&
            path != WearSyncContract.COMMAND_START_MATCH_TEAM_B
        ) {
            SyncLog.warn(TAG, "Ignored watch command while no phone match is active", "Ignored watch command while no phone match is active: $path")
            publishScoreState(store.state.value)
            return
        }

        val next = when (path) {
            WearSyncContract.COMMAND_START_MATCH_TEAM_A -> {
                SyncLog.debug(TAG) { "Watch command: START_MATCH_TEAM_A" }
                reset(settings = store.state.value.settings, startingTeam = Team.A)
            }
            WearSyncContract.COMMAND_START_MATCH_TEAM_B -> {
                SyncLog.debug(TAG) { "Watch command: START_MATCH_TEAM_B" }
                reset(settings = store.state.value.settings, startingTeam = Team.B)
            }
            WearSyncContract.COMMAND_A_WON_RALLY -> {
                SyncLog.debug(TAG) { "Watch command: A_WON_RALLY" }
                store.recordRallyWinner(Team.A)
            }
            WearSyncContract.COMMAND_B_WON_RALLY -> {
                SyncLog.debug(TAG) { "Watch command: B_WON_RALLY" }
                store.recordRallyWinner(Team.B)
            }
            WearSyncContract.COMMAND_UNDO -> {
                SyncLog.debug(TAG) { "Watch command: UNDO" }
                store.undo()
            }
            WearSyncContract.COMMAND_END_MATCH -> {
                SyncLog.debug(TAG) { "Watch command: END_MATCH" }
                endMatch()
            }
            else -> {
                SyncLog.warn(TAG, "Ignored unknown watch command", "Ignored unknown watch command: $path")
                return
            }
        }
        publishScoreState(next)
    }

    private fun handleTabletCommand(message: TabletCommandMessage) {
        scope.launch {
            if (!store.matchActive.value &&
                message.command != TabletCommand.StartMatch &&
                message.command != TabletCommand.ResumeMatch
            ) {
                SyncLog.warn(
                    TAG,
                    "Ignored tablet command while no phone match is active",
                    "Ignored tablet command while no phone match is active: ${message.command.wireValue}"
                )
                publishScoreState(store.state.value)
                return@launch
            }

            val next = when (message.command) {
                TabletCommand.TeamAWonRally -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_ME_WON_RALLY" }
                    store.recordRallyWinner(Team.A)
                }
                TabletCommand.TeamBWonRally -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_OPP_WON_RALLY" }
                    store.recordRallyWinner(Team.B)
                }
                TabletCommand.Undo -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_UNDO" }
                    store.undo()
                }
                TabletCommand.AdjustTeamAScoreDown -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_ADJUST_TEAM_A_SCORE_DOWN" }
                    store.adjustScore(Team.A, -1)
                }
                TabletCommand.AdjustTeamAScoreUp -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_ADJUST_TEAM_A_SCORE_UP" }
                    store.adjustScore(Team.A, 1)
                }
                TabletCommand.AdjustTeamBScoreDown -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_ADJUST_TEAM_B_SCORE_DOWN" }
                    store.adjustScore(Team.B, -1)
                }
                TabletCommand.AdjustTeamBScoreUp -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_ADJUST_TEAM_B_SCORE_UP" }
                    store.adjustScore(Team.B, 1)
                }
                TabletCommand.SetServingTeamA -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_SET_SERVING_TEAM_A" }
                    store.adjustServeState(Team.A, store.state.value.serverNumber)
                }
                TabletCommand.SetServingTeamB -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_SET_SERVING_TEAM_B" }
                    store.adjustServeState(Team.B, store.state.value.serverNumber)
                }
                TabletCommand.SetServerOne -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_SET_SERVER_ONE" }
                    store.adjustServeState(store.state.value.servingTeam, ServerNumber.One)
                }
                TabletCommand.SetServerTwo -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_SET_SERVER_TWO" }
                    store.adjustServeState(store.state.value.servingTeam, ServerNumber.Two)
                }
                TabletCommand.EndMatch -> {
                    SyncLog.debug(TAG) { "Tablet command: TABLET_END_MATCH" }
                    endMatch()
                }
                TabletCommand.StartMatch -> {
                    val payload = message.toSetupPayload() ?: run {
                        SyncLog.warn(TAG, "Ignored malformed tablet start-match payload")
                        publishScoreState(store.state.value)
                        return@launch
                    }
                    SyncLog.debug(TAG) { "Tablet command: TABLET_START_MATCH" }
                    PlayerRepository.markPlayersPlayed(payload.playerNames())
                    val next = startMatch(
                        teamAName = payload.teamAName,
                        teamBName = payload.teamBName,
                        teamAPlayer1 = payload.teamAPlayer1,
                        teamAPlayer2 = payload.teamAPlayer2,
                        teamBPlayer1 = payload.teamBPlayer1,
                        teamBPlayer2 = payload.teamBPlayer2,
                        scoringFormat = payload.scoringFormat,
                        startingTeam = payload.startingTeam ?: Team.A
                    )
                    publishPhoneUiSync(
                        action = PhoneUiSyncRequest.Action.StartMatch,
                        payload = payload
                    )
                    next
                }
                TabletCommand.ResumeMatch -> {
                    val payload = message.toSetupPayload() ?: run {
                        SyncLog.warn(TAG, "Ignored malformed tablet resume-match payload")
                        publishScoreState(store.state.value)
                        return@launch
                    }
                    SyncLog.debug(TAG) { "Tablet command: TABLET_RESUME_MATCH" }
                    PlayerRepository.markPlayersPlayed(payload.playerNames())
                    updateTeamNames(
                        teamAName = payload.teamAName,
                        teamBName = payload.teamBName,
                        teamAPlayer1 = payload.teamAPlayer1,
                        teamAPlayer2 = payload.teamAPlayer2,
                        teamBPlayer1 = payload.teamBPlayer1,
                        teamBPlayer2 = payload.teamBPlayer2,
                        scoringFormat = payload.scoringFormat
                    )
                    publishPhoneUiSync(
                        action = PhoneUiSyncRequest.Action.ResumeMatch,
                        payload = payload
                    )
                    store.state.value
                }
            }
            publishScoreState(next)
        }
    }

    private fun publishPhoneUiSync(
        action: PhoneUiSyncRequest.Action,
        payload: TabletSetupPayload
    ) {
        _phoneUiSyncRequest.value = PhoneUiSyncRequest(
            requestId = System.currentTimeMillis(),
            action = action,
            teamAPlayer1 = payload.teamAPlayer1,
            teamAPlayer2 = payload.teamAPlayer2,
            teamBPlayer1 = payload.teamBPlayer1,
            teamBPlayer2 = payload.teamBPlayer2,
            scoringFormat = payload.scoringFormat,
            startingTeam = payload.startingTeam,
            myTeamOnTop = payload.myTeamOnTop
        )
    }

    private fun TabletSetupPayload.playerNames(): List<String> =
        listOf(teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2)

    private fun TabletCommandMessage.toSetupPayload(): TabletSetupPayload? {
        if (args.size < 9) return null
        return TabletSetupPayload(
            teamAName = args[0].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            teamBName = args[1].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            teamAPlayer1 = args[2].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            teamAPlayer2 = args[3].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            teamBPlayer1 = args[4].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            teamBPlayer2 = args[5].fromWireField().take(MAX_NAME_FIELD_LENGTH),
            scoringFormat = args[6].fromWireField().toScoringFormat(),
            startingTeam = args[7].fromWireField().toTeamOrNull(),
            myTeamOnTop = args[8].toBooleanStrictOrNull() ?: true
        )
    }

    private fun String.toTeamOrNull(): Team? = when (this) {
        WearSyncContract.TEAM_A,
        Team.A.name -> Team.A
        WearSyncContract.TEAM_B,
        Team.B.name -> Team.B
        else -> null
    }

    private fun String.toScoringFormat(): ScoringFormat =
        runCatching { ScoringFormat.valueOf(this) }.getOrDefault(ScoringFormat.Traditional)

    private fun String.fromWireField(): String =
        replace("%7C", "|")
            .replace("%25", "%")

    private fun publishScoreState(state: GameState) {
        val context = appContext ?: return
        val request = PutDataMapRequest.create(WearSyncContract.SCORE_STATE_PATH).apply {
            dataMap.putInt(WearSyncContract.KEY_TEAM_A_SCORE, state.teamAScore)
            dataMap.putInt(WearSyncContract.KEY_TEAM_B_SCORE, state.teamBScore)
            dataMap.putString(WearSyncContract.KEY_SERVING_TEAM, state.servingTeam.toWireValue())
            dataMap.putInt(WearSyncContract.KEY_SERVER_NUMBER, state.serverNumber.displayValue)
            dataMap.putString(WearSyncContract.KEY_TEAM_A_NAME, state.settings.teamAName)
            dataMap.putString(WearSyncContract.KEY_TEAM_B_NAME, state.settings.teamBName)
            dataMap.putString(WearSyncContract.KEY_SCORE_CALL, state.scoreCall)
            dataMap.putString(WearSyncContract.KEY_SPOKEN_SCORE_CALL, state.spokenScoreCall())
            dataMap.putString(WearSyncContract.KEY_SERVING_PLAYER_NAME, state.servingPlayerName())
            dataMap.putLong(WearSyncContract.KEY_UPDATED_AT, System.currentTimeMillis())
            dataMap.putBoolean(WearSyncContract.KEY_MATCH_ACTIVE, store.matchActive.value)
            dataMap.putBoolean(WearSyncContract.KEY_CAN_UNDO, store.canUndo())
            dataMap.putString(WearSyncContract.KEY_VOICE_MODE, _voiceAnnouncementMode.value.wireValue)
            dataMap.putString(WearSyncContract.KEY_DEVICE_ROLE, deviceRole)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener {
                SyncLog.debug(TAG) { "Published score state" }
            }
            .addOnFailureListener { error ->
                SyncLog.warn(TAG, "Failed to publish score state", "Failed to publish score state", error)
            }
    }

    private fun Team.toWireValue(): String = when (this) {
        Team.A -> WearSyncContract.TEAM_A
        Team.B -> WearSyncContract.TEAM_B
    }

    private fun restorePersistedMatch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val matchActive = prefs.getBoolean(KEY_MATCH_ACTIVE, false)
        if (!matchActive) return

        val servingTeam = prefs.getString(KEY_SERVING_TEAM, Team.A.name)
            ?.let { runCatching { Team.valueOf(it) }.getOrNull() }
            ?: Team.A
        val serverNumber = prefs.getString(KEY_SERVER_NUMBER, ServerNumber.Two.name)
            ?.let { runCatching { ServerNumber.valueOf(it) }.getOrNull() }
            ?: ServerNumber.Two
        val state = GameState(
            teamAScore = prefs.getInt(KEY_TEAM_A_SCORE, 0),
            teamBScore = prefs.getInt(KEY_TEAM_B_SCORE, 0),
            servingTeam = servingTeam,
            serverNumber = serverNumber,
            isFirstServerException = prefs.getBoolean(KEY_FIRST_SERVER_EXCEPTION, true),
            settings = GameSettings(
                teamAName = prefs.getString(KEY_TEAM_A_NAME, null) ?: "Team A",
                teamBName = prefs.getString(KEY_TEAM_B_NAME, null) ?: "Team B",
                teamAPlayer1 = prefs.getString(KEY_TEAM_A_PLAYER1, null) ?: "P1",
                teamAPlayer2 = prefs.getString(KEY_TEAM_A_PLAYER2, null) ?: "P2",
                teamBPlayer1 = prefs.getString(KEY_TEAM_B_PLAYER1, null) ?: "P3",
                teamBPlayer2 = prefs.getString(KEY_TEAM_B_PLAYER2, null) ?: "P4",
                scoringFormat = prefs.getString(KEY_SCORING_FORMAT, null)
                    ?.let { runCatching { ScoringFormat.valueOf(it) }.getOrNull() }
                    ?: ScoringFormat.Traditional
            )
        )
        store.restore(state = state, matchActive = true)
        SyncLog.debug(TAG) { "Restored persisted phone match" }
    }

    private fun restoreIdentity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        hostId = prefs.getString(KEY_HOST_ID, null) ?: generateHostId().also {
            prefs.edit().putString(KEY_HOST_ID, it).apply()
        }
        sessionId = prefs.getString(KEY_SESSION_ID, null) ?: generateSessionId().also {
            prefs.edit().putString(KEY_SESSION_ID, it).apply()
        }
    }

    private fun persistMatch(context: Context, state: GameState, matchActive: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MATCH_ACTIVE, matchActive)
            .putString(KEY_TEAM_A_NAME, state.settings.teamAName)
            .putString(KEY_TEAM_B_NAME, state.settings.teamBName)
            .putString(KEY_TEAM_A_PLAYER1, state.settings.teamAPlayer1)
            .putString(KEY_TEAM_A_PLAYER2, state.settings.teamAPlayer2)
            .putString(KEY_TEAM_B_PLAYER1, state.settings.teamBPlayer1)
            .putString(KEY_TEAM_B_PLAYER2, state.settings.teamBPlayer2)
            .putString(KEY_SCORING_FORMAT, state.settings.scoringFormat.name)
            .putInt(KEY_TEAM_A_SCORE, state.teamAScore)
            .putInt(KEY_TEAM_B_SCORE, state.teamBScore)
            .putString(KEY_SERVING_TEAM, state.servingTeam.name)
            .putString(KEY_SERVER_NUMBER, state.serverNumber.name)
            .putBoolean(KEY_FIRST_SERVER_EXCEPTION, state.isFirstServerException)
            .putString(KEY_HOST_ID, hostId)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
    }

    private fun rotateSessionId() {
        sessionId = generateSessionId()
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_HOST_ID, hostId)
            ?.putString(KEY_SESSION_ID, sessionId)
            ?.apply()
        SyncLog.debug(TAG) { "Rotated phone session id" }
    }

    private fun generateHostId(): String = "phone-${UUID.randomUUID()}"

    private fun generateSessionId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun String.toCourtCode(): String =
        filter { it.isLetterOrDigit() }
            .takeLast(4)
            .uppercase()
            .ifBlank { "0000" }

}
