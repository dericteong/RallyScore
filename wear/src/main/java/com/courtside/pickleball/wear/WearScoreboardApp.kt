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
import androidx.compose.runtime.produceState
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
import com.courtside.pickleball.wear.scoreboard.WearConnectedScoreboardScreen
import com.courtside.pickleball.wear.scoreboard.WearScoreboardScreen
import com.courtside.pickleball.wear.setup.WearConnectedStartChoiceScreen
import com.courtside.pickleball.wear.setup.WearServeSetupScreen
import com.courtside.pickleball.wear.setup.floorMod
import com.courtside.pickleball.wear.theme.MainText
import com.courtside.pickleball.wear.theme.ProblemRed
import com.courtside.pickleball.wear.theme.SecondaryText
import com.courtside.pickleball.wear.theme.UndoButtonBackground
import com.courtside.pickleball.wear.theme.WatchBackground
import java.util.Locale
import kotlinx.coroutines.delay

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

internal enum class WatchCommandFeedback {
    Sent,
    Confirmed,
    Problem
}

private enum class ConnectedRemoteTarget {
    Phone,
    Tablet
}

internal enum class WatchStartMode {
    Watch,
    Phone,
    Tablet
}

internal enum class WearConnectionMode {
    WatchOnly,
    PhoneConnected,
    TabletConnected
}

private data class AmbientScoreInfo(
    val teamAName: String,
    val teamBName: String,
    val teamAScore: Int,
    val teamBScore: Int,
    val scoreCall: String
)

private fun GameState.toAmbientScoreInfo() = AmbientScoreInfo(
    teamAName = settings.teamAName,
    teamBName = settings.teamBName,
    teamAScore = teamAScore,
    teamBScore = teamBScore,
    scoreCall = scoreCall
)

private fun PhoneScoreState.toAmbientScoreInfo() = AmbientScoreInfo(
    teamAName = teamAName,
    teamBName = teamBName,
    teamAScore = teamAScore,
    teamBScore = teamBScore,
    scoreCall = scoreCall
)

/**
 * Low-power ambient rendering: mostly black with dim gray text and no large filled color
 * blocks, per Wear OS burn-in guidance. Shown in place of the normal interactive screens
 * whenever the system puts the display into ambient mode instead of turning it fully off.
 */
@Composable
private fun AmbientScoreView(info: AmbientScoreInfo?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (info == null) {
            Text(
                text = "RallyScore",
                color = Color(0xFF616161),
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = info.scoreCall,
                    color = Color(0xFFBDBDBD),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${info.teamAName} ${info.teamAScore} · ${info.teamBName} ${info.teamBScore}",
                    color = Color(0xFF757575),
                    fontSize = 12.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun rememberConnectedUiGraceState(
    lastSeenAtElapsed: Long,
    enabled: Boolean
): Boolean {
    val isGraceActive by produceState(
        initialValue = enabled && lastSeenAtElapsed > 0L,
        lastSeenAtElapsed,
        enabled
    ) {
        if (!enabled || lastSeenAtElapsed <= 0L) {
            value = false
            return@produceState
        }
        while (true) {
            val remainingMs = ConnectedUiGraceMs -
                (SystemClock.elapsedRealtime() - lastSeenAtElapsed)
            if (remainingMs <= 0L) {
                value = false
                break
            }
            value = true
            delay(remainingMs.coerceAtMost(1_000L))
        }
    }
    return isGraceActive
}

@Composable
fun WearScoreboardApp(isAmbient: Boolean = false) {
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

    val shouldPollPhoneState = awaitingPhoneConfirmation ||
        connectedEndRequest ||
        (selectedStartMode == WatchStartMode.Phone && state == null) ||
        (connectedRemoteTarget == ConnectedRemoteTarget.Phone &&
            (phoneConnected || phoneScoreState?.matchActive == true))

    // Deliberately not gated on selectedStartMode/connectedRemoteTarget/tabletConnected: this is
    // a passive listen-only UDP socket (the tablet does the broadcasting), so it's cheap to keep
    // running any time there's no active match. Gating it on "already in Tablet-related mode"
    // used to create a deadlock - Tablet mode only appears once a tablet is discovered, but
    // discovery only ran once Tablet mode was already selected - so switching away from Tablet
    // mode while no tablet was connected killed discovery permanently until the watch app was
    // fully relaunched (which resets selectedStartMode back to its default).
    val shouldRunTabletFallbackDiscovery = state == null &&
        activeConnectedScoreState?.matchActive != true

    LaunchedEffect(shouldPollPhoneState) {
        if (!shouldPollPhoneState) return@LaunchedEffect
        while (true) {
            WearPhoneSync.refreshPhoneState()
            delay(PhoneRefreshIntervalMs)
        }
    }

    LaunchedEffect(shouldRunTabletFallbackDiscovery) {
        if (shouldRunTabletFallbackDiscovery) {
            WearTabletFallbackSync.startDiscovery()
        } else {
            WearTabletFallbackSync.stopDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            WearTabletFallbackSync.stop()
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

    LaunchedEffect(phoneConnected, tabletConnected, phoneScoreState?.updatedAt, state) {
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
            val showConnectedGraceState = rememberConnectedUiGraceState(
                lastSeenAtElapsed = lastStablePhoneMatchSeenAt,
                enabled = state == null &&
                    !tabletConnected &&
                    phoneScoreState?.matchActive != false
            )
            val graceConnectedScoreState = lastStablePhoneMatchState?.takeIf {
                state == null &&
                    !tabletConnected &&
                    phoneScoreState?.matchActive != false &&
                    showConnectedGraceState
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

            if (isAmbient) {
                AmbientScoreView(
                    info = current?.toAmbientScoreInfo()
                        ?: connectedScoreState?.takeIf { it.matchActive }?.toAmbientScoreInfo()
                )
            } else if (current != null) {
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

private fun PhoneScoreState.scoreSignature(): String =
    "$teamAScore|$teamBScore|$servingTeam|$serverNumber|$scoreCall"

private fun VoiceAnnouncementMode.usesWatchSpeaker(): Boolean =
    this == VoiceAnnouncementMode.WatchOnly ||
        this == VoiceAnnouncementMode.WatchThenPhone ||
        this == VoiceAnnouncementMode.WatchThenTablet ||
        this == VoiceAnnouncementMode.WatchThenPhoneThenTablet

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
