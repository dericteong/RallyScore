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
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameSettings
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.PickleballScoringEngine
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
private const val TAG = "WearScoreboardApp"
private const val WatchActionDebounceMs = 700L
private const val PhoneConfirmationTimeoutMs = 2_200L
private const val FeedbackVisibleMs = 900L
private const val PhoneRefreshIntervalMs = 5_000L
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

private enum class ConnectedStartTarget {
    Phone,
    Watch
}

@Composable
fun WearScoreboardApp() {
    val engine = remember { PickleballScoringEngine() }
    val history = remember { mutableStateListOf<GameState>() }
    var state by remember { mutableStateOf<GameState?>(null) }
    val phoneScoreState by WearPhoneSync.phoneScoreState.collectAsState()
    val phoneConnected by WearPhoneSync.phoneConnected.collectAsState()
    var ttsReady by remember { mutableStateOf(false) }
    var pendingScoreCall by remember { mutableStateOf<String?>(null) }
    var lastWatchActionAt by remember { mutableStateOf(0L) }
    var awaitingPhoneConfirmation by remember { mutableStateOf(false) }
    var commandSentAt by remember { mutableStateOf(0L) }
    var baselinePhoneUpdateAt by remember { mutableStateOf<Long?>(null) }
    var watchCommandFeedback by remember { mutableStateOf<WatchCommandFeedback?>(null) }
    var lastConnectedScoreSignature by remember { mutableStateOf<String?>(null) }
    var showEndConfirmation by remember { mutableStateOf(false) }
    var connectedEndRequest by remember { mutableStateOf(false) }
    var pendingEndCommand by remember { mutableStateOf(false) }
    var connectedStartTarget by remember { mutableStateOf(ConnectedStartTarget.Phone) }
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

    fun sendPhoneCommand(commandPath: String) {
        awaitingPhoneConfirmation = true
        commandSentAt = SystemClock.elapsedRealtime()
        baselinePhoneUpdateAt = phoneScoreState?.updatedAt
        watchCommandFeedback = WatchCommandFeedback.Sent
        pendingEndCommand = commandPath == WearSyncContract.COMMAND_END_MATCH
        WearPhoneSync.sendCommand(context, commandPath)
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

    LaunchedEffect(phoneScoreState?.updatedAt, awaitingPhoneConfirmation) {
        val updatedAt = phoneScoreState?.updatedAt ?: return@LaunchedEffect
        val baseline = baselinePhoneUpdateAt
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

    LaunchedEffect(phoneConnected, phoneScoreState?.updatedAt) {
        val scoreState = phoneScoreState
        if (pendingEndCommand && phoneConnected && scoreState?.matchActive == false) {
            pendingEndCommand = false
            awaitingPhoneConfirmation = false
            watchCommandFeedback = null
            connectedEndRequest = false
            return@LaunchedEffect
        }

        if (!phoneConnected || scoreState?.matchActive != true) {
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

    LaunchedEffect(phoneConnected, phoneScoreState?.matchActive, state) {
        if (!phoneConnected || phoneScoreState?.matchActive == true || state != null) {
            connectedStartTarget = ConnectedStartTarget.Phone
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WatchBackground
        ) {
            val current = state
            val connectedScoreState = phoneScoreState.takeIf { phoneConnected }
            fun startStandaloneMatch(servingTeam: Team) {
                val next = GameState(
                    servingTeam = servingTeam,
                    settings = WatchStandaloneDefaults
                )
                history.clear()
                state = next
                announceScore(next)
            }

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
                    feedback = watchCommandFeedback,
                    actionsEnabled = !awaitingPhoneConfirmation,
                    onTeamAWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendPhoneCommand(WearSyncContract.COMMAND_A_WON_RALLY)
                        }
                    },
                    onTeamBWon = {
                        runWatchAction {
                            context.vibrateSingleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendPhoneCommand(WearSyncContract.COMMAND_B_WON_RALLY)
                        }
                    },
                    onUndo = {
                        runWatchAction {
                            context.vibrateDoubleTap()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sendPhoneCommand(WearSyncContract.COMMAND_UNDO)
                        }
                    },
                    onEndRequested = {
                        connectedEndRequest = true
                        showEndConfirmation = true
                    }
                )
            } else if (connectedScoreState != null) {
                if (connectedStartTarget == ConnectedStartTarget.Phone) {
                    WearConnectedStartChoiceScreen(
                        onTeamAStarts = {
                            runWatchAction {
                                context.vibrateSingleTap()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sendPhoneCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_A)
                            }
                        },
                        onTeamBStarts = {
                            runWatchAction {
                                context.vibrateSingleTap()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sendPhoneCommand(WearSyncContract.COMMAND_START_MATCH_TEAM_B)
                            }
                        },
                        title = "START ON PHONE",
                        secondaryLabel = "START ON WATCH",
                        onSecondaryAction = {
                            connectedStartTarget = ConnectedStartTarget.Watch
                        }
                    )
                } else {
                    WearConnectedStartChoiceScreen(
                        onTeamAStarts = {
                            runWatchAction {
                                startStandaloneMatch(Team.A)
                            }
                        },
                        onTeamBStarts = {
                            runWatchAction {
                                startStandaloneMatch(Team.B)
                            }
                        },
                        title = "START ON WATCH",
                        secondaryLabel = "START ON PHONE",
                        onSecondaryAction = {
                            connectedStartTarget = ConnectedStartTarget.Phone
                            WearPhoneSync.refreshPhoneState()
                        }
                    )
                }
            } else {
                WearServeSetupScreen(
                    onTeamAStarts = {
                        runWatchAction {
                            startStandaloneMatch(Team.A)
                        }
                    },
                    onTeamBStarts = {
                        runWatchAction {
                            startStandaloneMatch(Team.B)
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
                            text = "End game?",
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
                                    awaitingPhoneConfirmation = true
                                    commandSentAt = SystemClock.elapsedRealtime()
                                    baselinePhoneUpdateAt = phoneScoreState?.updatedAt
                                    watchCommandFeedback = WatchCommandFeedback.Sent
                                    pendingEndCommand = true
                                    context.vibrateDoubleTap()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    WearPhoneSync.sendCommand(context, WearSyncContract.COMMAND_END_MATCH)
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
    onTeamAStarts: () -> Unit,
    onTeamBStarts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(horizontal = 30.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = false)
        Text(
            text = "SERVES FIRST",
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
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.76f),
            label = "ME SERVES",
            color = TeamBlue,
            onClick = onTeamAStarts
        )
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.76f),
            label = "OPP SERVES",
            color = TeamGreen,
            onClick = onTeamBStarts
        )
    }
}

@Composable
private fun WearConnectedScoreboardScreen(
    state: PhoneScoreState,
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
            connected = true,
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
            teamAName = "ME WON",
            teamBName = "OPP WON",
            enabled = actionsEnabled,
            onTeamATapped = onTeamAWon,
            onTeamBTapped = onTeamBWon
        )
    }
}

