package com.courtside.pickleball.wear

import android.content.Context
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import java.util.Locale
import kotlinx.coroutines.delay

private val WatchBackground = Color(0xFF000000)
private val MainText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFFD1D5DB)
private val TeamBlue = Color(0xFF42A5F5)
private val TeamGreen = Color(0xFF66BB6A)
private val ConnectedAmber = Color(0xFFFFC107)
private val ProblemRed = Color(0xFFEF5350)
private val InactiveGray = Color(0xFF374151)
private val UndoButtonBackground = Color(0xFF111827)
private val TeamBluePanel = Color(0xFF1565C0)
private val TeamGreenPanel = Color(0xFF2E7D32)
private val ModeTealGreen = Color(0xFF2AA876)
private val ModeCyan = Color(0xFF26C6DA)
private val ModeSlate = Color(0xFF90A4AE)
private const val TAG = "WearScoreboardApp"
private const val WatchActionDebounceMs = 700L
private const val PhoneConfirmationTimeoutMs = 2_200L
private const val FeedbackVisibleMs = 900L
private const val PhoneRefreshIntervalMs = 5_000L
private const val CommandRefreshBurstCount = 6
private const val CommandRefreshBurstDelayMs = 400L
private const val ConnectedUiGraceMs = 15_000L
private const val ScoreSpeechRate = 0.9f
private val WatchStandaloneDefaults = GameSettings(
    teamAName = "P1 & P2",
    teamBName = "P3 & P4"
)

private enum class WatchCommandFeedback {
    Sent,
    Confirmed,
    Problem
}

private enum class ConnectedRemoteTarget {
    Phone,
    Tablet
}

private enum class WatchStartMode {
    Watch,
    Phone,
    Tablet
}

private enum class WearConnectionMode {
    WatchOnly,
    PhoneConnected,
    TabletConnected
}

