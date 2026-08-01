package com.texter.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.texter.app.R

// Chrome only — buttons, labels, the saved-documents list. Deliberately NOT used for the text
// being edited (see EditorFontFamily below): this subset only covers the glyphs the app's own UI
// renders, so arbitrary file content in other scripts would hit font-fallback substitution.
val MartianMono = FontFamily(
    Font(R.font.martian_mono_regular, FontWeight.Normal),
    Font(R.font.martian_mono_medium, FontWeight.Medium)
)

// The actual text-editing surface uses the platform's own monospace font (Roboto Mono on
// stock/AOSP Android) instead of a bundled file — full Unicode coverage for whatever a user opens,
// zero extra bytes shipped, and standard Android font-fallback still applies per-glyph for
// anything outside it (CJK, emoji) same as it would for any other font choice here.
val EditorFontFamily = FontFamily.Monospace

private val defaultTypography = Typography()

val TexterTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = MartianMono),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = MartianMono),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = MartianMono),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = MartianMono),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = MartianMono),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = MartianMono),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = MartianMono),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = MartianMono),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = MartianMono),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium)
)
