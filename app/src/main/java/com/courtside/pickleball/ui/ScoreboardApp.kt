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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletCommand
import com.courtside.pickleball.sync.TabletDisplayState
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
    val voiceAnnouncementMode by viewModel.voiceAnnouncementMode.collectAsStateWithLifecycle()
    val remoteTabletDisplayState by viewModel.remoteTabletDisplayState.collectAsStateWithLifecycle()
    val tabletConnectionState by viewModel.tabletConnectionState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val useTabletDisplayLayout = configuration.smallestScreenWidthDp >= TabletSmallestWidthDp
    val activeRemoteTabletState = remoteTabletDisplayState?.takeIf { it.matchActive }
    val activeRemoteTabletVoiceSignature = activeRemoteTabletState?.voiceSignature()
    val showRemoteTabletMatch = useTabletDisplayLayout && activeRemoteTabletState != null
    var setupTeamAPlayer1 by remember { mutableStateOf("P1") }
    var setupTeamAPlayer2 by remember { mutableStateOf("P2") }
    var setupTeamBPlayer1 by remember { mutableStateOf("P3") }
    var setupTeamBPlayer2 by remember { mutableStateOf("P4") }
    var startingTeam by remember { mutableStateOf<Team?>(Team.A) }
    var showEndMatchDialog by remember { mutableStateOf(false) }
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
            if (showRemoteTabletMatch) {
                TabletDisplayScreen(
                    state = activeRemoteTabletState!!,
                    connectionState = tabletConnectionState,
                    canUndo = activeRemoteTabletState.canUndo,
                    onTeamARally = { viewModel.sendTabletCommand(TabletCommand.TeamAWonRally) },
                    onTeamBRally = { viewModel.sendTabletCommand(TabletCommand.TeamBWonRally) },
                    onUndo = { viewModel.sendTabletCommand(TabletCommand.Undo) },
                    onEndMatchRequested = { viewModel.sendTabletCommand(TabletCommand.EndMatch) }
                )
            } else if (matchStarted && !editingSetupFromMatch) {
                if (useTabletDisplayLayout) {
                    TabletDisplayScreen(
                        state = state.toTabletDisplayState(
                            matchActive = true,
                            canUndo = viewModel.canUndo(),
                            voiceAnnouncementMode = voiceAnnouncementMode
                        ),
                        connectionState = tabletConnectionState,
                        canUndo = viewModel.canUndo(),
                        onTeamARally = { viewModel.recordRallyWinner(Team.A) },
                        onTeamBRally = { viewModel.recordRallyWinner(Team.B) },
                        onUndo = viewModel::undo,
                        onEndMatchRequested = { showEndMatchDialog = true }
                    )
                } else {
                    ScoreboardScreen(
                        state = state,
                        canUndo = viewModel.canUndo(),
                        watchConnected = watchConnected,
                        tabletConnectionState = tabletConnectionState,
                        onTeamARally = { viewModel.recordRallyWinner(Team.A) },
                        onTeamBRally = { viewModel.recordRallyWinner(Team.B) },
                        onUndo = viewModel::undo,
                        onEndMatchRequested = { showEndMatchDialog = true },
                        onNavigateToSetup = {
                            editingSetupFromMatch = true
                        }
                    )
                }
            } else {
                MatchSetupScreen(
                    editingFromMatch = editingSetupFromMatch,
                    isTabletLayout = true,
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
                    onStartingTeamChange = { startingTeam = it },
                    onSwapTeams = {
                        myTeamOnTop = !myTeamOnTop
                        startingTeam = when (startingTeam) {
                            Team.A -> Team.B
                            Team.B -> Team.A
                            null -> null
                        }
                    },
                    watchConnected = watchConnected,
                    tabletConnectionState = tabletConnectionState,
                    voiceAnnouncementMode = voiceAnnouncementMode,
                    onVoiceAnnouncementModeChange = {
                        voiceModeManuallySelected = true
                        viewModel.setVoiceAnnouncementMode(it)
                    },
                    onStart = {
                        val server = startingTeam ?: Team.A
                        viewModel.startMatch(
                            teamAName = formatTeamName(setupTeamAPlayer1, setupTeamAPlayer2, Team.A),
                            teamBName = formatTeamName(setupTeamBPlayer1, setupTeamBPlayer2, Team.B),
                            teamAPlayer1 = setupTeamAPlayer1,
                            teamAPlayer2 = setupTeamAPlayer2,
                            teamBPlayer1 = setupTeamBPlayer1,
                            teamBPlayer2 = setupTeamBPlayer2,
                            startingTeam = server
                        )
                        editingSetupFromMatch = false
                    },
                    onResumeMatch = {
                        viewModel.updateTeamNames(
                            teamAName = formatTeamName(setupTeamAPlayer1, setupTeamAPlayer2, Team.A),
                            teamBName = formatTeamName(setupTeamBPlayer1, setupTeamBPlayer2, Team.B),
                            teamAPlayer1 = setupTeamAPlayer1,
                            teamAPlayer2 = setupTeamAPlayer2,
                            teamBPlayer1 = setupTeamBPlayer1,
                            teamBPlayer2 = setupTeamBPlayer2
                        )
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
    }
}

@Composable
private fun MatchSetupScreen(
    teamAPlayer1: String,
    teamAPlayer2: String,
    teamBPlayer1: String,
    teamBPlayer2: String,
    startingTeam: Team?,
    onTeamAPlayer1Change: (String) -> Unit,
    onTeamAPlayer2Change: (String) -> Unit,
    onTeamBPlayer1Change: (String) -> Unit,
    onTeamBPlayer2Change: (String) -> Unit,
    onStartingTeamChange: (Team) -> Unit,
    editingFromMatch: Boolean = false,
    isTabletLayout: Boolean = true,
    myTeamOnTop: Boolean = true,
    onSwapTeams: () -> Unit = {},
    watchConnected: Boolean,
    tabletConnectionState: TabletConnectionState,
    voiceAnnouncementMode: VoiceAnnouncementMode,
    onVoiceAnnouncementModeChange: (VoiceAnnouncementMode) -> Unit,
    onStart: () -> Unit,
    onResumeMatch: () -> Unit = {}
) {
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val outerPadding = if (keyboardVisible) 10.dp else 18.dp
    val columnSpacing = if (keyboardVisible) 6.dp else 10.dp
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
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = if (keyboardVisible) Alignment.Top else Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1.38f)
                    .verticalScroll(setupScrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                    if (!keyboardVisible) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WatchConnectionStatusBar(
                                modifier = Modifier.widthIn(min = 138.dp),
                                connected = watchConnected
                            )
                            PhoneTabletStatusBar(
                                modifier = Modifier.widthIn(min = 148.dp),
                                connectionState = tabletConnectionState
                            )
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
                        fontSize = if (keyboardVisible) 20.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    if (!keyboardVisible) {
                        Text(
                            text = "Enter names by player standing position",
                            color = Ink.copy(alpha = 0.78f),
                            fontSize = 13.sp,
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
                modifier = Modifier.weight(0.62f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (keyboardVisible) 8.dp else 8.dp)
            ) {
                if (!keyboardVisible) {
                    Spacer(modifier = Modifier.height(38.dp))
                }
                ScorePreviewCard(
                    startingTeam = startingTeam,
                    compact = keyboardVisible,
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
                            .height(56.dp),
                        onClick = onResumeMatch,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315))
                    ) {
                        Text(
                            text = "RESUME GAME",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                } else if (!keyboardVisible) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        onClick = onStart,
                        enabled = canStart,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315))
                    ) {
                        Text(
                            text = "START NEW GAME",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                if (!keyboardVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                null -> "TAP A TEAM"
            },
            color = Color.White,
            fontSize = if (compact) 18.sp else 19.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun TabletDisplayScreen(
    state: TabletDisplayState,
    connectionState: TabletConnectionState = TabletConnectionState.Connected,
    canUndo: Boolean = false,
    onTeamARally: (() -> Unit)? = null,
    onTeamBRally: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null
) {
    val serverNumberEnum = state.serverNumber.toServerNumber()
    val isController = onUndo != null

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
                .padding(horizontal = 34.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusBar(
                connectionState = connectionState
            )
            TabletDisplayCallBar(
                state = state,
                canUndo = canUndo,
                onUndo = onUndo,
                onEndMatchRequested = onEndMatchRequested
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TabletTeamScorePanel(
                    modifier = Modifier.weight(1f),
                    name = state.teamAName,
                    score = state.teamAScore,
                    color = TeamABlue,
                    isServing = state.servingTeam == Team.A,
                    serverNumber = serverNumberEnum,
                    onTap = if (isController) onTeamARally else null
                )
                TabletTeamScorePanel(
                    modifier = Modifier.weight(1f),
                    name = state.teamBName,
                    score = state.teamBScore,
                    color = TeamBGreen,
                    isServing = state.servingTeam == Team.B,
                    serverNumber = serverNumberEnum,
                    onTap = if (isController) onTeamBRally else null
                )
            }
        }
    }
}

@Composable
private fun TabletTeamScorePanel(
    modifier: Modifier,
    name: String,
    score: Int,
    color: Color,
    isServing: Boolean,
    serverNumber: ServerNumber,
    isTablet: Boolean = true,
    enabled: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val nameFontSize = if (isTablet) 42.sp else 28.sp
    val nameLineHeight = if (isTablet) 44.sp else 32.sp
    val scoreFontSize = if (isTablet) 152.sp else 100.sp
    val scoreLineHeight = if (isTablet) 156.sp else 104.sp
    val dotSize = if (isTablet) 34.dp else 20.dp
    val dotSpacing = if (isTablet) 40.dp else 28.dp
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .then(
                if (onTap != null) {
                    Modifier.clickable(enabled = enabled, onClick = onTap)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTablet) 20.dp else 14.dp, vertical = if (isTablet) 12.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name.uppercase(),
                color = Color.White,
                fontSize = nameFontSize,
                fontWeight = FontWeight.Black,
                lineHeight = nameLineHeight,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isServing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(serverNumber.displayValue) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.size(dotSize))
                }
                Box(modifier = Modifier.width(dotSpacing))
                Text(
                    text = score.toString(),
                    color = Color.White,
                    fontSize = scoreFontSize,
                    fontWeight = FontWeight.Black,
                    lineHeight = scoreLineHeight,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Box(modifier = Modifier.width(dotSize + dotSpacing))
            }
        }
    }
}

