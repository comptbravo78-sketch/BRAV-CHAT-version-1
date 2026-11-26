package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * BRAV AFRICAN PATTERNS
 * Vector geometric African textile, Adinkra solar, and heritage diamond patterns
 * rendered with cached DrawScope paths for zero allocation during scrolling.
 */
enum class BravPatternType(val labelFr: String) {
    /** Losanges concentriques, lignes parallèles et symétries géométriques Kente */
    KENTE_DIAMOND("Kente Géométrique"),

    /** Motif solaire rayonnant et cercles concentriques d'inspiration Adinkra */
    SOLAR_ADINKRA("Soleil Adinkra"),

    /** Grille textile africaine contemporaine avec motifs imbriqués et chevrons */
    WAX_TEXTILE("Textile Wax Contemporain"),

    /** Facettes royales violettes et résille dorée pour couvertures et bannières */
    HERITAGE_COVER("Couverture Royale")
}

enum class BravPatternVariant {
    VIOLET_ON_IVORY,
    GOLD_ON_IVORY,
    WHITE_ON_VIOLET,
    RED_ON_IVORY,
    TONE_ON_TONE
}

object BravPatternIntensity {
    const val SUBTLE_WATERMARK = 0.085f
    const val LIGHT_DECOR = 0.12f
    const val HEADER_TONE = 0.16f
    const val COVER_ART = 0.24f
}

@Composable
fun rememberBravPatternColors(variant: BravPatternVariant): Pair<Color, Color> {
    val lux = BravTheme.colors
    return when (variant) {
        BravPatternVariant.VIOLET_ON_IVORY -> lux.watermarkViolet to lux.watermarkGold
        BravPatternVariant.GOLD_ON_IVORY -> lux.watermarkGold to lux.watermarkViolet
        BravPatternVariant.WHITE_ON_VIOLET -> BravWhite to BravChampagneGold
        BravPatternVariant.RED_ON_IVORY -> lux.watermarkRed to lux.watermarkGold
        BravPatternVariant.TONE_ON_TONE -> if (lux.isDark) {
            lux.watermarkGold to lux.watermarkViolet
        } else {
            lux.champagneGold to lux.inkSecondary
        }
    }
}

fun Modifier.bravAfricanPattern(
    pattern: BravPatternType,
    primaryColor: Color,
    secondaryColor: Color = primaryColor,
    opacity: Float = BravPatternIntensity.SUBTLE_WATERMARK,
    scale: Float = 1f,
    rotationDegrees: Float = 0f,
    offsetFraction: Offset = Offset.Zero
): Modifier = this.drawWithCache {
    val safeOpacity = opacity.coerceIn(0f, 1f)
    val safeScale = scale.coerceIn(0.4f, 3.0f)
    val pColor = primaryColor.copy(alpha = safeOpacity)
    val sColor = secondaryColor.copy(alpha = (safeOpacity * 0.88f).coerceIn(0f, 1f))
    val strokeThin = (1.35f * safeScale).coerceIn(0.8f, 4f)
    val strokeBold = (2.1f * safeScale).coerceIn(1.2f, 5.5f)

    onDrawBehind {
        if (safeOpacity <= 0.001f || size.width <= 0f || size.height <= 0f) return@onDrawBehind
        withTransform({
            if (offsetFraction != Offset.Zero) {
                translate(
                    left = size.width * offsetFraction.x,
                    top = size.height * offsetFraction.y
                )
            }
            if (rotationDegrees != 0f) {
                rotate(degrees = rotationDegrees, pivot = center)
            }
        }) {
            when (pattern) {
                BravPatternType.KENTE_DIAMOND -> drawKenteDiamondGrid(
                    pColor = pColor,
                    sColor = sColor,
                    scale = safeScale,
                    strokeThin = strokeThin,
                    strokeBold = strokeBold
                )
                BravPatternType.SOLAR_ADINKRA -> drawSolarAdinkraMotif(
                    pColor = pColor,
                    sColor = sColor,
                    scale = safeScale,
                    strokeBold = strokeBold
                )
                BravPatternType.WAX_TEXTILE -> drawWaxTextileGrid(
                    pColor = pColor,
                    sColor = sColor,
                    scale = safeScale,
                    strokeThin = strokeThin
                )
                BravPatternType.HERITAGE_COVER -> drawHeritageCoverComposition(
                    pColor = pColor,
                    sColor = sColor,
                    scale = safeScale,
                    strokeThin = strokeThin
                )
            }
        }
    }
}

