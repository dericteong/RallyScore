package com.courtside.pickleball.ui.setup

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.R
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.player.Player
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletPhoneCandidate
import com.courtside.pickleball.ui.status.CourtCodeBadge
import com.courtside.pickleball.ui.toAbbreviatedMatchLabel
import com.courtside.pickleball.ui.status.PhoneTabletStatusBar
import com.courtside.pickleball.ui.status.SetupStatusBadge
import com.courtside.pickleball.ui.status.TabletPhoneStatusBar
import com.courtside.pickleball.ui.status.WatchConnectionStatusBar
import com.courtside.pickleball.ui.theme.CallBackground
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.Ink
import com.courtside.pickleball.ui.theme.Paper
import com.courtside.pickleball.ui.theme.PanelWhite
import com.courtside.pickleball.ui.theme.ProblemRed
import com.courtside.pickleball.ui.theme.SetupControlCornerRadius
import com.courtside.pickleball.ui.theme.SetupPlayerInputCompactHeight
import com.courtside.pickleball.ui.theme.SetupPlayerInputHeight
import com.courtside.pickleball.ui.theme.SetupPlayerInputTabletHeight
import com.courtside.pickleball.ui.theme.SetupPlayerInputTabletText
import com.courtside.pickleball.ui.theme.SetupPrimaryButtonPhoneHeight
import com.courtside.pickleball.ui.theme.SetupPrimaryButtonPhoneText
import com.courtside.pickleball.ui.theme.SetupPrimaryButtonTabletHeight
import com.courtside.pickleball.ui.theme.SetupPrimaryButtonTabletText
import com.courtside.pickleball.ui.theme.SetupScoringButtonCompactHeight
import com.courtside.pickleball.ui.theme.SetupScoringButtonCompactText
import com.courtside.pickleball.ui.theme.SetupScoringButtonRegularHeight
import com.courtside.pickleball.ui.theme.SetupScoringButtonRegularText
import com.courtside.pickleball.ui.theme.SetupSecondaryButtonCompactHeight
import com.courtside.pickleball.ui.theme.SetupSecondaryButtonRegularHeight
import com.courtside.pickleball.ui.theme.SetupSectionLabelCompact
import com.courtside.pickleball.ui.theme.SetupSectionLabelRegular
import com.courtside.pickleball.ui.theme.SetupTeamCardCompactHeight
import com.courtside.pickleball.ui.theme.SetupTeamCardHeight
import com.courtside.pickleball.ui.theme.SetupTeamCardTabletHeight
import com.courtside.pickleball.ui.theme.SetupTeamLabelTabletText
import com.courtside.pickleball.ui.theme.TeamABlue
import com.courtside.pickleball.ui.theme.TeamBGreen
import java.util.Locale