private fun Int.toServerNumber(): ServerNumber = when (this) {
    1 -> ServerNumber.One
    else -> ServerNumber.Two
}

private fun TabletConnectionState.displayLabel(): String = when (this) {
    TabletConnectionState.Searching -> "SEARCHING FOR PHONE"
    TabletConnectionState.Reconnecting -> "PHONE RECONNECTING"
    TabletConnectionState.Connected -> "PHONE CONNECTED"
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
private fun ConnectionStatusBar(
    connectionState: TabletConnectionState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(connectionState.displayColor())
        )
        Text(
            text = connectionState.displayLabel(),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 14.dp)
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

@Composable
private fun TabletDisplayCallBar(
    state: TabletDisplayState,
    canUndo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null
) {
    val showControls = onUndo != null && onEndMatchRequested != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CallBackground)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "CALL",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 36.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                text = state.coloredScoreCall(),
                color = Color.White,
                fontSize = 212.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 204.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "${state.servingName().uppercase()} SERVES  •  SERVER ${state.serverNumber}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (showControls) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        modifier = Modifier.height(56.dp).width(120.dp),
                        onClick = onUndo!!,
                        enabled = canUndo,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("UNDO", fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                    OutlinedButton(
                        modifier = Modifier.height(56.dp).width(120.dp),
                        onClick = onEndMatchRequested!!,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("END", fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    watchConnected: Boolean,
    tabletConnectionState: TabletConnectionState,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit,
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
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WatchConnectionStatusBar(
                    modifier = Modifier.weight(1f),
                    connected = watchConnected
                )
                PhoneTabletStatusBar(
                    modifier = Modifier.weight(1f),
                    connectionState = tabletConnectionState
                )
                Button(
                    modifier = Modifier.height(36.dp),
                    onClick = onNavigateToSetup,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Setup",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            ScoreboardBody(
                modifier = Modifier.weight(1f),
                state = state,
                onTeamARally = onTeamARally,
                onTeamBRally = onTeamBRally
            )
            ControlBar(
                state = state,
                canUndo = canUndo,
                onUndo = onUndo,
                onEndMatchRequested = onEndMatchRequested
            )
        }
    }
}

@Composable
private fun ScoreboardBody(
    modifier: Modifier,
    state: GameState,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit
) {
    val gameOver = state.status is GameStatus.Complete

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TeamScoreTableRow(
            modifier = Modifier.weight(1f),
            name = state.courtOrderedTeamName(Team.A),
            score = state.teamAScore,
            color = TeamABlue,
            isServing = state.servingTeam == Team.A,
            serverNumber = state.serverNumber,
            enabled = !gameOver,
            onScoreClick = onTeamARally,
            servingPlayerName = if (state.servingTeam == Team.A) state.servingPlayerName() else ""
        )
        TeamScoreTableRow(
            modifier = Modifier.weight(1f),
            name = state.courtOrderedTeamName(Team.B),
            score = state.teamBScore,
            color = TeamBGreen,
            isServing = state.servingTeam == Team.B,
            serverNumber = state.serverNumber,
            enabled = !gameOver,
            onScoreClick = onTeamBRally,
            servingPlayerName = if (state.servingTeam == Team.B) state.servingPlayerName() else ""
        )
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
    servingPlayerName: String = ""
) {
    val parts = name.uppercase().split(" & ", limit = 2)
    val serving = servingPlayerName.uppercase().trim()
    val playerOne = parts.getOrNull(0)?.trim().orEmpty()
    val playerTwo = parts.getOrNull(1)?.trim().orEmpty()

    if (playerTwo.isNotEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PositionedPlayerName(
                modifier = Modifier.weight(1f),
                name = playerOne,
                color = color,
                highlightColor = highlightColor,
                isServing = playerOne == serving
            )
            Text(
                text = "|",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            PositionedPlayerName(
                modifier = Modifier.weight(1f),
                name = playerTwo,
                color = color,
                highlightColor = highlightColor,
                isServing = playerTwo == serving
            )
        }
    } else {
        PositionedPlayerName(
            modifier = modifier,
            name = name.uppercase(),
            color = color,
            highlightColor = highlightColor,
            isServing = name.uppercase().trim() == serving
        )
    }
}

@Composable
private fun PositionedPlayerName(
    modifier: Modifier,
    name: String,
    color: Color,
    highlightColor: Color,
    isServing: Boolean
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isServing) Color.White else Color.Transparent)
            .padding(horizontal = if (isServing) 8.dp else 0.dp, vertical = if (isServing) 4.dp else 0.dp),
        text = name,
        color = if (isServing) highlightColor else color,
        fontSize = if (isServing) 40.sp else 34.sp,
        fontWeight = FontWeight.Black,
        lineHeight = if (isServing) 42.sp else 38.sp,
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
    connected: Boolean
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
            fontSize = 10.sp,
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
    connectionState: TabletConnectionState
) {
    val color = connectionState.displayColor()
    val label = connectionState.phoneDisplayLabel()

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
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
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
            text = score.toString(),
            color = color,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 76.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ControlBar(
    state: GameState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .background(CallBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
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
                    .fillMaxWidth(),
                text = state.scoreOnlyCallBarText(state.status),
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 86.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .height(38.dp)
                        .width(82.dp),
                    onClick = onUndo,
                    enabled = canUndo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("UNDO", fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(38.dp)
                        .width(82.dp),
                    onClick = onEndMatchRequested,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("END", fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
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

private fun GameState.callBarText(status: GameStatus) = buildAnnotatedString {
    if (status is GameStatus.Complete) {
        pushStyle(SpanStyle(color = teamColor(status.winner)))
        append("${teamName(status.winner).uppercase()} WINS")
        pop()
        return@buildAnnotatedString
    }

    val receivingTeam = servingTeam.opponent()
    pushStyle(SpanStyle(color = Color.White, fontSize = 28.sp))
    append("CALL     ")
    pop()
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

private fun GameState.scoreOnlyCallBarText(status: GameStatus) = buildAnnotatedString {
    if (status is GameStatus.Complete) {
        pushStyle(SpanStyle(color = teamColor(status.winner)))
        append("${teamName(status.winner).uppercase()} WINS")
        pop()
        return@buildAnnotatedString
    }

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
    matchActive: Boolean,
    canUndo: Boolean,
    voiceAnnouncementMode: VoiceAnnouncementMode
): TabletDisplayState =
    TabletDisplayState(
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
