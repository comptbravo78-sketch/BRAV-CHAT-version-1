package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class BravStickerCategory(val id: String, val label: String) {
    IVORIAN_CULTURE("culture_ivoirienne", "Culture ivoirienne"),
    AFRICAN_EXPRESSIONS("expressions_africaines", "Expressions africaines"),
    EMOTIONS("emotions", "Émotions"),
    LOVE("amour", "Amour"),
    FRIENDSHIP("amitie", "Amitié"),
    CONGRATS("felicitations", "Félicitations"),
    HUMOR("humour", "Humour"),
    GREETINGS("salutations", "Salutations"),
    MOTIVATION("motivation", "Motivation"),
    REACTIONS("reactions", "Réactions"),
    BIRTHDAYS("anniversaires", "Anniversaires")
}

enum class BravStickerAnimation {
    PULSE,
    FLOAT,
    SHIMMER
}

enum class BravStickerMotif {
    KENTE_CROWN,
    SUN_DISC,
    DIAMOND_RIBBON,
    CRIMSON_HEART,
    BOGOLAN_CREST,
    CONFETTI_STAR
}

data class BravStickerPack(
    val id: String,
    val title: String,
    val description: String,
    val badgeColor: Color
)

data class BravSticker(
    val id: String,
    val packId: String,
    val category: BravStickerCategory,
    val label: String,
    val subtitle: String,
    val emblem: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val motif: BravStickerMotif,
    val animation: BravStickerAnimation,
    val keywords: String
)

object BravStickerCatalog {
    val packs: List<BravStickerPack> = listOf(
        BravStickerPack(
            id = "babi_nouchi",
            title = "Abidjan & Expressions Ivoiriennes",
            description = "Akwaba, C'est zo, Yako, Gbra, Babi est doux",
            badgeColor = Color(0xFFD97706)
        ),
        BravStickerPack(
            id = "afrique_royale",
            title = "Afrique Royale & Prestige",
            description = "Ubuntu, Teranga, Jambo, Fierté d'Afrique",
            badgeColor = BravPurplePrimary
        ),
        BravStickerPack(
            id = "emotions_vibes",
            title = "Émotions & Réactions",
            description = "Joie, humour, surprise et réactions rapides",
            badgeColor = BravRedSecondary
        ),
        BravStickerPack(
            id = "amour_amitie",
            title = "Amour & Amitié",
            description = "Tendresse, fraternité et messages du cœur",
            badgeColor = Color(0xFFE11D48)
        ),
        BravStickerPack(
            id = "celebrations_brav",
            title = "Fêtes & Anniversaires",
            description = "Félicitations, anniversaires et succès",
            badgeColor = Color(0xFFB89242)
        ),
        BravStickerPack(
            id = "motivation_reactions",
            title = "Force & Motivation",
            description = "Courage, salutations et énergie positive",
            badgeColor = Color(0xFF0E7490)
        )
    )

