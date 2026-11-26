package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.model.BravReactionCategory
import com.example.model.BravReactionType
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurpleBright
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom animated emotional, futuristic, and comic reaction glyph with Canvas particles,
 * tap bounce, and intensified long-press feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedReactionIcon(
    reaction: BravReactionType,
    size: Dp = 32.dp,
    intensified: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    var justTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(justTriggered) {
        if (justTriggered) {
            delay(550)
            justTriggered = false
        }
    }

    val infinite = rememberInfiniteTransition(label = "reaction_${reaction.key}")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (reaction) {
                    BravReactionType.HEART_PULSE, BravReactionType.AFFECTION_HEARTS -> 900
                    BravReactionType.FIRE_FLAME, BravReactionType.ROCKET_TURBO -> 620
                    BravReactionType.LAUGH_TEARS, BravReactionType.ROFL_DYNAMIC,
                    BravReactionType.CLOWN_MODE, BravReactionType.ZANY_CRAZY -> 700
                    BravReactionType.MIND_BLOWN, BravReactionType.SHOCK_BOUNCE,
                    BravReactionType.CYBER_BOT, BravReactionType.ALIEN_SIGNAL -> 780
                    else -> 1000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_${reaction.key}"
    )

    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_${reaction.key}"
    )

    val triggerScale by animateFloatAsState(
        targetValue = if (justTriggered || intensified) 1.32f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
        label = "triggerScale"
    )

    val auraColor = when (reaction) {
        BravReactionType.CROWN_ELITE, BravReactionType.HUNDRED_PURE, BravReactionType.THUMBS_UP_GOLD -> Color(0xFFD4AF37)
        BravReactionType.DIAMOND_LUX -> Color(0xFF67E8F9)
        BravReactionType.PRAY_GRATEFUL -> Color(0xFFC084FC)
        BravReactionType.CYBER_BOT, BravReactionType.STAR_HOLO -> Color(0xFF22D3EE)
        BravReactionType.ROCKET_TURBO -> Color(0xFF38BDF8)
        BravReactionType.ALIEN_SIGNAL -> Color(0xFF34D399)
        BravReactionType.TECH_GENIUS -> Color(0xFFA855F7)
        BravReactionType.HEART_PULSE, BravReactionType.AFFECTION_HEARTS, BravReactionType.HEART_EYES -> BravRed
        BravReactionType.FIRE_FLAME -> Color(0xFFFF6D00)
        BravReactionType.CRY_EXPRESSIVE -> Color(0xFF29B6F6)
        BravReactionType.MIND_BLOWN, BravReactionType.COOL_BOSS -> BravPurpleBright
        BravReactionType.CLOWN_MODE, BravReactionType.ZANY_CRAZY, BravReactionType.MELTING_LOL -> Color(0xFFF59E0B)
        BravReactionType.LAUGH_TEARS, BravReactionType.ROFL_DYNAMIC, BravReactionType.SHOCK_BOUNCE -> Color(0xFFFFCA28)
        BravReactionType.CLAP_HANDS -> Color(0xFF7E57C2)
    }

    Box(
        modifier = Modifier
            .size(size + 10.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    justTriggered = true
                    onClick()
                },
                onLongClick = {
                    justTriggered = true
                    onLongClick()
                }
            )
            .semantics { contentDescription = reaction.label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.36f
            val boost = if (justTriggered || intensified) 1.35f else 1f

            when (reaction) {
                BravReactionType.CROWN_ELITE, BravReactionType.DIAMOND_LUX, BravReactionType.HUNDRED_PURE,
                BravReactionType.THUMBS_UP_GOLD, BravReactionType.PRAY_GRATEFUL -> {
                    drawCircle(
                        color = auraColor.copy(alpha = 0.30f + pulse * 0.20f),
                        radius = baseRadius * (1.06f + pulse * 0.22f) * boost,
                        center = center,
                        style = Stroke(width = 1.8f * boost)
                    )
                    for (i in 0 until 4) {
                        val a = phase * 0.65f + i * (PI.toFloat() / 2f)
                        val r = baseRadius * 1.18f * boost
                        drawCircle(
                            color = auraColor.copy(alpha = 0.85f),
                            radius = 1.9f * boost,
                            center = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                        )
                    }
                }
                BravReactionType.CYBER_BOT, BravReactionType.ALIEN_SIGNAL, BravReactionType.TECH_GENIUS -> {
                    drawCircle(
                        color = auraColor.copy(alpha = 0.28f + pulse * 0.22f),
                        radius = baseRadius * (1.02f + pulse * 0.25f) * boost,
                        center = center,
                        style = Stroke(width = 1.6f * boost)
                    )
                    for (i in 0 until 4) {
                        val a = phase + i * (PI.toFloat() / 2f)
                        val r = baseRadius * 1.15f * boost
                        drawCircle(
                            color = auraColor.copy(alpha = 0.85f),
                            radius = 2.0f * boost,
                            center = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                        )
                    }
                }
                BravReactionType.ROCKET_TURBO -> {
                    for (i in 0 until 4) {
                        val lift = ((phase + i * 1.4f) % (2f * PI.toFloat())) / (2f * PI.toFloat())
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFF22D3EE) else Color(0xFFFF6D00),
                            alpha = (1f - lift) * 0.8f,
                            radius = (3.2f - lift * 1.8f).coerceAtLeast(1f) * boost,
                            center = Offset(center.x - 4f + i * 2.5f, center.y + lift * baseRadius * 1.2f)
                        )
                    }
                }
                BravReactionType.STAR_HOLO, BravReactionType.COOL_BOSS -> {
                    drawCircle(
                        color = auraColor.copy(alpha = 0.2f + pulse * 0.2f),
                        radius = baseRadius * (0.95f + pulse * 0.38f) * boost,
                        center = center
                    )
                    for (i in 0 until 5) {
                        val a = phase * 0.9f + i * 1.25f
                        val dist = baseRadius * (0.85f + 0.35f * pulse) * boost
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFF22D3EE) else Color(0xFFFDE047),
                            alpha = 0.85f * (1f - pulse * 0.3f),
                            radius = 2.1f * boost,
                            center = Offset(center.x + cos(a) * dist, center.y + sin(a) * dist)
                        )
                    }
                }
                BravReactionType.CLOWN_MODE, BravReactionType.ZANY_CRAZY, BravReactionType.MELTING_LOL -> {
                    val confettiColors = listOf(Color(0xFFEC4899), Color(0xFF22D3EE), Color(0xFFFACC15), Color(0xFF4ADE80))
                    for (i in 0 until 6) {
                        val a = phase + i * 1.05f
                        val dist = baseRadius * (0.85f + pulse * 0.45f) * boost
                        drawCircle(
                            color = confettiColors[i % confettiColors.size].copy(alpha = 0.82f),
                            radius = 2.2f * boost,
                            center = Offset(center.x + cos(a) * dist, center.y + sin(a) * dist)
                        )
                    }
                }
                BravReactionType.HEART_PULSE, BravReactionType.AFFECTION_HEARTS -> {
                    drawCircle(
                        color = auraColor.copy(alpha = 0.22f * (1f - pulse * 0.5f)),
                        radius = baseRadius * (0.9f + pulse * 0.45f) * boost,
                        center = center
                    )
                    for (i in 0 until 3) {
                        val angle = phase + i * 2.1f
                        val r = baseRadius * (0.75f + 0.35f * pulse)
                        drawCircle(
                            color = auraColor.copy(alpha = 0.65f),
                            radius = 2.6f * boost,
                            center = Offset(
                                center.x + cos(angle) * r,
                                center.y - sin(angle).coerceAtLeast(0f) * r - pulse * 6f
                            )
                        )
                    }
                }
                BravReactionType.FIRE_FLAME -> {
                    for (i in 0 until 5) {
                        val xOff = (i - 2) * 5f
                        val yLift = ((phase + i) % (2f * PI.toFloat())) / (2f * PI.toFloat())
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFFFF6D00) else BravRed,
                            alpha = (1f - yLift) * 0.7f,
                            radius = (3.5f - yLift * 2f).coerceAtLeast(1f) * boost,
                            center = Offset(center.x + xOff, center.y + 8f - yLift * baseRadius * 1.6f)
                        )
                    }
                }
                BravReactionType.MIND_BLOWN, BravReactionType.SHOCK_BOUNCE, BravReactionType.CLAP_HANDS -> {
                    val count = 6
                    for (i in 0 until count) {
                        val a = (2f * PI.toFloat() / count) * i + phase * 0.4f
                        val dist = baseRadius * (0.65f + pulse * 0.55f) * boost
                        drawCircle(
                            color = auraColor.copy(alpha = 0.7f * (1f - pulse * 0.4f)),
                            radius = 2.4f * boost,
                            center = Offset(center.x + cos(a) * dist, center.y + sin(a) * dist)
                        )
                    }
                }
                BravReactionType.CRY_EXPRESSIVE -> {
                    val dropProgress = (phase / (2f * PI.toFloat()))
                    drawCircle(
                        color = auraColor.copy(alpha = (1f - dropProgress) * 0.8f),
                        radius = 3f * boost,
                        center = Offset(center.x - 6f, center.y + dropProgress * baseRadius * 1.1f)
                    )
                    drawCircle(
                        color = auraColor.copy(alpha = (1f - dropProgress) * 0.8f),
                        radius = 3f * boost,
                        center = Offset(center.x + 6f, center.y + dropProgress * baseRadius * 1.1f)
                    )
                }
                else -> {
                    drawCircle(
                        color = auraColor.copy(alpha = 0.16f + pulse * 0.14f),
                        radius = baseRadius * (0.85f + pulse * 0.3f) * boost,
                        center = center
                    )
                }
            }
        }

        val dynamicRotation = when (reaction) {
            BravReactionType.ROFL_DYNAMIC -> -22f + pulse * 44f
            BravReactionType.ZANY_CRAZY -> -16f + pulse * 32f
            BravReactionType.CLOWN_MODE -> -12f + pulse * 24f
            BravReactionType.LAUGH_TEARS -> -6f + pulse * 12f
            BravReactionType.CLAP_HANDS -> -8f + pulse * 16f
            BravReactionType.ROCKET_TURBO -> -6f + pulse * 6f
            else -> 0f
        }
        val dynamicTranslationY = when (reaction) {
            BravReactionType.ROCKET_TURBO -> -pulse * 6f
            BravReactionType.MELTING_LOL -> pulse * 4f
            BravReactionType.SHOCK_BOUNCE, BravReactionType.MIND_BLOWN, BravReactionType.ALIEN_SIGNAL -> -pulse * 4f
            BravReactionType.HEART_PULSE, BravReactionType.CYBER_BOT -> -pulse * 2.5f
            else -> 0f
        }
        val dynamicScale = triggerScale * when (reaction) {
            BravReactionType.HEART_PULSE, BravReactionType.FIRE_FLAME, BravReactionType.ROCKET_TURBO -> 0.94f + pulse * 0.18f
            BravReactionType.CLOWN_MODE, BravReactionType.ZANY_CRAZY, BravReactionType.STAR_HOLO -> 0.92f + pulse * 0.2f
            BravReactionType.HEART_EYES -> 0.96f + pulse * 0.14f
            else -> 0.96f + pulse * 0.10f
        }

        Text(
            text = reaction.emoji,
            fontSize = (size.value * 0.68f).sp,
            modifier = Modifier.graphicsLayer {
                scaleX = dynamicScale
                scaleY = dynamicScale
                rotationZ = dynamicRotation
                translationY = dynamicTranslationY
            }
        )
    }
}

