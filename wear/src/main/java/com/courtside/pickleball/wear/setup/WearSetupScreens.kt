package com.courtside.pickleball.wear.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.wear.WatchStartMode
import com.courtside.pickleball.wear.WearConnectionMode
import com.courtside.pickleball.wear.scoreboard.PrimaryStartButton
import com.courtside.pickleball.wear.scoreboard.ServeChoiceButton
import com.courtside.pickleball.wear.scoreboard.WearConnectionLabel
import com.courtside.pickleball.wear.theme.ConnectedAmber
import com.courtside.pickleball.wear.theme.MainText
import com.courtside.pickleball.wear.theme.ModeCyan
import com.courtside.pickleball.wear.theme.ModeSlate
import com.courtside.pickleball.wear.theme.ModeTealGreen
import com.courtside.pickleball.wear.theme.SecondaryText
import com.courtside.pickleball.wear.theme.TeamBlue
import com.courtside.pickleball.wear.theme.TeamGreen
import com.courtside.pickleball.wear.theme.WatchBackground
import kotlinx.coroutines.launch

@Composable
internal fun WearServeSetupScreen(
    connectionMode: WearConnectionMode,
    tabletStatus: String? = null,
    selectedScoringFormat: ScoringFormat,
    onScoringFormatSelected: (ScoringFormat) -> Unit,
    selectedStartingTeam: Team?,
    onSelectTeamA: () -> Unit,
    onSelectTeamB: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
    ) {
        WearConnectionLabel(
            modifier = Modifier.padding(top = 4.dp),
            connectionMode = connectionMode
        )
        Text(
            text = "CHOOSE SERVER",
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
        if (tabletStatus != null) {
            Text(
                text = tabletStatus,
                color = SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        WearScoringFormatSelector(
            selectedFormat = selectedScoringFormat,
            onFormatSelected = onScoringFormatSelected
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "WE SERVE",
                color = TeamBlue,
                selected = selectedStartingTeam == Team.A,
                onClick = onSelectTeamA
            )
            ServeChoiceButton(
                modifier = Modifier.weight(1f),
                label = "OPP SERVE",
                color = TeamGreen,
                selected = selectedStartingTeam == Team.B,
                onClick = onSelectTeamB
            )
        }
        PrimaryStartButton(
            enabled = selectedStartingTeam != null,
            onClick = onStart
        )
    }
}

@Composable
internal fun WearConnectedStartChoiceScreen(
    connectionMode: WearConnectionMode,
    selectedMode: WatchStartMode,
    availableModes: List<WatchStartMode>,
    selectedTabletCourtCode: String?,
    showTabletCourtSelector: Boolean,
    tabletReady: Boolean,
    tabletModeStatus: String?,
    onModeCycle: () -> Unit,
    onTabletCourtCycle: () -> Unit,
    selectedScoringFormat: ScoringFormat,
    onScoringFormatSelected: (ScoringFormat) -> Unit,
    selectedStartingTeam: Team?,
    onSelectTeamA: () -> Unit,
    onSelectTeamB: () -> Unit,
    onStart: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val rotaryFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedMode, showTabletCourtSelector, tabletReady) {
        if (selectedMode == WatchStartMode.Tablet && !showTabletCourtSelector) {
            scrollState.scrollTo(scrollState.maxValue)
        } else {
            scrollState.scrollTo(0)
        }
    }
    LaunchedEffect(Unit) {
        rotaryFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBackground)
    ) {
        Column(
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
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
        ) {
            WearConnectionLabel(
                modifier = Modifier.padding(top = 4.dp),
                connectionMode = connectionMode
            )
            WearStartModeButton(
                label = when (selectedMode) {
                    WatchStartMode.Tablet -> "TABLET MODE"
                    WatchStartMode.Watch -> "WATCH MODE"
                    WatchStartMode.Phone -> "PHONE MODE"
                },
                subtitle = if (availableModes.size > 1) "TAP TO SWITCH" else null,
                color = when (selectedMode) {
                    WatchStartMode.Tablet -> ModeTealGreen
                    WatchStartMode.Watch -> ModeSlate
                    WatchStartMode.Phone -> ModeCyan
                },
                onClick = onModeCycle
            )
            if (selectedMode == WatchStartMode.Tablet && showTabletCourtSelector) {
                WearStartModeButton(
                    label = selectedTabletCourtCode?.let { "COURT $it" } ?: "SEARCHING TABLETS",
                    subtitle = when {
                        selectedTabletCourtCode != null && availableModes.contains(WatchStartMode.Tablet) -> "TAP TO SWITCH"
                        else -> tabletModeStatus
                    },
                    color = ConnectedAmber,
                    onClick = onTabletCourtCycle
                )
            } else if (tabletModeStatus != null) {
                Text(
                    text = tabletModeStatus,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            if (selectedMode == WatchStartMode.Watch) {
                WearScoringFormatSelector(
                    selectedFormat = selectedScoringFormat,
                    onFormatSelected = onScoringFormatSelected
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ServeChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = "WE SERVE",
                    color = TeamBlue,
                    selected = selectedStartingTeam == Team.A,
                    onClick = onSelectTeamA
                )
                ServeChoiceButton(
                    modifier = Modifier.weight(1f),
                    label = "OPP SERVE",
                    color = TeamGreen,
                    selected = selectedStartingTeam == Team.B,
                    onClick = onSelectTeamB
                )
            }
            PrimaryStartButton(
                enabled = selectedStartingTeam != null &&
                    (selectedMode != WatchStartMode.Tablet || tabletReady),
                onClick = onStart
            )
        }

        WearScrollIndicator(scrollState = scrollState)
    }
}

/**
 * Compose foundation's [verticalScroll] does not draw a scrollbar. Keep this visible whenever
 * this setup form overflows so users on small and round Wear displays can discover the remaining
 * controls and Play's scrollbar requirement is met.
 */
@Composable
private fun WearScrollIndicator(scrollState: androidx.compose.foundation.ScrollState) {
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
internal fun WearScoringFormatSelector(
    selectedFormat: ScoringFormat,
    onFormatSelected: (ScoringFormat) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WearScoringFormatButton(
            modifier = Modifier.weight(1f),
            label = "CLASSIC",
            selected = selectedFormat == ScoringFormat.Traditional,
            onClick = { onFormatSelected(ScoringFormat.Traditional) }
        )
        WearScoringFormatButton(
            modifier = Modifier.weight(1f),
            label = "RALLY",
            selected = selectedFormat == ScoringFormat.Rally,
            onClick = { onFormatSelected(ScoringFormat.Rally) }
        )
    }
}

@Composable
private fun WearScoringFormatButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) ConnectedAmber else MainText.copy(alpha = 0.28f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) ConnectedAmber.copy(alpha = 0.18f) else WatchBackground,
            contentColor = if (selected) ConnectedAmber else MainText
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WearStartModeButton(
    label: String,
    subtitle: String? = null,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .height(46.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            2.dp,
            color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = color.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun Int.floorMod(modulus: Int): Int =
    if (modulus == 0) 0 else ((this % modulus) + modulus) % modulus