@Composable
fun WearScoreboardApp() {
    val engine = remember { PickleballScoringEngine() }
    val history = remember { mutableStateListOf<GameState>() }
    var state by remember { mutableStateOf<GameState?>(null) }
    val phoneScoreState by WearPhoneSync.phoneScoreState.collectAsState()
    val phoneConnected by WearPhoneSync.phoneConnected.collectAsState()
    val tabletScoreState by WearTabletFallbackSync.tabletScoreState.collectAsState()
    val tabletConnected by WearTabletFallbackSync.tabletConnected.collectAsState()
    val tabletDiscoveryStatus by WearTabletFallbackSync.discoveryStatus.collectAsState()
    val discoveredTablets by WearTabletFallbackSync.discoveredTablets.collectAsState()
    val selectedTabletCourtCode by WearTabletFallbackSync.selectedCourtCode.collectAsState()
    var ttsReady by remember { mutableStateOf(false) }
    var pendingScoreCall by remember { mutableStateOf<String?>(null) }
    var lastWatchActionAt by remember { mutableStateOf(0L) }
    var awaitingPhoneConfirmation by remember { mutableStateOf(false) }
    var commandSentAt by remember { mutableStateOf(0L) }
    var baselineConnectedUpdateAt by remember { mutableStateOf<Long?>(null) }
    var watchCommandFeedback by remember { mutableStateOf<WatchCommandFeedback?>(null) }
    var lastConnectedScoreSignature by remember { mutableStateOf<String?>(null) }
    var lastStablePhoneMatchState by remember { mutableStateOf<PhoneScoreState?>(null) }
    var lastStablePhoneMatchSeenAt by remember { mutableStateOf(0L) }
    var showEndConfirmation by remember { mutableStateOf(false) }
    var connectedEndRequest by remember { mutableStateOf(false) }
    var pendingEndCommand by remember { mutableStateOf(false) }
    var connectedRemoteTarget by remember { mutableStateOf(ConnectedRemoteTarget.Phone) }
    var selectedStartMode by remember { mutableStateOf(WatchStartMode.Tablet) }
    var selectedStartingTeam by remember { mutableStateOf<Team?>(null) }
    var selectedStandaloneScoringFormat by remember { mutableStateOf(ScoringFormat.Traditional) }
    var uiElapsedRealtime by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun runWatchAction(action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWatchActionAt < WatchActionDebounceMs) return
        lastWatchActionAt = now
        action()
    }

    fun availableRemoteTargets(): List<ConnectedRemoteTarget> = buildList {
        if (phoneConnected) add(ConnectedRemoteTarget.Phone)
        if (tabletConnected) add(ConnectedRemoteTarget.Tablet)
    }

    fun availableStartModes(): List<WatchStartMode> = buildList {
        if (discoveredTablets.isNotEmpty() || tabletConnected) add(WatchStartMode.Tablet)
        add(WatchStartMode.Watch)
        if (phoneConnected) add(WatchStartMode.Phone)
    }

    fun activeConnectedState(): PhoneScoreState? = when {
        connectedRemoteTarget == ConnectedRemoteTarget.Phone && phoneConnected && phoneScoreState != null -> phoneScoreState
        connectedRemoteTarget == ConnectedRemoteTarget.Tablet && tabletConnected && tabletScoreState != null -> tabletScoreState
        phoneConnected && phoneScoreState != null -> phoneScoreState
        tabletConnected && tabletScoreState != null -> tabletScoreState
        connectedRemoteTarget == ConnectedRemoteTarget.Phone && phoneConnected -> phoneScoreState
        connectedRemoteTarget == ConnectedRemoteTarget.Tablet && tabletConnected -> tabletScoreState
        !tabletConnected -> phoneScoreState
        else -> tabletScoreState
    }

    fun activeConnectedRole(): ConnectedAndroidRole? = when {
        connectedRemoteTarget == ConnectedRemoteTarget.Phone && phoneConnected -> ConnectedAndroidRole.Phone
        connectedRemoteTarget == ConnectedRemoteTarget.Tablet && tabletConnected -> ConnectedAndroidRole.Tablet
        phoneConnected -> ConnectedAndroidRole.Phone
        tabletConnected -> ConnectedAndroidRole.Tablet
        else -> null
    }

    fun sendConnectedCommand(commandPath: String) {
        awaitingPhoneConfirmation = true
        commandSentAt = SystemClock.elapsedRealtime()
        baselineConnectedUpdateAt = activeConnectedState()?.updatedAt
        watchCommandFeedback = WatchCommandFeedback.Sent
        pendingEndCommand = commandPath == WearSyncContract.COMMAND_END_MATCH
        val sent = when {
            connectedRemoteTarget == ConnectedRemoteTarget.Phone && phoneConnected -> {
                WearPhoneSync.sendCommand(context, commandPath)
                true
            }
            connectedRemoteTarget == ConnectedRemoteTarget.Tablet -> {
                WearTabletFallbackSync.sendCommand(commandPath)
            }
            phoneConnected -> {
                WearPhoneSync.sendCommand(context, commandPath)
                true
            }
            else -> {
                WearTabletFallbackSync.sendCommand(commandPath)
            }
        }
        if (!sent) {
            awaitingPhoneConfirmation = false
            pendingEndCommand = false
            watchCommandFeedback = WatchCommandFeedback.Problem
        }
    }

    fun speakScoreCall(scoreCall: String) {
        tts.useBestAvailableVoice()
        tts.setSpeechRate(ScoreSpeechRate)
        tts.setPitch(1.0f)
        tts.setAudioAttributes(scoreAudioAttributes())
        tts.speak(scoreCall, TextToSpeech.QUEUE_FLUSH, scoreSpeechParams(), "wear-score-${System.nanoTime()}")
    }

    fun announceScore(gameState: GameState) {
        val scoreCall = gameState.spokenScoreCall()
        if (ttsReady) {
            speakScoreCall(scoreCall)
        } else {
            pendingScoreCall = scoreCall
        }
    }

    fun announceConfirmedPhoneScore(scoreState: PhoneScoreState) {
        if (!scoreState.voiceAnnouncementMode.usesWatchSpeaker()) {
            pendingScoreCall = null
            return
        }

        val scoreCall = scoreState.spokenScoreCall.ifBlank { scoreState.scoreCall }
        if (ttsReady) {
            speakScoreCall(scoreCall)
        } else {
            pendingScoreCall = scoreCall
        }
    }

    fun recordWinner(team: Team) {
        val current = state ?: return
        val next = engine.recordRallyWinner(current, team)
        if (next != current) {
            history += current
            state = next
            announceScore(next)
        }
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
        }
    }

    val activeConnectedScoreState = activeConnectedState()

    LaunchedEffect(activeConnectedScoreState?.updatedAt, awaitingPhoneConfirmation) {
        val updatedAt = activeConnectedScoreState?.updatedAt ?: return@LaunchedEffect
        val baseline = baselineConnectedUpdateAt
        if (awaitingPhoneConfirmation && (baseline == null || updatedAt > baseline)) {
            awaitingPhoneConfirmation = false
            pendingEndCommand = false
            watchCommandFeedback = WatchCommandFeedback.Confirmed
            context.vibrateWatchConfirmed()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(FeedbackVisibleMs)
            if (!awaitingPhoneConfirmation && watchCommandFeedback == WatchCommandFeedback.Confirmed) {
                watchCommandFeedback = null
            }
        }
    }

    LaunchedEffect(
        phoneConnected,
        tabletConnected,
        phoneScoreState?.updatedAt,
        tabletScoreState?.updatedAt,
        activeConnectedScoreState?.matchActive
    ) {
        val scoreState = activeConnectedScoreState
        if (pendingEndCommand && scoreState?.matchActive == false) {
            pendingEndCommand = false
            awaitingPhoneConfirmation = false
            watchCommandFeedback = null
            connectedEndRequest = false
            return@LaunchedEffect
        }

        if ((!(phoneConnected || tabletConnected)) || scoreState?.matchActive != true) {
            lastConnectedScoreSignature = null
            return@LaunchedEffect
        }

        val signature = scoreState.scoreSignature()
        val previousSignature = lastConnectedScoreSignature
        lastConnectedScoreSignature = signature

        if (previousSignature != null && signature != previousSignature) {
            announceConfirmedPhoneScore(scoreState)
        }
    }

    LaunchedEffect(commandSentAt, awaitingPhoneConfirmation) {
        if (!awaitingPhoneConfirmation) return@LaunchedEffect
        val pendingCommandSentAt = commandSentAt
        delay(PhoneConfirmationTimeoutMs)
        if (awaitingPhoneConfirmation && commandSentAt == pendingCommandSentAt) {
            awaitingPhoneConfirmation = false
            pendingEndCommand = false
            watchCommandFeedback = WatchCommandFeedback.Problem
            context.vibrateWatchProblem()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(FeedbackVisibleMs)
            if (!awaitingPhoneConfirmation && watchCommandFeedback == WatchCommandFeedback.Problem) {
                watchCommandFeedback = null
            }
        }
    }

    LaunchedEffect(commandSentAt, awaitingPhoneConfirmation) {
        if (!awaitingPhoneConfirmation) return@LaunchedEffect
        val pendingCommandSentAt = commandSentAt
        repeat(CommandRefreshBurstCount) {
            if (!awaitingPhoneConfirmation || commandSentAt != pendingCommandSentAt) return@LaunchedEffect
            WearPhoneSync.refreshPhoneState()
            if (!phoneConnected) {
                delay(CommandRefreshBurstDelayMs)
            }
            delay(CommandRefreshBurstDelayMs)
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            WearPhoneSync.refreshPhoneState()
            delay(PhoneRefreshIntervalMs)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            uiElapsedRealtime = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    LaunchedEffect(phoneConnected, tabletConnected, discoveredTablets.size, selectedTabletCourtCode) {
        val availableTargets = availableRemoteTargets()
        connectedRemoteTarget = when {
            connectedRemoteTarget in availableTargets -> connectedRemoteTarget
            ConnectedRemoteTarget.Tablet in availableTargets -> ConnectedRemoteTarget.Tablet
            ConnectedRemoteTarget.Phone in availableTargets -> ConnectedRemoteTarget.Phone
            else -> ConnectedRemoteTarget.Phone
        }

        val availableModes = availableStartModes()
        selectedStartMode = when {
            selectedStartMode in availableModes -> selectedStartMode
            else -> availableModes.firstOrNull() ?: WatchStartMode.Watch
        }
    }

    LaunchedEffect(phoneConnected, tabletConnected, phoneScoreState?.updatedAt, uiElapsedRealtime, state) {
        when {
            phoneConnected && phoneScoreState?.matchActive == true -> {
                lastStablePhoneMatchState = phoneScoreState
                lastStablePhoneMatchSeenAt = SystemClock.elapsedRealtime()
            }
            phoneScoreState?.matchActive == false -> {
                lastStablePhoneMatchState = null
                lastStablePhoneMatchSeenAt = 0L
            }
            state != null -> {
                lastStablePhoneMatchState = null
                lastStablePhoneMatchSeenAt = 0L
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WatchBackground
        ) {
            val current = state
            val remoteTargets = availableRemoteTargets()
            val selectedTabletAvailable = selectedTabletCourtCode != null &&
                discoveredTablets.any { it.courtCode == selectedTabletCourtCode }
            val liveConnectedScoreState = when {
                connectedRemoteTarget == ConnectedRemoteTarget.Phone && phoneConnected -> phoneScoreState
                connectedRemoteTarget == ConnectedRemoteTarget.Tablet && tabletConnected -> tabletScoreState
                phoneConnected -> phoneScoreState
                tabletConnected -> tabletScoreState
                else -> null
            }
            val graceConnectedScoreState = lastStablePhoneMatchState?.takeIf {
                state == null &&
                    !tabletConnected &&
                    phoneScoreState?.matchActive != false &&
                    uiElapsedRealtime - lastStablePhoneMatchSeenAt <= ConnectedUiGraceMs
            }
            val connectedScoreState = when {
                phoneConnected && liveConnectedScoreState?.sourceRole == ConnectedAndroidRole.Phone && liveConnectedScoreState.matchActive == true -> liveConnectedScoreState
                tabletConnected && liveConnectedScoreState?.sourceRole == ConnectedAndroidRole.Tablet && liveConnectedScoreState.matchActive == true -> liveConnectedScoreState
                tabletConnected && liveConnectedScoreState != null -> liveConnectedScoreState
                phoneConnected && liveConnectedScoreState != null -> liveConnectedScoreState
                graceConnectedScoreState != null -> graceConnectedScoreState
                phoneConnected || tabletConnected -> liveConnectedScoreState
                else -> null
            }
            fun startStandaloneMatch(servingTeam: Team) {
                val next = GameState(
                    servingTeam = servingTeam,
                    settings = WatchStandaloneDefaults.copy(
                        scoringFormat = selectedStandaloneScoringFormat
                    )
                )
                history.clear()
                state = next
                announceScore(next)
            }

            val connectedRemoteRole = connectedScoreState?.sourceRole ?: activeConnectedRole()

            if (current != null) {
                WearScoreboardScreen(
                    state = current,
                    canUndo = history.isNotEmpty(),
                    onTeamAWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            recordWinner(Team.A)
                        }
                    },
                    onTeamBWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            recordWinner(Team.B)
                        }
                    },
                    onUndo = {
                        runWatchAction {
                            context.vibrateDoubleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val previous = history.removeLastOrNull() ?: return@runWatchAction
                            state = previous
                            announceScore(previous)
                        }
                    },
                    onEndRequested = {
                        connectedEndRequest = false
                        showEndConfirmation = true
                    },
                    onReset = {
                        runWatchAction {
                            history.clear()
                            tts.stop()
                            state = null
                        }
                    }
                )
            } else if (connectedScoreState?.matchActive == true) {
                WearConnectedScoreboardScreen(
                    state = connectedScoreState,
                    connectionMode = when (connectedRemoteTarget) {
                        ConnectedRemoteTarget.Phone -> WearConnectionMode.PhoneConnected
                        ConnectedRemoteTarget.Tablet -> WearConnectionMode.TabletConnected
                    },
                    feedback = watchCommandFeedback,
                    actionsEnabled = !awaitingPhoneConfirmation,
                    onTeamAWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendConnectedCommand(WearSyncContract.COMMAND_A_WON_RALLY)
                        }
                    },
                    onTeamBWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendConnectedCommand(WearSyncContract.COMMAND_B_WON_RALLY)
                        }
                    },
                    onUndo = {
                        runWatchAction {
                            context.vibrateDoubleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sendConnectedCommand(WearSyncContract.COMMAND_UNDO)
                        }
                    },
                    onEndRequested = {
                        connectedEndRequest = true
                        showEndConfirmation = true
                    }
                )
            } else if (connectedRemoteRole != null || discoveredTablets.isNotEmpty()) {
                WearConnectedStartChoiceScreen(
                    connectionMode = when (selectedStartMode) {
                        WatchStartMode.Watch -> WearConnectionMode.WatchOnly
                        WatchStartMode.Phone -> if (phoneConnected) WearConnectionMode.PhoneConnected else WearConnectionMode.WatchOnly
                        WatchStartMode.Tablet -> if (tabletConnected) WearConnectionMode.TabletConnected else WearConnectionMode.WatchOnly
                    },
                    selectedMode = selectedStartMode,
                    availableModes = availableStartModes(),
                    selectedTabletCourtCode = selectedTabletCourtCode,
                    showTabletCourtSelector = discoveredTablets.size > 1,
                    tabletReady = tabletConnected || selectedTabletAvailable,
                    tabletModeStatus = when {
                        selectedStartMode != WatchStartMode.Tablet -> null
                        tabletConnected && selectedTabletCourtCode != null -> "COURT $selectedTabletCourtCode CONNECTED"
                        selectedTabletAvailable && selectedTabletCourtCode != null -> "COURT $selectedTabletCourtCode READY"
                        discoveredTablets.isNotEmpty() -> "TABLET FOUND"
                        tabletDiscoveryStatus == WearTabletFallbackSync.DiscoveryStatus.Found -> "TABLET FOUND"
                        else -> "SEARCHING TABLETS"
                    },
                    onModeCycle = {
                        val modes = availableStartModes()
                        val currentIndex = modes.indexOf(selectedStartMode).takeIf { it >= 0 } ?: -1
                        val nextMode = modes[(currentIndex + 1).floorMod(modes.size)]
                        selectedStartMode = nextMode
                        when (nextMode) {
                            WatchStartMode.Tablet -> connectedRemoteTarget = ConnectedRemoteTarget.Tablet
                            WatchStartMode.Phone -> {
                                connectedRemoteTarget = ConnectedRemoteTarget.Phone
                                WearPhoneSync.refreshPhoneState()
                            }
                            WatchStartMode.Watch -> Unit
                        }
                    },
                    onTabletCourtCycle = {
                        WearTabletFallbackSync.cycleSelectedTablet()
                    },
                    selectedScoringFormat = selectedStandaloneScoringFormat,
                    onScoringFormatSelected = { selectedStandaloneScoringFormat = it },
                    selectedStartingTeam = selectedStartingTeam,
                    onSelectTeamA = {
                        selectedStartingTeam = Team.A
                    },
                    onSelectTeamB = {
                        selectedStartingTeam = Team.B
                    },
                    onStart = {
                        runWatchAction {
                            when (selectedStartingTeam) {
                                Team.A -> when (selectedStartMode) {
                                    WatchStartMode.Watch -> startStandaloneMatch(Team.A)
                                    WatchStartMode.Phone -> {
                                        connectedRemoteTarget = ConnectedRemoteTarget.Phone
                                        context.vibrateSingleTap()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sendConnectedCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_A)
                                    }
                                    WatchStartMode.Tablet -> {
                                        connectedRemoteTarget = ConnectedRemoteTarget.Tablet
                                        context.vibrateSingleTap()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sendConnectedCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_A)
                                    }
                                }
                                Team.B -> when (selectedStartMode) {
                                    WatchStartMode.Watch -> startStandaloneMatch(Team.B)
                                    WatchStartMode.Phone -> {
                                        connectedRemoteTarget = ConnectedRemoteTarget.Phone
                                        context.vibrateSingleTap()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sendConnectedCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_B)
                                    }
                                    WatchStartMode.Tablet -> {
                                        connectedRemoteTarget = ConnectedRemoteTarget.Tablet
                                        context.vibrateSingleTap()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sendConnectedCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_B)
                                    }
                                }
                                null -> Unit
                            }
                        }
                    }
                )
            } else {
                WearServeSetupScreen(
                    connectionMode = when {
                        tabletConnected -> WearConnectionMode.TabletConnected
                        phoneConnected -> WearConnectionMode.PhoneConnected
                        else -> WearConnectionMode.WatchOnly
                    },
                    tabletStatus = when {
                        phoneConnected -> null
                        tabletConnected -> "TABLET CONNECTED"
                        tabletDiscoveryStatus == WearTabletFallbackSync.DiscoveryStatus.Found -> "TABLET FOUND"
                        else -> "SEARCHING TABLET"
                    },
                    selectedScoringFormat = selectedStandaloneScoringFormat,
                    onScoringFormatSelected = { selectedStandaloneScoringFormat = it },
                    selectedStartingTeam = selectedStartingTeam,
                    onSelectTeamA = {
                        selectedStartingTeam = Team.A
                    },
                    onSelectTeamB = {
                        selectedStartingTeam = Team.B
                    },
                    onStart = {
                        runWatchAction {
                            when (selectedStartingTeam) {
                                Team.A -> startStandaloneMatch(Team.A)
                                Team.B -> startStandaloneMatch(Team.B)
                                null -> Unit
                            }
                        }
                    }
                )
            }

            if (showEndConfirmation) {
                AlertDialog(
                    onDismissRequest = { showEndConfirmation = false },
                    containerColor = UndoButtonBackground,
                    title = {
                        Text(
                            text = "End Game ?",
                            color = MainText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showEndConfirmation = false
                                if (connectedEndRequest) {
                                    context.vibrateDoubleTap()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sendConnectedCommand(WearSyncContract.COMMAND_END_MATCH)
                                } else {
                                    history.clear()
                                    tts.stop()
                                    state = null
                                }
                            }
                        ) {
                            Text("END", color = ProblemRed, fontWeight = FontWeight.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndConfirmation = false }) {
                            Text("CANCEL", color = SecondaryText, fontWeight = FontWeight.Black)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WearServeSetupScreen(
    connectionMode: WearConnectionMode,
    tabletStatus: String? = null,
    selectedScoringFormat: ScoringFormat,
    onScoringFormatSelected: (ScoringFormat) -> Unit,
    selectedStartingTeam: Team?,
    onSelectTeamA: () -> Unit,
    onSelectTeamB: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
    ) {
        WearConnectionLabel(
            modifier = Modifier.padding(top = 4.dp),
            connectionMode = connectionMode
        )
        Text(
            text = "CHOOSE SERVER",
            color = SecondaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "0 - 0 - 2",
            color = MainText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (tabletStatus != null) {
            Text(
                text = tabletStatus,
                color = SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        WearScoringFormatSelector(
            selectedFormat = selectedScoringFormat,
            onFormatSelected = onScoringFormatSelected
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "WE SERVE\nFIRST",
                color = TeamBlue,
                selected = selectedStartingTeam == Team.A,
                onClick = onSelectTeamA
            )
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "OPP SERVE\nFIRST",
                color = TeamGreen,
                selected = selectedStartingTeam == Team.B,
                onClick = onSelectTeamB
            )
        }
        PrimaryStartButton(
            enabled = selectedStartingTeam != null,
            onClick = onStart
        )
    }
}

@Composable
private fun WearConnectedScoreboardScreen(
    state: PhoneScoreState,
    connectionMode: WearConnectionMode,
    feedback: WatchCommandFeedback?,
    actionsEnabled: Boolean,
    onTeamAWon: () -> Unit,
    onTeamBWon: () -> Unit,
    onUndo: () -> Unit,
    onEndRequested: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
    ) {
        WearConnectionLabel(
            modifier = Modifier.align(Alignment.TopCenter),
            connectionMode = connectionMode,
            feedback = feedback
        )

        ConnectedScoreSummary(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp),
            state = state,
            canUndo = state.canUndo && actionsEnabled,
            onUndo = onUndo,
            onEnd = onEndRequested,
            endEnabled = true
        )

        WearScorePanels(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            teamAScore = state.teamAScore,
            teamBScore = state.teamBScore,
            servingTeam = state.servingTeam,
            serverNumber = state.serverNumber,
            teamAName = "WE WON",
            teamBName = "OPP WON",
            enabled = actionsEnabled,
            onTeamATapped = onTeamAWon,
            onTeamBTapped = onTeamBWon
        )
    }
}

@Composable
private fun WearConnectedStartChoiceScreen(
    connectionMode: WearConnectionMode,
    selectedMode: WatchStartMode,
    availableModes: List<WatchStartMode>,
    selectedTabletCourtCode: String?,
    showTabletCourtSelector: Boolean,
    tabletReady: Boolean,
    tabletModeStatus: String?,
    onModeCycle: () -> Unit,
    onTabletCourtCycle: () -> Unit,
    selectedScoringFormat: ScoringFormat,
    onScoringFormatSelected: (ScoringFormat) -> Unit,
    selectedStartingTeam: Team?,
    onSelectTeamA: () -> Unit,
    onSelectTeamB: () -> Unit,
    onStart: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedMode, showTabletCourtSelector, tabletReady) {
        if (selectedMode == WatchStartMode.Tablet && !showTabletCourtSelector) {
            scrollState.scrollTo(scrollState.maxValue)
        } else {
            scrollState.scrollTo(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
    ) {
        WearConnectionLabel(
            modifier = Modifier.padding(top = 4.dp),
            connectionMode = connectionMode
        )
        WearStartModeButton(
            label = when (selectedMode) {
                WatchStartMode.Tablet -> "TABLET MODE"
                WatchStartMode.Watch -> "WATCH MODE"
                WatchStartMode.Phone -> "PHONE MODE"
            },
            subtitle = if (availableModes.size > 1) "TAP TO SWITCH" else null,
            color = when (selectedMode) {
                WatchStartMode.Tablet -> ModeTealGreen
                WatchStartMode.Watch -> ModeSlate
                WatchStartMode.Phone -> ModeCyan
            },
            onClick = onModeCycle
        )
        if (selectedMode == WatchStartMode.Tablet && showTabletCourtSelector) {
            WearStartModeButton(
                label = selectedTabletCourtCode?.let { "COURT $it" } ?: "SEARCHING TABLETS",
                subtitle = when {
                    selectedTabletCourtCode != null && availableModes.contains(WatchStartMode.Tablet) -> "TAP TO SWITCH"
                    else -> tabletModeStatus
                },
                color = ConnectedAmber,
                onClick = onTabletCourtCycle
            )
        } else if (tabletModeStatus != null) {
            Text(
                text = tabletModeStatus,
                color = SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        if (selectedMode == WatchStartMode.Watch) {
            WearScoringFormatSelector(
                selectedFormat = selectedScoringFormat,
                onFormatSelected = onScoringFormatSelected
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "WE SERVE\nFIRST",
                color = TeamBlue,
                selected = selectedStartingTeam == Team.A,
                onClick = onSelectTeamA
            )
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "OPP SERVE\nFIRST",
                color = TeamGreen,
                selected = selectedStartingTeam == Team.B,
                onClick = onSelectTeamB
            )
        }
        PrimaryStartButton(
            enabled = selectedStartingTeam != null &&
                (selectedMode != WatchStartMode.Tablet || tabletReady),
            onClick = onStart
        )
    }
}

@Composable
private fun WearScoringFormatSelector(
    selectedFormat: ScoringFormat,
    onFormatSelected: (ScoringFormat) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WearScoringFormatButton(
            modifier = Modifier.weight(1f),
            label = "CLASSIC",
            selected = selectedFormat == ScoringFormat.Traditional,
            onClick = { onFormatSelected(ScoringFormat.Traditional) }
        )
        WearScoringFormatButton(
            modifier = Modifier.weight(1f),
            label = "RALLY",
            selected = selectedFormat == ScoringFormat.Rally,
            onClick = { onFormatSelected(ScoringFormat.Rally) }
        )
    }
}

@Composable
private fun WearScoringFormatButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) ConnectedAmber else MainText.copy(alpha = 0.28f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) ConnectedAmber.copy(alpha = 0.18f) else WatchBackground,
            contentColor = if (selected) ConnectedAmber else MainText
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WearStartModeButton(
    label: String,
    subtitle: String? = null,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .height(54.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            2.dp,
            color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = color.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun Int.floorMod(modulus: Int): Int =
    if (modulus == 0) 0 else ((this % modulus) + modulus) % modulus

@Composable
private fun WearScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    onTeamAWon: () -> Unit,
    onTeamBWon: () -> Unit,
    onUndo: () -> Unit,
    onEndRequested: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
    ) {
        WearConnectionLabel(
            modifier = Modifier.align(Alignment.TopCenter),
            connectionMode = WearConnectionMode.WatchOnly
        )

        ScoreSummary(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp),
            state = state,
            canUndo = canUndo,
            onUndo = onUndo,
            onEnd = onEndRequested
        )

        WearScorePanels(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            teamAScore = state.teamAScore,
            teamBScore = state.teamBScore,
            servingTeam = state.servingTeam,
            serverNumber = state.serverNumber.displayValue,
            teamAName = "WE WON",
            teamBName = "OPP WON",
            enabled = true,
            onTeamATapped = onTeamAWon,
            onTeamBTapped = onTeamBWon
        )
    }
}

