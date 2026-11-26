package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BravReactionType

internal enum class ActusGlyphKind {
    APPROVE_THUMB,
    APPRECIATE_HEART,
    INSIGHT_SPARK,
    SUPPORT_HANDS,
    REMARKABLE_CREST,
    SMILE_SERENE,
    WARM_SMILE,
    THOUGHTFUL,
    ASTONISHED,
    CHECK_VERIFIED,
    STAR_BOOKMARK,
    LIGHTBULB_IDEA,
    TROPHY_ACHIEVEMENT,
    TARGET_FOCUS,
    TREND_GROWTH,
    SHIELD_TRUST,
    CLOCK_TIMELINE,
    LOCATION_PIN,
    PEN_EDITORIAL,
    CHAT_DIALOGUE,
    GLOBE_WORLD,
    SPARKLE_CELEBRATE,
    MUSIC_NOTE,
    CAMERA_FRAME
}

internal enum class ActusExpressionCategory(val title: String) {
    ESSENTIALS("Essentiels"),
    APPROVAL("Approbation"),
    EXCHANGE("Échanges"),
    IDEAS("Idées & Pro"),
    NUANCES("Nuances")
}

internal data class ActusExpressionItem(
    val id: String,
    val label: String,
    val insertToken: String,
    val keywords: String,
    val category: ActusExpressionCategory,
    val glyph: ActusGlyphKind,
    val accentTint: Color
)

