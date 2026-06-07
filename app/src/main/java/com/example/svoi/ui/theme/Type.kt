package com.example.svoi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.svoi.R
import kotlin.math.max

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )
)

fun scaledTypography(base: Typography, scale: Float): Typography = Typography(
    displayLarge = base.displayLarge.scaled(scale),
    displayMedium = base.displayMedium.scaled(scale),
    displaySmall = base.displaySmall.scaled(scale),
    headlineLarge = base.headlineLarge.scaled(scale),
    headlineMedium = base.headlineMedium.scaled(scale),
    headlineSmall = base.headlineSmall.scaled(scale),
    titleLarge = base.titleLarge.scaled(scale),
    titleMedium = base.titleMedium.scaled(scale),
    titleSmall = base.titleSmall.scaled(scale),
    bodyLarge = base.bodyLarge.scaled(scale),
    bodyMedium = base.bodyMedium.scaled(scale),
    bodySmall = base.bodySmall.scaled(scale),
    labelLarge = base.labelLarge.scaled(scale),
    labelMedium = base.labelMedium.scaled(scale),
    labelSmall = base.labelSmall.scaled(scale)
)

private fun TextStyle.scaled(scale: Float): TextStyle {
    val scaledFontSize = fontSize.scaledTextUnit(scale)
    val scaledLineHeight = if (lineHeight.isSpecified) {
        val lineHeightValue = lineHeight.value * scale
        val minLineHeight = if (scaledFontSize.isSpecified) scaledFontSize.value else 0f
        max(lineHeightValue, minLineHeight).sp
    } else {
        lineHeight
    }
    return copy(
        fontSize = scaledFontSize,
        lineHeight = scaledLineHeight
    )
}

private fun TextUnit.scaledTextUnit(scale: Float): TextUnit =
    if (isSpecified) (value * scale).sp else this