@Composable
fun ReactionPickerBar(
    selectedKey: String?,
    onSelect: (BravReactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(BravReactionCategory.ALL) }
    var longPressedKey by remember { mutableStateOf<String?>(null) }
    val filtered = remember(selectedCategory) {
        if (selectedCategory == BravReactionCategory.ALL) BravReactionType.entries
        else BravReactionType.entries.filter { it.category == selectedCategory }
    }

    val lux = BravTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = lux.modalSurface,
        border = BorderStroke(0.9.dp, lux.champagneGold.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(BravReactionCategory.entries) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) BravPurple else lux.translucentSubtleSurface,
                        border = if (isSelected) null else BorderStroke(0.65.dp, lux.violetContour),
                        onClick = { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filtered, key = { it.key }) { reaction ->
                    ReactionPickerItem(
                        reaction = reaction,
                        selected = selectedKey == reaction.key,
                        intensified = longPressedKey == reaction.key,
                        onSelect = { onSelect(reaction) },
                        onLongPress = {
                            longPressedKey = reaction.key
                            onSelect(reaction)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionPickerItem(
    reaction: BravReactionType,
    selected: Boolean,
    intensified: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedReactionIcon(
            reaction = reaction,
            size = 30.dp,
            intensified = intensified,
            onClick = onSelect,
            onLongClick = onLongPress
        )
    }
}

/**
 * Futuristic cyber-glass emoji deck for statuses, reactions & funny comments.
 */
@Composable
fun FuturisticEmojiStudioDock(
    selectedKey: String? = null,
    onEmitReaction: (BravReactionType) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Émotions & Réactions"
) {
    var activeCategory by remember { mutableStateOf(BravReactionCategory.LUXURY) }
    val filtered = remember(activeCategory) {
        if (activeCategory == BravReactionCategory.ALL) BravReactionType.entries
        else BravReactionType.entries.filter { it.category == activeCategory }
    }
    val luxGold = Color(0xFFD4AF37)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(luxGold.copy(alpha = 0.55f), BravPurpleBright.copy(alpha = 0.65f))),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF130F1D)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = luxGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    listOf(
                        BravReactionCategory.LUXURY,
                        BravReactionCategory.CLASSIC,
                        BravReactionCategory.FUNNY,
                        BravReactionCategory.FUTURISTIC,
                        BravReactionCategory.ALL
                    )
                ) { cat ->
                    val active = activeCategory == cat
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) luxGold.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (active) luxGold else Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(999.dp)
                            )
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { activeCategory = cat }
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) luxGold else Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(filtered, key = { it.key }) { reaction ->
                    val isSelected = selectedKey == reaction.key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BravPurple.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f),
                        modifier = Modifier
                            .width(82.dp)
                            .border(
                                1.dp,
                                if (isSelected) luxGold else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onEmitReaction(reaction) }
                            .testTag("futuristic_emoji_${reaction.key}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedReactionIcon(
                                reaction = reaction,
                                size = 32.dp,
                                intensified = isSelected,
                                onClick = { onEmitReaction(reaction) },
                                onLongClick = { onEmitReaction(reaction) }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = reaction.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.90f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class EmojiBurstParticle(
    val id: Long,
    val emoji: String,
    val startXOffsetDp: Float,
    val driftXDp: Float,
    val riseHeightDp: Float,
    val rotationDeg: Float,
    val scale: Float
)

/**
 * Floating burst stream rendered over statuses/viewers whenever a user emits a futuristic/funny emoji.
 */
@Composable
fun FloatingEmojiBurstOverlay(
    burstTrigger: Pair<BravReactionType, Long>?,
    modifier: Modifier = Modifier
) {
    val particles = remember { mutableStateListOf<EmojiBurstParticle>() }

    LaunchedEffect(burstTrigger?.second) {
        val trigger = burstTrigger ?: return@LaunchedEffect
        val baseId = trigger.second * 100L
        val generated = List(9) { idx ->
            EmojiBurstParticle(
                id = baseId + idx,
                emoji = trigger.first.emoji,
                startXOffsetDp = Random.nextInt(-115, 115).toFloat(),
                driftXDp = Random.nextInt(-55, 55).toFloat(),
                riseHeightDp = Random.nextInt(180, 360).toFloat(),
                rotationDeg = Random.nextInt(-32, 32).toFloat(),
                scale = Random.nextFloat() * 0.55f + 0.85f
            )
        }
        particles.addAll(generated)
        delay(1350L)
        particles.removeAll(generated.toSet())
    }

    if (particles.isEmpty()) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        particles.forEach { particle ->
            FloatingParticleItem(particle)
        }
    }
}

