package com.courtside.pickleball.ui.tablet

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.sync.TabletDisplayState
import com.courtside.pickleball.ui.ads.ADS_ENABLED
import com.courtside.pickleball.ui.ads.TabletAdBanner
import com.courtside.pickleball.ui.scoreboard.TableCell
import com.courtside.pickleball.ui.scoreboard.TableDivider
import com.courtside.pickleball.ui.scoreboard.TeamNameDisplay
import com.courtside.pickleball.ui.status.CourtCodeBadge
import com.courtside.pickleball.ui.status.TabletPhoneStatusBar
import com.courtside.pickleball.ui.status.WatchConnectionStatusBar
import com.courtside.pickleball.ui.theme.CallBackground
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.Paper
import com.courtside.pickleball.ui.theme.ProblemRed
import com.courtside.pickleball.ui.theme.TableLine
import com.courtside.pickleball.ui.theme.TabletCallBarControlInset
import com.courtside.pickleball.ui.theme.TabletCallBarHeight
import com.courtside.pickleball.ui.theme.TabletCallBarLineHeight
import com.courtside.pickleball.ui.theme.TabletCallBarTextSize
import com.courtside.pickleball.ui.theme.TabletCallBarWideLineHeight
import com.courtside.pickleball.ui.theme.TabletCallBarWideTextSize
import com.courtside.pickleball.ui.theme.TeamABlue
import com.courtside.pickleball.ui.theme.TeamBGreen

@Composable
internal fun TabletDisplayScreen(
    state: TabletDisplayState,
    connectionState: TabletConnectionState = TabletConnectionState.Connected,
    courtCode: String? = null,
    myTeamOnTop: Boolean = true,
    watchConnected: Boolean = false,
    canUndo: Boolean = false,
    // True only for the connected-controller flow, where this tablet sends commands to a
    // phone-owned match over the sync channel. A local standalone Tablet Only match scores
    // through the shared engine directly and must stay usable regardless of
    // tabletConnectionState (which here only reflects unrelated phone-pairing status).
    isRemoteControlled: Boolean = false,
    onTeamARally: (() -> Unit)? = null,
    onTeamBRally: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null,
    // Local-only escape hatch for when the phone is unreachable: forgets the paired phone and
    // its stale match snapshot on this tablet, without trying to notify the (unreachable) phone.
    onForgetPhoneRequested: (() -> Unit)? = null,
    onCorrectionRequested: (() -> Unit)? = null,
    onNavigateToSetup: (() -> Unit)? = null
) {
    val isController = onUndo != null
    // When remote-controlled, commands only reach the phone while actually Connected; once the
    // phone drops off (e.g. its app was closed) and the connection goes stale, disable rally/undo/
    // end taps instead of silently accepting taps that go nowhere. "SETUP" stays available so the
    // tablet is never stuck with no way out.
    val remoteCommandsEnabled = !isRemoteControlled || connectionState == TabletConnectionState.Connected

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
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabletStatusHeader(
                connectionState = connectionState,
                courtCode = courtCode,
                watchConnected = watchConnected
            )
            TabletControlBar(
                state = state,
                canUndo = canUndo,
                remoteCommandsEnabled = remoteCommandsEnabled,
                onUndo = onUndo,
                onEndMatchRequested = onEndMatchRequested,
                onForgetPhoneRequested = onForgetPhoneRequested,
                onCorrectionRequested = onCorrectionRequested,
                onNavigateToSetup = onNavigateToSetup
            )
            TabletScoreboardBody(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = state,
                myTeamOnTop = myTeamOnTop,
                enabled = isController && remoteCommandsEnabled,
                onTeamARally = onTeamARally,
                onTeamBRally = onTeamBRally
            )
            // Bottom ad slot. TabletScoreboardBody has weight(1f), so it absorbs the banner's
            // height (the call bar is untouched). The divider + gap keeps the banner clear of the
            // bottom team row's score tap target, per AdMob's accidental-click policy. No-op when
            // ADS_ENABLED is false. TabletDisplayScreen renders only in the tablet layout, so this
            // is inherently phone-excluded.
            if (ADS_ENABLED) {
                HorizontalDivider(color = TableLine.copy(alpha = 0.25f))
                TabletAdBanner()
            }
        }
    }
}

