package com.courtside.pickleball.ui.onboarding

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.TeamABlue
import com.courtside.pickleball.ui.theme.TeamBGreen

private const val PREFS_NAME = "rallyscore_onboarding"
private const val KEY_SCORING_TUTORIAL_SEEN = "scoring_tutorial_seen"

/**
 * Tracks whether the one-time "tap the score" coach mark has already been shown.
 *
 * Kept in its own preferences file rather than the match or player stores so that clearing a
 * match, or deleting all players, never re-triggers onboarding.
 */
internal object ScoringTutorialPrefs {
    fun hasSeenScoringTutorial(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCORING_TUTORIAL_SEEN, false)

    fun markScoringTutorialSeen(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCORING_TUTORIAL_SEEN, true)
            .apply()
    }
}

/**
 * One-time coach mark explaining rally-winner input.
 *
 * Only the score number is a tap target (see `ScoreTapTarget`), which is not obvious from looking
 * at the scoreboard, so the mock rows below highlight the number rather than the whole row.
 */
@Composable
internal fun ScoringTutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tap to score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "When a team wins the rally, tap that team's score. " +
                        "RallyScore works out the serve and side-out for you.",
                    fontSize = 15.sp
                )
                TutorialTeamRow(
                    label = "MY TEAM",
                    score = 4,
                    color = TeamABlue,
                    highlightScore = true
                )
                TutorialTeamRow(
                    label = "OPPONENT",
                    score = 2,
                    color = TeamBGreen,
                    highlightScore = true
                )
                Text(
                    text = "Tap the big number, not the name. Blue is your team, green is your opponent.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

/** Miniature scoreboard row used only inside the tutorial dialog. */
@Composable
private fun TutorialTeamRow(
    label: String,
    score: Int,
    color: Color,
    highlightScore: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(44.dp)
                .padding(end = 6.dp)
                .then(
                    if (highlightScore) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                BorderStroke(3.dp, ConnectedAmber),
                                RoundedCornerShape(6.dp)
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = score.toString(),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
