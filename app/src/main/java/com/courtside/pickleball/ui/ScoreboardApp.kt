package com.courtside.pickleball.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.courtside.pickleball.R
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.sync.PhoneUiSyncRequest
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletCommand
import com.courtside.pickleball.sync.TabletDisplayState
import com.courtside.pickleball.sync.TabletPhoneCandidate
import com.courtside.pickleball.sync.TabletSetupPayload
import com.courtside.pickleball.sync.TabletDisplaySync
import java.util.Locale
import kotlinx.coroutines.delay

private val Ink = Color(0xFF050607)
private val Paper = Color(0xFFFFFFFF)
private val PanelWhite = Color(0xFFFFFFFF)
private val TeamABlue = Color(0xFF005BBB)
private val TeamBGreen = Color(0xFF007A3D)
private val ConnectedAmber = Color(0xFFFFC107)
private val ProblemRed = Color(0xFFB00020)
private val ServerDot = Color(0xFFECEBE3)
private val ServerAccent = Color.White
private val Warning = Color(0xFFB23A48)
private val TableLine = Color(0xFF242A31)
private val CallBackground = Color(0xFF111827)
private val SetupTeamCardHeight = 104.dp
private val SetupTeamCardCompactHeight = 98.dp
private val SetupPlayerInputHeight = 50.dp
private val SetupPlayerInputCompactHeight = 48.dp
private val ScoreCellWidth = 156.dp
private val ServeCellWidth = 96.dp
private val ScoreControlButtonWidth = 92.dp
private const val SecondaryVoiceDelayMs = 2_000L
private const val WatchConnectionRefreshIntervalMs = 5_000L
private const val TabletSmallestWidthDp = 600
private const val ScoreSpeechRate = 0.9f
private const val VoiceTag = "RallyScoreVoice"