    val stickers: List<BravSticker> = listOf(
        // Culture & Expressions Ivoiriennes
        BravSticker("st_akwaba", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "AKWABA !", "Bienvenue royale", "👑", Color(0xFF570CA2), Color(0xFFD4AF37), BravStickerMotif.KENTE_CROWN, BravStickerAnimation.SHIMMER, "akwaba bienvenue cote d ivoire abidjan"),
        BravSticker("st_cest_zo", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "C'EST ZO !", "Magnifique & chic", "✨", Color(0xFF7C3AED), Color(0xFFEAB308), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.PULSE, "c est zo magnifique stylé chic abidjan"),
        BravSticker("st_yako", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "YAKO !", "Courage & soutien", "🙏", Color(0xFF3E0678), Color(0xFFC6A66B), BravStickerMotif.BOGOLAN_CREST, BravStickerAnimation.FLOAT, "yako soutien reconfort courage desole"),
        BravSticker("st_on_est_ensemble", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "ON EST ENSEMBLE !", "Fraternité unie", "🤝", Color(0xFF570CA2), Color(0xFF16A34A), BravStickerMotif.SUN_DISC, BravStickerAnimation.PULSE, "on est ensemble fraternite solidarite"),
        BravSticker("st_gbra", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "GBRA !", "Puissance totale", "🔥", Color(0xFFEA0D1B), Color(0xFFD4AF37), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.PULSE, "gbra fort puissant top bravo"),
        BravSticker("st_babi_doux", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "BABI EST DOUX", "Bonne ambiance", "🌴", Color(0xFF0E7490), Color(0xFFD4AF37), BravStickerMotif.SUN_DISC, BravStickerAnimation.FLOAT, "babi abidjan doux ambiance"),
        BravSticker("st_ca_va_aller", "babi_nouchi", BravStickerCategory.IVORIAN_CULTURE, "ÇA VA ALLER !", "Confiance & force", "☀️", Color(0xFFB45309), Color(0xFFFDE047), BravStickerMotif.SUN_DISC, BravStickerAnimation.SHIMMER, "ca va aller espoir courage"),

        // Expressions africaines
        BravSticker("st_ubuntu", "afrique_royale", BravStickerCategory.AFRICAN_EXPRESSIONS, "UBUNTU", "Je suis car nous sommes", "🌍", Color(0xFF3E0678), Color(0xFFC6A66B), BravStickerMotif.SUN_DISC, BravStickerAnimation.SHIMMER, "ubuntu afrique humanite solidarite"),
        BravSticker("st_teranga", "afrique_royale", BravStickerCategory.AFRICAN_EXPRESSIONS, "TERANGA !", "Hospitalité d'or", "🏺", Color(0xFF9A3412), Color(0xFFD4AF37), BravStickerMotif.BOGOLAN_CREST, BravStickerAnimation.FLOAT, "teranga senegal hospitalite accueil"),
        BravSticker("st_jambo", "afrique_royale", BravStickerCategory.AFRICAN_EXPRESSIONS, "JAMBO !", "Salut radieux", "🌞", Color(0xFF6D28D9), Color(0xFFF59E0B), BravStickerMotif.SUN_DISC, BravStickerAnimation.PULSE, "jambo bonjour swahili salut"),
        BravSticker("st_fierte_afrique", "afrique_royale", BravStickerCategory.AFRICAN_EXPRESSIONS, "FIERTÉ AFRIQUE", "Excellence continentale", "🦁", Color(0xFF570CA2), Color(0xFFC6A66B), BravStickerMotif.KENTE_CROWN, BravStickerAnimation.SHIMMER, "fierte afrique lion excellence"),

        // Émotions
        BravSticker("st_joie_pure", "emotions_vibes", BravStickerCategory.EMOTIONS, "JOIE PURE", "Rayonnant", "🤩", Color(0xFF7C3AED), Color(0xFFFACC15), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.PULSE, "joie heureux sourire content"),
        BravSticker("st_fou_rire", "emotions_vibes", BravStickerCategory.EMOTIONS, "TROP DRÔLE !", "Mort de rire", "🤣", Color(0xFFEA0D1B), Color(0xFFF59E0B), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.FLOAT, "drole rire mdr haha"),
        BravSticker("st_waouh", "emotions_vibes", BravStickerCategory.EMOTIONS, "WAOUH !", "Impressionné", "🤯", Color(0xFF4C1D95), Color(0xFF38BDF8), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.SHIMMER, "waouh choc surpris wow"),

        // Amour
        BravSticker("st_mon_coeur", "amour_amitie", BravStickerCategory.LOVE, "MON CŒUR", "Tendresse infinie", "❤️", Color(0xFFBE123C), Color(0xFFFDA4AF), BravStickerMotif.CRIMSON_HEART, BravStickerAnimation.PULSE, "amour coeur love je t aime"),
        BravSticker("st_reine_d_or", "amour_amitie", BravStickerCategory.LOVE, "PRÉCIEUX(SE)", "Toujours dans mon cœur", "💎", Color(0xFF701A75), Color(0xFFD4AF37), BravStickerMotif.CRIMSON_HEART, BravStickerAnimation.SHIMMER, "precieux amour diamant coeur"),

        // Amitié
        BravSticker("st_mon_frere", "amour_amitie", BravStickerCategory.FRIENDSHIP, "VRAI AMI", "Fidélité & lien", "🤜", Color(0xFF570CA2), Color(0xFFC6A66B), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.FLOAT, "ami frere soeur amitie fidele"),
        BravSticker("st_merci_beaucoup", "amour_amitie", BravStickerCategory.FRIENDSHIP, "MERCI !", "Gratitude sincère", "💐", Color(0xFF0F766E), Color(0xFFD4AF37), BravStickerMotif.SUN_DISC, BravStickerAnimation.PULSE, "merci gratitude reconnaissance"),

        // Félicitations
        BravSticker("st_bravo", "celebrations_brav", BravStickerCategory.CONGRATS, "FÉLICITATIONS !", "Victoire méritée", "🏆", Color(0xFF570CA2), Color(0xFFD4AF37), BravStickerMotif.KENTE_CROWN, BravStickerAnimation.SHIMMER, "felicitations bravo succes trophee"),
        BravSticker("st_chapeau", "celebrations_brav", BravStickerCategory.CONGRATS, "CHAPEAU !", "Grand respect", "🎩", Color(0xFF3E0678), Color(0xFFEAB308), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.FLOAT, "chapeau respect bravo"),

        // Humour
        BravSticker("st_en_tout_cas", "emotions_vibes", BravStickerCategory.HUMOR, "EN TOUT CAS !", "On observe seulement", "😎", Color(0xFF1E293B), Color(0xFFD4AF37), BravStickerMotif.BOGOLAN_CREST, BravStickerAnimation.FLOAT, "en tout cas humour drole lol"),
        BravSticker("st_doucement", "emotions_vibes", BravStickerCategory.HUMOR, "DOUCEMENT !", "Calme le jeu", "🫠", Color(0xFF9333EA), Color(0xFFF472B6), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.PULSE, "doucement calme humour"),

        // Salutations
        BravSticker("st_bonjour_royal", "motivation_reactions", BravStickerCategory.GREETINGS, "BONJOUR !", "Belle journée", "🌅", Color(0xFFD97706), Color(0xFF570CA2), BravStickerMotif.SUN_DISC, BravStickerAnimation.SHIMMER, "bonjour salut matin hello"),
        BravSticker("st_bonsoir_paix", "motivation_reactions", BravStickerCategory.GREETINGS, "BONSOIR !", "Douce soirée", "🌙", Color(0xFF312E81), Color(0xFFC6A66B), BravStickerMotif.BOGOLAN_CREST, BravStickerAnimation.FLOAT, "bonsoir bonne nuit soiree"),

        // Motivation
        BravSticker("st_fonce", "motivation_reactions", BravStickerCategory.MOTIVATION, "FONCE !", "Rien ne t'arrête", "🚀", Color(0xFF570CA2), Color(0xFFEA0D1B), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.PULSE, "fonce motivation courage reussite"),
        BravSticker("st_force_calme", "motivation_reactions", BravStickerCategory.MOTIVATION, "RESTE FORT", "Tu vas réussir", "💪", Color(0xFF0E7490), Color(0xFFD4AF37), BravStickerMotif.KENTE_CROWN, BravStickerAnimation.SHIMMER, "force courage succes motivation"),

        // Réactions
        BravSticker("st_valide", "emotions_vibes", BravStickerCategory.REACTIONS, "VALIDÉ 100%", "Approuvé", "💯", Color(0xFF15803D), Color(0xFFD4AF37), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.PULSE, "valide ok 100 parfait approuve"),
        BravSticker("st_jarrive", "emotions_vibes", BravStickerCategory.REACTIONS, "J'ARRIVE !", "En route", "⚡", Color(0xFF570CA2), Color(0xFFFACC15), BravStickerMotif.DIAMOND_RIBBON, BravStickerAnimation.FLOAT, "j arrive en route vite"),

        // Anniversaires
        BravSticker("st_joyeux_anniv", "celebrations_brav", BravStickerCategory.BIRTHDAYS, "JOYEUX ANNIV !", "Longue vie & bonheur", "🎂", Color(0xFFEA0D1B), Color(0xFFD4AF37), BravStickerMotif.CONFETTI_STAR, BravStickerAnimation.SHIMMER, "joyeux anniversaire birthday fete gateau"),
        BravSticker("st_champagne", "celebrations_brav", BravStickerCategory.BIRTHDAYS, "LONGUE VIE !", "Santé & prospérité", "🥂", Color(0xFF570CA2), Color(0xFFD4AF37), BravStickerMotif.KENTE_CROWN, BravStickerAnimation.PULSE, "sante longue vie anniversaire celebration")
    )