internal object ActusExpressionCatalog {
    val items: List<ActusExpressionItem> = listOf(
        // Essentiels
        ActusExpressionItem("approuver", "Approuvé", "👍", "approuve oui accord valide", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.APPROVE_THUMB, Color(0xFF4F46E5)),
        ActusExpressionItem("apprecier", "Apprécié", "❤️", "coeur amour apprecier bravo", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.APPRECIATE_HEART, Color(0xFFE11D48)),
        ActusExpressionItem("pertinent", "Pertinent", "✨", "pertinent eclat brillant top", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.INSIGHT_SPARK, Color(0xFFD97706)),
        ActusExpressionItem("soutien", "Soutien", "🤝", "soutien merci respect accord", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.SUPPORT_HANDS, Color(0xFF059669)),
        ActusExpressionItem("remarquable", "Remarquable", "🏆", "remarquable excellence victoire succes", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.REMARKABLE_CREST, Color(0xFF570CA2)),
        ActusExpressionItem("sourire", "Serein", "😊", "sourire serein content joie", ActusExpressionCategory.ESSENTIALS, ActusGlyphKind.SMILE_SERENE, Color(0xFFD97706)),

        // Approbation & Validation
        ActusExpressionItem("valide", "Confirmé", "✅", "confirme valide coche ok", ActusExpressionCategory.APPROVAL, ActusGlyphKind.CHECK_VERIFIED, Color(0xFF059669)),
        ActusExpressionItem("trophee", "Succès", "🥇", "trophee medaille succes reussite", ActusExpressionCategory.APPROVAL, ActusGlyphKind.TROPHY_ACHIEVEMENT, Color(0xFFB45309)),
        ActusExpressionItem("etoile", "Référence", "⭐", "etoile favori reference qualite", ActusExpressionCategory.APPROVAL, ActusGlyphKind.STAR_BOOKMARK, Color(0xFFD97706)),
        ActusExpressionItem("celebration", "Célébration", "🥂", "celebration fete reussite", ActusExpressionCategory.APPROVAL, ActusGlyphKind.SPARKLE_CELEBRATE, Color(0xFF570CA2)),

        // Échanges & Ton
        ActusExpressionItem("chaleureux", "Chaleureux", "😌", "chaleureux calme bienveillant", ActusExpressionCategory.EXCHANGE, ActusGlyphKind.WARM_SMILE, Color(0xFF0D9488)),
        ActusExpressionItem("dialogue", "À débattre", "💬", "dialogue discussion avis echange", ActusExpressionCategory.EXCHANGE, ActusGlyphKind.CHAT_DIALOGUE, Color(0xFF2563EB)),
        ActusExpressionItem("reflexion", "Réflexion", "🤔", "reflexion analyse questionnement", ActusExpressionCategory.EXCHANGE, ActusGlyphKind.THOUGHTFUL, Color(0xFF475569)),
        ActusExpressionItem("etonnement", "Marquant", "😮", "marquant surprise etonnement", ActusExpressionCategory.EXCHANGE, ActusGlyphKind.ASTONISHED, Color(0xFF7C3AED)),

        // Idées & Pro
        ActusExpressionItem("idee", "Idée clé", "💡", "idee cle innovation intuition", ActusExpressionCategory.IDEAS, ActusGlyphKind.LIGHTBULB_IDEA, Color(0xFFD97706)),
        ActusExpressionItem("objectif", "Objectif", "🎯", "objectif cible precision cap", ActusExpressionCategory.IDEAS, ActusGlyphKind.TARGET_FOCUS, Color(0xFFE11D48)),
        ActusExpressionItem("croissance", "Croissance", "📈", "croissance evolution bilan hausse", ActusExpressionCategory.IDEAS, ActusGlyphKind.TREND_GROWTH, Color(0xFF059669)),
        ActusExpressionItem("redaction", "Édito", "✍️", "edition article redaction note", ActusExpressionCategory.IDEAS, ActusGlyphKind.PEN_EDITORIAL, Color(0xFF334155)),
        ActusExpressionItem("fiable", "Sécurisé", "🛡️", "securite fiable protection confiance", ActusExpressionCategory.IDEAS, ActusGlyphKind.SHIELD_TRUST, Color(0xFF1D4ED8)),

        // Nuances & Contexte
        ActusExpressionItem("temps", "Actualité", "⏳", "temps delai bientot calendrier", ActusExpressionCategory.NUANCES, ActusGlyphKind.CLOCK_TIMELINE, Color(0xFF475569)),
        ActusExpressionItem("lieu", "Sur place", "📍", "lieu ville endroit adresse", ActusExpressionCategory.NUANCES, ActusGlyphKind.LOCATION_PIN, Color(0xFFDC2626)),
        ActusExpressionItem("monde", "Ouverture", "🌍", "monde global international reseau", ActusExpressionCategory.NUANCES, ActusGlyphKind.GLOBE_WORLD, Color(0xFF0284C7)),
        ActusExpressionItem("image", "Reportage", "📸", "photo reportage visuel studio", ActusExpressionCategory.NUANCES, ActusGlyphKind.CAMERA_FRAME, Color(0xFF4F46E5)),
        ActusExpressionItem("audio", "Son", "🎶", "musique son podcast culture", ActusExpressionCategory.NUANCES, ActusGlyphKind.MUSIC_NOTE, Color(0xFF7C3AED))
    )

    fun glyphForReaction(type: BravReactionType): Pair<ActusGlyphKind, Color> = when (type) {
        BravReactionType.THUMBS_UP_GOLD -> ActusGlyphKind.APPROVE_THUMB to Color(0xFF4F46E5)
        BravReactionType.HEART_PULSE -> ActusGlyphKind.APPRECIATE_HEART to Color(0xFFE11D48)
        BravReactionType.STAR_HOLO -> ActusGlyphKind.INSIGHT_SPARK to Color(0xFFD97706)
        BravReactionType.PRAY_GRATEFUL -> ActusGlyphKind.SUPPORT_HANDS to Color(0xFF059669)
        BravReactionType.FIRE_FLAME -> ActusGlyphKind.REMARKABLE_CREST to Color(0xFF570CA2)
        else -> ActusGlyphKind.INSIGHT_SPARK to Color(0xFF570CA2)
    }
}