@Composable
private fun WearConnectedStartChoiceScreen(
    onTeamAStarts: () -> Unit,
    onTeamBStarts: () -> Unit,
    title: String,
    secondaryLabel: String,
    onSecondaryAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = true)
        Text(
            text = title,
            color = MainText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.82f),
            label = "ME SERVES",
            color = TeamBlue,
            onClick = onTeamAStarts
        )
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.82f),
            label = "OPP SERVES",
            color = TeamGreen,
            onClick = onTeamBStarts
        )
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(44.dp),
            onClick = onSecondaryAction,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, ConnectedAmber),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = WatchBackground,
                contentColor = ConnectedAmber
            )
        ) {
            Text(
                text = secondaryLabel,
                color = ConnectedAmber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

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
    val gameOver = state.status is GameStatus.Complete

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
    ) {
        WearConnectionLabel(
            modifier = Modifier.align(Alignment.TopCenter),
            connected = false
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
            teamAName = "ME WON",
            teamBName = "OPP WON",
            enabled = !gameOver,
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
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(40.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MainText,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
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
    val status = state.status

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

        if (status is GameStatus.Complete) {
            Text(
                text = "${state.teamName(status.winner).takeLast(1)} WINS",
                color = ProblemRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
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
    connected: Boolean,
    feedback: WatchCommandFeedback? = null
) {
    val isProblem = feedback == WatchCommandFeedback.Problem
    val color = when {
        isProblem -> ProblemRed
        connected -> ConnectedAmber
        else -> ProblemRed
    }
    val text = when (feedback) {
        WatchCommandFeedback.Sent -> "SENT"
        WatchCommandFeedback.Confirmed -> "SCORE OK"
        WatchCommandFeedback.Problem -> "PHONE?"
        null -> if (connected) "CONNECTED" else "STANDALONE"
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
                .size(5.dp)
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

@Composable
private fun WearScorePanels(
    modifier: Modifier = Modifier,
    teamAScore: Int,
    teamBScore: Int,
    servingTeam: Team,
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
            panelColor = TeamBluePanel,
            enabled = enabled,
            onClick = onTeamATapped
        )
        WearScorePanel(
            modifier = Modifier.weight(1f),
            label = teamBName,
            score = teamBScore,
            isServing = servingTeam == Team.B,
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
                WearServeDots(isServing = isServing)
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
private fun WearServeDots(isServing: Boolean) {
    Row(
        modifier = Modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
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
