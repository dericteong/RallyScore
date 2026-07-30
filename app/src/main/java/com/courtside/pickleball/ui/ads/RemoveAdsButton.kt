package com.courtside.pickleball.ui.ads

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.courtside.pickleball.ui.theme.ConnectedAmber
import com.courtside.pickleball.ui.theme.Ink

/**
 * "REMOVE ADS" action shown next to the banner. Filled amber (the app's highlight colour) with
 * dark bold text so it reads as an app control and stands clearly apart from the ad creative.
 * Launches the purchase flow.
 */
@Composable
internal fun RemoveAdsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ConnectedAmber,
            contentColor = Ink
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        Text(
            text = "REMOVE ADS",
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}