@Composable
fun BravAfricanPatternCanvas(
    pattern: BravPatternType,
    modifier: Modifier = Modifier,
    variant: BravPatternVariant = BravPatternVariant.GOLD_ON_IVORY,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    opacity: Float = BravPatternIntensity.SUBTLE_WATERMARK,
    scale: Float = 1f,
    rotationDegrees: Float = 0f,
    offsetFraction: Offset = Offset.Zero
) {
    val (defaultPrimary, defaultSecondary) = rememberBravPatternColors(variant)
    val effectivePrimary = primaryColor ?: defaultPrimary
    val effectiveSecondary = secondaryColor ?: defaultSecondary

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .bravAfricanPattern(
                pattern = pattern,
                primaryColor = effectivePrimary,
                secondaryColor = effectiveSecondary,
                opacity = opacity,
                scale = scale,
                rotationDegrees = rotationDegrees,
                offsetFraction = offsetFraction
            )
    ) {
        // Rendered via cached DrawScope modifier above
    }
}

private fun DrawScope.drawKenteDiamondGrid(
    pColor: Color,
    sColor: Color,
    scale: Float,
    strokeThin: Float,
    strokeBold: Float
) {
    val tile = (76f * scale).coerceAtLeast(36f)
    val cols = ceil(size.width / tile).toInt() + 2
    val rows = ceil(size.height / tile).toInt() + 2
    val strokePrimary = Stroke(width = strokeThin, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    val strokeSecondary = Stroke(width = strokeBold * 0.72f, cap = StrokeCap.Round)

    val path = Path()
    val accentPath = Path()

    for (r in -1..rows) {
        for (c in -1..cols) {
            val x0 = c * tile
            val y0 = r * tile
            val cx = x0 + tile * 0.5f
            val cy = y0 + tile * 0.5f
            val rx = tile * 0.42f
            val ry = tile * 0.42f

            // Outer concentric diamond
            path.moveTo(cx, cy - ry)
            path.lineTo(cx + rx, cy)
            path.lineTo(cx, cy + ry)
            path.lineTo(cx - rx, cy)
            path.close()

            // Inner diamond
            val irx = rx * 0.52f
            val iry = ry * 0.52f
            path.moveTo(cx, cy - iry)
            path.lineTo(cx + irx, cy)
            path.lineTo(cx, cy + iry)
            path.lineTo(cx - irx, cy)
            path.close()

            // Parallel vertical & horizontal rhythm bars at tile corners
            accentPath.moveTo(x0 + tile * 0.08f, cy - tile * 0.14f)
            accentPath.lineTo(x0 + tile * 0.08f, cy + tile * 0.14f)
            accentPath.moveTo(cx - tile * 0.14f, y0 + tile * 0.08f)
            accentPath.lineTo(cx + tile * 0.14f, y0 + tile * 0.08f)
            // Center Champagne jewel dot inside concentric diamond
            drawCircle(color = sColor, radius = tile * 0.042f, center = Offset(cx, cy))
        }
    }

    drawPath(path = path, color = pColor, style = strokePrimary)
    drawPath(path = accentPath, color = sColor, style = strokeSecondary)
}

private fun DrawScope.drawSolarAdinkraMotif(
    pColor: Color,
    sColor: Color,
    scale: Float,
    strokeBold: Float
) {
    // Draw hero asymmetric solar emblem + secondary counter-solar medallion for balance
    val canvasW = size.width
    val canvasH = size.height
    val baseRadius = (min(canvasW, canvasH) * 0.40f * scale).coerceIn(58f, 260f)
    val positions = buildList {
        add(Offset(canvasW * 0.86f, canvasH * 0.22f) to baseRadius)
        if (canvasH > 260f) {
            add(Offset(canvasW * 0.12f, canvasH * 0.80f) to (baseRadius * 0.78f))
        }
    }

    positions.forEachIndexed { idx, (sunCenter, radius) ->
        val mainStroke = Stroke(width = (radius * 0.055f).coerceIn(strokeBold, 8f), cap = StrokeCap.Round)
        val innerColor = if (idx == 0) pColor else sColor

        // Split upper & lower concentric Adinkra arcs (matching attached reference #4)
        for (ringIdx in 1..3) {
            val r = radius * (0.24f + ringIdx * 0.13f)
            val topLeft = Offset(sunCenter.x - r, sunCenter.y - r)
            val arcSize = Size(r * 2f, r * 2f)
            drawArc(
                color = innerColor,
                startAngle = 12f,
                sweepAngle = 156f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = mainStroke
            )
            drawArc(
                color = innerColor,
                startAngle = 192f,
                sweepAngle = 156f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = mainStroke
            )
        }

        // Radiating solar beams with crisp rounded tips
        val innerRayR = radius * 0.68f
        val outerRayR = radius * 0.98f
        val beamCount = 24
        for (i in 0 until beamCount) {
            val deg = (360f / beamCount) * i
            // Horizontal gap left/right like reference Adinkra emblem
            if (deg < 8f || deg > 352f || (deg in 172f..188f)) continue
            val rad = deg * (PI / 180.0)
            val start = Offset(
                x = sunCenter.x + (innerRayR * cos(rad)).toFloat(),
                y = sunCenter.y + (innerRayR * sin(rad)).toFloat()
            )
            val end = Offset(
                x = sunCenter.x + (outerRayR * cos(rad)).toFloat(),
                y = sunCenter.y + (outerRayR * sin(rad)).toFloat()
            )
            drawLine(
                color = innerColor,
                start = start,
                end = end,
                strokeWidth = mainStroke.width * 0.85f,
                cap = StrokeCap.Round
            )
        }

        // Subtle center dot
        drawCircle(
            color = sColor,
            radius = radius * 0.06f,
            center = sunCenter
        )
    }
}

private fun DrawScope.drawWaxTextileGrid(
    pColor: Color,
    sColor: Color,
    scale: Float,
    strokeThin: Float
) {
    val cell = (112f * scale).coerceAtLeast(64f)
    val cols = ceil(size.width / cell).toInt() + 1
    val rows = ceil(size.height / cell).toInt() + 1
    val lineStroke = Stroke(width = strokeThin, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val geomPath = Path()
    val petalPath = Path()

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val x = c * cell
            val y = r * cell
            val cx = x + cell * 0.5f
            val cy = y + cell * 0.5f
            val isAlt = (r + c) % 2 == 0

            if (isAlt) {
                // Artisanal woven square frame + quadrant ticks (inspired by image #2)
                val pad = cell * 0.16f
                geomPath.moveTo(x + pad, y + pad)
                geomPath.lineTo(x + cell - pad, y + pad)
                geomPath.lineTo(x + cell - pad, y + cell - pad)
                geomPath.lineTo(x + pad, y + cell - pad)
                geomPath.close()

                // Crosshatch center bars
                geomPath.moveTo(cx, y + pad * 1.4f)
                geomPath.lineTo(cx, y + cell - pad * 1.4f)
                geomPath.moveTo(x + pad * 1.4f, cy)
                geomPath.lineTo(x + cell - pad * 1.4f, cy)

                // Corner small rings
                drawCircle(color = sColor, radius = cell * 0.045f, center = Offset(x + pad * 1.8f, y + pad * 1.8f), style = lineStroke)
                drawCircle(color = sColor, radius = cell * 0.045f, center = Offset(x + cell - pad * 1.8f, y + cell - pad * 1.8f), style = lineStroke)
            } else {
                // Radial flower/starburst rosette + chevron wings
                val rOuter = cell * 0.30f
                for (k in 0 until 8) {
                    val angle = k * (PI / 4.0)
                    petalPath.moveTo(cx, cy)
                    petalPath.lineTo(
                        cx + (rOuter * cos(angle)).toFloat(),
                        cy + (rOuter * sin(angle)).toFloat()
                    )
                }
                drawCircle(color = sColor, radius = cell * 0.14f, center = Offset(cx, cy), style = lineStroke)
            }
        }
    }

    drawPath(path = geomPath, color = pColor, style = lineStroke)
    drawPath(path = petalPath, color = sColor, style = lineStroke)
}

