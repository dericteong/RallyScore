package com.courtside.pickleball.ui.scoreboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.ui.theme.Ink
import com.courtside.pickleball.ui.theme.TableLine
import com.courtside.pickleball.ui.theme.TabletSmallestWidthDp
import com.courtside.pickleball.ui.theme.TeamABlue
import com.courtside.pickleball.ui.theme.TeamBGreen

@Composable
internal fun MatchCorrectionDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onAdjustTeamA: (Int) -> Unit,
    onAdjustTeamB: (Int) -> Unit,
    onServingTeamSelected: (Team) -> Unit,
    onServerNumberSelected: (ServerNumber) -> Unit
) {
    val configuration = LocalConfiguration.current
    val compactPhoneDialog = configuration.smallestScreenWidthDp < TabletSmallestWidthDp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Match",
                fontSize = if (compactPhoneDialog) 16.sp else 22.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (compactPhoneDialog) {
                            Modifier
                        } else {
                            Modifier
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState())
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compactPhoneDialog) 4.dp else 14.dp)
            ) {
                if (compactPhoneDialog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CorrectionScoreCard(
                            modifier = Modifier.weight(1f),
                            label = "My Team",
                            score = state.teamAScore,
                            color = TeamABlue,
                            onDecrease = { onAdjustTeamA(-1) },
                            onIncrease = { onAdjustTeamA(1) }
                        )
                        CorrectionScoreCard(
                            modifier = Modifier.weight(1f),
                            label = "Opponent",
                            score = state.teamBScore,
                            color = TeamBGreen,
                            onDecrease = { onAdjustTeamB(-1) },
                            onIncrease = { onAdjustTeamB(1) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CorrectionChoiceCard(
                            modifier = Modifier.weight(1f),
                            label = "Serving",
                            options = listOf(
                                CorrectionChoiceOption(
                                    label = "My Team",
                                    selected = state.servingTeam == Team.A,
                                    accent = TeamABlue,
                                    onClick = { onServingTeamSelected(Team.A) }
                                ),
                                CorrectionChoiceOption(
                                    label = "Opponent",
                                    selected = state.servingTeam == Team.B,
                                    accent = TeamBGreen,
                                    onClick = { onServingTeamSelected(Team.B) }
                                )
                            )
                        )
                        CorrectionChoiceCard(
                            modifier = Modifier.weight(1f),
                            label = "Server",
                            options = listOf(
                                CorrectionChoiceOption(
                                    label = "1",
                                    selected = state.serverNumber == ServerNumber.One,
                                    accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                    onClick = { onServerNumberSelected(ServerNumber.One) }
                                ),
                                CorrectionChoiceOption(
                                    label = "2",
                                    selected = state.serverNumber == ServerNumber.Two,
                                    accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                    onClick = { onServerNumberSelected(ServerNumber.Two) }
                                )
                            )
                        )
                    }
                } else {
                    CorrectionScoreRow(
                        label = "My Team",
                        score = state.teamAScore,
                        color = TeamABlue,
                        compact = false,
                        onDecrease = { onAdjustTeamA(-1) },
                        onIncrease = { onAdjustTeamA(1) }
                    )
                    CorrectionScoreRow(
                        label = "Opponent Team",
                        score = state.teamBScore,
                        color = TeamBGreen,
                        compact = false,
                        onDecrease = { onAdjustTeamB(-1) },
                        onIncrease = { onAdjustTeamB(1) }
                    )
                    CorrectionChoiceRow(
                        label = "Serving",
                        compact = false,
                        options = listOf(
                            CorrectionChoiceOption(
                                label = "My Team",
                                selected = state.servingTeam == Team.A,
                                accent = TeamABlue,
                                onClick = { onServingTeamSelected(Team.A) }
                            ),
                            CorrectionChoiceOption(
                                label = "Opponent",
                                selected = state.servingTeam == Team.B,
                                accent = TeamBGreen,
                                onClick = { onServingTeamSelected(Team.B) }
                            )
                        )
                    )
                    CorrectionChoiceRow(
                        label = "Server",
                        compact = false,
                        options = listOf(
                            CorrectionChoiceOption(
                                label = "1",
                                selected = state.serverNumber == ServerNumber.One,
                                accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                onClick = { onServerNumberSelected(ServerNumber.One) }
                            ),
                            CorrectionChoiceOption(
                                label = "2",
                                selected = state.serverNumber == ServerNumber.Two,
                                accent = if (state.servingTeam == Team.A) TeamABlue else TeamBGreen,
                                onClick = { onServerNumberSelected(ServerNumber.Two) }
                            )
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "DONE",
                    fontSize = if (compactPhoneDialog) 12.sp else 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    )
}

private data class CorrectionChoiceOption(
    val label: String,
    val selected: Boolean,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun CorrectionScoreCard(
    modifier: Modifier = Modifier,
    label: String,
    score: Int,
    color: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = Modifier.width(32.dp),
                onClick = onDecrease,
                enabled = score > 0,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("−", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 3.dp),
                    text = score.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                modifier = Modifier.width(32.dp),
                onClick = onIncrease,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CorrectionChoiceCard(
    modifier: Modifier = Modifier,
    label: String,
    options: List<CorrectionChoiceOption>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = option.onClick,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (option.selected) option.accent else TableLine
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (option.selected) option.accent.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (option.selected) option.accent else Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = option.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrectionScoreRow(
    label: String,
    score: Int,
    color: Color,
    compact: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = label,
            color = Ink,
            fontSize = if (compact) 11.sp else 17.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = Modifier.width(if (compact) 42.dp else 64.dp),
                onClick = onDecrease,
                enabled = score > 0,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("−", fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(vertical = if (compact) 4.dp else 10.dp),
                    text = score.toString(),
                    color = Color.White,
                    fontSize = if (compact) 16.sp else 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                modifier = Modifier.width(if (compact) 42.dp else 64.dp),
                onClick = onIncrease,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CorrectionChoiceRow(
    label: String,
    compact: Boolean,
    options: List<CorrectionChoiceOption>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = label,
            color = Ink,
            fontSize = if (compact) 11.sp else 17.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            options.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = option.onClick,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (option.selected) option.accent else TableLine
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (option.selected) option.accent.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (option.selected) option.accent else Ink
                    ),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 4.dp else 10.dp,
                        vertical = if (compact) 4.dp else 10.dp
                    )
                ) {
                    Text(
                        text = option.label,
                        fontSize = if (compact) 10.sp else 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