@Composable
fun ScoreboardApp(viewModel: ScoreboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val matchStarted by viewModel.matchActive.collectAsStateWithLifecycle()
    val watchConnected by viewModel.watchConnected.collectAsStateWithLifecycle()
    val tabletWatchConnected by viewModel.tabletWatchConnected.collectAsStateWithLifecycle()
    val voiceAnnouncementMode by viewModel.voiceAnnouncementMode.collectAsStateWithLifecycle()
    val remoteTabletDisplayState by viewModel.remoteTabletDisplayState.collectAsStateWithLifecycle()
    val tabletConnectionState by viewModel.tabletConnectionState.collectAsStateWithLifecycle()
    val discoveredTabletPhones by viewModel.discoveredTabletPhones.collectAsStateWithLifecycle()
    val pairedTabletPhoneHost by viewModel.pairedTabletPhoneHost.collectAsStateWithLifecycle()
    val phoneUiSyncRequest by viewModel.phoneUiSyncRequest.collectAsStateWithLifecycle()
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
    var setupTeamAPlayer1 by remember { mutableStateOf("P1") }
    var setupTeamAPlayer2 by remember { mutableStateOf("P2") }
    var setupTeamBPlayer1 by remember { mutableStateOf("P3") }
    var setupTeamBPlayer2 by remember { mutableStateOf("P4") }
    var setupScoringFormat by remember { mutableStateOf(ScoringFormat.Traditional) }
    var startingTeam by remember { mutableStateOf<Team?>(Team.A) }
    var showEndMatchDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var myTeamOnTop by remember { mutableStateOf(true) }
    var editingSetupFromMatch by remember { mutableStateOf(false) }
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
                delay(SecondaryVoiceDelayMs)
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
                delay(SecondaryVoiceDelayMs)
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
            if (showRemoteTabletMatch && !editingSetupFromMatch) {
                TabletDisplayScreen(
                    state = activeRemoteTabletState!!,
                    connectionState = tabletConnectionState,
                    courtCode = pairedTabletCourtCode ?: localCourtCode,
                    myTeamOnTop = activeRemoteTabletState.myTeamOnTop,
                    watchConnected = activeRemoteTabletState.watchConnected,
                    canUndo = activeRemoteTabletState.canUndo,
                    onTeamARally = { viewModel.sendTabletCommand(TabletCommand.TeamAWonRally) },
                    onTeamBRally = { viewModel.sendTabletCommand(TabletCommand.TeamBWonRally) },
                    onUndo = { viewModel.sendTabletCommand(TabletCommand.Undo) },
                    onEndMatchRequested = { viewModel.sendTabletCommand(TabletCommand.EndMatch) },
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
                        onTeamARally = { viewModel.recordRallyWinner(Team.A) },
                        onTeamBRally = { viewModel.recordRallyWinner(Team.B) },
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
                        tabletConnectionState = tabletConnectionState,
                        onTeamARally = { viewModel.recordRallyWinner(Team.A) },
                        onTeamBRally = { viewModel.recordRallyWinner(Team.B) },
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
                    startingTeam = startingTeam,
                    onTeamAPlayer1Change = { setupTeamAPlayer1 = normalizePlayerNamesInput(it) },
                    onTeamAPlayer2Change = { setupTeamAPlayer2 = normalizePlayerNamesInput(it) },
                    onTeamBPlayer1Change = { setupTeamBPlayer1 = normalizePlayerNamesInput(it) },
                    onTeamBPlayer2Change = { setupTeamBPlayer2 = normalizePlayerNamesInput(it) },
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
                    tabletConnectionState = tabletConnectionState,
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
                        val startPayload = TabletSetupPayload(
                            teamAName = formatTeamName(setupTeamAPlayer1, setupTeamAPlayer2, Team.A),
                            teamBName = formatTeamName(setupTeamBPlayer1, setupTeamBPlayer2, Team.B),
                            teamAPlayer1 = setupTeamAPlayer1,
                            teamAPlayer2 = setupTeamAPlayer2,
                            teamBPlayer1 = setupTeamBPlayer1,
                            teamBPlayer2 = setupTeamBPlayer2,
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

@Composable
private fun MatchSetupScreen(
    teamAPlayer1: String,
    teamAPlayer2: String,
    teamBPlayer1: String,
    teamBPlayer2: String,
    scoringFormat: ScoringFormat,
    startingTeam: Team?,
    onTeamAPlayer1Change: (String) -> Unit,
    onTeamAPlayer2Change: (String) -> Unit,
    onTeamBPlayer1Change: (String) -> Unit,
    onTeamBPlayer2Change: (String) -> Unit,
    onScoringFormatChange: (ScoringFormat) -> Unit,
    onStartingTeamChange: (Team) -> Unit,
    editingFromMatch: Boolean = false,
    isTabletLayout: Boolean = true,
    myTeamOnTop: Boolean = true,
    onSwapTeams: () -> Unit = {},
    watchConnected: Boolean,
    tabletConnectionState: TabletConnectionState,
    voiceAnnouncementMode: VoiceAnnouncementMode,
    onVoiceAnnouncementModeChange: (VoiceAnnouncementMode) -> Unit,
    localCourtCode: String? = null,
    discoveredPhones: List<TabletPhoneCandidate> = emptyList(),
    selectedPhoneHostId: String? = null,
    selectedCourtCode: String? = null,
    onJoinPhoneRequested: (String) -> Unit = {},
    onStart: () -> Unit,
    onResumeMatch: () -> Unit = {}
) {
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val outerHorizontalPadding = when {
        keyboardVisible -> 18.dp
        isTabletLayout -> 28.dp
        else -> 18.dp
    }
    val outerVerticalPadding = when {
        keyboardVisible -> 6.dp
        isTabletLayout -> 12.dp
        else -> 4.dp
    }
    val headerSpacing = if (isTabletLayout) 12.dp else 8.dp
    val teamColumnWeight = if (isTabletLayout) 1.18f else 1.34f
    val controlColumnWeight = if (isTabletLayout) 0.82f else 0.66f
    val helperText = "Enter players by court position"
    val setupScrollState = rememberScrollState()
    val context = LocalContext.current
    val view = LocalView.current
    val inputMethodManager = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val canStart = startingTeam != null &&
        teamAPlayer1.trim().isNotEmpty() &&
        teamAPlayer2.trim().isNotEmpty() &&
        teamBPlayer1.trim().isNotEmpty() &&
        teamBPlayer2.trim().isNotEmpty()
    val hideKeyboard = {
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        Unit
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Paper),
        color = Paper
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isTabletLayout) 14.dp else 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(teamColumnWeight)
                    .verticalScroll(setupScrollState),
                verticalArrangement = Arrangement.spacedBy(if (isTabletLayout) 10.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(if (isTabletLayout) 1f else 0.84f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "RallyScore icon",
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "RallyScore",
                            color = Ink,
                            fontSize = if (isTabletLayout) 20.sp else 17.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                    if (!keyboardVisible) {
                        if (isTabletLayout) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CourtCodeBadge(
                                    modifier = Modifier.widthIn(min = 108.dp),
                                    code = localCourtCode ?: "0000"
                                )
                                WatchConnectionStatusBar(
                                    modifier = Modifier.widthIn(min = 150.dp),
                                    connected = watchConnected
                                )
                                PhoneTabletStatusBar(
                                    modifier = Modifier.widthIn(min = 160.dp),
                                    connectionState = tabletConnectionState,
                                    isTabletPerspective = true
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.weight(1.18f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CourtCodeBadge(
                                    modifier = Modifier.weight(1f),
                                    code = localCourtCode ?: "0000",
                                    compact = true
                                )
                                WatchConnectionStatusBar(
                                    modifier = Modifier.weight(1f),
                                    connected = watchConnected,
                                    compact = true
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SET UP GAME",
                        color = Ink,
                        fontSize = when {
                            keyboardVisible -> 20.sp
                            isTabletLayout -> 28.sp
                            else -> 24.sp
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    if (!keyboardVisible) {
                        Text(
                            text = helperText,
                            color = Ink.copy(alpha = 0.78f),
                            fontSize = if (isTabletLayout) 14.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                if (myTeamOnTop) {
                    SetupTeamNameFields(
                        label = "My Team",
                        player1 = teamAPlayer1,
                        player2 = teamAPlayer2,
                        color = TeamABlue,
                        selected = startingTeam == Team.A,
                        compact = keyboardVisible,
                        onPlayer1Change = onTeamAPlayer1Change,
                        onPlayer2Change = onTeamAPlayer2Change,
                        onKeyboardAction = hideKeyboard,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.A) }
                        }
                    )
                    SwapTeamsButton(onSwap = onSwapTeams)
                    SetupTeamNameFields(
                        label = "Opponent Team",
                        player1 = teamBPlayer1,
                        player2 = teamBPlayer2,
                        color = TeamBGreen,
                        selected = startingTeam == Team.B,
                        compact = keyboardVisible,
                        onPlayer1Change = onTeamBPlayer1Change,
                        onPlayer2Change = onTeamBPlayer2Change,
                        onKeyboardAction = hideKeyboard,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.B) }
                        }
                    )
                } else {
                    SetupTeamNameFields(
                        label = "Opponent Team",
                        player1 = teamBPlayer1,
                        player2 = teamBPlayer2,
                        color = TeamBGreen,
                        selected = startingTeam == Team.B,
                        compact = keyboardVisible,
                        onPlayer1Change = onTeamBPlayer1Change,
                        onPlayer2Change = onTeamBPlayer2Change,
                        onKeyboardAction = hideKeyboard,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.B) }
                        }
                    )
                    SwapTeamsButton(onSwap = onSwapTeams)
                    SetupTeamNameFields(
                        label = "My Team",
                        player1 = teamAPlayer1,
                        player2 = teamAPlayer2,
                        color = TeamABlue,
                        selected = startingTeam == Team.A,
                        compact = keyboardVisible,
                        onPlayer1Change = onTeamAPlayer1Change,
                        onPlayer2Change = onTeamAPlayer2Change,
                        onKeyboardAction = hideKeyboard,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.A) }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(controlColumnWeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (keyboardVisible) 8.dp else headerSpacing)
            ) {
                if (!keyboardVisible) {
                    if (isTabletLayout) {
                        Spacer(modifier = Modifier.height(44.dp))
                    } else {
                        PhoneTabletStatusBar(
                            modifier = Modifier.fillMaxWidth(),
                            connectionState = tabletConnectionState,
                            compact = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                if (!keyboardVisible) {
                    if (isTabletLayout && discoveredPhones.isNotEmpty()) {
                        AvailablePhonesCard(
                            phones = discoveredPhones,
                            selectedPhoneHostId = selectedPhoneHostId,
                            selectedCourtCode = selectedCourtCode,
                            connectionState = tabletConnectionState,
                            onJoinPhoneRequested = onJoinPhoneRequested
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    ScoringFormatSelector(
                        selectedFormat = scoringFormat,
                        onFormatSelected = onScoringFormatChange,
                        compact = !isTabletLayout
                    )
                    ScorePreviewCard(
                        startingTeam = startingTeam,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        enabled = !editingFromMatch,
                        onTap = {
                            onStartingTeamChange(
                                when (startingTeam) {
                                    Team.A -> Team.B
                                    Team.B -> Team.A
                                    null -> Team.A
                                }
                            )
                        }
                    )
                    if (editingFromMatch) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isTabletLayout) 60.dp else 56.dp),
                            onClick = onResumeMatch,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315))
                        ) {
                            Text(
                                text = "RESUME GAME",
                                fontSize = if (isTabletLayout) 22.sp else 20.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    } else {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isTabletLayout) 60.dp else 56.dp),
                            onClick = onStart,
                            enabled = canStart,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315))
                        ) {
                            Text(
                                text = "START GAME",
                                fontSize = if (isTabletLayout) 22.sp else 20.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(if (isTabletLayout) 10.dp else 8.dp))
                    VoiceAnnouncementControls(
                        selectedMode = voiceAnnouncementMode,
                        onModeChange = onVoiceAnnouncementModeChange
                    )
                }
                if (keyboardVisible) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        onClick = hideKeyboard,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "DONE",
                            color = Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoringFormatSelector(
    selectedFormat: ScoringFormat,
    onFormatSelected: (ScoringFormat) -> Unit,
    compact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SCORING STYLE",
            color = Ink,
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoringFormatButton(
                modifier = Modifier.weight(1f),
                label = "CLASSIC",
                selected = selectedFormat == ScoringFormat.Traditional,
                compact = compact,
                onClick = { onFormatSelected(ScoringFormat.Traditional) }
            )
            ScoringFormatButton(
                modifier = Modifier.weight(1f),
                label = "RALLY",
                selected = selectedFormat == ScoringFormat.Rally,
                compact = compact,
                onClick = { onFormatSelected(ScoringFormat.Rally) }
            )
        }
    }
}

