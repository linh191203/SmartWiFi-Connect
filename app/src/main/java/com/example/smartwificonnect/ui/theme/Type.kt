package com.example.smartwificonnect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.smartwificonnect.R

val RoundedUiFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val BaseTypography = Typography()

private fun TextStyle.withRoundedUiFont(): TextStyle = copy(fontFamily = RoundedUiFontFamily)

val AppTypography = Typography(
    displayLarge = BaseTypography.displayLarge.withRoundedUiFont(),
    displayMedium = BaseTypography.displayMedium.withRoundedUiFont(),
    displaySmall = BaseTypography.displaySmall.withRoundedUiFont(),
    headlineLarge = BaseTypography.headlineLarge.withRoundedUiFont(),
    headlineMedium = BaseTypography.headlineMedium.withRoundedUiFont(),
    headlineSmall = BaseTypography.headlineSmall.withRoundedUiFont(),
    titleLarge = BaseTypography.titleLarge.withRoundedUiFont(),
    titleMedium = BaseTypography.titleMedium.withRoundedUiFont(),
    titleSmall = BaseTypography.titleSmall.withRoundedUiFont(),
    bodyLarge = BaseTypography.bodyLarge.withRoundedUiFont(),
    bodyMedium = BaseTypography.bodyMedium.withRoundedUiFont(),
    bodySmall = BaseTypography.bodySmall.withRoundedUiFont(),
    labelLarge = BaseTypography.labelLarge.withRoundedUiFont(),
    labelMedium = BaseTypography.labelMedium.withRoundedUiFont(),
    labelSmall = BaseTypography.labelSmall.withRoundedUiFont(),
)
