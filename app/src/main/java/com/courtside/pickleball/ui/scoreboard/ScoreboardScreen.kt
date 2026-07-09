package com.courtside.pickleball.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.ui.callHasDoubleDigitScore
import com.courtside.pickleball.ui.scoreOnlyCallBarText
import com.courtside.pickleball.ui.toScoreboardDisplayName
import com.courtside.pickleball.ui.status.CourtCodeBadge
import com.courtside.pickleball.ui.status.PhoneTabletStatusBar
import com.courtside.pickleball.ui.status.WatchConnectionStatusBar
import com.courtside.pickleball.ui.theme.CallBackground
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.Ink
import com.courtside.pickleball.ui.theme.Paper
import com.courtside.pickleball.ui.theme.PhoneCallBarCompactHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarCompactLineHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarCompactTextSize
import com.courtside.pickleball.ui.theme.PhoneCallBarCompactWideLineHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarCompactWideTextSize
import com.courtside.pickleball.ui.theme.PhoneCallBarHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarLineHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarTextSize
import com.courtside.pickleball.ui.theme.PhoneCallBarWideLineHeight
import com.courtside.pickleball.ui.theme.PhoneCallBarWideTextSize
import com.courtside.pickleball.ui.theme.PhoneScoreboardSectionSpacing
import com.courtside.pickleball.ui.theme.PhoneScoreboardVerticalPadding
import com.courtside.pickleball.ui.theme.PhoneTeamRowSpacing
import com.courtside.pickleball.ui.theme.ProblemRed
import com.courtside.pickleball.ui.theme.ScoreCellWidth
import com.courtside.pickleball.ui.theme.ServeCellWidth
import com.courtside.pickleball.ui.theme.TableLine
import com.courtside.pickleball.ui.theme.TeamABlue
import com.courtside.pickleball.ui.theme.TeamBGreen

@Composable
internal fun ScoreboardScreen(
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
    val compactHeight = LocalConfiguration.current.screenHeightDp < 340

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
                .padding(horizontal = 22.dp, vertical = PhoneScoreboardVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(PhoneScoreboardSectionSpacing)
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
                    connectionState = tabletConnectionState
                )
            }
            ControlBar(
                state = state,
                canUndo = canUndo,
                compactHeight = compactHeight,
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
internal fun ScoreboardBody(
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
            .clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(PhoneTeamRowSpacing)
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
internal fun TeamScoreTableRow(
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
internal fun TeamNameDisplay(
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
                name = playerOne.toScoreboardDisplayName(),
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
                name = playerTwo.toScoreboardDisplayName(),
                color = color,
                highlightColor = highlightColor,
                isServing = playerTwo == serving,
                isTablet = isTablet
            )
        }
    } else {
        PositionedPlayerName(
            modifier = modifier,
            name = name.uppercase().toScoreboardDisplayName(),
            color = color,
            highlightColor = highlightColor,
            isServing = name.uppercase().trim() == serving,
            isTablet = isTablet
        )
    }
}

@Composable
internal fun PositionedPlayerName(
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
internal fun TableCell(
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
internal fun TableDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(4.dp)
            .background(TableLine)
    )
}

@Composable
internal fun ConnectionStatusBadge(
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
internal fun ServeDots(color: Color, isServing: Boolean, serverNumber: ServerNumber) {
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
internal fun ScoreTapTarget(
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
                    placeable.placeRelative(0, -18)
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
internal fun ControlBar(
    state: GameState,
    canUndo: Boolean,
    compactHeight: Boolean,
    onNavigateToSetup: () -> Unit,
    onCorrectionRequested: () -> Unit,
    onUndo: () -> Unit,
    onEndMatchRequested: () -> Unit
) {
    val useWideCallText = state.callHasDoubleDigitScore()
    val callFontSize = when {
        compactHeight && useWideCallText -> PhoneCallBarCompactWideTextSize
        compactHeight -> PhoneCallBarCompactTextSize
        useWideCallText -> PhoneCallBarWideTextSize
        else -> PhoneCallBarTextSize
    }
    val callLineHeight = when {
        compactHeight && useWideCallText -> PhoneCallBarCompactWideLineHeight
        compactHeight -> PhoneCallBarCompactLineHeight
        useWideCallText -> PhoneCallBarWideLineHeight
        else -> PhoneCallBarLineHeight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compactHeight) PhoneCallBarCompactHeight else PhoneCallBarHeight)
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
                    .padding(start = 78.dp, end = 78.dp),
                text = state.scoreOnlyCallBarText(),
                color = Color.White,
                fontSize = callFontSize,
                fontWeight = FontWeight.Black,
                lineHeight = callLineHeight,
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
                    Text("EDIT", fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    ),
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
