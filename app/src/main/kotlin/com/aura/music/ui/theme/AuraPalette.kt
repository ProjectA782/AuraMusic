/**
 * Aura Music
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Design tokens for the Aura TV shell. Every value here is read straight off the
 * material_you_music_tv mock: the palette, the corner radii, the rail and footer geometry and the
 * Roboto type scale. Screens must take their colors from this file (or from the ColorScheme built
 * below) instead of inventing their own, so the whole app stays on one design.
 */

package com.aura.music.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.R

// ---------------------------------------------------------------- palette ---

/** Page and window background. */
val AuraBackground = Color(0xFF141218)
/** The player footer surface. */
val AuraFooter = Color(0xFF1D1B20)
/** Cards, chips, settings rows, modal panel. */
val AuraContainer = Color(0xFF211F26)
/** Focused/hovered surface, list row separators, card borders. */
val AuraContainerHigh = Color(0xFF2B2930)
val AuraContainerHighest = Color(0xFF36333C)
val AuraContainerLowest = Color(0xFF0D0B10)
/** Progress and toggle tracks, modal border. */
val AuraTrack = Color(0xFF4A4458)
/** Text and icons on the primary action color. */
val AuraOnPrimary = Color(0xFF381E72)
/** Selected rail row, secondary hero badge. */
val AuraPrimaryContainer = Color(0xFF4F378B)
/** Inactive labels, subtitles, secondary text (`text-[#CAC4D0]` throughout the mock). */
val AuraOnSurfaceVariant = Color(0xFFCAC4D0)
/** Card titles and the rail brand wordmark (`text-[#E6E0E9]`). */
val AuraTextPrimary = Color(0xFFE6E0E9)

val AuraSurfaceContainerLowest = AuraContainerLowest
val AuraSurfaceContainerLow = AuraFooter
val AuraSurfaceContainer = AuraContainer
val AuraSurfaceContainerHigh = AuraContainerHigh
val AuraSurfaceContainerHighest = AuraContainerHighest

/**
 * The mock's exact Material You purple scheme. Named `AuraDarkColorScheme` because it is also what
 * the light-mode branch resolves to on television: the shell is designed for a dark-room surface.
 */
val AuraDarkColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = AuraOnPrimary,
    primaryContainer = AuraPrimaryContainer,
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF543E8F),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = AuraTrack,
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AuraBackground,
    onBackground = Color(0xFFE6E0E9),
    surface = AuraBackground,
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = AuraTrack,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFD0BCFF),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    scrim = Color(0xFF000000),
    surfaceBright = AuraContainerHighest,
    surfaceDim = AuraBackground,
    surfaceContainerLowest = AuraContainerLowest,
    surfaceContainerLow = AuraFooter,
    surfaceContainer = AuraContainer,
    surfaceContainerHigh = AuraContainerHigh,
    surfaceContainerHighest = AuraContainerHighest,
    primaryFixed = Color(0xFFD0BCFF),
    primaryFixedDim = Color(0xFFB69DF8),
    onPrimaryFixed = Color(0xFF1D192B),
    onPrimaryFixedVariant = AuraPrimaryContainer,
    secondaryFixed = Color(0xFFE8DEF8),
    secondaryFixedDim = Color(0xFFCCC2DC),
    onSecondaryFixed = Color(0xFF1D192B),
    onSecondaryFixedVariant = AuraTrack,
    tertiaryFixed = Color(0xFFFFD8E4),
    tertiaryFixedDim = Color(0xFFEFB8C8),
    onTertiaryFixed = Color(0xFF31111D),
    onTertiaryFixedVariant = Color(0xFF633B48),
)

// --------------------------------------------------------------- geometry ---

object AuraMetrics {
    // Shell
    val PagePadding = 28.dp
    val SectionSpacing = 22.dp
    val HeaderHeight = 64.dp

    // Navigation rail: w-24 collapsed, w-72 focused, brand circle, row geometry
    val RailCollapsedWidth = 84.dp
    val RailExpandedWidth = 260.dp
    val RailPadding = 14.dp
    val RailItemSpacing = 10.dp
    val RailIconSize = 26.dp
    val RailBrandIconSize = 44.dp
    val RailRowPaddingVertical = 12.dp
    val RailRowPaddingHorizontal = 14.dp
    val RailLabelGap = 14.dp

    // Header chips
    val ChipPaddingHorizontal = 16.dp
    val ChipPaddingVertical = 8.dp
    val ChipDotSize = 8.dp
    val HeaderAccountSize = 44.dp

    // Hero
    val HeroMinHeight = 210.dp
    val HeroBadgePaddingHorizontal = 12.dp
    val HeroBadgePaddingVertical = 4.dp
    val HeroButtonPaddingHorizontal = 26.dp
    val HeroButtonPaddingVertical = 13.dp
    val HeroButtonGap = 18.dp
    val HeroIconSize = 24.dp

