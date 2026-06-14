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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import java.util.Locale
import kotlinx.coroutines.delay

private val Ink = Color(0xFF050607)
private val Paper = Color(0xFFFFFFFF)
private val TeamBlue = Color(0xFF005BBB)
private val TeamGreen = Color(0xFF007A3D)
private val ConnectedAmber = Color(0xFFFFC107)
private val ProblemRed = Color(0xFFB00020)
private val InactiveGray = Color(0xFF6C737D)
private val TableLine = Color(0xFF242A31)
private const val TAG = "WearScoreboardApp"
private const val WatchActionDebounceMs = 700L
private const val PhoneConfirmationTimeoutMs = 2_200L
private const val FeedbackVisibleMs = 900L
private const val PhoneRefreshIntervalMs = 5_000L
private const val ScoreSpeechRate = 0.9f

private enum class WatchCommandFeedback {
    Sent,
    Confirmed,
    Problem
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
        context.vibrateWatchAction()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }

    fun sendPhoneCommand(commandPath: String) {
        awaitingPhoneConfirmation = true
        commandSentAt = SystemClock.elapsedRealtime()
        baselinePhoneUpdateAt = phoneScoreState?.updatedAt
        watchCommandFeedback = WatchCommandFeedback.Sent
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

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Paper
        ) {
            val current = state
            val connectedScoreState = phoneScoreState.takeIf { phoneConnected }
            if (connectedScoreState?.matchActive == true) {
                WearConnectedScoreboardScreen(
                    state = connectedScoreState,
                    feedback = watchCommandFeedback,
                    actionsEnabled = !awaitingPhoneConfirmation,
                    onTeamAWon = {
                        runWatchAction {
                            sendPhoneCommand(WearSyncContract.COMMAND_A_WON_RALLY)
                        }
                    },
                    onTeamBWon = {
                        runWatchAction {
                            sendPhoneCommand(WearSyncContract.COMMAND_B_WON_RALLY)
                        }
                    },
                    onUndo = {
                        runWatchAction {
                            sendPhoneCommand(WearSyncContract.COMMAND_UNDO)
                        }
                    }
                )
            } else if (connectedScoreState != null) {
                WearConnectedIdleScreen()
            } else if (current == null) {
                WearServeSetupScreen(
                    onTeamAStarts = {
                        runWatchAction {
                            val next = GameState(servingTeam = Team.A)
                            history.clear()
                            state = next
                            announceScore(next)
                        }
                    },
                    onTeamBStarts = {
                        runWatchAction {
                            val next = GameState(servingTeam = Team.B)
                            history.clear()
                            state = next
                            announceScore(next)
                        }
                    }
                )
            } else {
                WearScoreboardScreen(
                    state = current,
                    canUndo = history.isNotEmpty(),
                    onTeamAWon = { runWatchAction { recordWinner(Team.A) } },
                    onTeamBWon = { runWatchAction { recordWinner(Team.B) } },
                    onUndo = {
                        runWatchAction {
                            val previous = history.removeLastOrNull() ?: return@runWatchAction
                            state = previous
                            announceScore(previous)
                        }
                    },
                    onReset = {
                        runWatchAction {
                            history.clear()
                            tts.stop()
                            state = null
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
            .background(Paper)
            .padding(horizontal = 30.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = false)
        Text(
            text = "SERVES FIRST",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "0 - 0 - 2",
            color = Ink,
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
    onUndo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 26.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = true, feedback = feedback)
        ConnectedScoreBlock(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "ME\nWON",
                color = TeamBlue,
                enabled = actionsEnabled,
                onClick = onTeamAWon
            )
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "OPP\nWON",
                color = TeamGreen,
                enabled = actionsEnabled,
                onClick = onTeamBWon
            )
        }

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(30.dp),
            onClick = onUndo,
            enabled = state.canUndo && actionsEnabled,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(
                text = "UNDO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WearConnectedIdleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 30.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = true)
        Text(
            text = "START ON",
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "PHONE",
            color = Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "0 - 0 - 2",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WearScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    onTeamAWon: () -> Unit,
    onTeamBWon: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val gameOver = state.status is GameStatus.Complete

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 26.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        WearConnectionLabel(connected = false)
        ScoreBlock(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "ME\nWON",
                color = TeamBlue,
                enabled = !gameOver,
                onClick = onTeamAWon
            )
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "OPP\nWON",
                color = TeamGreen,
                enabled = !gameOver,
                onClick = onTeamBWon
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                onClick = onUndo,
                enabled = canUndo,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "UNDO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            TextButton(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                onClick = onReset,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = ProblemRed),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "RESET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
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
        colors = ButtonDefaults.buttonColors(containerColor = color),
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
private fun ScoreBlock(state: GameState) {
    val status = state.status

    Column(
        modifier = Modifier.fillMaxWidth(0.78f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        WearScoreTableRow(
            label = "A",
            score = state.teamAScore,
            color = TeamBlue,
            isServing = state.servingTeam == Team.A,
            serverNumber = state.serverNumber.displayValue
        )
        WearScoreTableRow(
            label = "B",
            score = state.teamBScore,
            color = TeamGreen,
            isServing = state.servingTeam == Team.B,
            serverNumber = state.serverNumber.displayValue
        )
        Text(
            text = state.callText(),
            color = state.teamColor(state.servingTeam),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
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
private fun ConnectedScoreBlock(state: PhoneScoreState) {
    Column(
        modifier = Modifier.fillMaxWidth(0.78f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        WearScoreTableRow(
            label = "A",
            score = state.teamAScore,
            color = TeamBlue,
            isServing = state.servingTeam == Team.A,
            serverNumber = state.serverNumber
        )
        WearScoreTableRow(
            label = "B",
            score = state.teamBScore,
            color = TeamGreen,
            isServing = state.servingTeam == Team.B,
            serverNumber = state.serverNumber
        )
        Text(
            text = state.scoreCall,
            color = state.teamColor(state.servingTeam),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WearConnectionLabel(
    connected: Boolean,
    feedback: WatchCommandFeedback? = null
) {
    val isProblem = feedback == WatchCommandFeedback.Problem
    val color = when {
        isProblem -> ProblemRed
        connected -> ConnectedAmber
        else -> InactiveGray
    }
    val text = when (feedback) {
        WatchCommandFeedback.Sent -> "SENT"
        WatchCommandFeedback.Confirmed -> "SCORE OK"
        WatchCommandFeedback.Problem -> "PHONE?"
        null -> if (connected) "PHONE CONNECTED" else "STANDALONE"
    }
    val textColor = if (connected && !isProblem) Ink else Color.White

    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        text = text,
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun WearScoreTableRow(
    label: String,
    score: Int,
    color: Color,
    isServing: Boolean,
    serverNumber: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            WearServeDots(
                color = color,
                isServing = isServing,
                serverNumber = serverNumber
            )
        }
        WearTableDivider()
        Text(
            modifier = Modifier
                .width(30.dp)
                .padding(horizontal = 4.dp),
            text = label,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        WearTableDivider()
        Text(
            modifier = Modifier.weight(1f),
            text = score.toString(),
            color = color,
            fontSize = 27.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun WearServeDots(color: Color, isServing: Boolean, serverNumber: Int) {
    Row(
        modifier = Modifier.size(width = 26.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(serverNumber) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun WearTableDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(2.dp)
            .background(TableLine)
    )
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
        modifier = modifier.height(44.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            maxLines = 2
        )
    }
}

private fun GameState.teamColor(team: Team): Color =
    when (team) {
        Team.A -> TeamBlue
        Team.B -> TeamGreen
    }

private fun PhoneScoreState.teamColor(team: Team): Color =
    when (team) {
        Team.A -> TeamBlue
        Team.B -> TeamGreen
    }

private fun PhoneScoreState.scoreSignature(): String =
    "$teamAScore|$teamBScore|$servingTeam|$serverNumber|$scoreCall"

private fun VoiceAnnouncementMode.usesWatchSpeaker(): Boolean =
    this == VoiceAnnouncementMode.WatchOnly || this == VoiceAnnouncementMode.WatchThenPhone

private fun Context.vibrateWatchAction() {
    vibratePattern(longArrayOf(0, 35), intArrayOf(0, 160))
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

private fun GameState.callText(): String =
    "${servingScore} - ${receivingScore} - ${serverNumber.displayValue}"

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
