package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite

/**
 * BRAV AFRICAN LUXURY DESIGN SYSTEM
 * Harmonized reusable UI components across the application.
 */

@Composable
fun BravLuxuryHeader(
    title: String = "BRAV CHAT",
    showLogo: Boolean = true,
    pattern: BravPatternType = BravPatternType.KENTE_DIAMOND,
    patternOpacity: Float = BravPatternIntensity.HEADER_TONE,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(lux.headerTop, lux.headerBottom))
            )
            .bravAfricanPattern(
                pattern = pattern,
                primaryColor = BravWhite,
                secondaryColor = lux.champagneGold,
                opacity = patternOpacity,
                scale = 0.9f
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 58.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                }
                if (showLogo) {
                    Surface(
                        shape = CircleShape,
                        color = BravWhite.copy(alpha = 0.94f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(3.dp)) {
                            BravLogoMark(size = 26.dp)
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = lux.headerOnColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions
            )
        }
        if (bottomContent != null) {
            Column(modifier = Modifier.fillMaxWidth(), content = bottomContent)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            lux.champagneGold.copy(alpha = 0.72f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun BravLuxuryCard(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 18.dp,
    isSelected: Boolean = false,
    isError: Boolean = false,
    accentBorder: Boolean = false,
    translucent: Boolean = true,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val lux = BravTheme.colors
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> BravRedSecondary
            isSelected -> MaterialTheme.colorScheme.primary
            accentBorder -> lux.champagneGold.copy(alpha = 0.62f)
            else -> lux.violetContour
        },
        label = "brav_card_border"
    )
    val shape = RoundedCornerShape(shapeRadius)
    val borderStroke = BorderStroke(if (isSelected || isError) 1.25.dp else 0.75.dp, borderColor)
    val resolvedBg = containerColor ?: if (translucent) lux.translucentCardSurface else lux.ivoryCanvas
    val colors = CardDefaults.cardColors(
        containerColor = resolvedBg,
        contentColor = lux.inkPrimary
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = shape,
            colors = colors,
            border = borderStroke,
            elevation = elevation,
            modifier = modifier,
            content = content
        )
    } else {
        Card(
            shape = shape,
            colors = colors,
            border = borderStroke,
            elevation = elevation,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun BravPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = BravPurplePrimary,
    contentColor: Color = BravWhite,
    testTagName: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.42f),
            disabledContentColor = contentColor.copy(alpha = 0.75f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BravSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    accentBorder: Color? = null,
    contentColor: Color? = null,
    testTagName: String? = null
) {
    val lux = BravTheme.colors
    val fg = contentColor ?: if (isError) BravRedSecondary else lux.titlePurple
    val border = accentBorder ?: if (isError) BravRedSecondary.copy(alpha = 0.65f) else lux.violetContour
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.9.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = fg
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BravCircleIconButton(
    icon: ImageVector,
    contentDescriptionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    iconTint: Color? = null,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    testTagName: String? = null
) {
    val lux = BravTheme.colors
    Surface(
        shape = CircleShape,
        color = containerColor ?: lux.translucentSubtleSurface,
        border = BorderStroke(0.75.dp, lux.violetContour),
        modifier = modifier
            .size(size.coerceAtLeast(44.dp))
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDescriptionText }
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescriptionText,
                tint = iconTint ?: lux.inkPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BravSearchBarField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    inputTestTag: String? = null,
    inputModifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.85.dp, lux.violetContour),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = lux.inkSecondary,
                modifier = Modifier.size(19.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = lux.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = lux.inkPrimary),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(inputModifier)
                        .then(if (inputTestTag != null) Modifier.testTag(inputTestTag) else Modifier)
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Effacer",
                    tint = lux.inkSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                )
            }
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}

@Composable
fun BravUnreadCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val text = if (count > 99) "99+" else count.toString()
    Surface(
        shape = CircleShape,
        color = BravRedSecondary,
        modifier = modifier.heightIn(min = 21.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (text.length > 1) 6.dp else 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                color = BravWhite,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BravGoldHairlineDivider(
    modifier: Modifier = Modifier,
    withGoldTouch: Boolean = false
) {
    val lux = BravTheme.colors
    if (!withGoldTouch) {
        HorizontalDivider(
            modifier = modifier,
            thickness = 0.75.dp,
            color = lux.hairline
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            lux.hairline,
                            lux.champagneGold.copy(alpha = 0.55f),
                            lux.hairline,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun BravConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = lux.modalSurface,
        titleContentColor = lux.inkPrimary,
        textContentColor = lux.inkSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) BravRedSecondary else BravPurplePrimary,
                    contentColor = BravWhite
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun bravOutlinedTextFieldColors(): TextFieldColors {
    val lux = BravTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = lux.translucentSubtleSurface,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = BravRedSecondary.copy(alpha = 0.05f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = lux.violetContour,
        disabledBorderColor = lux.hairline,
        focusedTextColor = lux.inkPrimary,
        unfocusedTextColor = lux.inkPrimary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = lux.inkSecondary,
        focusedPlaceholderColor = lux.inkSecondary,
        unfocusedPlaceholderColor = lux.inkSecondary,
        cursorColor = MaterialTheme.colorScheme.primary
    )
}
