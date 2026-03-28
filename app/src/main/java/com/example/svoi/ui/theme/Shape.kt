package com.example.svoi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object SvoiShapes {
    val Button = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(16.dp)
    val TextField = RoundedCornerShape(14.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val Chip = RoundedCornerShape(12.dp)
    val InputBar = RoundedCornerShape(24.dp)

    // Message bubbles — asymmetric corners
    val BubbleOwn = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    val BubbleOther = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
}
