package com.courtside.pickleball.ui

import androidx.compose.ui.text.buildAnnotatedString
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.ServerNumber
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.sync.TabletDisplayState

internal fun GameState.callHasDoubleDigitScore(): Boolean =
    servingScore >= 10 || receivingScore >= 10

internal fun GameState.scoreOnlyCallBarText() = buildAnnotatedString {
    append(servingScore.toString())
    append("-")
    append(receivingScore.toString())
    append("-")
    append(serverNumber.displayValue.toString())
}

internal fun GameState.servingSummary(): String {
    val servingSide = when (servingTeam) {
        Team.A -> settings.teamAName
        Team.B -> settings.teamBName
    }
    return "${servingSide.uppercase()} SERVES"
}

internal fun GameState.toTabletDisplayState(
    myTeamOnTop: Boolean,
    matchActive: Boolean,
    canUndo: Boolean,
    voiceAnnouncementMode: VoiceAnnouncementMode,
    watchConnected: Boolean
): TabletDisplayState =
    TabletDisplayState(
        hostId = "",
        sessionId = "",
        myTeamOnTop = myTeamOnTop,
        watchConnected = watchConnected,
        teamAName = settings.teamAName,
        teamBName = settings.teamBName,
        teamACourtOrderedName = courtOrderedTeamName(Team.A),
        teamBCourtOrderedName = courtOrderedTeamName(Team.B),
        teamAPlayer1 = settings.teamAPlayer1,
        teamAPlayer2 = settings.teamAPlayer2,
        teamBPlayer1 = settings.teamBPlayer1,
        teamBPlayer2 = settings.teamBPlayer2,
        teamAScore = teamAScore,
        teamBScore = teamBScore,
        servingTeam = servingTeam,
        serverNumber = serverNumber.displayValue,
        servingPlayerName = servingPlayerName(),
        scoreCall = scoreCall,
        spokenScoreCall = spokenScoreCall(),
        voiceAnnouncementMode = voiceAnnouncementMode,
        matchActive = matchActive,
        canUndo = canUndo,
        updatedAt = System.currentTimeMillis()
    )

internal fun VoiceAnnouncementMode.usesPhoneSpeaker(): Boolean =
    this == VoiceAnnouncementMode.PhoneOnly ||
        this == VoiceAnnouncementMode.WatchThenPhone ||
        this == VoiceAnnouncementMode.PhoneThenTablet

internal fun VoiceAnnouncementMode.usesTabletSpeaker(): Boolean =
    this == VoiceAnnouncementMode.TabletOnly ||
        this == VoiceAnnouncementMode.WatchThenTablet ||
        this == VoiceAnnouncementMode.PhoneThenTablet

internal fun VoiceAnnouncementMode.usesThisDeviceSpeaker(isTablet: Boolean): Boolean =
    if (isTablet) usesTabletSpeaker() else usesPhoneSpeaker()

internal fun VoiceAnnouncementMode.isDelayedOnThisDevice(isTablet: Boolean, watchConnected: Boolean): Boolean =
    when {
        isTablet -> isDelayedOnTablet()
        this == VoiceAnnouncementMode.WatchThenPhone && watchConnected -> true
        else -> false
    }

internal fun VoiceAnnouncementMode.isDelayedOnTablet(): Boolean =
    this == VoiceAnnouncementMode.WatchThenTablet ||
        this == VoiceAnnouncementMode.PhoneThenTablet

internal fun TabletDisplayState.voiceSignature(): String =
    "$teamAScore|$teamBScore|$servingTeam|$serverNumber|$spokenScoreCall"

internal fun TabletDisplayState.toCorrectionGameState(): GameState =
    GameState(
        teamAScore = teamAScore,
        teamBScore = teamBScore,
        servingTeam = servingTeam,
        serverNumber = if (serverNumber == 1) ServerNumber.One else ServerNumber.Two
    )