@Composable
internal fun TabletScoreboardBody(
    modifier: Modifier,
    state: TabletDisplayState,
    myTeamOnTop: Boolean,
    enabled: Boolean,
    onTeamARally: (() -> Unit)?,
    onTeamBRally: (() -> Unit)?
) {
    val orderedTeams = if (myTeamOnTop) listOf(Team.A, Team.B) else listOf(Team.B, Team.A)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        orderedTeams.forEach { team ->
            TabletTeamScoreTableRow(
                modifier = Modifier.weight(1f),
                name = if (team == Team.A) state.teamACourtOrderedName else state.teamBCourtOrderedName,
                score = if (team == Team.A) state.teamAScore else state.teamBScore,
                color = if (team == Team.A) TeamABlue else TeamBGreen,
                isServing = state.servingTeam == team,
                serverNumber = state.serverNumber.toServerNumber(),
                enabled = enabled,
                onScoreClick = if (team == Team.A) onTeamARally else onTeamBRally,
                servingPlayerName = if (state.servingTeam == team) state.servingPlayerName else ""
            )
        }
    }
}

@Composable
internal fun TabletTeamScoreTableRow(
    modifier: Modifier,
    name: String,
    score: Int,
    color: Color,
    isServing: Boolean,
    serverNumber: ServerNumber,
    enabled: Boolean,
    onScoreClick: (() -> Unit)?,
    servingPlayerName: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        TableCell(
            modifier = Modifier
                .width(132.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            TabletServeDots(
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
                modifier = Modifier.padding(horizontal = 30.dp),
                name = name,
                color = Color.White,
                highlightColor = color,
                servingPlayerName = servingPlayerName,
                isTablet = true
            )
        }
        TableDivider()
        TabletScoreTapTarget(
            modifier = Modifier.fillMaxHeight(),
            score = score,
            color = Color.White,
            enabled = enabled,
            onClick = onScoreClick
        )
    }
}

