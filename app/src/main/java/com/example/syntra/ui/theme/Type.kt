@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.syntra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.syntra.R

// Inter — the clean, neutral, highly legible UI sans used across modern apps. One
// variable-font file (`inter_variable.ttf`) carries every weight; each named weight
// below drives the font's `wght` axis via FontVariation. minSdk is 26, which is
// where Android's variable-font support begins, so no static per-weight files needed.
private fun interWeight(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val AppFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Light, variationSettings = interWeight(300)),
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = interWeight(400)),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = interWeight(500)),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = interWeight(600)),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = interWeight(700)),
    Font(R.font.inter_variable, FontWeight.ExtraBold, variationSettings = interWeight(800)),
)

// Kept as an alias so existing references to `Raleway` keep compiling; both now
// point at the current app font.
val Raleway = AppFontFamily

// Base Material 3 typography, re-pointed so every text style uses the app font.
private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily),
)