@Composable
internal fun ActusVectorReactionGlyph(
    reaction: BravReactionType,
    size: Dp = 18.dp,
    tintOverride: Color? = null,
    modifier: Modifier = Modifier
) {
    val (glyph, defaultTint) = ActusExpressionCatalog.glyphForReaction(reaction)
    ActusVectorGlyph(
        kind = glyph,
        tint = tintOverride ?: defaultTint,
        size = size,
        modifier = modifier
    )
}

@Composable
internal fun ActusVectorGlyph(
    kind: ActusGlyphKind,
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = (w * 0.085f).coerceAtLeast(1.4f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val softFill = tint.copy(alpha = 0.16f)

        when (kind) {
            ActusGlyphKind.APPROVE_THUMB -> {
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.48f)
                    lineTo(w * 0.44f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.18f)
                    cubicTo(w * 0.56f, h * 0.16f, w * 0.62f, h * 0.22f, w * 0.60f, h * 0.34f)
                    lineTo(w * 0.58f, h * 0.42f)
                    lineTo(w * 0.80f, h * 0.42f)
                    cubicTo(w * 0.88f, h * 0.42f, w * 0.89f, h * 0.52f, w * 0.85f, h * 0.64f)
                    lineTo(w * 0.80f, h * 0.80f)
                    cubicTo(w * 0.78f, h * 0.85f, w * 0.72f, h * 0.86f, w * 0.66f, h * 0.86f)
                    lineTo(w * 0.36f, h * 0.86f)
                    lineTo(w * 0.36f, h * 0.48f)
                    close()
                }
                drawPath(p, color = softFill, style = Fill)
                drawPath(p, color = tint, style = stroke)
                drawRoundRect(
                    color = softFill,
                    topLeft = Offset(w * 0.14f, h * 0.48f),
                    size = Size(w * 0.16f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.48f),
                    size = Size(w * 0.16f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                    style = stroke
                )
            }
            ActusGlyphKind.APPRECIATE_HEART -> {
                val p = heartPath(w, h)
                drawPath(p, color = softFill, style = Fill)
                drawPath(p, color = tint, style = stroke)
            }
            ActusGlyphKind.INSIGHT_SPARK -> {
                val main = fourPointStarPath(w * 0.48f, h * 0.52f, w * 0.34f, w * 0.11f)
                drawPath(main, color = softFill, style = Fill)
                drawPath(main, color = tint, style = stroke)
                val mini = fourPointStarPath(w * 0.78f, h * 0.24f, w * 0.13f, w * 0.045f)
                drawPath(mini, color = tint, style = Fill)
            }
            ActusGlyphKind.SUPPORT_HANDS -> {
                // Clean twin supportive pillars / handshake clasp
                val left = Path().apply {
                    moveTo(w * 0.15f, h * 0.56f)
                    lineTo(w * 0.40f, h * 0.32f)
                    lineTo(w * 0.56f, h * 0.48f)
                    lineTo(w * 0.38f, h * 0.76f)
                    close()
                }
                val right = Path().apply {
                    moveTo(w * 0.85f, h * 0.56f)
                    lineTo(w * 0.60f, h * 0.32f)
                    lineTo(w * 0.44f, h * 0.48f)
                    lineTo(w * 0.62f, h * 0.76f)
                    close()
                }
                drawPath(left, color = softFill, style = Fill)
                drawPath(right, color = softFill, style = Fill)
                drawPath(left, color = tint, style = stroke)
                drawPath(right, color = tint, style = stroke)
            }
            ActusGlyphKind.REMARKABLE_CREST -> {
                val cup = Path().apply {
                    moveTo(w * 0.26f, h * 0.20f)
                    lineTo(w * 0.74f, h * 0.20f)
                    lineTo(w * 0.68f, h * 0.52f)
                    cubicTo(w * 0.64f, h * 0.66f, w * 0.36f, h * 0.66f, w * 0.32f, h * 0.52f)
                    close()
                }
                drawPath(cup, color = softFill, style = Fill)
                drawPath(cup, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.63f), Offset(w * 0.50f, h * 0.82f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.82f), Offset(w * 0.68f, h * 0.82f), strokeWidth, StrokeCap.Round)
            }
            ActusGlyphKind.SMILE_SERENE -> {
                drawFaceFrame(w, h, tint, softFill, stroke)
                drawCircle(tint, w * 0.045f, Offset(w * 0.38f, h * 0.42f))
                drawCircle(tint, w * 0.045f, Offset(w * 0.62f, h * 0.42f))
                val smile = Path().apply {
                    moveTo(w * 0.35f, h * 0.58f)
                    quadraticTo(w * 0.50f, h * 0.72f, w * 0.65f, h * 0.58f)
                }
                drawPath(smile, color = tint, style = stroke)
            }
            ActusGlyphKind.WARM_SMILE -> {
                drawFaceFrame(w, h, tint, softFill, stroke)
                val leftEye = Path().apply {
                    moveTo(w * 0.32f, h * 0.44f)
                    quadraticTo(w * 0.38f, h * 0.38f, w * 0.44f, h * 0.44f)
                }
                val rightEye = Path().apply {
                    moveTo(w * 0.56f, h * 0.44f)
                    quadraticTo(w * 0.62f, h * 0.38f, w * 0.68f, h * 0.44f)
                }
                drawPath(leftEye, color = tint, style = stroke)
                drawPath(rightEye, color = tint, style = stroke)
                val smile = Path().apply {
                    moveTo(w * 0.36f, h * 0.60f)
                    quadraticTo(w * 0.50f, h * 0.70f, w * 0.64f, h * 0.60f)
                }
                drawPath(smile, color = tint, style = stroke)
            }
            ActusGlyphKind.THOUGHTFUL -> {
                drawFaceFrame(w, h, tint, softFill, stroke)
                drawCircle(tint, w * 0.042f, Offset(w * 0.38f, h * 0.43f))
                drawCircle(tint, w * 0.042f, Offset(w * 0.62f, h * 0.40f))
                drawLine(tint, Offset(w * 0.38f, h * 0.63f), Offset(w * 0.62f, h * 0.60f), strokeWidth, StrokeCap.Round)
            }
            ActusGlyphKind.ASTONISHED -> {
                drawFaceFrame(w, h, tint, softFill, stroke)
                drawCircle(tint, w * 0.045f, Offset(w * 0.38f, h * 0.41f))
                drawCircle(tint, w * 0.045f, Offset(w * 0.62f, h * 0.41f))
                drawCircle(tint, w * 0.08f, Offset(w * 0.50f, h * 0.63f), style = stroke)
            }
            ActusGlyphKind.CHECK_VERIFIED -> {
                drawCircle(softFill, w * 0.38f, Offset(w * 0.50f, h * 0.50f))
                drawCircle(tint, w * 0.38f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.33f, h * 0.51f)
                    lineTo(w * 0.45f, h * 0.63f)
                    lineTo(w * 0.68f, h * 0.38f)
                }
                drawPath(check, color = tint, style = stroke)
            }
            ActusGlyphKind.STAR_BOOKMARK -> {
                val star = fivePointStarPath(w * 0.50f, h * 0.52f, w * 0.38f, w * 0.17f)
                drawPath(star, color = softFill, style = Fill)
                drawPath(star, color = tint, style = stroke)
            }
            ActusGlyphKind.LIGHTBULB_IDEA -> {
                val bulb = Path().apply {
                    moveTo(w * 0.37f, h * 0.64f)
                    cubicTo(w * 0.24f, h * 0.52f, w * 0.24f, h * 0.22f, w * 0.50f, h * 0.18f)
                    cubicTo(w * 0.76f, h * 0.22f, w * 0.76f, h * 0.52f, w * 0.63f, h * 0.64f)
                    close()
                }
                drawPath(bulb, color = softFill, style = Fill)
                drawPath(bulb, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.40f, h * 0.74f), Offset(w * 0.60f, h * 0.74f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.43f, h * 0.84f), Offset(w * 0.57f, h * 0.84f), strokeWidth, StrokeCap.Round)
            }
            ActusGlyphKind.TROPHY_ACHIEVEMENT -> {
                drawCircle(softFill, w * 0.30f, Offset(w * 0.50f, h * 0.38f))
                drawCircle(tint, w * 0.30f, Offset(w * 0.50f, h * 0.38f), style = stroke)
                val ribbon = Path().apply {
                    moveTo(w * 0.38f, h * 0.64f)
                    lineTo(w * 0.34f, h * 0.88f)
                    lineTo(w * 0.50f, h * 0.78f)
                    lineTo(w * 0.66f, h * 0.88f)
                    lineTo(w * 0.62f, h * 0.64f)
                }
                drawPath(ribbon, color = tint, style = stroke)
            }
            ActusGlyphKind.TARGET_FOCUS -> {
                drawCircle(softFill, w * 0.38f, Offset(w * 0.50f, h * 0.50f))
                drawCircle(tint, w * 0.38f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, w * 0.22f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, w * 0.08f, Offset(w * 0.50f, h * 0.50f))
            }
            ActusGlyphKind.TREND_GROWTH -> {
                val chart = Path().apply {
                    moveTo(w * 0.16f, h * 0.72f)
                    lineTo(w * 0.40f, h * 0.48f)
                    lineTo(w * 0.56f, h * 0.60f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(chart, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.66f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.82f, h * 0.28f), Offset(w * 0.82f, h * 0.44f), strokeWidth, StrokeCap.Round)
            }
            ActusGlyphKind.SHIELD_TRUST -> {
                val shield = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.82f, h * 0.26f)
                    lineTo(w * 0.80f, h * 0.56f)
                    cubicTo(w * 0.76f, h * 0.74f, w * 0.50f, h * 0.86f, w * 0.50f, h * 0.86f)
                    cubicTo(w * 0.50f, h * 0.86f, w * 0.24f, h * 0.74f, w * 0.20f, h * 0.56f)
                    lineTo(w * 0.18f, h * 0.26f)
                    close()
                }
                drawPath(shield, color = softFill, style = Fill)
                drawPath(shield, color = tint, style = stroke)
            }
            ActusGlyphKind.CLOCK_TIMELINE -> {
                drawCircle(softFill, w * 0.37f, Offset(w * 0.50f, h * 0.50f))
                drawCircle(tint, w * 0.37f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.30f), Offset(w * 0.50f, h * 0.52f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.52f), Offset(w * 0.65f, h * 0.60f), strokeWidth, StrokeCap.Round)
            }
            ActusGlyphKind.LOCATION_PIN -> {
                val pin = Path().apply {
                    moveTo(w * 0.50f, h * 0.86f)
                    cubicTo(w * 0.24f, h * 0.60f, w * 0.24f, h * 0.34f, w * 0.32f, h * 0.24f)
                    cubicTo(w * 0.42f, h * 0.14f, w * 0.58f, h * 0.14f, w * 0.68f, h * 0.24f)
                    cubicTo(w * 0.76f, h * 0.34f, w * 0.76f, h * 0.60f, w * 0.50f, h * 0.86f)
                    close()
                }
                drawPath(pin, color = softFill, style = Fill)
                drawPath(pin, color = tint, style = stroke)
                drawCircle(tint, w * 0.09f, Offset(w * 0.50f, h * 0.42f), style = stroke)
            }
            ActusGlyphKind.PEN_EDITORIAL -> {
                val nib = Path().apply {
                    moveTo(w * 0.22f, h * 0.80f)
                    lineTo(w * 0.30f, h * 0.56f)
                    lineTo(w * 0.68f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.32f)
                    lineTo(w * 0.44f, h * 0.70f)
                    close()
                }
                drawPath(nib, color = softFill, style = Fill)
                drawPath(nib, color = tint, style = stroke)
            }
            ActusGlyphKind.CHAT_DIALOGUE -> {
                val bubble = Path().apply {
                    moveTo(w * 0.20f, h * 0.24f)
                    lineTo(w * 0.80f, h * 0.24f)
                    quadraticTo(w * 0.86f, h * 0.24f, w * 0.86f, h * 0.32f)
                    lineTo(w * 0.86f, h * 0.62f)
                    quadraticTo(w * 0.86f, h * 0.70f, w * 0.78f, h * 0.70f)
                    lineTo(w * 0.42f, h * 0.70f)
                    lineTo(w * 0.24f, h * 0.84f)
                    lineTo(w * 0.26f, h * 0.70f)
                    lineTo(w * 0.20f, h * 0.70f)
                    quadraticTo(w * 0.14f, h * 0.70f, w * 0.14f, h * 0.62f)
                    lineTo(w * 0.14f, h * 0.32f)
                    quadraticTo(w * 0.14f, h * 0.24f, w * 0.20f, h * 0.24f)
                    close()
                }
                drawPath(bubble, color = softFill, style = Fill)
                drawPath(bubble, color = tint, style = stroke)
            }
            ActusGlyphKind.GLOBE_WORLD -> {
                drawCircle(softFill, w * 0.37f, Offset(w * 0.50f, h * 0.50f))
                drawCircle(tint, w * 0.37f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), strokeWidth)
                drawOval(tint, Offset(w * 0.34f, h * 0.13f), Size(w * 0.32f, h * 0.74f), style = stroke)
            }
            ActusGlyphKind.SPARKLE_CELEBRATE -> {
                val main = fourPointStarPath(w * 0.50f, h * 0.50f, w * 0.36f, w * 0.10f)
                drawPath(main, color = softFill, style = Fill)
                drawPath(main, color = tint, style = stroke)
                drawCircle(tint, w * 0.05f, Offset(w * 0.22f, h * 0.24f))
                drawCircle(tint, w * 0.05f, Offset(w * 0.78f, h * 0.76f))
            }
            ActusGlyphKind.MUSIC_NOTE -> {
                val note = Path().apply {
                    moveTo(w * 0.40f, h * 0.72f)
                    lineTo(w * 0.40f, h * 0.26f)
                    lineTo(w * 0.76f, h * 0.20f)
                    lineTo(w * 0.76f, h * 0.64f)
                }
                drawPath(note, color = tint, style = stroke)
                drawCircle(softFill, w * 0.11f, Offset(w * 0.31f, h * 0.74f))
                drawCircle(tint, w * 0.11f, Offset(w * 0.31f, h * 0.74f), style = stroke)
                drawCircle(softFill, w * 0.11f, Offset(w * 0.67f, h * 0.66f))
                drawCircle(tint, w * 0.11f, Offset(w * 0.67f, h * 0.66f), style = stroke)
            }
            ActusGlyphKind.CAMERA_FRAME -> {
                drawRoundRect(
                    color = softFill,
                    topLeft = Offset(w * 0.14f, h * 0.28f),
                    size = Size(w * 0.72f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.28f),
                    size = Size(w * 0.72f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                drawCircle(tint, w * 0.13f, Offset(w * 0.50f, h * 0.54f), style = stroke)
            }
        }
    }
}