@Composable
private fun ScoringFormatButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) ConnectedAmber else Color(0xFF374151)
    val containerColor = if (selected) CallBackground else PanelWhite
    val contentColor = if (selected) ConnectedAmber else Ink

    OutlinedButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SwapTeamsButton(onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedButton(
            onClick = onSwap,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            border = BorderStroke(2.dp, Color(0xFF374151)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Ink
            ),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = "\u21C5 SWAP SIDE",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SetupTeamNameFields(
    label: String,
    player1: String,
    player2: String,
    color: Color,
    selected: Boolean,
    compact: Boolean,
    onPlayer1Change: (String) -> Unit,
    onPlayer2Change: (String) -> Unit,
    onKeyboardAction: () -> Unit,
    onSelect: () -> Unit
) {
    val cardHeight = if (compact) SetupTeamCardCompactHeight else SetupTeamCardHeight
    val inputHeight = if (compact) SetupPlayerInputCompactHeight else SetupPlayerInputHeight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SetupPlayerInput(
                modifier = Modifier
                    .weight(1f)
                    .height(inputHeight),
                value = player1,
                hint = "Player 1",
                compact = compact,
                onValueChange = onPlayer1Change,
                onKeyboardAction = onKeyboardAction,
                onSelect = onSelect
            )
            SetupPlayerInput(
                modifier = Modifier
                    .weight(1f)
                    .height(inputHeight),
                value = player2,
                hint = "Player 2",
                compact = compact,
                onValueChange = onPlayer2Change,
                onKeyboardAction = onKeyboardAction,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun SetupPlayerInput(
    modifier: Modifier,
    value: String,
    hint: String,
    compact: Boolean,
    onValueChange: (String) -> Unit,
    onKeyboardAction: () -> Unit,
    onSelect: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            EditText(context).apply {
                setSingleLine(true)
                setTextColor(Ink.toArgb())
                setHintTextColor(android.graphics.Color.rgb(110, 118, 126))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 18f else 20f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(20, 0, 20, 0)
                setBackgroundColor(android.graphics.Color.WHITE)
                filters = arrayOf(InputFilter.AllCaps())
                imeOptions = EditorInfo.IME_ACTION_DONE
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onSelect()
                }
                setOnEditorActionListener { _, actionId, event ->
                    val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_UP
                    if (actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                        onKeyboardAction()
                        true
                    } else {
                        false
                    }
                }
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                            onValueChange(s?.toString().orEmpty())
                        }

                        override fun afterTextChanged(s: Editable?) = Unit
                    }
                )
            }
        },
        update = { editText ->
            editText.hint = hint
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
            editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 18f else 20f)
        }
    )
}

