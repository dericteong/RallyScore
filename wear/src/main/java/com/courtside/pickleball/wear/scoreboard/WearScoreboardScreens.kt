package com.courtside.pickleball.wear.scoreboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
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
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.wear.ConnectedAndroidRole
import com.courtside.pickleball.wear.PhoneScoreState
import com.courtside.pickleball.wear.WatchCommandFeedback
import com.courtside.pickleball.wear.WearConnectionMode
import com.courtside.pickleball.wear.theme.ConnectedAmber
import com.courtside.pickleball.wear.theme.InactiveGray
import com.courtside.pickleball.wear.theme.MainText
import com.courtside.pickleball.wear.theme.ProblemRed
import com.courtside.pickleball.wear.theme.SecondaryText
import com.courtside.pickleball.wear.theme.TeamBlue
import com.courtside.pickleball.wear.theme.TeamBluePanel
import com.courtside.pickleball.wear.theme.TeamGreen
import com.courtside.pickleball.wear.theme.TeamGreenPanel
import com.courtside.pickleball.wear.theme.UndoButtonBackground
import com.courtside.pickleball.wear.theme.WatchBackground
import kotlinx.coroutines.launch

@Composable
internal fun WearConnectedScoreboardScreen(
    state: PhoneScoreState,
    connectionMode: WearConnectionMode,
    feedback: WatchCommandFeedback?,
    actionsEnabled: Boolean,
    onTeamAWon: () -> Unit,
    onTeamBWon: () -> Unit,
    onUndo: () -> Unit,
    onEndRequested: () -> Unit
) {
    val isRoundDisplay = LocalConfiguration.current.isScreenRound
    WearScrollableScoreboard { contentModifier ->
        Box(
            modifier = contentModifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = if (isRoundDisplay) 32.dp else 14.dp
                )
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
            isRoundDisplay = isRoundDisplay,
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
}

@Composable
internal fun WearScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    onTeamAWon: () -> Unit,
    onTeamBWon: () -> Unit,
    onUndo: () -> Unit,
    onEndRequested: () -> Unit,
    onReset: () -> Unit
) {
    val isRoundDisplay = LocalConfiguration.current.isScreenRound
    WearScrollableScoreboard { contentModifier ->
        Box(
            modifier = contentModifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = if (isRoundDisplay) 32.dp else 14.dp
                )
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
            isRoundDisplay = isRoundDisplay,
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
}

/** Keeps the full scoreboard usable on small Wear displays without shrinking score text. */
@Composable
private fun WearScrollableScoreboard(
    content: @Composable (Modifier) -> Unit
) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rotaryFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch {
                        scrollState.scrollBy(event.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(rotaryFocusRequester)
                .focusable()
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    // Leave a little more vertical separation between Undo and the score tiles
                    // on normal-size watches. Smaller displays scroll this same safe layout.
                    .heightIn(min = 230.dp)
            )
        }
        WearScorePageScrollIndicator(scrollState)
    }
}

@Composable
private fun WearScorePageScrollIndicator(scrollState: androidx.compose.foundation.ScrollState) {
    if (scrollState.maxValue <= 0) return

    val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 7.dp)
            .semantics { contentDescription = "Scroll position" },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .height(42.dp)
                .padding(vertical = 3.dp)
                .width(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(SecondaryText.copy(alpha = 0.28f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .offset(y = (24f * progress).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SecondaryText)
            )
        }
    }
}

@Composable
internal fun ServeChoiceButton(
    modifier: Modifier,
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(48.dp),
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
internal fun PrimaryStartButton(
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
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
internal fun WearConnectionLabel(
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

internal fun ConnectedAndroidRole.startLabel(): String = when (this) {
    ConnectedAndroidRole.Phone -> "PHONE"
    ConnectedAndroidRole.Tablet -> "TABLET"
}

@Composable
private fun WearScorePanels(
    modifier: Modifier = Modifier,
    isRoundDisplay: Boolean,
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
            // On a round watch, the lower corners of a full-width 92dp row lie outside the
            // physical display. Narrow and raise the controls while keeping both tap targets
            // comfortably larger than Wear's 48dp minimum.
            .padding(horizontal = if (isRoundDisplay) 20.dp else 0.dp)
            .height(if (isRoundDisplay) 70.dp else 92.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WearScorePanel(
            modifier = Modifier.weight(1f),
            label = teamAName,
            score = teamAScore,
            isRoundDisplay = isRoundDisplay,
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
            isRoundDisplay = isRoundDisplay,
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
    isRoundDisplay: Boolean,
    isServing: Boolean,
    serverNumber: Int,
    panelColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = buildString {
                    append("$label score $score")
                    if (isServing) append(", serving, server $serverNumber")
                }
            },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = panelColor,
            contentColor = MainText,
            disabledContainerColor = InactiveGray,
            disabledContentColor = SecondaryText.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = MainText,
                    fontSize = if (isRoundDisplay) 12.sp else 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                if (isServing) {
                    WearServeMarker(serverNumber = serverNumber)
                }
            }
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

@Composable
private fun WearServeMarker(serverNumber: Int) {
    if (serverNumber == 2) {
        // Draw the colon from the same 7dp dots used for Server 1. Font glyphs make the
        // colon dots noticeably smaller and lighter than the single-server marker.
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(ConnectedAmber, CircleShape)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(ConnectedAmber, CircleShape)
        )
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
        fontSize = 41.sp,
        lineHeight = 43.sp,
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

internal fun String.watchTeamLabel(): String {
    val players = uppercase().split(" & ").map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        players.size >= 2 -> "${players[0].take(2)} ${players[1].take(2)}"
        players.isNotEmpty() -> players.first().take(2)
        else -> "--"
    }
}

@Composable
internal fun RallyButton(
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