private fun DrawScope.drawFaceFrame(
    w: Float,
    h: Float,
    tint: Color,
    softFill: Color,
    stroke: Stroke
) {
    drawCircle(softFill, w * 0.38f, Offset(w * 0.50f, h * 0.50f))
    drawCircle(tint, w * 0.38f, Offset(w * 0.50f, h * 0.50f), style = stroke)
}

private fun heartPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.82f)
    cubicTo(w * 0.18f, h * 0.62f, w * 0.12f, h * 0.36f, w * 0.26f, h * 0.23f)
    cubicTo(w * 0.38f, h * 0.13f, w * 0.49f, h * 0.24f, w * 0.50f, h * 0.32f)
    cubicTo(w * 0.51f, h * 0.24f, w * 0.62f, h * 0.13f, w * 0.74f, h * 0.23f)
    cubicTo(w * 0.88f, h * 0.36f, w * 0.82f, h * 0.62f, w * 0.50f, h * 0.82f)
    close()
}

private fun fourPointStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path = Path().apply {
    moveTo(cx, cy - outerR)
    lineTo(cx + innerR, cy - innerR)
    lineTo(cx + outerR, cy)
    lineTo(cx + innerR, cy + innerR)
    lineTo(cx, cy + outerR)
    lineTo(cx - innerR, cy + innerR)
    lineTo(cx - outerR, cy)
    lineTo(cx - innerR, cy - innerR)
    close()
}