@Composable
private fun FloatingParticleItem(particle: EmojiBurstParticle) {
    val progress = remember(particle.id) { Animatable(0f) }
    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
        )
    }
    val p = progress.value
    val yOffset = -(p * particle.riseHeightDp) - 64f
    val xOffset = particle.startXOffsetDp + particle.driftXDp * p
    val alpha = (1f - (p * p)).coerceIn(0f, 1f)
    val scale = particle.scale * (0.75f + 0.45f * sin(p * PI.toFloat()))

    Text(
        text = particle.emoji,
        fontSize = 30.sp,
        modifier = Modifier
            .offset { IntOffset(xOffset.roundToInt(), yOffset.roundToInt()) }
            .alpha(alpha)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = particle.rotationDeg * p
            }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageReactionsRow(
    reactions: Map<String, List<String>>,
    myUid: String,
    onToggleReaction: (BravReactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    FlowRow(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { (key, uids) ->
            if (uids.isEmpty()) return@forEach
            val type = BravReactionType.fromKey(key) ?: return@forEach
            val isMine = myUid in uids
            val lux = BravTheme.colors
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else lux.translucentSubtleSurface,
                border = BorderStroke(0.6.dp, if (isMine) lux.champagneGold.copy(alpha = 0.5f) else lux.violetContour),
                tonalElevation = 0.dp,
                onClick = { onToggleReaction(type) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedReactionIcon(
                        reaction = type,
                        size = 18.dp,
                        onClick = { onToggleReaction(type) }
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = uids.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