@Composable
private fun ScorePreviewCard(
    startingTeam: Team?,
    compact: Boolean = false,
    isTabletLayout: Boolean = true,
    enabled: Boolean = true,
    onTap: () -> Unit = {}
) {
    val bgColor = when (startingTeam) {
        Team.A -> TeamABlue
        Team.B -> TeamBGreen
        null -> Ink
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = if (compact) 14.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 6.dp)
    ) {
        Text(
            text = when (startingTeam) {
                Team.A -> "WE SERVE FIRST"
                Team.B -> "OPP SERVE FIRST"
                null -> "CHOOSE SERVER"
            },
            color = Color.White,
            fontSize = when {
                compact -> 18.sp
                isTabletLayout -> 19.sp
                else -> 16.sp
            },
            fontWeight = FontWeight.Black,
            lineHeight = if (isTabletLayout || compact) 20.sp else 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun TabletDisplayScreen(
    state: TabletDisplayState,
    connectionState: TabletConnectionState = TabletConnectionState.Connected,
    courtCode: String? = null,
    myTeamOnTop: Boolean = true,
    watchConnected: Boolean = false,
    canUndo: Boolean = false,
    onTeamARally: (() -> Unit)? = null,
    onTeamBRally: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null,
    onCorrectionRequested: (() -> Unit)? = null,
    onNavigateToSetup: (() -> Unit)? = null
) {
    val isController = onUndo != null
    val isConnectedController = connectionState == TabletConnectionState.Connected

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        color = Paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabletStatusHeader(
                connectionState = connectionState,
                courtCode = courtCode,
                watchConnected = watchConnected
            )
            TabletControlBar(
                state = state,
                canUndo = canUndo,
                onUndo = onUndo,
                onEndMatchRequested = onEndMatchRequested,
                onCorrectionRequested = onCorrectionRequested,
                onNavigateToSetup = onNavigateToSetup
            )
            TabletScoreboardBody(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = state,
                myTeamOnTop = myTeamOnTop,
                enabled = !isConnectedController || isController,
                onTeamARally = onTeamARally,
                onTeamBRally = onTeamBRally
            )
        }
    }
}

@Composable
private fun TabletScoreboardBody(
    modifier: Modifier,
    state: TabletDisplayState,
    myTeamOnTop: Boolean,
    enabled: Boolean,
    onTeamARally: (() -> Unit)?,
    onTeamBRally: (() -> Unit)?
) {
    val orderedTeams = if (myTeamOnTop) listOf(Team.A, Team.B) else listOf(Team.B, Team.A)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        orderedTeams.forEach { team ->
            TabletTeamScoreTableRow(
                modifier = Modifier.weight(1f),
                name = if (team == Team.A) state.teamACourtOrderedName else state.teamBCourtOrderedName,
                score = if (team == Team.A) state.teamAScore else state.teamBScore,
                color = if (team == Team.A) TeamABlue else TeamBGreen,
                isServing = state.servingTeam == team,
                serverNumber = state.serverNumber.toServerNumber(),
                enabled = enabled,
                onScoreClick = if (team == Team.A) onTeamARally else onTeamBRally,
                servingPlayerName = if (state.servingTeam == team) state.servingPlayerName else ""
            )
        }
    }
}

@Composable
private fun TabletTeamScoreTableRow(
    modifier: Modifier,
    name: String,
    score: Int,
    color: Color,
    isServing: Boolean,
    serverNumber: ServerNumber,
    enabled: Boolean,
    onScoreClick: (() -> Unit)?,
    servingPlayerName: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        TableCell(
            modifier = Modifier
                .width(132.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            TabletServeDots(
                color = Color.White,
                isServing = isServing,
                serverNumber = serverNumber
            )
        }
        TableDivider()
        TableCell(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            TeamNameDisplay(
                modifier = Modifier.padding(horizontal = 30.dp),
                name = name,
                color = Color.White,
                highlightColor = color,
                servingPlayerName = servingPlayerName,
                isTablet = true
            )
        }
        TableDivider()
        TabletScoreTapTarget(
            modifier = Modifier.fillMaxHeight(),
            score = score,
            color = Color.White,
            enabled = enabled,
            onClick = onScoreClick
        )
    }
}