private fun fivePointStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path = Path().apply {
    val step = Math.PI / 5.0
    var angle = -Math.PI / 2.0
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val x = cx + (Math.cos(angle) * r).toFloat()
        val y = cy + (Math.sin(angle) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
        angle += step
    }
    close()
}

/**
 * Luminous, distraction-free inline Expression Picker integrated smoothly into ACTUS composer and comments.
 * Strictly bright surface (#FFFFFF / #F8F9FB), bespoke vector illustrations, smart search, zero dark backdrop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActusInlineExpressionPanel(
    palette: ActusEditorialPalette,
    onInsertToken: (String) -> Unit,
    onSelectReaction: ((BravReactionType) -> Unit)? = null,
    onOpenFullEmojiPicker: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(ActusExpressionCategory.ESSENTIALS) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredExpressions = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            ActusExpressionCatalog.items.filter { it.category == selectedCategory }
        } else {
            ActusExpressionCatalog.items.filter { item ->
                item.label.lowercase().contains(q) ||
                    item.keywords.contains(q) ||
                    item.insertToken == q
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.hairline),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("actus_expression_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expressions & Nuances",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryInk
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onOpenFullEmojiPicker != null) {
                        TextButton(
                            onClick = onOpenFullEmojiPicker,
                            modifier = Modifier.testTag("actus_open_full_emoji_picker_button")
                        ) {
                            Text(
                                text = "Tous les émojis",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accent
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fermer le panneau d'expressions",
                            tint = palette.secondaryInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (onSelectReaction != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ACTUS_CURATED_REACTIONS.forEach { reaction ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = palette.subtleSurface,
                            border = BorderStroke(0.6.dp, palette.hairline),
                            onClick = { onSelectReaction(reaction) },
                            modifier = Modifier.testTag("actus_panel_reaction_${reaction.key}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                ActusVectorReactionGlyph(reaction = reaction, size = 16.dp)
                                Text(
                                    text = reactionDisplayLabel(reaction),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.primaryInk
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Rechercher une expression…",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryInk
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = palette.secondaryInk,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Effacer", modifier = Modifier.size(15.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.subtleSurface,
                    unfocusedContainerColor = palette.subtleSurface,
                    focusedBorderColor = palette.accent.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("actus_expression_search")
            )

            if (searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActusExpressionCategory.entries.forEach { category ->
                        val active = selectedCategory == category
                        val chipBg by animateColorAsState(
                            targetValue = if (active) palette.accent.copy(alpha = 0.12f) else palette.subtleSurface,
                            animationSpec = tween(140),
                            label = "cat_bg"
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = chipBg,
                            border = if (active) BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.45f)) else null,
                            onClick = { selectedCategory = category },
                            modifier = Modifier.testTag("actus_expression_cat_${category.name.lowercase()}")
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) palette.accent else palette.secondaryInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = palette.hairline)

            if (filteredExpressions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucune expression correspondante",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryInk
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (onSelectReaction != null) 120.dp else 176.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredExpressions.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.subtleSurface,
                                border = BorderStroke(0.6.dp, palette.hairline),
                                onClick = { onInsertToken(item.insertToken) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("actus_expr_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    ActusVectorGlyph(
                                        kind = item.glyph,
                                        tint = item.accentTint,
                                        size = 18.dp
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.primaryInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
