package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppNotification
import com.example.model.NotificationCategory
import com.example.model.NotificationCounters
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravUnreadCountBadge
import com.example.ui.components.bravAfricanPattern
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravVerifiedBlue
import com.example.ui.theme.BravWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsCenterScreen(
    notifications: List<AppNotification>,
    counters: NotificationCounters,
    onNotificationClick: (AppNotification) -> Unit,
    onMarkAllRead: (NotificationCategory) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val lux = BravTheme.colors
    var selectedTab by remember { mutableStateOf(NotificationCategory.ALL) }

    val filtered = remember(notifications, selectedTab) {
        if (selectedTab == NotificationCategory.ALL) notifications
        else notifications.filter { it.categoryEnum == selectedTab }
    }
    val unreadInSelected = counters.countFor(selectedTab)

    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Notifications",
                showLogo = false,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = lux.headerOnColor
                        )
                    }
                },
                actions = {
                    if (unreadInSelected > 0) {
                        TextButton(
                            onClick = { onMarkAllRead(selectedTab) },
                            modifier = Modifier.testTag("notifications_mark_all_read_button")
                        ) {
                            Icon(
                                Icons.Rounded.DoneAll,
                                contentDescription = "Tout marquer lu",
                                tint = lux.headerOnColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Tout lire",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.headerOnColor
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(lux.ivoryCanvas)
                .bravAfricanPattern(
                    pattern = BravPatternType.SOLAR_ADINKRA,
                    primaryColor = lux.watermarkGold,
                    secondaryColor = lux.watermarkViolet,
                    opacity = BravPatternIntensity.SUBTLE_WATERMARK
                )
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("notifications_filter_row")
            ) {
                items(NotificationCategory.filterTabs, key = { it.raw }) { tab ->
                    val selected = selectedTab == tab
                    val count = counters.countFor(tab)
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) BravPurplePrimary else lux.translucentSubtleSurface,
                        border = BorderStroke(
                            width = if (selected) 1.dp else 0.75.dp,
                            color = if (selected) BravPurplePrimary else lux.violetContour
                        ),
                        modifier = Modifier.testTag("notif_tab_${tab.raw}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tab.labelFr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) BravWhite else lux.inkPrimary
                            )
                            if (count > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) BravWhite.copy(alpha = 0.22f) else BravRedSecondary
                                ) {
                                    Text(
                                        text = if (count > 99) "99+" else count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BravWhite,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BravGoldHairlineDivider(withGoldTouch = true)

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BravLuxuryCard(
                        shapeRadius = 22.dp,
                        accentBorder = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.85.dp, lux.violetContour),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsNone,
                                        contentDescription = null,
                                        tint = lux.titlePurple,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Aucune notification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = lux.inkPrimary
                            )
                            Text(
                                text = if (selectedTab == NotificationCategory.ALL) {
                                    "Vos alertes messages, contacts, ACTUS, appels, offres et CERTIFICATION apparaîtront ici en temps réel."
                                } else {
                                    "Aucune alerte dans la catégorie « ${selectedTab.labelFr} »."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = lux.inkSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notifications_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id.ifBlank { it.dedupKey } }) { notif ->
                        NotificationItemCard(
                            notification = notif,
                            onClick = { onNotificationClick(notif) },
                            onDelete = { onDeleteNotification(notif.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val lux = BravTheme.colors
    val unread = !notification.isRead
    val icon = categoryIcon(notification.categoryEnum)
    val accentColor = when (notification.categoryEnum) {
        NotificationCategory.CALLS -> BravRedSecondary
        NotificationCategory.ACCOUNT_SECURITY -> BravVerifiedBlue
        NotificationCategory.OFFERS_JOBS -> lux.champagneGold
        else -> lux.titlePurple
    }

    BravLuxuryCard(
        shapeRadius = 18.dp,
        isSelected = false,
        accentBorder = unread,
        translucent = true,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notif_card_${notification.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = lux.translucentSubtleSurface,
                border = BorderStroke(0.85.dp, if (unread) lux.champagneGold.copy(alpha = 0.72f) else lux.violetContour),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = notification.categoryEnum.labelFr,
                        tint = accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                        if (unread) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(BravRedSecondary, CircleShape)
                            )
                        }
                        Text(
                            text = notification.title.ifBlank { notification.categoryEnum.labelFr },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                            color = lux.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = formatNotificationRelativeTime(notification.effectiveTimestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) lux.titlePurple else lux.inkSecondary,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (notification.body.isNotBlank()) {
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unread) lux.inkPrimary else lux.inkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                        border = BorderStroke(0.6.dp, lux.violetContour)
                    ) {
                        Text(
                            text = notification.categoryEnum.labelFr,
                            style = MaterialTheme.typography.labelSmall,
                            color = lux.titlePurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (unread) {
                        BravUnreadCountBadge(count = 1)
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("notif_delete_${notification.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Supprimer la notification",
                    tint = lux.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun categoryIcon(category: NotificationCategory): ImageVector = when (category) {
    NotificationCategory.ALL -> Icons.Rounded.NotificationsNone
    NotificationCategory.MESSAGES -> Icons.Rounded.ChatBubble
    NotificationCategory.CONTACTS -> Icons.Rounded.Contacts
    NotificationCategory.ACTUS -> Icons.Rounded.Newspaper
    NotificationCategory.CALLS -> Icons.Rounded.Call
    NotificationCategory.OFFERS_JOBS -> Icons.Rounded.Storefront
    NotificationCategory.ACCOUNT_SECURITY -> Icons.Rounded.Security
}

private fun formatNotificationRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Récent"
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    return when {
        mins < 1L -> "À l'instant"
        mins < 60L -> "Il y a ${mins} min"
        hours < 24L -> "Il y a ${hours} h"
        else -> SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(timestampMs))
    }
}