    fun byId(id: String): BravSticker? = stickers.firstOrNull { it.id == id }
}

@Composable
fun BravStickerArtwork(
    sticker: BravSticker,
    size: Dp = 132.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "sticker_anim_${sticker.id}")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sticker_pulse"
    )
    val scaleFactor = if (animated && sticker.animation == BravStickerAnimation.PULSE) {
        0.97f + animProgress * 0.06f
    } else 1f
    val floatOffsetY = if (animated && sticker.animation == BravStickerAnimation.FLOAT) {
        (animProgress - 0.5f) * 6f
    } else 0f
    val shimmerAlpha = if (animated && sticker.animation == BravStickerAnimation.SHIMMER) {
        0.75f + animProgress * 0.25f
    } else 0.92f

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
                translationY = floatOffsetY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Floating luminous halo behind sticker without solid white box
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        sticker.secondaryColor.copy(alpha = 0.28f * shimmerAlpha),
                        sticker.primaryColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.49f
                ),
                radius = w * 0.49f,
                center = Offset(cx, cy)
            )

            // Main sculpted crest card
            val pad = w * 0.08f
            val badgeRectSize = Size(w - pad * 2f, h * 0.72f)
            val badgeTopLeft = Offset(pad, h * 0.14f)
            val corner = CornerRadius(w * 0.18f, w * 0.18f)

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        sticker.primaryColor.copy(alpha = 0.94f),
                        BravPurpleDeep.copy(alpha = 0.92f)
                    )
                ),
                topLeft = badgeTopLeft,
                size = badgeRectSize,
                cornerRadius = corner
            )

            // African geometric motif overlay inside the badge
            when (sticker.motif) {
                BravStickerMotif.KENTE_CROWN, BravStickerMotif.DIAMOND_RIBBON -> {
                    val diamondPath = Path().apply {
                        moveTo(cx, badgeTopLeft.y + 8f)
                        lineTo(w - pad - 10f, cy)
                        lineTo(cx, badgeTopLeft.y + badgeRectSize.height - 8f)
                        lineTo(pad + 10f, cy)
                        close()
                    }
                    drawPath(
                        path = diamondPath,
                        color = sticker.secondaryColor.copy(alpha = 0.24f),
                        style = Stroke(width = 2.5f)
                    )
                }
                BravStickerMotif.SUN_DISC -> {
                    for (i in 0 until 12) {
                        val angle = (i * 30.0) * PI / 180.0
                        val r1 = w * 0.26f
                        val r2 = w * 0.36f
                        drawLine(
                            color = sticker.secondaryColor.copy(alpha = 0.34f * shimmerAlpha),
                            start = Offset(cx + (cos(angle) * r1).toFloat(), cy + (sin(angle) * r1).toFloat()),
                            end = Offset(cx + (cos(angle) * r2).toFloat(), cy + (sin(angle) * r2).toFloat()),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                BravStickerMotif.CRIMSON_HEART -> {
                    drawCircle(
                        color = sticker.secondaryColor.copy(alpha = 0.22f),
                        radius = w * 0.27f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                }
                BravStickerMotif.BOGOLAN_CREST, BravStickerMotif.CONFETTI_STAR -> {
                    val zig = Path().apply {
                        val yBase = badgeTopLeft.y + badgeRectSize.height * 0.22f
                        moveTo(pad + 12f, yBase)
                        lineTo(cx - w * 0.14f, yBase - 8f)
                        lineTo(cx, yBase + 6f)
                        lineTo(cx + w * 0.14f, yBase - 8f)
                        lineTo(w - pad - 12f, yBase)
                    }
                    drawPath(
                        path = zig,
                        color = sticker.secondaryColor.copy(alpha = 0.38f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            // Gold contour
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(BravChampagneGold, sticker.secondaryColor, BravChampagneGold)
                ),
                topLeft = badgeTopLeft,
                size = badgeRectSize,
                cornerRadius = corner,
                style = Stroke(width = 2.6f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = sticker.emblem,
                fontSize = (size.value * 0.24f).sp
            )
            Text(
                text = sticker.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = (size.value * 0.105f).coerceIn(10f, 17f).sp,
                    lineHeight = (size.value * 0.12f).coerceIn(12f, 20f).sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = BravWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (size >= 110.dp && sticker.subtitle.isNotBlank()) {
                Text(
                    text = sticker.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = BravChampagneGold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