private fun DrawScope.drawHeritageCoverComposition(
    pColor: Color,
    sColor: Color,
    scale: Float,
    strokeThin: Float
) {
    // Bold royal geometric facets + champagne gold diamond trellis (inspired by image #3 & maquette profile cover)
    val unit = (96f * scale).coerceAtLeast(56f)
    val cols = ceil(size.width / unit).toInt() + 2
    val rows = ceil(size.height / unit).toInt() + 2
    val trellisStroke = Stroke(width = strokeThin * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val facetPath = Path()
    val latticePath = Path()

    for (r in -1..rows) {
        for (c in -1..cols) {
            val x = c * unit + (if (r % 2 == 0) 0f else unit * 0.5f)
            val y = r * unit * 0.85f

            // Faceted geometric diamond + sunburst ribs
            facetPath.moveTo(x, y - unit * 0.42f)
            facetPath.lineTo(x + unit * 0.38f, y)
            facetPath.lineTo(x, y + unit * 0.42f)
            facetPath.lineTo(x - unit * 0.38f, y)
            facetPath.close()

            // Champagne lattice ribs inside diamond
            latticePath.moveTo(x - unit * 0.24f, y - unit * 0.14f)
            latticePath.lineTo(x + unit * 0.24f, y + unit * 0.14f)
            latticePath.moveTo(x + unit * 0.24f, y - unit * 0.14f)
            latticePath.lineTo(x - unit * 0.24f, y + unit * 0.14f)
            latticePath.moveTo(x - unit * 0.38f, y)
            latticePath.lineTo(x + unit * 0.38f, y)
        }
    }

    drawPath(path = facetPath, color = pColor)
    drawPath(path = latticePath, color = sColor, style = trellisStroke)
}

/**
 * Helper for preset profile cover art compositions when no photo is uploaded.
 */
enum class ProfileCoverArtPreset(
    val id: String,
    val labelFr: String,
    val pattern: BravPatternType,
    val topColor: Color,
    val bottomColor: Color,
    val accentPrimary: Color,
    val accentSecondary: Color
) {
    ROYAL_HERITAGE(
        id = "royal_heritage",
        labelFr = "Royal Violet & Or",
        pattern = BravPatternType.HERITAGE_COVER,
        topColor = BravPurpleDeep,
        bottomColor = Color(0xFF7B1FA2),
        accentPrimary = Color(0xFF4A0A8C),
        accentSecondary = BravChampagneGold
    ),
    KENTE_PRESTIGE(
        id = "kente_prestige",
        labelFr = "Kente Prune",
        pattern = BravPatternType.KENTE_DIAMOND,
        topColor = BravPurplePrimary,
        bottomColor = BravPurpleDeep,
        accentPrimary = BravWhite,
        accentSecondary = BravChampagneGold
    ),
    SOLAR_IVORY(
        id = "solar_ivory",
        labelFr = "Soleil Adinkra",
        pattern = BravPatternType.SOLAR_ADINKRA,
        topColor = Color(0xFF4B0C8B),
        bottomColor = Color(0xFF2E055B),
        accentPrimary = BravChampagneGold,
        accentSecondary = BravRedSecondary
    ),
    WAX_CONTEMPORARY(
        id = "wax_contemporary",
        labelFr = "Textile Wax",
        pattern = BravPatternType.WAX_TEXTILE,
        topColor = Color(0xFF3E0678),
        bottomColor = Color(0xFF6B149E),
        accentPrimary = BravChampagneGold,
        accentSecondary = BravWhite
    );

    companion object {
        fun fromId(raw: String?): ProfileCoverArtPreset =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: ROYAL_HERITAGE
    }
}

/**
 * Icône vectorielle africaine haut de gamme dédiée à « OFFRES ET ANNONCES » :
 * vitrine stylisée Violet BRAV, auvent Or champagne, mégaphone Rouge BRAV et facettes royales.
 */
@Composable
fun BravOffersLuxuryIcon(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Halo géométrique Or champagne (losange rotatif africain)
        val haloPath = Path().apply {
            moveTo(cx, h * 0.05f)
            lineTo(w * 0.95f, cy)
            lineTo(cx, h * 0.95f)
            lineTo(w * 0.05f, cy)
            close()
        }
        drawPath(
            path = haloPath,
            color = BravChampagneGold.copy(alpha = 0.22f)
        )
        drawPath(
            path = haloPath,
            color = BravChampagneGold.copy(alpha = 0.72f),
            style = Stroke(width = w * 0.03f, join = StrokeJoin.Round)
        )

        // Corps de la vitrine Violet BRAV
        val storeBody = Path().apply {
            moveTo(w * 0.20f, h * 0.42f)
            lineTo(w * 0.80f, h * 0.42f)
            lineTo(w * 0.77f, h * 0.82f)
            lineTo(w * 0.23f, h * 0.82f)
            close()
        }
        drawPath(path = storeBody, color = BravPurplePrimary)

        // Auvent Kente / Royal (blanc & or)
        val awningPath = Path().apply {
            moveTo(w * 0.16f, h * 0.42f)
            lineTo(w * 0.24f, h * 0.23f)
            lineTo(w * 0.76f, h * 0.23f)
            lineTo(w * 0.84f, h * 0.42f)
            close()
        }
        drawPath(path = awningPath, color = BravChampagneGold)
        for (i in 1..3) {
            val sx = w * (0.24f + i * 0.13f)
            drawLine(
                color = BravPurpleDeep,
                start = Offset(sx, h * 0.24f),
                end = Offset(sx - w * 0.02f, h * 0.41f),
                strokeWidth = w * 0.032f,
                cap = StrokeCap.Round
            )
        }

        // Vitrine intérieure blanche
        drawRect(
            color = BravWhite.copy(alpha = 0.95f),
            topLeft = Offset(w * 0.30f, h * 0.50f),
            size = Size(w * 0.25f, h * 0.20f)
        )

        // Badge mégaphone / annonce Rouge BRAV en haut à droite
        val badgeCenter = Offset(w * 0.74f, h * 0.64f)
        drawCircle(
            color = BravRedSecondary,
            radius = w * 0.17f,
            center = badgeCenter
        )
        drawCircle(
            color = BravWhite,
            radius = w * 0.17f,
            center = badgeCenter,
            style = Stroke(width = w * 0.028f)
        )
        // Pavillon mégaphone blanc
        val megaPath = Path().apply {
            moveTo(badgeCenter.x - w * 0.07f, badgeCenter.y - w * 0.02f)
            lineTo(badgeCenter.x + w * 0.05f, badgeCenter.y - w * 0.07f)
            lineTo(badgeCenter.x + w * 0.05f, badgeCenter.y + w * 0.07f)
            lineTo(badgeCenter.x - w * 0.07f, badgeCenter.y + w * 0.02f)
            close()
        }
        drawPath(path = megaPath, color = BravWhite)
    }
}
