package com.courtside.pickleball.wear

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.PickleballScoringEngine
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.displayValue

private val Ink = Color(0xFF050607)
private val Paper = Color(0xFFF7F7F2)
private val TeamBlue = Color(0xFF1E73BE)
private val TeamGreen = Color(0xFF2E9D62)
private val ServerYellow = Color(0xFFFFD447)
private val WarningRed = Color(0xFFDA3E52)
private val TableLine = Color(0xFF6C737D)

@Composable
fun WearScoreboardApp() {
    val engine = remember { PickleballScoringEngine() }
    val history = remember { mutableStateListOf<GameState>() }
    var state by remember { mutableStateOf<GameState?>(null) }

    fun recordWinner(team: Team) {
        val current = state ?: return
        val next = engine.recordRallyWinner(current, team)
        if (next != current) {
            history += current
            state = next
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Paper
        ) {
            val current = state
            if (current == null) {
                WearServeSetupScreen(
                    onTeamAStarts = {
                        history.clear()
                        state = GameState(servingTeam = Team.A)
                    },
                    onTeamBStarts = {
                        history.clear()
                        state = GameState(servingTeam = Team.B)
                    }
                )
            } else {
                WearScoreboardScreen(
                    state = current,
                    canUndo = history.isNotEmpty(),
                    onTeamAWon = { recordWinner(Team.A) },
                    onTeamBWon = { recordWinner(Team.B) },
                    onUndo = {
                        val previous = history.removeLastOrNull() ?: return@WearScoreboardScreen
                        state = previous
                    },
                    onReset = {
                        history.clear()
                        state = null
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
            .padding(horizontal = 30.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "SERVES FIRST",
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "0 - 0 - 2",
            color = ServerYellow,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.76f),
            label = "A SERVES",
            color = TeamBlue,
            onClick = onTeamAStarts
        )
        ServeChoiceButton(
            modifier = Modifier.fillMaxWidth(0.76f),
            label = "B SERVES",
            color = TeamGreen,
            onClick = onTeamBStarts
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
        ScoreBlock(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "A\nWON",
                color = TeamBlue,
                enabled = !gameOver,
                onClick = onTeamAWon
            )
            RallyButton(
                modifier = Modifier.weight(1f),
                label = "B\nWON",
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
                colors = ButtonDefaults.textButtonColors(contentColor = WarningRed),
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
        modifier = modifier.height(44.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
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
                color = WarningRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
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
            .height(36.dp)
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
            fontSize = 29.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
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
        modifier = modifier.height(52.dp),
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

private fun GameState.callText(): String =
    "${servingScore} - ${receivingScore} - ${serverNumber.displayValue}"
