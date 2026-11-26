package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen

/**
 * Compact interactive AI dot ("petit point") displayed inside conversations and message composers.
 * - In conversation rows/headers: indicates & toggles direct AI auto-reply status for that conversation.
 * - In message reply/send bars: generates an immediate context-aware AI response via Cloudflare Workers AI.
 */
@Composable
fun AiReplyDotButton(
    isActive: Boolean = true,
    isLoading: Boolean = false,
    dotSize: Dp = 14.dp,
    touchSize: Dp = 48.dp,
    contentDescriptionText: String = "Assistant IA direct",
    testTagName: String = "",
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "ai_dot_pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isLoading) 1.24f else if (isActive) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLoading) 550 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_dot_scale"
    )

    val activeBrush = Brush.linearGradient(
        colors = listOf(BravPurple, Color(0xFF00BFA5))
    )
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .size(touchSize)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDescriptionText }
            .then(if (testTagName.isNotBlank()) Modifier.testTag(testTagName) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(dotSize + 6.dp),
                strokeWidth = 2.dp,
                color = BravPurple
            )
        } else {
            // Subtle ambient halo when active
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(dotSize + 8.dp)
                        .scale(pulseScale)
                        .background(BravPurple.copy(alpha = 0.16f), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .then(
                        if (isActive) {
                            Modifier.background(activeBrush, CircleShape)
                        } else {
                            Modifier.background(inactiveColor, CircleShape)
                        }
                    )
            )
        }
    }
}

/**
 * Compact pill/card displayed right above a chat or SMS composer when the user taps the AI dot
 * to draft or directly send an intelligent reply powered by Cloudflare Workers AI.
 */
@Composable
fun AiReplySuggestionBar(
    visible: Boolean,
    suggestedText: String,
    modelLabel: String = "",
    onSendDirect: () -> Unit,
    onEditInComposer: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && suggestedText.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val lux = BravTheme.colors
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = lux.modalSurface,
            border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.55f)),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("ai_reply_suggestion_bar")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(
                                    Brush.linearGradient(listOf(BravPurple, StatusOnlineGreen)),
                                    CircleShape
                                )
                        )
                        Text(
                            text = "Réponse IA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BravPurple
                        )
                        if (modelLabel.isNotBlank()) {
                            Text(
                                text = "• ${modelLabel.substringAfterLast('/')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(36.dp).testTag("ai_suggestion_regenerate")
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Régénérer",
                                tint = BravPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).testTag("ai_suggestion_dismiss")
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Fermer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = suggestedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onEditInComposer,
                        modifier = Modifier.testTag("ai_suggestion_edit_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Modifier")
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BravPurple,
                        onClick = onSendDirect,
                        modifier = Modifier.testTag("ai_suggestion_send_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Envoyer",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