@Composable
private fun ServeChoiceButton(
    modifier: Modifier,
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else WatchBackground,
            contentColor = MainText,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) color else MainText.copy(alpha = 0.28f)
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 1.05.em,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun PrimaryStartButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .height(40.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MainText,
            contentColor = WatchBackground,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = "START",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ScoreSummary(
    modifier: Modifier = Modifier,
    state: GameState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onEnd: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        WearCallScoreText(
            teamAScore = state.teamAScore,
            teamBScore = state.teamBScore,
            servingTeam = state.servingTeam,
            serverNumber = state.serverNumber.displayValue
        )
        WearUtilityRow(
            canUndo = canUndo,
            onUndo = onUndo,
            endEnabled = true,
            onEnd = onEnd
        )
    }
}

@Composable
private fun ConnectedScoreSummary(
    modifier: Modifier = Modifier,
    state: PhoneScoreState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onEnd: () -> Unit,
    endEnabled: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        WearCallScoreText(
            teamAScore = state.teamAScore,
            teamBScore = state.teamBScore,
            servingTeam = state.servingTeam,
            serverNumber = state.serverNumber
        )
        WearUtilityRow(
            canUndo = canUndo,
            onUndo = onUndo,
            endEnabled = endEnabled,
            onEnd = onEnd
        )
    }
}

