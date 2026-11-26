package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class BravLuxuryColors(
    val isDark: Boolean,
    val ivoryCanvas: Color,
    val cardSurface: Color,
    val translucentCardSurface: Color,
    val translucentSubtleSurface: Color,
    val modalSurface: Color,
    val elevatedSurface: Color,
    val softGray: Color,
    val hairline: Color,
    val violetContour: Color,
    val champagneGold: Color,
    val champagneSoftTint: Color,
    val headerTop: Color,
    val headerBottom: Color,
    val headerOnColor: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val titlePurple: Color,
    val watermarkViolet: Color,
    val watermarkGold: Color,
    val watermarkRed: Color,
    val bubbleOutgoing: Color,
    val bubbleIncoming: Color
)

private val LightLuxuryColors = BravLuxuryColors(
    isDark = false,
    ivoryCanvas = BravIvory,
    cardSurface = Color(0xB8FAF8F3),
    translucentCardSurface = Color(0xB8FAF8F3),
    translucentSubtleSurface = Color(0x78F1EEE8),
    modalSurface = Color(0xF5FAF8F3),
    elevatedSurface = Color(0xCCF6F2EA),
    softGray = Color(0x88F1EEE8),
    hairline = BravWarmBorder,
    violetContour = BravPurplePrimary.copy(alpha = 0.28f),
    champagneGold = BravChampagneGold,
    champagneSoftTint = Color(0x1EC6A66B),
    headerTop = BravPurpleDeep,
    headerBottom = BravPurplePrimary,
    headerOnColor = BravWhite,
    inkPrimary = BravInkPrimary,
    inkSecondary = BravInkSecondary,
    titlePurple = BravPurplePrimary,
    watermarkViolet = BravPurplePrimary,
    watermarkGold = BravChampagneGold,
    watermarkRed = BravRedSecondary,
    bubbleOutgoing = BravPurplePrimary,
    bubbleIncoming = Color(0xCCFAF8F3)
)

private val DarkLuxuryColors = BravLuxuryColors(
    isDark = true,
    ivoryCanvas = SlateBackgroundDark,
    cardSurface = Color(0xBF191423),
    translucentCardSurface = Color(0xBF191423),
    translucentSubtleSurface = Color(0x88231D2F),
    modalSurface = Color(0xF5191423),
    elevatedSurface = Color(0xD6241E30),
    softGray = Color(0x90231D2F),
    hairline = Color(0xFF3A304E),
    violetContour = BravPurpleBright.copy(alpha = 0.34f),
    champagneGold = BravChampagneGoldSoft,
    champagneSoftTint = Color(0x26D9BF8C),
    headerTop = Color(0xFF26034D),
    headerBottom = BravPurpleDeep,
    headerOnColor = Color(0xFFFDFBF7),
    inkPrimary = SlateOnSurfaceDark,
    inkSecondary = SlateOnSurfaceVariantDark,
    titlePurple = Color(0xFFD6A8FF),
    watermarkViolet = Color(0xFFB366FF),
    watermarkGold = BravChampagneGoldSoft,
    watermarkRed = BravRedBright,
    bubbleOutgoing = BravPurplePrimary,
    bubbleIncoming = Color(0xD91F192B)
)

val LocalBravLuxuryColors = staticCompositionLocalOf { LightLuxuryColors }

object BravTheme {
    val colors: BravLuxuryColors
        @Composable
        get() = LocalBravLuxuryColors.current
}

val BravShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateOnSurfaceDark,
    surface = SlateSurfaceDark,
    surfaceContainerLow = Color(0xFF1D1729),
    surfaceContainer = SlateSurfaceVariantDark,
    surfaceContainerHigh = Color(0xFF2A2238),
    surfaceContainerHighest = Color(0xFF342A45),
    surfaceVariant = SlateSurfaceVariantDark,
    onSurface = SlateOnSurfaceDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark,
    outline = Color(0xFF473D5C),
    outlineVariant = Color(0xFF302840)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = SlateBackgroundLight,
    onBackground = BravInkPrimary,
    surface = SlateSurfaceLight,
    surfaceContainerLow = BravIvory,
    surfaceContainer = BravSoftGray,
    surfaceContainerHigh = Color(0xFFECE7DE),
    surfaceContainerHighest = Color(0xFFE4DDD1),
    surfaceVariant = SlateSurfaceVariantLight,
    onSurface = SlateOnSurfaceLight,
    onSurfaceVariant = SlateOnSurfaceVariantLight,
    outline = Color(0xFFD2C9B8),
    outlineVariant = BravWarmBorder
)

@Composable
fun BravChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val luxuryColors = if (darkTheme) DarkLuxuryColors else LightLuxuryColors

    CompositionLocalProvider(LocalBravLuxuryColors provides luxuryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = BravShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = BravChatTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
