package com.courtside.pickleball.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.sync.TabletConnectionState
import com.courtside.pickleball.ui.tablet.displayColor
import com.courtside.pickleball.ui.tablet.displayLabel
import com.courtside.pickleball.ui.tablet.phoneDisplayLabel
import com.courtside.pickleball.ui.theme.CallBackground
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.ProblemRed
import com.courtside.pickleball.ui.theme.SetupControlCornerRadius
import com.courtside.pickleball.ui.theme.SetupStatusHeight
import com.courtside.pickleball.ui.theme.SetupStatusHorizontalPadding
import com.courtside.pickleball.ui.theme.SetupStatusIndicatorHeight
import com.courtside.pickleball.ui.theme.SetupStatusIndicatorSize

@Composable
internal fun WatchConnectionStatusBar(
    modifier: Modifier = Modifier,
    connected: Boolean,
    compact: Boolean = false
) {
    SetupStatusBadge(
        modifier = modifier,
        label = when {
            compact -> "WATCH"
            connected -> "WATCH CONNECTED"
            else -> "WATCH OFFLINE"
        },
        color = if (connected) ConnectedAmber else ProblemRed,
        compact = compact
    )
}

@Composable
internal fun PhoneTabletStatusBar(
    modifier: Modifier = Modifier,
    connectionState: TabletConnectionState,
    compact: Boolean = false
) {
    SetupStatusBadge(
        modifier = modifier,
        label = if (compact) "TABLET" else connectionState.phoneDisplayLabel(),
        color = connectionState.displayColor(),
        compact = compact
    )
}

@Composable
internal fun TabletPhoneStatusBar(
    modifier: Modifier = Modifier,
    connectionState: TabletConnectionState,
    compact: Boolean = false
) {
    SetupStatusBadge(
        modifier = modifier,
        label = connectionState.displayLabel(pairedCourtCode = null),
        color = connectionState.displayColor(),
        compact = compact
    )
}

@Composable
internal fun CourtCodeBadge(
    modifier: Modifier = Modifier,
    code: String,
    compact: Boolean = false
) {
    SetupStatusBadge(
        modifier = modifier,
        label = if (compact) code else "COURT $code",
        color = ConnectedAmber,
        compact = compact
    )
}

@Composable
internal fun SetupStatusBadge(
    modifier: Modifier = Modifier,
    label: String,
    color: Color,
    compact: Boolean
) {
    Row(
        modifier = modifier
            .height(SetupStatusHeight)
            .clip(RoundedCornerShape(SetupControlCornerRadius))
            .background(CallBackground)
            .padding(horizontal = if (compact) 6.dp else SetupStatusHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = SetupStatusIndicatorSize, height = SetupStatusIndicatorHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = if (compact) 9.sp else 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (compact) 3.dp else 6.dp)
        )
    }
}
