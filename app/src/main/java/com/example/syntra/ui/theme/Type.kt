package com.example.syntra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.syntra.R

// Raleway is shipped as a single variable font (weight axis); each weight is a
// named instance created via FontVariation on the same resource.
@OptIn(ExperimentalTextApi::class)
private fun ralewayFont(weight: Int) = Font(
    resId = R.font.raleway_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Raleway = FontFamily(
    ralewayFont(300), // Light
    ralewayFont(400), // Normal
    ralewayFont(500), // Medium
    ralewayFont(600), // SemiBold
    ralewayFont(700), // Bold
    ralewayFont(800), // ExtraBold
)

// Base Material 3 typography, re-pointed so every text style uses Raleway.
private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Raleway),
    displayMedium = base.displayMedium.copy(fontFamily = Raleway),
    displaySmall = base.displaySmall.copy(fontFamily = Raleway),
    headlineLarge = base.headlineLarge.copy(fontFamily = Raleway),
    headlineMedium = base.headlineMedium.copy(fontFamily = Raleway),
    headlineSmall = base.headlineSmall.copy(fontFamily = Raleway),
    titleLarge = base.titleLarge.copy(fontFamily = Raleway),
    titleMedium = base.titleMedium.copy(fontFamily = Raleway),
    titleSmall = base.titleSmall.copy(fontFamily = Raleway),
    bodyLarge = base.bodyLarge.copy(fontFamily = Raleway),
    bodyMedium = base.bodyMedium.copy(fontFamily = Raleway),
    bodySmall = base.bodySmall.copy(fontFamily = Raleway),
    labelLarge = base.labelLarge.copy(fontFamily = Raleway),
    labelMedium = base.labelMedium.copy(fontFamily = Raleway),
    labelSmall = base.labelSmall.copy(fontFamily = Raleway),
)