@Composable
internal fun MatchSetupScreen(
    teamAPlayer1: String,
    teamAPlayer2: String,
    teamBPlayer1: String,
    teamBPlayer2: String,
    players: List<Player>,
    scoringFormat: ScoringFormat,
    startingTeam: Team?,
    onTeamAPlayer1Change: (String) -> Unit,
    onTeamAPlayer2Change: (String) -> Unit,
    onTeamBPlayer1Change: (String) -> Unit,
    onTeamBPlayer2Change: (String) -> Unit,
    onManagePlayers: () -> Unit,
    onScoringFormatChange: (ScoringFormat) -> Unit,
    onStartingTeamChange: (Team) -> Unit,
    editingFromMatch: Boolean = false,
    isTabletLayout: Boolean = true,
    myTeamOnTop: Boolean = true,
    onSwapTeams: () -> Unit = {},
    watchConnected: Boolean,
    tabletConnectionState: TabletConnectionState,
    voiceAnnouncementMode: VoiceAnnouncementMode,
    onVoiceAnnouncementModeChange: (VoiceAnnouncementMode) -> Unit,
    localCourtCode: String? = null,
    discoveredPhones: List<TabletPhoneCandidate> = emptyList(),
    selectedPhoneHostId: String? = null,
    selectedCourtCode: String? = null,
    onJoinPhoneRequested: (String) -> Unit = {},
    onStart: () -> Unit,
    onResumeMatch: () -> Unit = {}
) {
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val outerHorizontalPadding = when {
        keyboardVisible -> 18.dp
        isTabletLayout -> 28.dp
        else -> 18.dp
    }
    val outerVerticalPadding = when {
        keyboardVisible -> 6.dp
        isTabletLayout -> 12.dp
        else -> 4.dp
    }
    val headerSpacing = if (isTabletLayout) 12.dp else 6.dp
    val teamColumnWeight = if (isTabletLayout) 1.18f else 1.34f
    val controlColumnWeight = if (isTabletLayout) 0.82f else 0.66f
    val setupSectionSpacing = if (isTabletLayout) 12.dp else 8.dp
    val secondarySectionSpacing = if (isTabletLayout) 10.dp else 6.dp
    val helperText = "Enter players by court position"
    val setupScrollState = rememberScrollState()
    val controlScrollState = rememberScrollState()
    val context = LocalContext.current
    val view = LocalView.current
    val inputMethodManager = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val enteredNames = listOf(teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val hasDuplicateNames = enteredNames.size != enteredNames.map { it.lowercase(Locale.ENGLISH) }.distinct().size
    // Player names are optional: any field left blank falls back to its court-position
    // placeholder (P1/P2 vs P3/P4) when the match starts, so a game can begin without typing
    // anyone in. Duplicate protection still applies to the names that WERE entered.
    val canStart = startingTeam != null && !hasDuplicateNames
    val hideKeyboard = {
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        Unit
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Paper),
        color = Paper
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isTabletLayout) 14.dp else 8.dp),
            verticalAlignment = if (keyboardVisible) Alignment.Top else Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(teamColumnWeight)
                    .verticalScroll(setupScrollState),
                verticalArrangement = Arrangement.spacedBy(if (isTabletLayout) 10.dp else 6.dp)
            ) {
                if (isTabletLayout && !keyboardVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "RallyScore icon",
                            modifier = Modifier.size(68.dp)
                        )
                        Text(
                            text = "RallyScore",
                            color = Ink,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        TabletPhoneStatusBar(
                            modifier = Modifier.weight(1f),
                            connectionState = tabletConnectionState
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(if (isTabletLayout) 1f else 0.84f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "RallyScore icon",
                                modifier = Modifier.size(if (isTabletLayout) 26.dp else 22.dp)
                            )
                            Text(
                                text = "RallyScore",
                                color = Ink,
                                fontSize = if (isTabletLayout) 20.sp else 17.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                        if (!keyboardVisible) {
                            Row(
                                modifier = Modifier.weight(1.18f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CourtCodeBadge(
                                    modifier = Modifier.weight(1f),
                                    code = localCourtCode ?: "0000",
                                    compact = true
                                )
                                WatchConnectionStatusBar(
                                    modifier = Modifier.weight(1f),
                                    connected = watchConnected,
                                    compact = true
                                )
                                PhoneTabletStatusBar(
                                    modifier = Modifier.weight(1f),
                                    connectionState = tabletConnectionState,
                                    compact = true
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isTabletLayout) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.spacedBy(12.dp)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SET UP GAME",
                        color = Ink,
                        fontSize = when {
                            !isTabletLayout -> 13.sp
                            keyboardVisible -> 17.sp
                            isTabletLayout -> 23.sp
                            else -> 13.sp
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    if (!keyboardVisible) {
                        Text(
                            text = helperText,
                            color = Ink.copy(alpha = 0.78f),
                            fontSize = if (isTabletLayout) 14.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        OutlinedButton(
                            modifier = Modifier.height(if (isTabletLayout) 48.dp else 34.dp),
                            onClick = onManagePlayers,
                            shape = RoundedCornerShape(SetupControlCornerRadius),
                            contentPadding = PaddingValues(horizontal = if (isTabletLayout) 18.dp else 10.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "MANAGE PLAYERS",
                                color = Ink,
                                fontSize = if (isTabletLayout) 16.sp else 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (myTeamOnTop) {
                    SetupTeamNameFields(
                        label = "MY TEAM (BLUE)",
                        player1 = teamAPlayer1,
                        player2 = teamAPlayer2,
                        otherTeamPlayer1 = teamBPlayer1,
                        otherTeamPlayer2 = teamBPlayer2,
                        color = TeamABlue,
                        selected = startingTeam == Team.A,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        players = players,
                        onPlayer1Change = onTeamAPlayer1Change,
                        onPlayer2Change = onTeamAPlayer2Change,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.A) }
                        }
                    )
                    SwapTeamsButton(onSwap = onSwapTeams)
                    SetupTeamNameFields(
                        label = "OPPONENT (GREEN)",
                        player1 = teamBPlayer1,
                        player2 = teamBPlayer2,
                        otherTeamPlayer1 = teamAPlayer1,
                        otherTeamPlayer2 = teamAPlayer2,
                        color = TeamBGreen,
                        selected = startingTeam == Team.B,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        players = players,
                        onPlayer1Change = onTeamBPlayer1Change,
                        onPlayer2Change = onTeamBPlayer2Change,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.B) }
                        }
                    )
                } else {
                    SetupTeamNameFields(
                        label = "OPPONENT (GREEN)",
                        player1 = teamBPlayer1,
                        player2 = teamBPlayer2,
                        otherTeamPlayer1 = teamAPlayer1,
                        otherTeamPlayer2 = teamAPlayer2,
                        color = TeamBGreen,
                        selected = startingTeam == Team.B,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        players = players,
                        onPlayer1Change = onTeamBPlayer1Change,
                        onPlayer2Change = onTeamBPlayer2Change,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.B) }
                        }
                    )
                    SwapTeamsButton(onSwap = onSwapTeams)
                    SetupTeamNameFields(
                        label = "MY TEAM (BLUE)",
                        player1 = teamAPlayer1,
                        player2 = teamAPlayer2,
                        otherTeamPlayer1 = teamBPlayer1,
                        otherTeamPlayer2 = teamBPlayer2,
                        color = TeamABlue,
                        selected = startingTeam == Team.A,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        players = players,
                        onPlayer1Change = onTeamAPlayer1Change,
                        onPlayer2Change = onTeamAPlayer2Change,
                        onSelect = if (editingFromMatch) {
                            {}
                        } else {
                            { onStartingTeamChange(Team.A) }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(controlColumnWeight)
                    // On phone the control column is noticeably shorter than the team column
                    // it sits beside, so centering it in the Row (the tablet-appropriate look)
                    // pushed Voice Announcements toward the bottom edge with no room to breathe.
                    // Anchoring it to the top on phone instead gives it that room back.
                    .then(if (isTabletLayout) Modifier else Modifier.align(Alignment.Top))
                    .verticalScroll(controlScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (keyboardVisible) 8.dp else headerSpacing)
            ) {
                if (!keyboardVisible) {
                    Spacer(modifier = Modifier.height(if (isTabletLayout) 44.dp else 28.dp))
                }
                if (!keyboardVisible) {
                    ScorePreviewCard(
                        startingTeam = startingTeam,
                        compact = keyboardVisible,
                        isTabletLayout = isTabletLayout,
                        enabled = !editingFromMatch,
                        onTap = {
                            onStartingTeamChange(
                                when (startingTeam) {
                                    Team.A -> Team.B
                                    Team.B -> Team.A
                                    null -> Team.A
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(setupSectionSpacing))
                    if (editingFromMatch) {
                        SetupPrimaryActionButton(
                            label = "RESUME GAME",
                            isTabletLayout = isTabletLayout,
                            onClick = onResumeMatch
                        )
                    } else {
                        SetupPrimaryActionButton(
                            label = "START GAME",
                            isTabletLayout = isTabletLayout,
                            enabled = canStart,
                            onClick = onStart
                        )
                    }
                    if (isTabletLayout && discoveredPhones.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(setupSectionSpacing))
                        AvailablePhonesCard(
                            phones = discoveredPhones,
                            selectedPhoneHostId = selectedPhoneHostId,
                            selectedCourtCode = selectedCourtCode,
                            connectionState = tabletConnectionState,
                            onJoinPhoneRequested = onJoinPhoneRequested
                        )
                    }
                    Spacer(modifier = Modifier.height(secondarySectionSpacing))
                    ScoringFormatSelector(
                        selectedFormat = scoringFormat,
                        onFormatSelected = onScoringFormatChange,
                        compact = !isTabletLayout
                    )
                    Spacer(modifier = Modifier.height(secondarySectionSpacing))
                    VoiceAnnouncementControls(
                        selectedMode = voiceAnnouncementMode,
                        onModeChange = onVoiceAnnouncementModeChange,
                        compact = !isTabletLayout
                    )
                }
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
                }
            }
        }
        Text(
            text = "© 2026 Deric Teong",
            color = Ink.copy(alpha = 0.35f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(end = 6.dp, bottom = 4.dp)
        )
        }
    }
}

@Composable
private fun ScoringFormatSelector(
    selectedFormat: ScoringFormat,
    onFormatSelected: (ScoringFormat) -> Unit,
    compact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SetupSectionLabel(text = "SCORING STYLE", compact = compact)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoringFormatButton(
                modifier = Modifier.weight(1f),
                label = "CLASSIC",
                selected = selectedFormat == ScoringFormat.Traditional,
                compact = compact,
                onClick = { onFormatSelected(ScoringFormat.Traditional) }
            )
            ScoringFormatButton(
                modifier = Modifier.weight(1f),
                label = "RALLY",
                selected = selectedFormat == ScoringFormat.Rally,
                compact = compact,
                onClick = { onFormatSelected(ScoringFormat.Rally) }
            )
        }
    }
}

@Composable
private fun ScoringFormatButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) ConnectedAmber else Color(0xFF374151)
    val containerColor = if (selected) CallBackground else PanelWhite
    val contentColor = if (selected) ConnectedAmber else Ink

    OutlinedButton(
        modifier = modifier.height(if (compact) SetupScoringButtonCompactHeight else SetupScoringButtonRegularHeight),
        onClick = onClick,
        shape = RoundedCornerShape(SetupControlCornerRadius),
        border = BorderStroke(2.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = if (compact) SetupScoringButtonCompactText else SetupScoringButtonRegularText,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SwapTeamsButton(onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedButton(
            onClick = onSwap,
            shape = RoundedCornerShape(SetupControlCornerRadius),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            border = BorderStroke(2.dp, Color(0xFF374151)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Ink
            ),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = "⇅ SWAP SIDE",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SetupTeamNameFields(
    label: String,
    player1: String,
    player2: String,
    otherTeamPlayer1: String,
    otherTeamPlayer2: String,
    color: Color,
    selected: Boolean,
    compact: Boolean,
    isTabletLayout: Boolean,
    players: List<Player>,
    onPlayer1Change: (String) -> Unit,
    onPlayer2Change: (String) -> Unit,
    onSelect: () -> Unit
) {
    val player1OtherNames = remember(player2, otherTeamPlayer1, otherTeamPlayer2) {
        listOf(player2, otherTeamPlayer1, otherTeamPlayer2)
            .map { it.trim().lowercase(Locale.ENGLISH) }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    val player2OtherNames = remember(player1, otherTeamPlayer1, otherTeamPlayer2) {
        listOf(player1, otherTeamPlayer1, otherTeamPlayer2)
            .map { it.trim().lowercase(Locale.ENGLISH) }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    val cardHeight = when {
        compact -> SetupTeamCardCompactHeight
        isTabletLayout -> SetupTeamCardTabletHeight
        else -> SetupTeamCardHeight
    }
    val inputHeight = when {
        compact -> SetupPlayerInputCompactHeight
        isTabletLayout -> SetupPlayerInputTabletHeight
        else -> SetupPlayerInputHeight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = when {
                    compact -> 13.sp
                    isTabletLayout -> SetupTeamLabelTabletText
                    else -> 14.sp
                },
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
                isTabletLayout = isTabletLayout,
                players = players,
                otherSelectedNames = player1OtherNames,
                onValueChange = onPlayer1Change,
                onSelect = onSelect
            )
            SetupPlayerInput(
                modifier = Modifier
                    .weight(1f)
                    .height(inputHeight),
                value = player2,
                hint = "Player 2",
                otherSelectedNames = player2OtherNames,
                compact = compact,
                isTabletLayout = isTabletLayout,
                players = players,
                onValueChange = onPlayer2Change,
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
    isTabletLayout: Boolean = false,
    players: List<Player>,
    otherSelectedNames: Set<String> = emptySet(),
    onValueChange: (String) -> Unit,
    onSelect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldFontSize = when {
        compact -> 18.sp
        isTabletLayout -> SetupPlayerInputTabletText
        else -> 20.sp
    }
    // Exclude players already entered in another field so the same person
    // can't be selected into two court positions at once.
    val availablePlayers = remember(players, otherSelectedNames) {
        players.filter { it.name.trim().lowercase(Locale.ENGLISH) !in otherSelectedNames }
    }
    val recentPlayers = remember(availablePlayers) {
        availablePlayers
            .filter { it.lastPlayed != null }
            .sortedByDescending { it.lastPlayed }
            .take(6)
    }
    val allPlayers = remember(availablePlayers, recentPlayers) {
        availablePlayers
            .filter { player -> recentPlayers.none { recent -> recent.id == player.id } }
            .sortedBy { it.name.lowercase(Locale.ENGLISH) }
    }
    val showMenu = expanded
    val isDuplicate = value.trim().isNotEmpty() && value.trim().lowercase(Locale.ENGLISH) in otherSelectedNames

    Box(modifier = modifier) {
        // Player fields are select-only: names come from the saved player list via this
        // dropdown, never typed, so the on-screen keyboard never opens for these fields.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper)
                .then(
                    if (isDuplicate) {
                        Modifier.border(2.dp, ProblemRed, RoundedCornerShape(SetupControlCornerRadius))
                    } else {
                        Modifier
                    }
                )
                .clickable {
                    onSelect()
                    expanded = true
                }
                .padding(start = 10.dp, end = if (value.isEmpty()) 10.dp else 30.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value.ifEmpty { hint },
                color = if (value.isEmpty()) Color(0xFF6E767E) else Ink,
                fontSize = fieldFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = "×",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onValueChange("")
                        expanded = false
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Ink.copy(alpha = 0.5f),
                fontSize = fieldFontSize,
                fontWeight = FontWeight.Black
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = Paper,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            if (recentPlayers.isEmpty() && allPlayers.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No players available — add via Manage Players",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 12.sp else 14.sp
                        )
                    },
                    enabled = false,
                    onClick = {}
                )
            }
            if (recentPlayers.isNotEmpty()) {
                PlayerMenuHeader("RECENT PLAYERS", compact = compact)
                recentPlayers.forEach { player ->
                    PlayerMenuItem(player = player, isRecent = true, compact = compact) {
                        expanded = false
                        onValueChange(player.name)
                    }
                }
            }
            if (allPlayers.isNotEmpty()) {
                PlayerMenuHeader("ALL PLAYERS", compact = compact)
                allPlayers.forEach { player ->
                    PlayerMenuItem(player = player, isRecent = false, compact = compact) {
                        expanded = false
                        onValueChange(player.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuHeader(text: String, compact: Boolean = false) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 6.dp),
        text = text,
        color = Ink.copy(alpha = 0.64f),
        fontSize = if (compact) 10.sp else 11.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1
    )
}

@Composable
private fun PlayerMenuItem(
    player: Player,
    isRecent: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val chipSize = if (compact) 24.dp else 28.dp
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(chipSize)
                        .clip(CircleShape)
                        .background(colorForName(player.name)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialsFor(player.name),
                        color = Color.White,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    if (isRecent) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(if (compact) 7.dp else 8.dp)
                                .clip(CircleShape)
                                .background(ConnectedAmber)
                        )
                    }
                }
                Text(
                    text = player.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 14.sp else 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        onClick = onClick
    )
}

private val PlayerAvatarPalette = listOf(
    Color(0xFF005BBB),
    Color(0xFF007A3D),
    Color(0xFFB23A48),
    Color(0xFF6E4FA3),
    Color(0xFFC96E1B),
    Color(0xFF1B7A8C)
)

private fun colorForName(name: String): Color {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return PlayerAvatarPalette.first()
    val index = (cleanName.lowercase(Locale.ENGLISH).sumOf { it.code } % PlayerAvatarPalette.size)
    return PlayerAvatarPalette[index]
}

private fun initialsFor(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

@Composable
private fun ScorePreviewCard(
    startingTeam: Team?,
    compact: Boolean = false,
    isTabletLayout: Boolean = true,
    enabled: Boolean = true,
    onTap: () -> Unit = {}
) {
    val bgColor = when (startingTeam) {
        Team.A -> TeamABlue
        Team.B -> TeamBGreen
        null -> Ink
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTabletLayout) SetupPrimaryButtonTabletHeight else SetupPrimaryButtonPhoneHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (startingTeam) {
                Team.A -> if (isTabletLayout) "WE SERVE FIRST (BLUE)" else "WE SERVE FIRST"
                Team.B -> if (isTabletLayout) "OPP SERVE FIRST (GREEN)" else "OPP SERVE FIRST"
                null -> "CHOOSE SERVER"
            },
            color = Color.White,
            fontSize = when {
                compact -> SetupPrimaryButtonPhoneText
                isTabletLayout -> SetupPrimaryButtonTabletText
                else -> SetupPrimaryButtonPhoneText
            },
            fontWeight = FontWeight.Black,
            lineHeight = if (isTabletLayout) 24.sp else 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SetupPrimaryActionButton(
    label: String,
    isTabletLayout: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTabletLayout) SetupPrimaryButtonTabletHeight else SetupPrimaryButtonPhoneHeight),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(SetupControlCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = if (isTabletLayout) SetupPrimaryButtonTabletText else SetupPrimaryButtonPhoneText,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun VoiceAnnouncementControls(
    selectedMode: VoiceAnnouncementMode,
    onModeChange: (VoiceAnnouncementMode) -> Unit,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SetupSectionLabel(text = "VOICE ANNOUNCEMENTS", compact = compact)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) SetupSecondaryButtonCompactHeight else SetupSecondaryButtonRegularHeight),
                onClick = { expanded = true },
                shape = RoundedCornerShape(SetupControlCornerRadius),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = selectedMode.setupLabel(),
                    color = Ink,
                    fontSize = if (compact) 15.sp else 16.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                VoiceAnnouncementMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.setupLabel(),
                                fontSize = 16.sp,
                                fontWeight = if (mode == selectedMode) FontWeight.Black else FontWeight.Medium
                            )
                        },
                        onClick = {
                            expanded = false
                            onModeChange(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SetupSectionLabel(
    text: String,
    compact: Boolean,
    color: Color = Ink
) {
    Text(
        text = text,
        color = color,
        fontSize = if (compact) SetupSectionLabelCompact else SetupSectionLabelRegular,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp,
        maxLines = 1
    )
}

private fun VoiceAnnouncementMode.setupLabel(): String = when (this) {
    VoiceAnnouncementMode.Off -> "Off"
    VoiceAnnouncementMode.PhoneOnly -> "Phone"
    VoiceAnnouncementMode.WatchOnly -> "Watch"
    VoiceAnnouncementMode.TabletOnly -> "Tablet"
    VoiceAnnouncementMode.WatchThenPhone -> "Watch -> Phone"
    VoiceAnnouncementMode.WatchThenTablet -> "Watch -> Tablet"
    VoiceAnnouncementMode.PhoneThenTablet -> "Phone -> Tablet"
    VoiceAnnouncementMode.WatchThenPhoneThenTablet -> "Watch -> Phone -> Tablet"
}

@Composable
private fun AvailablePhonesCard(
    phones: List<TabletPhoneCandidate>,
    selectedPhoneHostId: String?,
    selectedCourtCode: String?,
    connectionState: TabletConnectionState,
    onJoinPhoneRequested: (String) -> Unit
) {
    val onlyCandidate = phones.singleOrNull()
    if (onlyCandidate != null && onlyCandidate.hostId == selectedPhoneHostId) {
        val isConnected = connectionState == TabletConnectionState.Connected
        val suffix = when {
            isConnected -> "CONNECTED"
            selectedCourtCode != null -> "JOINING"
            else -> "SELECTED"
        }
        // Stays tappable (re-sends pairToDiscoveredPhone for the same host) even once already
        // "selected," since selection just means this tablet remembers a pairing - it says
        // nothing about whether the connection is actually healthy. Without this, a stuck
        // pairing had no in-UI way to force a retry.
        SetupStatusBadge(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onJoinPhoneRequested(onlyCandidate.hostId) },
            label = if (isConnected) {
                "COURT ${onlyCandidate.courtCode} · ${onlyCandidate.label.toAbbreviatedMatchLabel()} · $suffix"
            } else {
                "COURT ${onlyCandidate.courtCode} · ${onlyCandidate.label.toAbbreviatedMatchLabel()} · $suffix · TAP TO RETRY"
            },
            color = ConnectedAmber,
            compact = false
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CallBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AVAILABLE PHONES",
            color = Color.White,
            fontSize = SetupSectionLabelRegular,
            fontWeight = FontWeight.Black
        )
        phones.forEach { phone ->
            val isSelected = phone.hostId == selectedPhoneHostId
            val selectionSuffix = when {
                !isSelected -> null
                connectionState == TabletConnectionState.Connected -> "CONNECTED"
                selectedCourtCode != null -> "JOINING"
                else -> "SELECTED"
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                onClick = { onJoinPhoneRequested(phone.hostId) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ConnectedAmber else Color.White.copy(alpha = 0.35f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) ConnectedAmber.copy(alpha = 0.16f) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "COURT ${phone.courtCode}",
                        color = if (isSelected) ConnectedAmber else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        text = if (selectionSuffix != null) {
                            "${phone.label.toAbbreviatedMatchLabel()}  $selectionSuffix"
                        } else {
                            phone.label.toAbbreviatedMatchLabel()
                        },
                        color = if (isSelected) ConnectedAmber else Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

internal fun normalizePlayerNamesInput(rawValue: String): String =
    rawValue
        .replace('\n', ' ')
        .replace('\r', ' ')
        .uppercase(Locale.ENGLISH)

internal fun Player.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    return cleanQuery.isEmpty() || name.contains(cleanQuery, ignoreCase = true)
}

internal fun formatTeamName(player1: String, player2: String, team: Team): String {
    val fallback = when (team) {
        Team.A -> "ME"
        Team.B -> "OPPONENT"
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
