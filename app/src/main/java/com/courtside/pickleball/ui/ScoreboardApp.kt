package com.courtside.pickleball.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import java.util.Locale

private val Ink = Color(0xFF182333)
private val Paper = Color(0xFFF5F2EA)
private val PanelWhite = Color(0xFFF8F8F3)
private val CourtBlue = Color(0xFF087BA8)
private val TeamABlue = Color(0xFF1E73BE)
private val TeamBGreen = Color(0xFF2E9D62)
private val ServerDot = Color(0xFFECEBE3)
private val ServerAccent = Color(0xFFD6FF3F)
private val Warning = Color(0xFFB23A48)
private val TableLine = Color(0xFF5F6872)
private val SetupTeamCardHeight = 96.dp
private val SetupTeamCardCompactHeight = 88.dp
private val SetupPlayerInputHeight = 46.dp
private val SetupPlayerInputCompactHeight = 44.dp
private val ScoreCellWidth = 160.dp
private val ServeCellWidth = 78.dp

@Composable
fun ScoreboardApp(viewModel: ScoreboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var matchStarted by remember { mutableStateOf(false) }
    var setupTeamAPlayer1 by remember { mutableStateOf("") }
    var setupTeamAPlayer2 by remember { mutableStateOf("") }
    var setupTeamBPlayer1 by remember { mutableStateOf("") }
    var setupTeamBPlayer2 by remember { mutableStateOf("") }
    var startingTeam by remember { mutableStateOf<Team?>(null) }
    var showEndMatchDialog by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var pendingScoreCall by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun speakScoreCall(scoreCall: String) {
        tts.useBestAvailableVoice()
        tts.speak(scoreCall, TextToSpeech.QUEUE_FLUSH, null, "score-${System.nanoTime()}")
    }

    fun announceScore(gameState: GameState) {
        val scoreCall = gameState.spokenScoreCall()
        if (ttsReady) {
            speakScoreCall(scoreCall)
        } else {
            pendingScoreCall = scoreCall
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
        if (ttsReady) tts.useBestAvailableVoice()
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    MaterialTheme {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f)
        ) {
            if (matchStarted) {
                ScoreboardScreen(
                    state = state,
                    canUndo = viewModel.canUndo(),
                    onTeamARally = { announceScore(viewModel.recordRallyWinner(Team.A)) },
                    onTeamBRally = { announceScore(viewModel.recordRallyWinner(Team.B)) },
                    onUndo = viewModel::undo,
                    onEndMatchRequested = { showEndMatchDialog = true }
                )
            } else {
                MatchSetupScreen(
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
                    onStart = {
                        val server = startingTeam ?: Team.A
                        viewModel.startMatch(
                            teamAName = formatTeamName(setupTeamAPlayer1, setupTeamAPlayer2, Team.A),
                            teamBName = formatTeamName(setupTeamBPlayer1, setupTeamBPlayer2, Team.B),
                            startingTeam = server
                        )
                        matchStarted = true
                        announceScore(viewModel.state.value)
                    }
                )
            }
        }

        if (showEndMatchDialog) {
            AlertDialog(
                onDismissRequest = { showEndMatchDialog = false },
                title = { Text("End game?") },
                text = { Text("This clears the current game and returns to player setup.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.reset()
                            showEndMatchDialog = false
                            matchStarted = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        Text("End game")
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
    onStart: () -> Unit
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
            .background(Paper),
        color = Paper
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 26.dp, vertical = outerPadding),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = if (keyboardVisible) Alignment.Top else Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .then(
                        if (keyboardVisible) {
                            Modifier.verticalScroll(setupScrollState)
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(columnSpacing)
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
                        text = "Enter names, then tap who serves first.",
                        color = Color(0xFF4D5963),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                SetupTeamNameFields(
                    label = "Team A players",
                    player1 = teamAPlayer1,
                    player2 = teamAPlayer2,
                    color = TeamABlue,
                    selected = startingTeam == Team.A,
                    compact = keyboardVisible,
                    onPlayer1Change = onTeamAPlayer1Change,
                    onPlayer2Change = onTeamAPlayer2Change,
                    onKeyboardAction = hideKeyboard,
                    onSelect = { onStartingTeamChange(Team.A) }
                )
                SetupTeamNameFields(
                    label = "Team B players",
                    player1 = teamBPlayer1,
                    player2 = teamBPlayer2,
                    color = TeamBGreen,
                    selected = startingTeam == Team.B,
                    compact = keyboardVisible,
                    onPlayer1Change = onTeamBPlayer1Change,
                    onPlayer2Change = onTeamBPlayer2Change,
                    onKeyboardAction = hideKeyboard,
                    onSelect = { onStartingTeamChange(Team.B) }
                )
            }

            Column(
                modifier = Modifier.weight(0.85f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (keyboardVisible) 8.dp else 12.dp)
            ) {
                ScorePreviewCard(startingTeam, compact = keyboardVisible)
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
                } else {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        onClick = onStart,
                        enabled = canStart,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CourtBlue)
                    ) {
                        Text(
                            text = "START 0 - 0 - 2",
                            fontSize = 24.sp,
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
            .background(if (selected) color.copy(alpha = 0.12f) else Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 24.dp)
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                color = color,
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
private fun ScorePreviewCard(startingTeam: Team?, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        Text(
            text = "FIRST SCORE CALL",
            color = Color.White,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = "0 - 0 - 2",
            color = ServerAccent,
            fontSize = if (compact) 34.sp else 44.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = if (compact) 38.sp else 48.sp,
            maxLines = 1
        )
        Text(
            text = when (startingTeam) {
                Team.A -> "TEAM A SERVES FIRST"
                Team.B -> "TEAM B SERVES FIRST"
                null -> "TAP A TEAM NAME"
            },
            color = Color.White,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
            .clip(RoundedCornerShape(18.dp))
            .background(TableLine)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TeamScoreTableRow(
            modifier = Modifier.weight(1f),
            name = state.settings.teamAName,
            score = state.teamAScore,
            color = TeamABlue,
            isServing = state.servingTeam == Team.A,
            serverNumber = state.serverNumber,
            enabled = !gameOver,
            onScoreClick = onTeamARally
        )
        TeamScoreTableRow(
            modifier = Modifier.weight(1f),
            name = state.settings.teamBName,
            score = state.teamBScore,
            color = TeamBGreen,
            isServing = state.servingTeam == Team.B,
            serverNumber = state.serverNumber,
            enabled = !gameOver,
            onScoreClick = onTeamBRally
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
    onScoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PanelWhite),
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
                color = color,
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
                color = color,
            )
        }
        TableDivider()
        ScoreTapTarget(
            modifier = Modifier
                .width(ScoreCellWidth)
                .fillMaxHeight(),
            score = score,
            color = color,
            enabled = enabled,
            onClick = onScoreClick
        )
    }
}

@Composable
private fun TeamNameDisplay(
    modifier: Modifier,
    name: String,
    color: Color
) {
    val parts = name.uppercase().split(" & ", limit = 2)
    if (parts.size == 2) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = parts[0],
                color = color,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "&",
                color = color,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
                maxLines = 1
            )
            Text(
                modifier = Modifier.weight(1f),
                text = parts[1],
                color = color,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Text(
            modifier = modifier,
            text = name.uppercase(),
            color = color,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
            .width(3.dp)
            .background(TableLine)
    )
}

@Composable
private fun ServeDots(color: Color, isServing: Boolean, serverNumber: ServerNumber) {
    Row(
        modifier = Modifier.size(width = 68.dp, height = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            val dotCount = when (serverNumber) {
                ServerNumber.One -> 1
                ServerNumber.Two -> 2
            }
            repeat(dotCount) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
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
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score.toString(),
            color = color,
            fontSize = 76.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 80.sp,
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
    val status = state.status

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Ink, RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = state.callBarText(status),
                color = Color.White,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 50.sp,
                maxLines = 1
            )
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.22f),
            onClick = onUndo,
            enabled = canUndo,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text("UNDO", fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.22f),
            onClick = onEndMatchRequested,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text("END", fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
    append("CALL ")
    pushStyle(SpanStyle(color = teamColor(servingTeam)))
    append(servingScore.toString())
    pop()
    append(" - ")
    pushStyle(SpanStyle(color = teamColor(receivingTeam)))
    append(receivingScore.toString())
    pop()
    append(" - ")
    pushStyle(SpanStyle(color = teamColor(servingTeam)))
    append(
        when (serverNumber) {
            ServerNumber.One -> "1"
            ServerNumber.Two -> "2"
        }
    )
    pop()
}

private fun normalizePlayerNamesInput(rawValue: String): String =
    rawValue
        .replace('\n', ' ')
        .replace('\r', ' ')
        .uppercase(Locale.ENGLISH)

private fun formatTeamName(player1: String, player2: String, team: Team): String {
    val fallback = when (team) {
        Team.A -> "TEAM A"
        Team.B -> "TEAM B"
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

private fun GameState.spokenScoreCall(): String =
    "${servingScore.spokenNumber()} ${receivingScore.spokenNumber()} ${serverNumber.spokenNumber()}"

private fun ServerNumber.spokenNumber(): String = when (this) {
    ServerNumber.One -> "one"
    ServerNumber.Two -> "two"
}

private fun Int.spokenNumber(): String = when (this) {
    0 -> "zero"
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    11 -> "eleven"
    12 -> "twelve"
    13 -> "thirteen"
    14 -> "fourteen"
    15 -> "fifteen"
    16 -> "sixteen"
    17 -> "seventeen"
    18 -> "eighteen"
    19 -> "nineteen"
    20 -> "twenty"
    else -> toString()
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