@Composable
internal fun TabletServeDots(color: Color, isServing: Boolean, serverNumber: ServerNumber) {
    Row(
        modifier = Modifier.size(width = 124.dp, height = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isServing) {
            repeat(serverNumber.displayValue) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

internal fun Int.toServerNumber(): ServerNumber = when (this) {
    1 -> ServerNumber.One
    else -> ServerNumber.Two
}

internal fun TabletConnectionState.displayLabel(pairedCourtCode: String?): String = when (this) {
    TabletConnectionState.Searching -> pairedCourtCode?.let { "JOINING COURT $it" } ?: "SEARCHING COURTS"
    TabletConnectionState.Reconnecting -> pairedCourtCode?.let { "RECONNECTING COURT $it" } ?: "PHONE RECONNECTING"
    TabletConnectionState.Connected -> pairedCourtCode?.let { "CONNECTED TO COURT $it" } ?: "PHONE CONNECTED"
}

internal fun TabletConnectionState.phoneDisplayLabel(): String = when (this) {
    TabletConnectionState.Searching -> "FINDING TABLET"
    TabletConnectionState.Reconnecting -> "TABLET RECONNECTING"
    TabletConnectionState.Connected -> "TABLET CONNECTED"
}

internal fun TabletConnectionState.displayColor(): Color = when (this) {
    TabletConnectionState.Searching -> Color.Gray
    TabletConnectionState.Reconnecting -> ProblemRed
    TabletConnectionState.Connected -> ConnectedAmber
}

@Composable
internal fun TabletStatusHeader(
    connectionState: TabletConnectionState,
    courtCode: String? = null,
    watchConnected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CourtCodeBadge(
            modifier = Modifier.weight(1f),
            code = courtCode ?: "0000"
        )
        WatchConnectionStatusBar(
            modifier = Modifier.weight(1f),
            connected = watchConnected
        )
        TabletPhoneStatusBar(
            modifier = Modifier.weight(1f),
            connectionState = connectionState
        )
    }
}

// The call bar's digits render at a very large font size, so a full-size hyphen looks like an
// oversized dash between them; shrinking it (relative to the surrounding text) keeps it short
// and proportionate instead. Shrinking a span's font size keeps it on the same baseline, which
// drags a small glyph down toward the bottom of the big digits, so nudge it back up toward
// digit-center with a baseline shift. The surrounding spaces render at this same smaller size
// so the gap they add stays proportionate rather than full-digit-sized.
private val CallBarHyphenStyle = SpanStyle(fontSize = 0.55.em, baselineShift = BaselineShift(0.19f))

internal fun TabletDisplayState.callHasDoubleDigitScore(): Boolean =
    teamAScore >= 10 || teamBScore >= 10

internal fun TabletDisplayState.coloredScoreCall() = buildAnnotatedString {
    val (servingScore, receivingScore) = when (servingTeam) {
        Team.A -> teamAScore to teamBScore
        Team.B -> teamBScore to teamAScore
    }
    append(servingScore.toString())
    withStyle(CallBarHyphenStyle) { append(" - ") }
    append(receivingScore.toString())
    withStyle(CallBarHyphenStyle) { append(" - ") }
    append(serverNumber.toString())
}

internal fun String.toCourtCode(): String =
    filter { it.isLetterOrDigit() }
        .takeLast(4)
        .uppercase()
        .ifBlank { "0000" }

@Composable
internal fun TabletControlBar(
    state: TabletDisplayState,
    canUndo: Boolean = false,
    remoteCommandsEnabled: Boolean = true,
    onUndo: (() -> Unit)? = null,
    onEndMatchRequested: (() -> Unit)? = null,
    onForgetPhoneRequested: (() -> Unit)? = null,
    onCorrectionRequested: (() -> Unit)? = null,
    onNavigateToSetup: (() -> Unit)? = null
) {
    val showControls = onUndo != null && onEndMatchRequested != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(TabletCallBarHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(CallBackground)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        val useWideCallText = state.callHasDoubleDigitScore()
        Text(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(end = if (showControls) TabletCallBarControlInset else 0.dp, start = 12.dp),
            text = state.coloredScoreCall(),
            color = Color.White,
            fontSize = if (useWideCallText) TabletCallBarWideTextSize else TabletCallBarTextSize,
            fontWeight = FontWeight.Black,
            lineHeight = if (useWideCallText) TabletCallBarWideLineHeight else TabletCallBarLineHeight,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (onNavigateToSetup != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .height(52.dp)
                            .width(104.dp),
                        onClick = onNavigateToSetup,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("SETUP", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                if (onCorrectionRequested != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .height(52.dp)
                            .width(104.dp),
                        onClick = onCorrectionRequested,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("EDIT", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
                OutlinedButton(
                    modifier = Modifier
                        .height(52.dp)
                        .width(104.dp),
                    onClick = onUndo!!,
                    enabled = canUndo && remoteCommandsEnabled,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("UNDO", fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                // While disconnected, sending "end match" to the phone would just be dropped
                // silently, so this button instead falls back to a local-only action that
                // forgets the paired phone and its stale match snapshot on this tablet.
                val useLocalEndFallback = !remoteCommandsEnabled && onForgetPhoneRequested != null
                OutlinedButton(
                    modifier = Modifier
                        .height(52.dp)
                        .width(104.dp),
                    onClick = if (useLocalEndFallback) onForgetPhoneRequested!! else onEndMatchRequested!!,
                    enabled = remoteCommandsEnabled || useLocalEndFallback,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (useLocalEndFallback) "END\n(LOCAL)" else "END",
                        fontSize = if (useLocalEndFallback) 14.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
internal fun TabletScoreTapTarget(
    modifier: Modifier,
    score: Int,
    color: Color,
    enabled: Boolean,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = modifier
            .width(210.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, -10)
                }
            },
            text = score.toString(),
            color = if (enabled) color else color.copy(alpha = 0.35f),
            fontSize = 122.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 126.sp,
            maxLines = 1
        )
    }
}
