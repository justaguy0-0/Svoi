package com.example.svoi.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.svoi.R

/**
 * Большой лоадер с анимацией котика — для центра экрана пока загружается контент.
 */
@Composable
fun SvoiLoader(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.cat_loader))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}

/**
 * Маленький лоадер-спиннер — для вспомогательных мест (пагинация, поиск, диалоги).
 */
@Composable
fun SvoiSpinner(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.spinner_loader))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}
