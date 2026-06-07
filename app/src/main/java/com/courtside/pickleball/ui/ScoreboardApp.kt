package com.courtside.pickleball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.GameStatus
import com.courtside.pickleball.domain.Team

private val Ink = Color(0xFF111315)
private val Paper = Color(0xFFF7F7F2)
private val Lime = Color(0xFFD6FF3F)
private val CourtBlue = Color(0xFF16697A)
private val CourtGreen = Color(0xFF2E7D32)
private val Warning = Color(0xFFB23A48)

@Composable
fun ScoreboardApp(viewModel: ScoreboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    MaterialTheme {
        ScoreboardScreen(
            state = state,
            canUndo = viewModel.canUndo(),
            onTeamARally = { viewModel.recordRallyWinner(Team.A) },
            onTeamBRally = { viewModel.recordRallyWinner(Team.B) },
            onUndo = viewModel::undo,
            onResetRequested = { showResetDialog = true }
        )

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset game?") },
                text = { Text("This clears the current score and starts again at 0 - 0 - 2.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.reset()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ScoreboardScreen(
    state: GameState,
    canUndo: Boolean,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit,
    onUndo: () -> Unit,
    onResetRequested: () -> Unit
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
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TeamScorePanel(
                    modifier = Modifier.weight(1f),
                    name = state.settings.teamAName,
                    score = state.teamAScore,
                    isServing = state.servingTeam == Team.A,
                    accent = CourtBlue
                )
                CenterStatusPanel(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    state = state
                )
                TeamScorePanel(
                    modifier = Modifier.weight(1f),
                    name = state.settings.teamBName,
                    score = state.teamBScore,
                    isServing = state.servingTeam == Team.B,
                    accent = CourtGreen
                )
            }

            Controls(
                state = state,
                canUndo = canUndo,
                onTeamARally = onTeamARally,
                onTeamBRally = onTeamBRally,
                onUndo = onUndo,
                onResetRequested = onResetRequested
            )
        }
    }
}

@Composable
private fun Header(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "COURTSIDE PICKLEBALL",
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = state.scoreCall,
            color = Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun TeamScorePanel(
    modifier: Modifier,
    name: String,
    score: Int,
    isServing: Boolean,
    accent: Color
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(
                width = if (isServing) 6.dp else 2.dp,
                color = if (isServing) Lime else Ink.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name.uppercase(),
            color = accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = score.toString().padStart(2, '0'),
            color = Ink,
            fontSize = 142.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 142.sp
        )
        Text(
            text = if (isServing) "SERVING" else "RECEIVING",
            color = if (isServing) Ink else Ink.copy(alpha = 0.48f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CenterStatusPanel(modifier: Modifier, state: GameState) {
    Column(
        modifier = modifier
            .background(Ink, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "SERVER",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.scoreCall.takeLast(1),
            color = Lime,
            fontSize = 88.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 88.sp
        )
        Text(
            text = "Score call",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.scoreCall,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        val status = state.status
        if (status is GameStatus.Complete) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Lime, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${state.teamName(status.winner)} wins",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: GameState,
    canUndo: Boolean,
    onTeamARally: () -> Unit,
    onTeamBRally: () -> Unit,
    onUndo: () -> Unit,
    onResetRequested: () -> Unit
) {
    val gameOver = state.status is GameStatus.Complete

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onTeamARally,
            enabled = !gameOver,
            colors = ButtonDefaults.buttonColors(containerColor = CourtBlue)
        ) {
            Text("Team A won rally", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onTeamBRally,
            enabled = !gameOver,
            colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
        ) {
            Text("Team B won rally", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            modifier = Modifier.width(130.dp).fillMaxHeight(),
            onClick = onUndo,
            enabled = canUndo
        ) {
            Text("Undo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            modifier = Modifier.width(130.dp).fillMaxHeight(),
            onClick = onResetRequested
        ) {
            Text("Reset", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
