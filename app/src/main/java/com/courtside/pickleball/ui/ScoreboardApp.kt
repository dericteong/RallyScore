package com.courtside.pickleball.ui

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.MAX_MATCH_SCORE
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.sync.TabletCommand
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletDisplaySync
import com.courtside.pickleball.sync.TabletSetupPayload
import com.courtside.pickleball.ui.scoreboard.MatchCorrectionDialog
import com.courtside.pickleball.ui.scoreboard.ScoreboardScreen
import com.courtside.pickleball.ui.setup.MatchSetupScreen
import com.courtside.pickleball.ui.setup.PlayerManagementScreen
import com.courtside.pickleball.ui.setup.formatTeamName
import com.courtside.pickleball.ui.tablet.TabletDisplayScreen
import com.courtside.pickleball.ui.tablet.toCourtCode
import com.courtside.pickleball.ui.theme.ScoreSpeechRate
import com.courtside.pickleball.ui.theme.SecondaryVoiceDelayMs
import com.courtside.pickleball.ui.theme.TabletSmallestWidthDp
import com.courtside.pickleball.ui.theme.VoiceTag
import com.courtside.pickleball.ui.theme.Warning
import com.courtside.pickleball.ui.theme.WatchConnectionRefreshIntervalMs
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ScoreboardApp(viewModel: ScoreboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val matchStarted by viewModel.matchActive.collectAsStateWithLifecycle()
    val watchConnected by viewModel.watchConnected.collectAsStateWithLifecycle()
    val tabletWatchConnected by viewModel.tabletWatchConnected.collectAsStateWithLifecycle()
    val voiceAnnouncementMode by viewModel.voiceAnnouncementMode.collectAsStateWithLifecycle()
    val remoteTabletDisplayState by viewModel.remoteTabletDisplayState.collectAsStateWithLifecycle()
    val tabletConnectionState by viewModel.tabletConnectionState.collectAsStateWithLifecycle()
    val tabletHostConnectionState by viewModel.tabletHostConnectionState.collectAsStateWithLifecycle()
    val discoveredTabletPhones by viewModel.discoveredTabletPhones.collectAsStateWithLifecycle()
    val pairedTabletPhoneHost by viewModel.pairedTabletPhoneHost.collectAsStateWithLifecycle()
    val phoneUiSyncRequest by viewModel.phoneUiSyncRequest.collectAsStateWithLifecycle()
    val players by viewModel.players.collectAsStateWithLifecycle()
    val pairedTabletCourtCode = remember(pairedTabletPhoneHost) { pairedTabletPhoneHost?.toCourtCode() }
    val singleTabletPhoneHostId = remember(discoveredTabletPhones) {
        discoveredTabletPhones.singleOrNull()?.hostId
    }
    val configuration = LocalConfiguration.current
    val useTabletDisplayLayout = configuration.smallestScreenWidthDp >= TabletSmallestWidthDp
    val localCourtCode = remember { viewModel.localCourtCode() }
    val activeRemoteTabletState = remoteTabletDisplayState?.takeIf { it.matchActive }
    val activeRemoteTabletVoiceSignature = activeRemoteTabletState?.voiceSignature()
    val showRemoteTabletMatch = useTabletDisplayLayout && activeRemoteTabletState != null
    val effectiveTabletWatchConnected = watchConnected || tabletWatchConnected
    var setupTeamAPlayer1 by remember { mutableStateOf("") }
    var setupTeamAPlayer2 by remember { mutableStateOf("") }
    var setupTeamBPlayer1 by remember { mutableStateOf("") }
    var setupTeamBPlayer2 by remember { mutableStateOf("") }
    var setupScoringFormat by remember { mutableStateOf(ScoringFormat.Traditional) }
    var startingTeam by remember { mutableStateOf<Team?>(Team.A) }
    var showEndMatchDialog by remember { mutableStateOf(false) }
    var showForgetPhoneDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var myTeamOnTop by remember { mutableStateOf(true) }
    var editingSetupFromMatch by remember { mutableStateOf(false) }
    var showPlayerManagement by remember { mutableStateOf(false) }
    var voiceModeManuallySelected by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var pendingScoreCall by remember { mutableStateOf<String?>(null) }
    var lastObservedMatchState by remember { mutableStateOf<GameState?>(null) }
    var lastObservedRemoteTabletSignature by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(AudioManager::class.java)
    }
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun showMaxScoreReachedToast() {
        Toast.makeText(
            context,
            "Maximum score of $MAX_MATCH_SCORE reached — start a new game to continue.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun speakScoreCall(scoreCall: String) {
        val deviceLabel = if (useTabletDisplayLayout) "tablet" else "phone"
        val utteranceId = "score-${System.nanoTime()}"
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        Log.d(VoiceTag, "Speaking score on $deviceLabel: $scoreCall")
        tts.useBestAvailableVoice()
        tts.setSpeechRate(ScoreSpeechRate)
        tts.setPitch(1.0f)
        tts.setAudioAttributes(scoreAudioAttributes())
        val result = tts.speak(scoreCall, TextToSpeech.QUEUE_FLUSH, scoreSpeechParams(), utteranceId)
        Log.d(VoiceTag, "TTS speak result on $deviceLabel: $result ($utteranceId)")
    }

    fun announceScoreCall(scoreCall: String) {
        if (ttsReady) {
            speakScoreCall(scoreCall)
        } else {
            pendingScoreCall = scoreCall
        }
    }

    fun announceScore(gameState: GameState) {
        announceScoreCall(gameState.spokenScoreCall())
    }

    LaunchedEffect(ttsReady, pendingScoreCall) {
        val scoreCall = pendingScoreCall
        if (ttsReady && scoreCall != null) {
            speakScoreCall(scoreCall)
            pendingScoreCall = null
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            tts.useBestAvailableVoice()
            tts.setSpeechRate(ScoreSpeechRate)
            tts.setPitch(1.0f)
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(VoiceTag, "TTS started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(VoiceTag, "TTS done: $utteranceId")
                        audioManager?.abandonAudioFocus(null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(VoiceTag, "TTS error: $utteranceId")
                        audioManager?.abandonAudioFocus(null)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(VoiceTag, "TTS error: $utteranceId code=$errorCode")
                        audioManager?.abandonAudioFocus(null)
                    }
                }
            )
        }
    }

    LaunchedEffect(watchConnected, tabletConnectionState, useTabletDisplayLayout, voiceModeManuallySelected) {
        if (!voiceModeManuallySelected) {
            viewModel.setVoiceAnnouncementMode(
                when {
                    useTabletDisplayLayout -> VoiceAnnouncementMode.TabletOnly
                    watchConnected && tabletConnectionState == TabletConnectionState.Connected -> {
                        VoiceAnnouncementMode.WatchThenTablet
                    }
                    watchConnected -> VoiceAnnouncementMode.WatchThenPhone
                    tabletConnectionState == TabletConnectionState.Connected -> VoiceAnnouncementMode.PhoneThenTablet
                    else -> VoiceAnnouncementMode.PhoneOnly
                }
            )
        }
    }

    LaunchedEffect(voiceAnnouncementMode, useTabletDisplayLayout) {
        if (!voiceAnnouncementMode.usesThisDeviceSpeaker(useTabletDisplayLayout)) {
            pendingScoreCall = null
            tts.stop()
        }
    }

    LaunchedEffect(matchStarted, state, voiceAnnouncementMode, watchConnected, useTabletDisplayLayout) {
        if (!matchStarted) {
            lastObservedMatchState = null
            return@LaunchedEffect
        }

        if (showRemoteTabletMatch) {
            lastObservedMatchState = state
            return@LaunchedEffect
        }

        val previous = lastObservedMatchState
        lastObservedMatchState = state
        if (previous != null && state != previous && voiceAnnouncementMode.usesThisDeviceSpeaker(useTabletDisplayLayout)) {
            if (voiceAnnouncementMode.isDelayedOnThisDevice(useTabletDisplayLayout, watchConnected)) {
                val delayMs = if (useTabletDisplayLayout && voiceAnnouncementMode.isDoublyDelayedOnTablet()) {
                    SecondaryVoiceDelayMs * 2
                } else {
                    SecondaryVoiceDelayMs
                }
                delay(delayMs)
            }
            announceScore(state)
        }
    }

    LaunchedEffect(activeRemoteTabletVoiceSignature, useTabletDisplayLayout) {
        if (!useTabletDisplayLayout || activeRemoteTabletState == null) {
            lastObservedRemoteTabletSignature = null
            return@LaunchedEffect
        }

        val signature = activeRemoteTabletVoiceSignature ?: return@LaunchedEffect
        val previousSignature = lastObservedRemoteTabletSignature
        lastObservedRemoteTabletSignature = signature

        val mode = activeRemoteTabletState.voiceAnnouncementMode
        if (previousSignature != null && signature != previousSignature && mode.usesTabletSpeaker()) {
            if (mode.isDelayedOnTablet()) {
                delay(if (mode.isDoublyDelayedOnTablet()) SecondaryVoiceDelayMs * 2 else SecondaryVoiceDelayMs)
            }
            announceScoreCall(activeRemoteTabletState.spokenScoreCall.ifBlank { activeRemoteTabletState.scoreCall })
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshWatchConnection()
            delay(WatchConnectionRefreshIntervalMs)
        }
    }

    LaunchedEffect(useTabletDisplayLayout) {
        TabletDisplaySync.setTabletDisplayAvailable(useTabletDisplayLayout)
    }

    LaunchedEffect(myTeamOnTop) {
        TabletDisplaySync.setMyTeamOnTop(myTeamOnTop)
    }

    LaunchedEffect(phoneUiSyncRequest?.requestId, useTabletDisplayLayout) {
        val request = phoneUiSyncRequest ?: return@LaunchedEffect
        if (useTabletDisplayLayout) return@LaunchedEffect

        setupTeamAPlayer1 = request.teamAPlayer1
        setupTeamAPlayer2 = request.teamAPlayer2
        setupTeamBPlayer1 = request.teamBPlayer1
        setupTeamBPlayer2 = request.teamBPlayer2
        setupScoringFormat = request.scoringFormat
        request.startingTeam?.let { startingTeam = it }
        myTeamOnTop = request.myTeamOnTop
        editingSetupFromMatch = false
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
            TabletDisplaySync.setTabletDisplayAvailable(false)
        }
    }

    MaterialTheme {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f)
        ) {
            if (showPlayerManagement) {
                PlayerManagementScreen(
                    players = players,
                    isTabletLayout = useTabletDisplayLayout,
                    onBack = { showPlayerManagement = false },
                    onAddPlayer = viewModel::addPlayer,
                    onRenamePlayer = viewModel::renamePlayer,
                    onDeletePlayer = viewModel::deletePlayer,
                    onDeleteAllPlayers = viewModel::deleteAllPlayers
                )
            } else if (showRemoteTabletMatch && !editingSetupFromMatch) {
                TabletDisplayScreen(
                    state = activeRemoteTabletState!!,
                    connectionState = tabletConnectionState,
                    courtCode = pairedTabletCourtCode ?: localCourtCode,
                    myTeamOnTop = activeRemoteTabletState.myTeamOnTop,
                    watchConnected = activeRemoteTabletState.watchConnected,
                    canUndo = activeRemoteTabletState.canUndo,
                    isRemoteControlled = true,
                    onTeamARally = {
                        if (activeRemoteTabletState!!.teamAScore >= MAX_MATCH_SCORE) {
                            showMaxScoreReachedToast()
                        } else {
                            viewModel.sendTabletCommand(TabletCommand.TeamAWonRally)
                        }
                    },
                    onTeamBRally = {
                        if (activeRemoteTabletState!!.teamBScore >= MAX_MATCH_SCORE) {
                            showMaxScoreReachedToast()
                        } else {
                            viewModel.sendTabletCommand(TabletCommand.TeamBWonRally)
                        }
                    },
                    onUndo = { viewModel.sendTabletCommand(TabletCommand.Undo) },
                    onEndMatchRequested = { viewModel.sendTabletCommand(TabletCommand.EndMatch) },
                    onForgetPhoneRequested = { showForgetPhoneDialog = true },
                    onCorrectionRequested = { showCorrectionDialog = true },
                    onNavigateToSetup = {
                        editingSetupFromMatch = true
                    }
                )
            } else if (matchStarted && !editingSetupFromMatch) {
                if (useTabletDisplayLayout) {
                    TabletDisplayScreen(
                        state = state.toTabletDisplayState(
                            myTeamOnTop = myTeamOnTop,
                            matchActive = true,
                            canUndo = viewModel.canUndo(),
                            voiceAnnouncementMode = voiceAnnouncementMode,
                            watchConnected = effectiveTabletWatchConnected
                        ),
                        connectionState = tabletConnectionState,
                        courtCode = pairedTabletCourtCode ?: localCourtCode,
                        myTeamOnTop = myTeamOnTop,
                        watchConnected = effectiveTabletWatchConnected,
                        canUndo = viewModel.canUndo(),
                        onTeamARally = {
                            if (state.teamAScore >= MAX_MATCH_SCORE) {
                                showMaxScoreReachedToast()
                            } else {
                                viewModel.recordRallyWinner(Team.A)
                            }
                        },
                        onTeamBRally = {
                            if (state.teamBScore >= MAX_MATCH_SCORE) {
                                showMaxScoreReachedToast()
                            } else {
                                viewModel.recordRallyWinner(Team.B)
                            }
                        },
                        onUndo = viewModel::undo,
                        onEndMatchRequested = { showEndMatchDialog = true },
                        onCorrectionRequested = { showCorrectionDialog = true },
                        onNavigateToSetup = {
                            editingSetupFromMatch = true
                        }
                    )
                } else {
                    ScoreboardScreen(
                        state = state,
                        myTeamOnTop = myTeamOnTop,
                        localCourtCode = localCourtCode,
                        canUndo = viewModel.canUndo(),
                        watchConnected = watchConnected,
                        tabletConnectionState = tabletHostConnectionState,
                        onTeamARally = {
                            if (state.teamAScore >= MAX_MATCH_SCORE) {
                                showMaxScoreReachedToast()
                            } else {
                                viewModel.recordRallyWinner(Team.A)
                            }
                        },
                        onTeamBRally = {
                            if (state.teamBScore >= MAX_MATCH_SCORE) {
                                showMaxScoreReachedToast()
                            } else {
                                viewModel.recordRallyWinner(Team.B)
                            }
                        },
                        onUndo = viewModel::undo,
                        onEndMatchRequested = { showEndMatchDialog = true },
                        onCorrectionRequested = { showCorrectionDialog = true },
                        onNavigateToSetup = {
                            editingSetupFromMatch = true
                        }
                    )
                }
            } else {
                MatchSetupScreen(
                    editingFromMatch = editingSetupFromMatch,
                    isTabletLayout = useTabletDisplayLayout,
                    myTeamOnTop = myTeamOnTop,
                    teamAPlayer1 = setupTeamAPlayer1,
                    teamAPlayer2 = setupTeamAPlayer2,
                    teamBPlayer1 = setupTeamBPlayer1,
                    teamBPlayer2 = setupTeamBPlayer2,
                    players = players,
                    startingTeam = startingTeam,
                    onTeamAPlayer1Change = { setupTeamAPlayer1 = it },
                    onTeamAPlayer2Change = { setupTeamAPlayer2 = it },
                    onTeamBPlayer1Change = { setupTeamBPlayer1 = it },
                    onTeamBPlayer2Change = { setupTeamBPlayer2 = it },
                    onManagePlayers = { showPlayerManagement = true },
                    scoringFormat = setupScoringFormat,
                    onScoringFormatChange = { setupScoringFormat = it },
                    onStartingTeamChange = { startingTeam = it },
                    onSwapTeams = {
                        myTeamOnTop = !myTeamOnTop
                        startingTeam = when (startingTeam) {
                            Team.A -> Team.B
                            Team.B -> Team.A
                            null -> null
                        }
                    },
                    watchConnected = effectiveTabletWatchConnected,
                    // A tablet cares about its own client-role pairing to a phone
                    // (tabletConnectionState); a phone cares about whether a tablet has joined
                    // its hosted service (tabletHostConnectionState) - MatchSetupScreen is
                    // shared by both roles, so pick the state that matches this device's role.
                    tabletConnectionState = if (useTabletDisplayLayout) {
                        tabletConnectionState
                    } else {
                        tabletHostConnectionState
                    },
                    voiceAnnouncementMode = voiceAnnouncementMode,
                    onVoiceAnnouncementModeChange = {
                        voiceModeManuallySelected = true
                        viewModel.setVoiceAnnouncementMode(it)
                    },
                    localCourtCode = localCourtCode,
                    discoveredPhones = discoveredTabletPhones,
                    selectedPhoneHostId = pairedTabletPhoneHost,
                    selectedCourtCode = pairedTabletCourtCode,
                    onJoinPhoneRequested = { hostId ->
                        viewModel.pairTabletToPhone(hostId)
                    },
                    onStart = {
                        val server = startingTeam ?: Team.A
                        // Only the names actually typed are persisted - PlayerRepository ignores
                        // blanks, so the placeholders below never pollute the saved player list.
                        viewModel.saveMatchPlayers(
                            listOf(
                                setupTeamAPlayer1,
                                setupTeamAPlayer2,
                                setupTeamBPlayer1,
                                setupTeamBPlayer2
                            )
                        )
                        // Blank fields fall back to court-position placeholders. This is applied
                        // before building the payload so the tablet -> phone StartMatch path
                        // carries the same labels as a locally started match.
                        val startPlayerA1 = setupTeamAPlayer1.ifBlankPlaceholder(DEFAULT_TEAM_A_PLAYER_1)
                        val startPlayerA2 = setupTeamAPlayer2.ifBlankPlaceholder(DEFAULT_TEAM_A_PLAYER_2)
                        val startPlayerB1 = setupTeamBPlayer1.ifBlankPlaceholder(DEFAULT_TEAM_B_PLAYER_1)
                        val startPlayerB2 = setupTeamBPlayer2.ifBlankPlaceholder(DEFAULT_TEAM_B_PLAYER_2)
                        val startPayload = TabletSetupPayload(
                            teamAName = formatTeamName(startPlayerA1, startPlayerA2, Team.A),
                            teamBName = formatTeamName(startPlayerB1, startPlayerB2, Team.B),
                            teamAPlayer1 = startPlayerA1,
                            teamAPlayer2 = startPlayerA2,
                            teamBPlayer1 = startPlayerB1,
                            teamBPlayer2 = startPlayerB2,
                            scoringFormat = setupScoringFormat,
                            startingTeam = server,
                            myTeamOnTop = myTeamOnTop
                        )
                        val sentToPhone = useTabletDisplayLayout &&
                            singleTabletPhoneHostId != null &&
                            viewModel.sendTabletSetupCommand(
                                hostId = singleTabletPhoneHostId,
                                command = TabletCommand.StartMatch,
                                payload = startPayload
                            )
                        if (!sentToPhone) {
                            viewModel.startMatch(
                                teamAName = startPayload.teamAName,
                                teamBName = startPayload.teamBName,
                                teamAPlayer1 = startPayload.teamAPlayer1,
                                teamAPlayer2 = startPayload.teamAPlayer2,
                                teamBPlayer1 = startPayload.teamBPlayer1,
                                teamBPlayer2 = startPayload.teamBPlayer2,
                                scoringFormat = startPayload.scoringFormat,
                                startingTeam = server
                            )
                        }
                        editingSetupFromMatch = false
                    },
                    onResumeMatch = {
                        viewModel.saveMatchPlayers(
                            listOf(
                                setupTeamAPlayer1,
                                setupTeamAPlayer2,
                                setupTeamBPlayer1,
                                setupTeamBPlayer2
                            )
                        )
                        val resumePayload = TabletSetupPayload(
                            teamAName = formatTeamName(setupTeamAPlayer1, setupTeamAPlayer2, Team.A),
                            teamBName = formatTeamName(setupTeamBPlayer1, setupTeamBPlayer2, Team.B),
                            teamAPlayer1 = setupTeamAPlayer1,
                            teamAPlayer2 = setupTeamAPlayer2,
                            teamBPlayer1 = setupTeamBPlayer1,
                            teamBPlayer2 = setupTeamBPlayer2,
                            scoringFormat = setupScoringFormat,
                            startingTeam = startingTeam,
                            myTeamOnTop = myTeamOnTop
                        )
                        val sentToPhone = useTabletDisplayLayout &&
                            singleTabletPhoneHostId != null &&
                            viewModel.sendTabletSetupCommand(
                                hostId = singleTabletPhoneHostId,
                                command = TabletCommand.ResumeMatch,
                                payload = resumePayload
                            )
                        if (!sentToPhone) {
                            viewModel.updateTeamNames(
                                teamAName = resumePayload.teamAName,
                                teamBName = resumePayload.teamBName,
                                teamAPlayer1 = resumePayload.teamAPlayer1,
                                teamAPlayer2 = resumePayload.teamAPlayer2,
                                teamBPlayer1 = resumePayload.teamBPlayer1,
                                teamBPlayer2 = resumePayload.teamBPlayer2,
                                scoringFormat = resumePayload.scoringFormat
                            )
                        }
                        editingSetupFromMatch = false
                    }
                )
            }
        }

        if (showEndMatchDialog) {
            AlertDialog(
                onDismissRequest = { showEndMatchDialog = false },
                title = { Text("End Game?") },
                text = { Text("This clears the current game and returns to player setup.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.endMatch()
                            showEndMatchDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        Text("End Game")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEndMatchDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showForgetPhoneDialog) {
            AlertDialog(
                onDismissRequest = { showForgetPhoneDialog = false },
                title = { Text("End Game Locally?") },
                text = {
                    Text(
                        "The phone isn't reachable, so this can't send an end-game command to it. " +
                            "This instead forgets the paired phone on this tablet and clears this " +
                            "match here. You'll need to re-pair with a court code to connect again."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.forgetPairedTabletPhone()
                            showForgetPhoneDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        Text("End Game Locally")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgetPhoneDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        val correctionState = when {
            showRemoteTabletMatch -> activeRemoteTabletState?.toCorrectionGameState()
            matchStarted -> state
            else -> null
        }

        if (showCorrectionDialog && correctionState != null) {
            MatchCorrectionDialog(
                state = correctionState,
                onDismiss = { showCorrectionDialog = false },
                onAdjustTeamA = { delta ->
                    if (showRemoteTabletMatch) {
                        viewModel.sendTabletCommand(
                            if (delta < 0) TabletCommand.AdjustTeamAScoreDown else TabletCommand.AdjustTeamAScoreUp
                        )
                    } else {
                        viewModel.adjustScore(Team.A, delta)
                    }
                },
                onAdjustTeamB = { delta ->
                    if (showRemoteTabletMatch) {
                        viewModel.sendTabletCommand(
                            if (delta < 0) TabletCommand.AdjustTeamBScoreDown else TabletCommand.AdjustTeamBScoreUp
                        )
                    } else {
                        viewModel.adjustScore(Team.B, delta)
                    }
                },
                onServingTeamSelected = { team ->
                    if (showRemoteTabletMatch) {
                        viewModel.sendTabletCommand(
                            if (team == Team.A) TabletCommand.SetServingTeamA else TabletCommand.SetServingTeamB
                        )
                    } else {
                        viewModel.adjustServeState(team, state.serverNumber)
                    }
                },
                onServerNumberSelected = { number ->
                    if (showRemoteTabletMatch) {
                        viewModel.sendTabletCommand(
                            if (number == ServerNumber.One) TabletCommand.SetServerOne else TabletCommand.SetServerTwo
                        )
                    } else {
                        viewModel.adjustServeState(state.servingTeam, number)
                    }
                }
            )
        }
    }
}

private fun scoreAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

private fun scoreSpeechParams(): Bundle =
    Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
    }

private fun TextToSpeech.useBestAvailableVoice() {
    val bestEnglishVoice = voices
        ?.filter { it.locale.language == Locale.ENGLISH.language }
        ?.maxWithOrNull(
            compareBy<Voice> { it.quality }
                .thenBy { if (it.isNetworkConnectionRequired) 0 else 1 }
        )

    if (bestEnglishVoice != null) {
        voice = bestEnglishVoice
    } else {
        language = Locale.ENGLISH
    }
}
