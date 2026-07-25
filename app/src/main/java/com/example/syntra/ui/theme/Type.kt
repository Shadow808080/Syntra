package com.example.syntra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.syntra.R

// Plus Jakarta Sans — a modern, friendly geometric sans (by an Indonesian type
// designer). Shipped as static weight files so no variable-font axis handling is
// needed; each named instance maps to its own ttf.
val AppFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_light, FontWeight.Light),
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
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