    // Media card: w-64, p-4, rounded-[24px], cover rounded-[16px], 48px play FAB
    val CardWidth = 210.dp
    val CardPadding = 12.dp
    val CardCornerRadius = 20.dp
    val CardImageCornerRadius = 14.dp
    val CardPlayFabSize = 40.dp
    val CardPlayFabMargin = 10.dp
    val CardPlayFabCornerRadius = 12.dp
    val CardGap = 18.dp
    val CardTitleGap = 10.dp
    val CardChipPaddingHorizontal = 7.dp
    val CardChipPaddingVertical = 2.dp

    // Track row (album/playlist/modal)
    val RowPadding = 12.dp
    val RowCornerRadius = 16.dp
    val RowThumbnailSize = 48.dp
    val RowGap = 8.dp

    // Detail modal
    val ModalPadding = 24.dp
    val ModalCornerRadius = 26.dp
    val ModalCoverSize = 170.dp
    val ModalCoverCornerRadius = 20.dp
    val ModalMaxWidth = 760.dp

    // Settings row
    val SettingsPadding = 20.dp
    val SettingsCornerRadius = 24.dp
    val ToggleWidth = 44.dp
    val ToggleHeight = 26.dp
    val ToggleThumbSize = 20.dp

    // Profile card
    val ProfilePadding = 24.dp
    val ProfileCornerRadius = 28.dp
    val ProfileAvatarSize = 120.dp
    val ProfileTopTrackSize = 76.dp

    /** The mock's focus ring: 4px outline plus a soft drop shadow. */
    val FocusRingWidth = 3.dp
    val FocusRingPadding = 2.dp
    val FocusScale = 1.05f
}

val AuraCardShape = RoundedCornerShape(AuraMetrics.CardCornerRadius)
val AuraCardImageShape = RoundedCornerShape(AuraMetrics.CardImageCornerRadius)
val AuraFabShape = RoundedCornerShape(AuraMetrics.CardPlayFabCornerRadius)
val AuraRowShape = RoundedCornerShape(AuraMetrics.RowCornerRadius)
val AuraSettingsShape = RoundedCornerShape(AuraMetrics.SettingsCornerRadius)
val AuraModalShape = RoundedCornerShape(AuraMetrics.ModalCornerRadius)
val AuraProfileShape = RoundedCornerShape(AuraMetrics.ProfileCornerRadius)
val AuraPillShape = CircleShape


/**
 * A hairline on one side only, for the seams the mock draws between the shell surfaces:
 * `border-r` on the rail, `border-t` on the player footer.
 */
fun Modifier.auraEdge(edge: Edge, color: Color, width: Dp = 1.dp): Modifier =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        when (edge) {
            Edge.Left -> drawRect(color, Offset.Zero, Size(w, size.height))
            Edge.Top -> drawRect(color, Offset.Zero, Size(size.width, w))
            Edge.Right -> drawRect(color, Offset(size.width - w, 0f), Size(w, size.height))
            Edge.Bottom -> drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
        }
    }

enum class Edge { Left, Top, Right, Bottom }

// ------------------------------------------------------------- typography ---

/**
 * The reference is set in Roboto, which is Android's default family. Bundling a custom face made the
 * app look unlike the mock on every screen that still reads MaterialTheme.typography.
 */
val AuraFontFamily = FontFamily.Default

private fun auraType(weight: FontWeight, size: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = AuraFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
)
/**
 * Type scale mirroring the mock's Tailwind classes: text-6xl hero, text-5xl view titles,
 * text-2xl section heads, text-lg card titles and rail labels, text-sm subtitles, text-[11px] chips.
 */
val AuraTypography = Typography(
    displayLarge = auraType(FontWeight.ExtraBold, 44, -1.0),
    displayMedium = auraType(FontWeight.ExtraBold, 34, -0.5),
    displaySmall = auraType(FontWeight.ExtraBold, 26),
    headlineLarge = auraType(FontWeight.ExtraBold, 22, 0.5),
    headlineMedium = auraType(FontWeight.Bold, 20, 0.4),
    headlineSmall = auraType(FontWeight.Bold, 17),
    titleLarge = auraType(FontWeight.Bold, 15),
    titleMedium = auraType(FontWeight.Medium, 15),
    titleSmall = auraType(FontWeight.SemiBold, 13),
    bodyLarge = auraType(FontWeight.Normal, 15, 0.2),
    bodyMedium = auraType(FontWeight.Medium, 12),
    bodySmall = auraType(FontWeight.Normal, 11),
    labelLarge = auraType(FontWeight.SemiBold, 13, 0.4),
    labelMedium = auraType(FontWeight.Bold, 10, 0.6),
    labelSmall = auraType(FontWeight.Bold, 9, 0.8),
)