@Composable
private fun WearConnectionLabel(
    modifier: Modifier = Modifier,
    connectionMode: WearConnectionMode,
    feedback: WatchCommandFeedback? = null
) {
    val isProblem = feedback == WatchCommandFeedback.Problem
    val color = when {
        isProblem -> ProblemRed
        connectionMode == WearConnectionMode.WatchOnly -> ConnectedAmber
        else -> ConnectedAmber
    }
    val text = when (feedback) {
        WatchCommandFeedback.Sent -> "SENT"
        WatchCommandFeedback.Confirmed -> "SCORE OK"
        WatchCommandFeedback.Problem -> "CHECK DEVICE"
        null -> when (connectionMode) {
            WearConnectionMode.TabletConnected -> "TABLET CONNECTED"
            WearConnectionMode.PhoneConnected -> "PHONE CONNECTED"
            WearConnectionMode.WatchOnly -> "WATCH ONLY"
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun ConnectedAndroidRole.startLabel(): String = when (this) {
    ConnectedAndroidRole.Phone -> "PHONE"
    ConnectedAndroidRole.Tablet -> "TABLET"
}

@Composable
private fun WearScorePanels(
    modifier: Modifier = Modifier,
    teamAScore: Int,
    teamBScore: Int,
    servingTeam: Team,
    serverNumber: Int,
    teamAName: String,
    teamBName: String,
    enabled: Boolean,
    onTeamATapped: () -> Unit,
    onTeamBTapped: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WearScorePanel(
            modifier = Modifier.weight(1f),
            label = teamAName,
            score = teamAScore,
            isServing = servingTeam == Team.A,
            serverNumber = serverNumber,
            panelColor = TeamBluePanel,
            enabled = enabled,
            onClick = onTeamATapped
        )
        WearScorePanel(
            modifier = Modifier.weight(1f),
            label = teamBName,
            score = teamBScore,
            isServing = servingTeam == Team.B,
            serverNumber = serverNumber,
            panelColor = TeamGreenPanel,
            enabled = enabled,
            onClick = onTeamBTapped
        )
    }
}

@Composable
private fun WearScorePanel(
    modifier: Modifier,
    label: String,
    score: Int,
    isServing: Boolean,
    serverNumber: Int,
    panelColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = "$label score" },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = panelColor,
            contentColor = MainText,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = label,
                color = MainText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
            ) {
                WearServeDots(
                    isServing = isServing,
                    serverNumber = serverNumber
                )
                Text(
                    text = score.toString(),
                    color = MainText,
                    fontSize = 35.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WearCallScoreText(
    teamAScore: Int,
    teamBScore: Int,
    servingTeam: Team,
    serverNumber: Int
) {
    val servingScore = if (servingTeam == Team.A) teamAScore else teamBScore
    val receivingScore = if (servingTeam == Team.A) teamBScore else teamAScore
    val servingColor = if (servingTeam == Team.A) TeamBlue else TeamGreen
    val receivingColor = if (servingTeam == Team.A) TeamGreen else TeamBlue

    Text(
        modifier = Modifier.fillMaxWidth(0.82f),
        text = AnnotatedString.Builder().apply {
            withStyle(SpanStyle(color = servingColor, fontWeight = FontWeight.Black)) {
                append(servingScore.toString())
            }
            withStyle(SpanStyle(color = SecondaryText, fontWeight = FontWeight.SemiBold)) {
                append(" - ")
            }
            withStyle(SpanStyle(color = receivingColor, fontWeight = FontWeight.Black)) {
                append(receivingScore.toString())
            }
            withStyle(SpanStyle(color = SecondaryText, fontWeight = FontWeight.SemiBold)) {
                append(" - ")
            }
            withStyle(SpanStyle(color = servingColor, fontWeight = FontWeight.Black)) {
                append(serverNumber.toString())
            }
        }.toAnnotatedString(),
        fontSize = 34.sp,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WearUtilityRow(
    canUndo: Boolean,
    onUndo: () -> Unit,
    endEnabled: Boolean,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(1.dp, if (canUndo) ConnectedAmber else InactiveGray),
                    RoundedCornerShape(14.dp)
                )
                .background(UndoButtonBackground)
                .combinedClickable(
                    enabled = canUndo || endEnabled,
                    onClick = {
                        if (canUndo) onUndo()
                    },
                    onLongClick = {
                        if (endEnabled) onEnd()
                    }
                )
                .semantics {
                    contentDescription = if (endEnabled) {
                        "Undo. Long press to end game"
                    } else {
                        "Undo"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                color = if (canUndo) MainText else SecondaryText.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun WearServeDots(
    isServing: Boolean,
    serverNumber: Int
) {
    Row(
        modifier = Modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(serverNumber.coerceIn(1, 2)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(ConnectedAmber, CircleShape)
                )
            }
        }
    }
}

private fun String.watchTeamLabel(): String {
    val players = uppercase().split(" & ").map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        players.size >= 2 -> "${players[0].take(2)} ${players[1].take(2)}"
        players.isNotEmpty() -> players.first().take(2)
        else -> "--"
    }
}

@Composable
private fun RallyButton(
    modifier: Modifier,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(40.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MainText,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            maxLines = 2
        )
    }
}

private fun PhoneScoreState.scoreSignature(): String =
    "$teamAScore|$teamBScore|$servingTeam|$serverNumber|$scoreCall"

private fun VoiceAnnouncementMode.usesWatchSpeaker(): Boolean =
    this == VoiceAnnouncementMode.WatchOnly ||
        this == VoiceAnnouncementMode.WatchThenPhone ||
        this == VoiceAnnouncementMode.WatchThenTablet

private fun Context.vibrateSingleTap() {
    vibratePattern(longArrayOf(0, 24), intArrayOf(0, 160))
}

private fun Context.vibrateDoubleTap() {
    vibratePattern(longArrayOf(0, 20, 42, 20), intArrayOf(0, 150, 0, 150))
}

private fun Context.vibrateWatchConfirmed() {
    vibratePattern(longArrayOf(0, 28, 55, 28), intArrayOf(0, 120, 0, 120))
}

private fun Context.vibrateWatchProblem() {
    vibratePattern(longArrayOf(0, 130), intArrayOf(0, 220))
}

@Suppress("DEPRECATION")
private fun Context.vibratePattern(timings: LongArray, amplitudes: IntArray) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(timings, -1)
        }
    } catch (error: SecurityException) {
        Log.w(TAG, "Skipping watch vibration because permission is unavailable", error)
    } catch (error: RuntimeException) {
        Log.w(TAG, "Skipping watch vibration after vibrator service error", error)
    }
}

private fun scoreAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

private fun scoreSpeechParams(): Bundle =
    Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
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