@Composable
private fun TabletServeDots(color: Color, isServing: Boolean, serverNumber: ServerNumber) {
    Row(
        modifier = Modifier.size(width = 124.dp, height = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(serverNumber.displayValue) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

private fun Int.toServerNumber(): ServerNumber = when (this) {
    1 -> ServerNumber.One
    else -> ServerNumber.Two
}

private fun TabletConnectionState.displayLabel(pairedCourtCode: String?): String = when (this) {
    TabletConnectionState.Searching -> pairedCourtCode?.let { "JOINING COURT $it" } ?: "SEARCHING COURTS"
    TabletConnectionState.Reconnecting -> pairedCourtCode?.let { "RECONNECTING COURT $it" } ?: "PHONE RECONNECTING"
    TabletConnectionState.Connected -> pairedCourtCode?.let { "CONNECTED TO COURT $it" } ?: "PHONE CONNECTED"
}

private fun TabletConnectionState.phoneDisplayLabel(): String = when (this) {
    TabletConnectionState.Searching -> "SEARCHING FOR TABLET"
    TabletConnectionState.Reconnecting -> "TABLET RECONNECTING"
    TabletConnectionState.Connected -> "TABLET CONNECTED"
}

private fun TabletConnectionState.displayColor(): Color = when (this) {
    TabletConnectionState.Searching -> Color.Gray
    TabletConnectionState.Reconnecting -> ProblemRed
    TabletConnectionState.Connected -> ConnectedAmber
}

@Composable
private fun TabletStatusHeader(
    connectionState: TabletConnectionState,
    courtCode: String? = null,
    watchConnected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CourtCodeBadge(
            modifier = Modifier.weight(1f),
            code = courtCode ?: "0000"
        )
        WatchConnectionStatusBar(
            modifier = Modifier.weight(1f),
            connected = watchConnected
        )
        PhoneTabletStatusBar(
            modifier = Modifier.weight(1f),
            connectionState = connectionState,
            isTabletPerspective = true
        )
    }
}

private fun TabletDisplayState.coloredScoreCall() = buildAnnotatedString {
    val receivingTeam = servingTeam.opponent()
    val servingColor = when (servingTeam) { Team.A -> TeamABlue; Team.B -> TeamBGreen }
    val receivingColor = when (receivingTeam) { Team.A -> TeamABlue; Team.B -> TeamBGreen }
    val (servingScore, receivingScore) = when (servingTeam) {
        Team.A -> teamAScore to teamBScore
        Team.B -> teamBScore to teamAScore
    }
    withStyle(SpanStyle(color = servingColor, fontWeight = FontWeight.Black)) {
        append(servingScore.toString())
    }
    append(" - ")
    withStyle(SpanStyle(color = receivingColor, fontWeight = FontWeight.Black)) {
        append(receivingScore.toString())
    }
    append(" - ")
    withStyle(SpanStyle(color = servingColor, fontWeight = FontWeight.Black)) {
        append(serverNumber.toString())
    }
}

private fun String.toCourtCode(): String =
    filter { it.isLetterOrDigit() }
        .takeLast(4)
        .uppercase()
        .ifBlank { "0000" }

@Composable
private fun TabletControlBar(
    state: TabletDisplayState,
    canUndo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null,
    onCorrectionRequested: (() -> Unit)? = null,
    onNavigateToSetup: (() -> Unit)? = null
) {
    val showControls = onUndo != null && onEndMatchRequested != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.54f)
            .clip(RoundedCornerShape(10.dp))
            .background(CallBackground)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(end = if (showControls) 138.dp else 0.dp, start = 12.dp),
            text = state.coloredScoreCall(),
            color = Color.White,
            fontSize = 198.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 202.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (onNavigateToSetup != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .height(52.dp)
                            .width(104.dp),
                        onClick = onNavigateToSetup,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("SETUP", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                if (onCorrectionRequested != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .height(52.dp)
                            .width(104.dp),
                        onClick = onCorrectionRequested,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("EDIT", fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(52.dp)
                        .width(104.dp),
                    onClick = onUndo!!,
                    enabled = canUndo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("UNDO", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(52.dp)
                        .width(104.dp),
                    onClick = onEndMatchRequested!!,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("END", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MatchCorrectionDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onAdjustTeamA: (Int) -> Unit,
    onAdjustTeamB: (Int) -> Unit,
    onServingTeamSelected: (Team) -> Unit,
    onServerNumberSelected: (ServerNumber) -> Unit
) {
    val configuration = LocalConfiguration.current
    val compactPhoneDialog = configuration.smallestScreenWidthDp < TabletSmallestWidthDp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Match",
                fontSize = if (compactPhoneDialog) 16.sp else 22.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (compactPhoneDialog) {
                            Modifier
                        } else {
                            Modifier
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState())
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compactPhoneDialog) 4.dp else 14.dp)
            ) {
                if (compactPhoneDialog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CorrectionScoreCard(
                            modifier = Modifier.weight(1f),
                            label = "My Team",
                            score = state.teamAScore,
                            color = TeamABlue,
                            onDecrease = { onAdjustTeamA(-1) },
                            onIncrease = { onAdjustTeamA(1) }
                        )
                        CorrectionScoreCard(
                            modifier = Modifier.weight(1f),
                            label = "Opponent",
                            score = state.teamBScore,
                            color = TeamBGreen,
                            onDecrease = { onAdjustTeamB(-1) },
                            onIncrease = { onAdjustTeamB(1) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CorrectionChoiceCard(
                            modifier = Modifier.weight(1f),
                            label = "Serving",
                            options = listOf(
                                CorrectionChoiceOption(
                                    label = "My Team",
                                    selected = state.servingTeam == Team.A,
                                    accent = TeamABlue,
                                    onClick = { onServingTeamSelected(Team.A) }
                                ),
                                CorrectionChoiceOption(
                                    label = "Opponent",
                                    selected = state.servingTeam == Team.B,
                                    accent = TeamBGreen,
                                    onClick = { onServingTeamSelected(Team.B) }
                                )
                            )
                        )
                        CorrectionChoiceCard(
                            modifier = Modifier.weight(1f),
                            label = "Server",
                            options = listOf(
                                CorrectionChoiceOption(
                                    label = "1",
                                    selected = state.serverNumber == ServerNumber.One,
                                    accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                    onClick = { onServerNumberSelected(ServerNumber.One) }
                                ),
                                CorrectionChoiceOption(
                                    label = "2",
                                    selected = state.serverNumber == ServerNumber.Two,
                                    accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                    onClick = { onServerNumberSelected(ServerNumber.Two) }
                                )
                            )
                        )
                    }
                } else {
                    CorrectionScoreRow(
                        label = "My Team",
                        score = state.teamAScore,
                        color = TeamABlue,
                        compact = false,
                        onDecrease = { onAdjustTeamA(-1) },
                        onIncrease = { onAdjustTeamA(1) }
                    )
                    CorrectionScoreRow(
                        label = "Opponent Team",
                        score = state.teamBScore,
                        color = TeamBGreen,
                        compact = false,
                        onDecrease = { onAdjustTeamB(-1) },
                        onIncrease = { onAdjustTeamB(1) }
                    )
                    CorrectionChoiceRow(
                        label = "Serving",
                        compact = false,
                        options = listOf(
                            CorrectionChoiceOption(
                                label = "My Team",
                                selected = state.servingTeam == Team.A,
                                accent = TeamABlue,
                                onClick = { onServingTeamSelected(Team.A) }
                            ),
                            CorrectionChoiceOption(
                                label = "Opponent",
                                selected = state.servingTeam == Team.B,
                                accent = TeamBGreen,
                                onClick = { onServingTeamSelected(Team.B) }
                            )
                        )
                    )
                    CorrectionChoiceRow(
                        label = "Server",
                        compact = false,
                        options = listOf(
                            CorrectionChoiceOption(
                                label = "1",
                                selected = state.serverNumber == ServerNumber.One,
                                accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                onClick = { onServerNumberSelected(ServerNumber.One) }
                            ),
                            CorrectionChoiceOption(
                                label = "2",
                                selected = state.serverNumber == ServerNumber.Two,
                                accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                onClick = { onServerNumberSelected(ServerNumber.Two) }
                            )
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "DONE",
                    fontSize = if (compactPhoneDialog) 12.sp else 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    )
}

private data class CorrectionChoiceOption(
    val label: String,
    val selected: Boolean,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun CorrectionScoreCard(
    modifier: Modifier = Modifier,
    label: String,
    score: Int,
    color: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = Modifier.width(32.dp),
                onClick = onDecrease,
                enabled = score > 0,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("−", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 3.dp),
                    text = score.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                modifier = Modifier.width(32.dp),
                onClick = onIncrease,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CorrectionChoiceCard(
    modifier: Modifier = Modifier,
    label: String,
    options: List<CorrectionChoiceOption>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = option.onClick,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (option.selected) option.accent else TableLine
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (option.selected) option.accent.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (option.selected) option.accent else Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = option.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrectionScoreRow(
    label: String,
    score: Int,
    color: Color,
    compact: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = label,
            color = Ink,
            fontSize = if (compact) 11.sp else 17.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = Modifier.width(if (compact) 42.dp else 64.dp),
                onClick = onDecrease,
                enabled = score > 0,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("−", fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(vertical = if (compact) 4.dp else 10.dp),
                    text = score.toString(),
                    color = Color.White,
                    fontSize = if (compact) 16.sp else 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                modifier = Modifier.width(if (compact) 42.dp else 64.dp),
                onClick = onIncrease,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CorrectionChoiceRow(
    label: String,
    compact: Boolean,
    options: List<CorrectionChoiceOption>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = label,
            color = Ink,
            fontSize = if (compact) 11.sp else 17.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            options.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = option.onClick,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (option.selected) option.accent else TableLine
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (option.selected) option.accent.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (option.selected) option.accent else Ink
                    ),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 4.dp else 10.dp,
                        vertical = if (compact) 4.dp else 10.dp
                    )
                ) {
                    Text(
                        text = option.label,
                        fontSize = if (compact) 10.sp else 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletScoreTapTarget(
    modifier: Modifier,
    score: Int,
    color: Color,
    enabled: Boolean,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = modifier
            .width(210.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score.toString(),
            color = color,
            fontSize = 122.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 126.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ScoreboardScreen(
    state: GameState,
    myTeamOnTop: Boolean,
    localCourtCode: String? = null,
    canUndo: Boolean,
    watchConnected: Boolean,
    tabletConnectionState: TabletConnectionState,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit,
    onCorrectionRequested: () -> Unit,
    onNavigateToSetup: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        color = Paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CourtCodeBadge(
                    modifier = Modifier.weight(1f),
                    code = localCourtCode ?: "0000"
                )
                WatchConnectionStatusBar(
                    modifier = Modifier.weight(1f),
                    connected = watchConnected
                )
                PhoneTabletStatusBar(
                    modifier = Modifier.weight(1f),
                    connectionState = tabletConnectionState,
                    isTabletPerspective = true
                )
            }
            ControlBar(
                state = state,
                canUndo = canUndo,
                onNavigateToSetup = onNavigateToSetup,
                onCorrectionRequested = onCorrectionRequested,
                onUndo = onUndo,
                onEndMatchRequested = onEndMatchRequested
            )
            ScoreboardBody(
                modifier = Modifier.weight(1f),
                state = state,
                myTeamOnTop = myTeamOnTop,
                onTeamARally = onTeamARally,
                onTeamBRally = onTeamBRally
            )
        }
    }
}

@Composable
private fun ScoreboardBody(
    modifier: Modifier,
    state: GameState,
    myTeamOnTop: Boolean,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit
) {
    val orderedTeams = if (myTeamOnTop) listOf(Team.A, Team.B) else listOf(Team.B, Team.A)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        orderedTeams.forEach { team ->
            TeamScoreTableRow(
                modifier = Modifier.weight(1f),
                name = state.courtOrderedTeamName(team),
                score = if (team == Team.A) state.teamAScore else state.teamBScore,
                color = if (team == Team.A) TeamABlue else TeamBGreen,
                isServing = state.servingTeam == team,
                serverNumber = state.serverNumber,
                enabled = true,
                onScoreClick = if (team == Team.A) onTeamARally else onTeamBRally,
                servingPlayerName = if (state.servingTeam == team) state.servingPlayerName() else ""
            )
        }
    }
}

@Composable
private fun TeamScoreTableRow(
    modifier: Modifier,
    name: String,
    score: Int,
    color: Color,
    isServing: Boolean,
    serverNumber: ServerNumber,
    enabled: Boolean,
    onScoreClick: () -> Unit,
    servingPlayerName: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        TableCell(
            modifier = Modifier
                .width(ServeCellWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            ServeDots(
                color = Color.White,
                isServing = isServing,
                serverNumber = serverNumber
            )
        }
        TableDivider()
        TableCell(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            TeamNameDisplay(
                modifier = Modifier.padding(horizontal = 16.dp),
                name = name,
                color = Color.White,
                highlightColor = color,
                servingPlayerName = servingPlayerName
            )
        }
        TableDivider()
        ScoreTapTarget(
            modifier = Modifier
                .fillMaxHeight(),
            score = score,
            color = Color.White,
            enabled = enabled,
            onClick = onScoreClick
        )
    }
}

@Composable
private fun TeamNameDisplay(
    modifier: Modifier,
    name: String,
    color: Color,
    highlightColor: Color,
    servingPlayerName: String = "",
    isTablet: Boolean = false
) {
    val parts = name.uppercase().split(" & ", limit = 2)
    val serving = servingPlayerName.uppercase().trim()
    val playerOne = parts.getOrNull(0)?.trim().orEmpty()
    val playerTwo = parts.getOrNull(1)?.trim().orEmpty()

    if (playerTwo.isNotEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp)
        ) {
            PositionedPlayerName(
                modifier = Modifier.weight(1f),
                name = playerOne,
                color = color,
                highlightColor = highlightColor,
                isServing = playerOne == serving,
                isTablet = isTablet
            )
            Box(
                modifier = Modifier
                    .width(if (isTablet) 6.dp else 4.dp)
                    .height(if (isTablet) 72.dp else 52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
            )
            PositionedPlayerName(
                modifier = Modifier.weight(1f),
                name = playerTwo,
                color = color,
                highlightColor = highlightColor,
                isServing = playerTwo == serving,
                isTablet = isTablet
            )
        }
    } else {
        PositionedPlayerName(
            modifier = modifier,
            name = name.uppercase(),
            color = color,
            highlightColor = highlightColor,
            isServing = name.uppercase().trim() == serving,
            isTablet = isTablet
        )
    }
}

@Composable
private fun PositionedPlayerName(
    modifier: Modifier,
    name: String,
    color: Color,
    highlightColor: Color,
    isServing: Boolean,
    isTablet: Boolean = false
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isServing) Color.White else Color.Transparent)
            .padding(
                horizontal = if (isServing) {
                    if (isTablet) 10.dp else 8.dp
                } else {
                    0.dp
                },
                vertical = if (isServing) {
                    if (isTablet) 5.dp else 4.dp
                } else {
                    0.dp
                }
            ),
        text = name,
        color = if (isServing) highlightColor else color,
        fontSize = when {
            isTablet && isServing -> 64.sp
            isTablet -> 56.sp
            isServing -> 40.sp
            else -> 34.sp
        },
        fontWeight = FontWeight.Black,
        lineHeight = when {
            isTablet && isServing -> 66.sp
            isTablet -> 60.sp
            isServing -> 42.sp
            else -> 38.sp
        },
        textDecoration = TextDecoration.None,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TableCell(
    modifier: Modifier,
    contentAlignment: Alignment,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
private fun TableDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(4.dp)
            .background(TableLine)
    )
}

@Composable
private fun ConnectionStatusBadge(
    connected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val background = if (connected) ConnectedAmber else ProblemRed
    val text = if (connected) "WATCH CONNECTED" else "WATCH OFFLINE"
    val textColor = if (connected) Ink else Color.White

    Box(
        modifier = modifier
            .then(
                if (compact) {
                    Modifier.size(32.dp)
                } else {
                    Modifier.height(32.dp)
                }
            )
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 0.dp else 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (compact) "⌚" else text,
            color = textColor,
            fontSize = if (compact) 17.sp else 11.sp,
            fontWeight = FontWeight.Black,
            lineHeight = if (compact) 18.sp else 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun VoiceAnnouncementControls(
    selectedMode: VoiceAnnouncementMode,
    onModeChange: (VoiceAnnouncementMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "VOICE ANNOUNCEMENTS",
            color = Ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = selectedMode.setupLabel(),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                VoiceAnnouncementMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.setupLabel(),
                                fontWeight = if (mode == selectedMode) FontWeight.Black else FontWeight.Medium
                            )
                        },
                        onClick = {
                            expanded = false
                            onModeChange(mode)
                        }
                    )
                }
            }
        }
    }
}

private fun VoiceAnnouncementMode.setupLabel(): String = when (this) {
    VoiceAnnouncementMode.Off -> "Off"
    VoiceAnnouncementMode.PhoneOnly -> "Phone"
    VoiceAnnouncementMode.WatchOnly -> "Watch"
    VoiceAnnouncementMode.TabletOnly -> "Tablet"
    VoiceAnnouncementMode.WatchThenPhone -> "Watch -> Phone"
    VoiceAnnouncementMode.WatchThenTablet -> "Watch -> Tablet"
    VoiceAnnouncementMode.PhoneThenTablet -> "Phone -> Tablet"
}

@Composable
private fun WatchConnectionStatusBar(
    modifier: Modifier = Modifier,
    connected: Boolean,
    compact: Boolean = false
) {
    val color = if (connected) ConnectedAmber else ProblemRed
    val label = if (connected) "WATCH CONNECTED" else "WATCH OFFLINE"

    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = if (compact) 8.sp else 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun PhoneTabletStatusBar(
    modifier: Modifier = Modifier,
    connectionState: TabletConnectionState,
    isTabletPerspective: Boolean = false,
    compact: Boolean = false
) {
    val color = connectionState.displayColor()
    val label = if (isTabletPerspective) {
        connectionState.displayLabel(pairedCourtCode = null)
    } else {
        connectionState.phoneDisplayLabel()
    }

    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = if (compact) 8.sp else 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun CourtCodeBadge(
    modifier: Modifier = Modifier,
    code: String,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ConnectedAmber)
        )
        Text(
            text = "COURT $code",
            color = Color.White,
            fontSize = if (compact) 8.sp else 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun AvailablePhonesCard(
    phones: List<TabletPhoneCandidate>,
    selectedPhoneHostId: String?,
    selectedCourtCode: String?,
    connectionState: TabletConnectionState,
    onJoinPhoneRequested: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AVAILABLE PHONES",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        phones.forEach { phone ->
            val isSelected = phone.hostId == selectedPhoneHostId
            val selectionSuffix = when {
                !isSelected -> null
                connectionState == TabletConnectionState.Connected -> "CONNECTED"
                selectedCourtCode != null -> "JOINING"
                else -> "SELECTED"
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                onClick = { onJoinPhoneRequested(phone.hostId) },
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ConnectedAmber else Color.White.copy(alpha = 0.35f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) ConnectedAmber.copy(alpha = 0.16f) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "COURT ${phone.courtCode}",
                        color = if (isSelected) ConnectedAmber else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        text = if (selectionSuffix != null) {
                            "${phone.label}  $selectionSuffix"
                        } else {
                            phone.label
                        },
                        color = if (isSelected) ConnectedAmber else Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ServeDots(color: Color, isServing: Boolean, serverNumber: ServerNumber) {
    Row(
        modifier = Modifier.size(width = 78.dp, height = 38.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(serverNumber.displayValue) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ScoreTapTarget(
    modifier: Modifier,
    score: Int,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(ScoreCellWidth)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, -4)
                }
            },
            text = score.toString(),
            color = color,
            fontSize = 62.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 62.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ControlBar(
    state: GameState,
    canUndo: Boolean,
    onNavigateToSetup: () -> Unit,
    onCorrectionRequested: () -> Unit,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(102.dp)
            .background(CallBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(start = 86.dp, end = 86.dp),
                text = state.scoreOnlyCallBarText(),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 74.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .height(36.dp)
                        .width(76.dp),
                    onClick = onNavigateToSetup,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("SETUP", fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(36.dp)
                        .width(76.dp),
                    onClick = onCorrectionRequested,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Text("EDIT", fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .height(36.dp)
                        .width(76.dp),
                    onClick = onUndo,
                    enabled = canUndo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("UNDO", fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(36.dp)
                        .width(76.dp),
                    onClick = onEndMatchRequested,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("END", fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

private fun GameState.teamColor(team: Team): Color =
    when (team) {
        Team.A -> TeamABlue
        Team.B -> TeamBGreen
    }

private fun GameState.scoreOnlyCallBarText() = buildAnnotatedString {
    val receivingTeam = servingTeam.opponent()
    pushStyle(SpanStyle(color = teamColor(servingTeam)))
    append(servingScore.toString())
    pop()
    append(" - ")
    pushStyle(SpanStyle(color = teamColor(receivingTeam)))
    append(receivingScore.toString())
    pop()
    append(" - ")
    pushStyle(SpanStyle(color = teamColor(servingTeam)))
    append(serverNumber.displayValue.toString())
    pop()
}

private fun GameState.servingSummary(): String {
    val servingSide = when (servingTeam) {
        Team.A -> settings.teamAName
        Team.B -> settings.teamBName
    }
    return "${servingSide.uppercase()} SERVES"
}

private fun GameState.toTabletDisplayState(
    myTeamOnTop: Boolean,
    matchActive: Boolean,
    canUndo: Boolean,
    voiceAnnouncementMode: VoiceAnnouncementMode,
    watchConnected: Boolean
): TabletDisplayState =
    TabletDisplayState(
        hostId = "",
        sessionId = "",
        myTeamOnTop = myTeamOnTop,
        watchConnected = watchConnected,
        teamAName = settings.teamAName,
        teamBName = settings.teamBName,
        teamACourtOrderedName = courtOrderedTeamName(Team.A),
        teamBCourtOrderedName = courtOrderedTeamName(Team.B),
        teamAScore = teamAScore,
        teamBScore = teamBScore,
        servingTeam = servingTeam,
        serverNumber = serverNumber.displayValue,
        servingPlayerName = servingPlayerName(),
        scoreCall = scoreCall,
        spokenScoreCall = spokenScoreCall(),
        voiceAnnouncementMode = voiceAnnouncementMode,
        matchActive = matchActive,
        canUndo = canUndo,
        updatedAt = System.currentTimeMillis()
    )

private fun VoiceAnnouncementMode.usesPhoneSpeaker(): Boolean =
    this == VoiceAnnouncementMode.PhoneOnly ||
        this == VoiceAnnouncementMode.WatchThenPhone ||
        this == VoiceAnnouncementMode.PhoneThenTablet

private fun VoiceAnnouncementMode.usesTabletSpeaker(): Boolean =
    this == VoiceAnnouncementMode.TabletOnly ||
        this == VoiceAnnouncementMode.WatchThenTablet ||
        this == VoiceAnnouncementMode.PhoneThenTablet

private fun VoiceAnnouncementMode.usesThisDeviceSpeaker(isTablet: Boolean): Boolean =
    if (isTablet) usesTabletSpeaker() else usesPhoneSpeaker()

private fun VoiceAnnouncementMode.isDelayedOnThisDevice(isTablet: Boolean, watchConnected: Boolean): Boolean =
    when {
        isTablet -> isDelayedOnTablet()
        this == VoiceAnnouncementMode.WatchThenPhone && watchConnected -> true
        else -> false
    }

private fun VoiceAnnouncementMode.isDelayedOnTablet(): Boolean =
    this == VoiceAnnouncementMode.WatchThenTablet ||
        this == VoiceAnnouncementMode.PhoneThenTablet

private fun TabletDisplayState.voiceSignature(): String =
    "$teamAScore|$teamBScore|$servingTeam|$serverNumber|$spokenScoreCall"

private fun TabletDisplayState.toCorrectionGameState(): GameState =
    GameState(
        teamAScore = teamAScore,
        teamBScore = teamBScore,
        servingTeam = servingTeam,
        serverNumber = if (serverNumber == 1) ServerNumber.One else ServerNumber.Two
    )

private fun normalizePlayerNamesInput(rawValue: String): String =
    rawValue
        .replace('\n', ' ')
        .replace('\r', ' ')
        .uppercase(Locale.ENGLISH)

private fun formatTeamName(player1: String, player2: String, team: Team): String {
    val fallback = when (team) {
        Team.A -> "ME"
        Team.B -> "OPPONENT"
    }
    val first = player1.trim()
    val second = player2.trim()
    return when {
        first.isNotEmpty() && second.isNotEmpty() -> "$first & $second"
        first.isNotEmpty() -> first
        second.isNotEmpty() -> second
        else -> fallback
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
