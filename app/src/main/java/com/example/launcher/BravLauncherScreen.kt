package com.example.launcher

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.local.AppPreferencesRepository
import com.example.local.AppPreferencesState
import com.example.local.ThemeMode
import com.example.telecom.CarrierCallController
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravInkPrimary
import com.example.ui.theme.BravIvory
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BravLauncherScreen(
    prefsRepository: AppPreferencesRepository,
    onOpenSettingsStudio: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
    val palette = remember(prefs.launcherThemeId) { LauncherThemePalette.fromId(prefs.launcherThemeId) }
    val iconShape = remember(prefs.launcherIconShape) { LauncherIconShape.fromId(prefs.launcherIconShape) }
    val customIcons = remember(prefs.launcherCustomIconsJson) { LauncherJsonCodec.parseStringMap(prefs.launcherCustomIconsJson) }
    val folders = remember(prefs.launcherFoldersJson) { LauncherJsonCodec.parseFolders(prefs.launcherFoldersJson) }
    val dockPackages = remember(prefs.launcherDockPackagesJson) { LauncherJsonCodec.parseStringList(prefs.launcherDockPackagesJson) }
    val carrierState by CarrierCallController.uiState.collectAsStateWithLifecycle()

    val isSystemDark = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val customWallpaperBmp = remember(prefs.launcherWallpaperPath) {
        LauncherRepository.loadWallpaperBitmap(prefs.launcherWallpaperPath)
    }

    val clockStyle = remember(prefs.launcherClockStyle) { LauncherClockStyle.fromId(prefs.launcherClockStyle) }
    val weatherCity = remember(prefs.launcherWeatherCityJson) { LauncherWeatherCity.fromJson(prefs.launcherWeatherCityJson) }

    var installedApps by remember { mutableStateOf<List<LauncherAppInfo>>(emptyList()) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var drawerSearchQuery by remember { mutableStateOf("") }
    var showAppDrawer by remember { mutableStateOf(false) }
    var showThemeStudio by remember { mutableStateOf(false) }
    var showWeatherPicker by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<LauncherFolder?>(null) }
    var actionTargetApp by remember { mutableStateOf<LauncherAppInfo?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var clockText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var weatherState by remember { mutableStateOf<LauncherWeatherState>(LauncherWeatherState.Loading(weatherCity.name)) }
    var badgeCountsByPkg by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var iconPickerTargetPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        installedApps = LauncherRepository.queryInstalledLauncherApps(context)
        val clockFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH)
        while (true) {
            val now = Date()
            clockText = clockFmt.format(now)
            dateText = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            val nm = context.getSystemService(NotificationManager::class.java)
            val active = runCatching { nm?.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            badgeCountsByPkg = active.groupBy { it.packageName }.mapValues { it.value.size }
            delay(15_000L)
        }
    }

    LaunchedEffect(weatherCity) {
        weatherState = LauncherWeatherState.Loading(weatherCity.name)
        weatherState = LauncherWeatherClient.fetchCurrentWeather(weatherCity)
    }

    BackHandler(enabled = showAppDrawer || showThemeStudio || selectedFolder != null) {
        when {
            showThemeStudio -> showThemeStudio = false
            selectedFolder != null -> selectedFolder = null
            showAppDrawer -> {
                showAppDrawer = false
                drawerSearchQuery = ""
            }
        }
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val pkg = iconPickerTargetPkg ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val path = LauncherRepository.saveCustomAppIconFromUri(context, pkg, uri)
                if (path != null) {
                    val updated = customIcons + (pkg to path)
                    prefsRepository.setLauncherCustomIconsJson(LauncherJsonCodec.serializeStringMap(updated))
                }
            }
        }
        iconPickerTargetPkg = null
    }

    val homeWallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = LauncherRepository.saveWallpaperFromUri(context, uri)
                if (!savedPath.isNullOrBlank()) {
                    prefsRepository.setLauncherWallpaperPath(savedPath)
                }
            }
        }
    }

    if (showThemeStudio) {
        LauncherThemeStudioScreen(
            prefsRepository = prefsRepository,
            installedApps = installedApps,
            onBack = { showThemeStudio = false }
        )
        return
    }

    val africanWallpaper = remember(prefs.launcherAfricanWallpaperId) {
        LauncherAfricanWallpaper.fromId(prefs.launcherAfricanWallpaperId)
    }
    val accentColor = Color(palette.primaryHex)
    val hasUserWallpaper = customWallpaperBmp != null
    val systemSurfaceColor = Color(if (isSystemDark) africanWallpaper.darkTopHex else africanWallpaper.lightTopHex)
    val homeTextColor = when {
        hasUserWallpaper -> Color.White
        isSystemDark -> Color(0xFFF6F2EA)
        else -> BravInkPrimary
    }
    val homeCardBg = when {
        hasUserWallpaper -> Color.Black.copy(alpha = 0.56f)
        isSystemDark -> Color(0xFF191423)
        else -> BravWhite.copy(alpha = 0.94f)
    }
    val visibleHomeApps = remember(installedApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) installedApps else installedApps.filter { it.label.lowercase().contains(q) }
    }
    val drawerFilteredApps = remember(installedApps, drawerSearchQuery) {
        val q = drawerSearchQuery.trim().lowercase()
        if (q.isBlank()) installedApps else installedApps.filter { it.label.lowercase().contains(q) }
    }
    val pinnedDockApps = remember(installedApps, dockPackages) {
        dockPackages.mapNotNull { pkg -> installedApps.firstOrNull { it.packageName == pkg } }.take(4)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(systemSurfaceColor)
            .pointerInput(Unit) {
                var cumulativeDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { cumulativeDrag = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        cumulativeDrag += dragAmount
                        if (cumulativeDrag < -80f) {
                            showAppDrawer = true
                        }
                    }
                )
            }
            .testTag("brav_launcher_home")
    ) {
        // 1. User Home Wallpaper OR African Luxury Canvas background
        if (customWallpaperBmp != null) {
            Image(
                bitmap = customWallpaperBmp.asImageBitmap(),
                contentDescription = "Fond d'écran d'accueil",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("launcher_custom_wallpaper_image")
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.52f)
                            )
                        )
                    )
            )
        } else {
            val bgColors = if (isSystemDark) {
                listOf(Color(africanWallpaper.darkTopHex), Color(africanWallpaper.darkBottomHex))
            } else {
                listOf(Color(africanWallpaper.lightTopHex), Color(africanWallpaper.lightBottomHex))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(bgColors))
            ) {
                africanWallpaper.pattern?.let { pat ->
                    BravAfricanPatternCanvas(
                        pattern = pat,
                        primaryColor = Color(
                            if (isSystemDark) africanWallpaper.patternPrimaryDarkHex
                            else africanWallpaper.patternPrimaryLightHex
                        ),
                        secondaryColor = Color(
                            if (isSystemDark) africanWallpaper.patternSecondaryDarkHex
                            else africanWallpaper.patternSecondaryLightHex
                        ),
                        opacity = if (isSystemDark) africanWallpaper.patternOpacityDark else africanWallpaper.patternOpacityLight,
                        scale = 1.05f
                    )
                }
            }
        }

        // 2. Home Content (hidden from semantics when full-screen App Drawer is open)
        if (!showAppDrawer) Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clock + Weather + Quick System Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LauncherClockWeatherHeader(
                    clockStyle = clockStyle,
                    clockText = clockText,
                    dateText = dateText,
                    weatherState = weatherState,
                    homeTextColor = homeTextColor,
                    accentColor = accentColor,
                    cardBgColor = homeCardBg,
                    hasUserWallpaper = hasUserWallpaper,
                    onOpenWeatherCityPicker = { showWeatherPicker = true },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (searchExpanded) accentColor.copy(alpha = 0.22f) else homeCardBg,
                        onClick = {
                            if (searchExpanded) searchQuery = ""
                            searchExpanded = !searchExpanded
                        },
                        modifier = Modifier.testTag("launcher_search_toggle")
                    ) {
                        Icon(
                            if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Rechercher sur l'accueil",
                            tint = if (searchExpanded) accentColor else homeTextColor,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = homeCardBg,
                        onClick = {
                            homeWallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("launcher_home_wallpaper_button")
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "Fond d'accueil",
                            tint = homeTextColor,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = homeCardBg,
                        onClick = { showCreateFolderDialog = true }
                    ) {
                        Icon(
                            Icons.Rounded.CreateNewFolder,
                            contentDescription = "Créer un dossier",
                            tint = homeTextColor,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = homeCardBg,
                        onClick = {
                            if (onOpenSettingsStudio != null) onOpenSettingsStudio() else showThemeStudio = true
                        },
                        modifier = Modifier.testTag("launcher_open_theme_studio")
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = "Système et Icônes BRAV",
                            tint = accentColor,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                }
            }

            // Collapsible Glass Home Search
            AnimatedVisibility(visible = searchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher une application...", color = homeTextColor.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = homeTextColor)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = homeTextColor,
                        unfocusedTextColor = homeTextColor,
                        focusedContainerColor = homeCardBg,
                        unfocusedContainerColor = homeCardBg,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = homeTextColor.copy(alpha = 0.16f)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().testTag("launcher_search_input")
                )
            }

            // Quick Native BRAV Widgets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = homeCardBg,
                    onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accentColor)
                        Text("BRAV IA & Maths", color = homeTextColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = homeCardBg,
                    onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (carrierState.cityLabel.isNotBlank()) Icons.Rounded.LocationOn else Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = accentColor
                        )
                        Text(
                            text = carrierState.cityLabel.ifBlank { "BRAV Musique" },
                            color = homeTextColor,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (folders.isNotEmpty() && searchQuery.isBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(folders, key = { it.id }) { folder ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = homeCardBg,
                            onClick = { selectedFolder = folder },
                            modifier = Modifier.testTag("launcher_folder_chip_${folder.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Text(folder.name, color = homeTextColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                if (folder.packageNames.isNotEmpty()) {
                                    Text(
                                        text = "${folder.packageNames.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = homeTextColor.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Multi-page Desktop HorizontalPager + Page Dots
            val pagedApps = remember(visibleHomeApps) {
                visibleHomeApps.chunked(12).ifEmpty { listOf(emptyList()) }
            }
            val pagerState = rememberPagerState(pageCount = { pagedApps.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("launcher_desktop_pager")
            ) { pageIndex ->
                val pageItems = pagedApps[pageIndex]
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pageItems, key = { it.packageName }) { app ->
                        val badgeCount = if (prefs.launcherNotificationBadgesEnabled) {
                            badgeCountsByPkg[app.packageName] ?: 0
                        } else 0
                        UniformBravAppTile(
                            app = app,
                            palette = palette,
                            shape = iconShape,
                            iconSize = prefs.launcherIconSizeDp.dp,
                            customIconPath = customIcons[app.packageName],
                            badgeCount = badgeCount,
                            textColorOverride = homeTextColor,
                            onClick = { LauncherRepository.launchApp(context, app) },
                            onLongClick = { actionTargetApp = app }
                        )
                    }
                }
            }

            if (pagedApps.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("launcher_page_dots"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pagedApps.size) { idx ->
                        val active = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) accentColor else homeTextColor.copy(alpha = 0.28f)
                                )
                        )
                    }
                }
            }

            // Subtle swipe/menu pill to open the App Drawer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showAppDrawer = true }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = homeTextColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Menu des applications",
                    style = MaterialTheme.typography.labelSmall,
                    color = homeTextColor.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Fixed BRAV Dock
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = homeCardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockQuickButton(Icons.Rounded.ChatBubble, "BRAV", accentColor, homeTextColor) {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    DockQuickButton(Icons.Rounded.Call, "Appels", accentColor, homeTextColor) {
                        context.startActivity(Intent(Intent.ACTION_DIAL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    DockQuickButton(Icons.Rounded.Sms, "SMS", accentColor, homeTextColor) {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    pinnedDockApps.take(1).forEach { app ->
                        DockQuickButton(Icons.Rounded.PushPin, app.label.take(7), accentColor, homeTextColor) {
                            LauncherRepository.launchApp(context, app)
                        }
                    }
                    DockQuickButton(
                        icon = Icons.Rounded.Apps,
                        label = "Menu",
                        accent = accentColor,
                        textColor = homeTextColor,
                        testTag = "launcher_open_app_drawer_button"
                    ) {
                        showAppDrawer = true
                    }
                }
            }
        }

        // 3. Full-Screen App Drawer ("Menu des applications") matching Ivory / Obsidian Plum Luxury
        AnimatedVisibility(
            visible = showAppDrawer,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            val drawerBg = if (isSystemDark) Color(0xFF100D16) else BravIvory
            val drawerOnBg = if (isSystemDark) Color(0xFFF6F2EA) else BravInkPrimary
            val drawerFieldBg = if (isSystemDark) Color(0xFF191423) else BravWhite

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(drawerBg)
                    .testTag("brav_app_drawer")
            ) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    primaryColor = if (isSystemDark) BravChampagneGold else BravPurplePrimary,
                    secondaryColor = BravChampagneGold,
                    opacity = 0.03f
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Menu des applications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = drawerOnBg
                        )
                        Text(
                            text = "${installedApps.size} applications installées",
                            style = MaterialTheme.typography.labelSmall,
                            color = drawerOnBg.copy(alpha = 0.65f)
                        )
                    }
                    IconButton(
                        onClick = {
                            showAppDrawer = false
                            drawerSearchQuery = ""
                        },
                        modifier = Modifier.testTag("launcher_close_app_drawer")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer le menu", tint = drawerOnBg)
                    }
                }

                OutlinedTextField(
                    value = drawerSearchQuery,
                    onValueChange = { drawerSearchQuery = it },
                    placeholder = { Text("Rechercher une application...", color = drawerOnBg.copy(alpha = 0.55f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor) },
                    trailingIcon = {
                        if (drawerSearchQuery.isNotBlank()) {
                            IconButton(onClick = { drawerSearchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = drawerOnBg)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = drawerOnBg,
                        unfocusedTextColor = drawerOnBg,
                        focusedContainerColor = drawerFieldBg,
                        unfocusedContainerColor = drawerFieldBg,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = drawerOnBg.copy(alpha = 0.14f)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_drawer_search_input")
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(drawerFilteredApps, key = { "drawer_${it.packageName}" }) { app ->
                        UniformBravAppTile(
                            app = app,
                            palette = palette,
                            shape = iconShape,
                            iconSize = prefs.launcherIconSizeDp.dp,
                            customIconPath = customIcons[app.packageName],
                            textColorOverride = drawerOnBg,
                            onClick = {
                                showAppDrawer = false
                                drawerSearchQuery = ""
                                LauncherRepository.launchApp(context, app)
                            },
                            onLongClick = { actionTargetApp = app }
                        )
                    }
                }
            }
            }
        }
    }

    selectedFolder?.let { folder ->
        val folderApps = installedApps.filter { it.packageName in folder.packageNames }
        var renameMode by remember(folder.id) { mutableStateOf(false) }
        var editedName by remember(folder.id, folder.name) { mutableStateOf(folder.name) }

        AlertDialog(
            onDismissRequest = { selectedFolder = null },
            title = {
                if (renameMode) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        label = { Text("Nom du dossier") },
                        modifier = Modifier.fillMaxWidth().testTag("launcher_rename_folder_input")
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(folder.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { renameMode = true },
                            modifier = Modifier.testTag("launcher_rename_folder_button")
                        ) {
                            Text("Renommer")
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (folderApps.isEmpty()) {
                        Text("Dossier vide. Appui long sur une application pour l'ajouter ici.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folderApps, key = { it.packageName }) { app ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    UniformBravAppTile(
                                        app = app,
                                        palette = palette,
                                        shape = iconShape,
                                        iconSize = 48.dp,
                                        customIconPath = customIcons[app.packageName],
                                        textColorOverride = MaterialTheme.colorScheme.onSurface,
                                        onClick = {
                                            selectedFolder = null
                                            LauncherRepository.launchApp(context, app)
                                        },
                                        onLongClick = {
                                            val updatedFolder = folder.copy(
                                                packageNames = folder.packageNames.filterNot { it == app.packageName }
                                            )
                                            val updatedList = folders.map { if (it.id == updatedFolder.id) updatedFolder else it }
                                            selectedFolder = updatedFolder
                                            scope.launch {
                                                prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(updatedList))
                                            }
                                        }
                                    )
                                    TextButton(
                                        onClick = {
                                            val updatedFolder = folder.copy(
                                                packageNames = folder.packageNames.filterNot { it == app.packageName }
                                            )
                                            val updatedList = folders.map { if (it.id == updatedFolder.id) updatedFolder else it }
                                            selectedFolder = updatedFolder
                                            scope.launch {
                                                prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(updatedList))
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Retirer", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (renameMode) {
                    Button(
                        enabled = editedName.isNotBlank(),
                        onClick = {
                            val updatedFolder = folder.copy(name = editedName.trim())
                            val updatedList = folders.map { if (it.id == updatedFolder.id) updatedFolder else it }
                            selectedFolder = updatedFolder
                            renameMode = false
                            scope.launch {
                                prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(updatedList))
                            }
                        }
                    ) { Text("Enregistrer") }
                } else {
                    TextButton(onClick = {
                        val remaining = folders.filterNot { it.id == folder.id }
                        scope.launch {
                            prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(remaining))
                        }
                        selectedFolder = null
                    }) {
                        Text("Supprimer dossier")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedFolder = null }) { Text("Fermer") }
            }
        )
    }

    if (showWeatherPicker) {
        LauncherWeatherCityPickerDialog(
            currentCity = weatherCity,
            onSelectCity = { chosen ->
                showWeatherPicker = false
                scope.launch {
                    prefsRepository.setLauncherWeatherCityJson(chosen.toJson())
                }
            },
            onDismiss = { showWeatherPicker = false }
        )
    }

    actionTargetApp?.let { app ->
        val shortcuts = remember(app.packageName) { LauncherRepository.loadAppShortcuts(context, app.packageName) }
        AlertDialog(
            onDismissRequest = { actionTargetApp = null },
            title = { Text(app.label, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortcuts.take(4).forEach { s ->
                        OutlinedButton(
                            onClick = {
                                LauncherRepository.startAppShortcut(context, s)
                                actionTargetApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(s.shortLabel)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            iconPickerTargetPkg = app.packageName
                            actionTargetApp = null
                            iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Changer l'icône (Galerie)")
                    }
                    if (customIcons.containsKey(app.packageName)) {
                        OutlinedButton(
                            onClick = {
                                LauncherRepository.removeCustomAppIcon(context, app.packageName)
                                val updated = customIcons - app.packageName
                                scope.launch {
                                    prefsRepository.setLauncherCustomIconsJson(LauncherJsonCodec.serializeStringMap(updated))
                                }
                                actionTargetApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Réinitialiser l'icône")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val updatedDock = if (app.packageName in dockPackages) {
                                dockPackages - app.packageName
                            } else {
                                (listOf(app.packageName) + dockPackages).distinct().take(4)
                            }
                            scope.launch {
                                prefsRepository.setLauncherDockPackagesJson(LauncherJsonCodec.serializeStringList(updatedDock))
                            }
                            actionTargetApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (app.packageName in dockPackages) "Retirer du dock" else "Épingler au dock")
                    }
                    if (folders.isNotEmpty()) {
                        val firstFolder = folders.first()
                        OutlinedButton(
                            onClick = {
                                val nextFolder = firstFolder.copy(
                                    packageNames = (firstFolder.packageNames + app.packageName).distinct()
                                )
                                val updatedFolders = folders.map { if (it.id == nextFolder.id) nextFolder else it }
                                scope.launch {
                                    prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(updatedFolders))
                                }
                                actionTargetApp = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ajouter à « ${firstFolder.name} »")
                        }
                    }
                    TextButton(
                        onClick = {
                            LauncherRepository.openAppSystemDetails(context, app.packageName)
                            actionTargetApp = null
                        }
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Infos application")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetApp = null }) { Text("Fermer") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Nouveau dossier") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nom du dossier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = folderName.isNotBlank(),
                    onClick = {
                        val next = LauncherFolder(
                            id = UUID.randomUUID().toString(),
                            name = folderName.trim(),
                            packageNames = emptyList()
                        )
                        scope.launch {
                            prefsRepository.setLauncherFoldersJson(LauncherJsonCodec.serializeFolders(folders + next))
                        }
                        showCreateFolderDialog = false
                    }
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherThemeStudioScreen(
    prefsRepository: AppPreferencesRepository,
    installedApps: List<LauncherAppInfo>? = null,
    onSyncCloudflare: ((themeId: String, shape: String, sizeDp: Int) -> Unit)? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
    val palette = remember(prefs.launcherThemeId) { LauncherThemePalette.fromId(prefs.launcherThemeId) }
    val shape = remember(prefs.launcherIconShape) { LauncherIconShape.fromId(prefs.launcherIconShape) }
    val customIcons = remember(prefs.launcherCustomIconsJson) { LauncherJsonCodec.parseStringMap(prefs.launcherCustomIconsJson) }
    val wallpaperBmp = remember(prefs.launcherWallpaperPath) { LauncherRepository.loadWallpaperBitmap(prefs.launcherWallpaperPath) }

    var refreshTick by remember { mutableIntStateOf(0) }
    val componentEnabled = remember(refreshTick, prefs.launcherEnabled) {
        LauncherRepository.isLauncherComponentEnabled(context)
    }
    val isDefaultHome = remember(refreshTick) {
        LauncherRepository.isBravDefaultLauncher(context)
    }
    val apps = remember(installedApps) {
        installedApps ?: LauncherRepository.queryInstalledLauncherApps(context)
    }
    var iconPickerTargetPkg by remember { mutableStateOf<String?>(null) }

    val defaultHomeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val pkg = iconPickerTargetPkg ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val path = LauncherRepository.saveCustomAppIconFromUri(context, pkg, uri)
                if (path != null) {
                    val updated = customIcons + (pkg to path)
                    prefsRepository.setLauncherCustomIconsJson(LauncherJsonCodec.serializeStringMap(updated))
                }
            }
        }
        iconPickerTargetPkg = null
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = LauncherRepository.saveWallpaperFromUri(context, uri)
                if (!savedPath.isNullOrBlank()) {
                    prefsRepository.setLauncherWallpaperPath(savedPath)
                }
            }
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Système & Icônes BRAV",
                showLogo = false,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("launcher_studio_back")) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = lux.headerOnColor
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                primaryColor = lux.watermarkViolet,
                secondaryColor = lux.watermarkGold,
                opacity = 0.035f
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("launcher_studio_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    BravLuxuryCard(accentBorder = true) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Activer l'accueil système BRAV", fontWeight = FontWeight.SemiBold)
                            }
                            Switch(
                                checked = componentEnabled,
                                onCheckedChange = { enabled ->
                                    LauncherRepository.setLauncherComponentEnabled(context, enabled)
                                    scope.launch { prefsRepository.setLauncherEnabled(enabled) }
                                    refreshTick++
                                },
                                modifier = Modifier.testTag("switch_launcher_enabled")
                            )
                        }

                        Button(
                            onClick = {
                                LauncherRepository.setLauncherComponentEnabled(context, true)
                                scope.launch { prefsRepository.setLauncherEnabled(true) }
                                val intent = LauncherRepository.createRequestDefaultLauncherIntent(context)
                                runCatching { defaultHomeLauncher.launch(intent) }
                                refreshTick++
                            },
                            modifier = Modifier.fillMaxWidth().testTag("set_default_launcher_button")
                        ) {
                            Text(
                                if (isDefaultHome) "BRAV est le système d'accueil par défaut"
                                else "Définir BRAV comme système par défaut"
                            )
                        }
                        if (isDefaultHome) {
                            OutlinedButton(
                                onClick = {
                                    runCatching { defaultHomeLauncher.launch(Intent(Settings.ACTION_HOME_SETTINGS)) }
                                    refreshTick++
                                },
                                modifier = Modifier.fillMaxWidth().testTag("revert_default_launcher_button")
                            ) {
                                Text("Revenir à l'accueil Android précédent")
                            }
                        }
                    }
                }
            }

            // Horloge, Météo & Pastilles de notification
            item {
                val studioClockStyle = remember(prefs.launcherClockStyle) { LauncherClockStyle.fromId(prefs.launcherClockStyle) }
                val studioCity = remember(prefs.launcherWeatherCityJson) { LauncherWeatherCity.fromJson(prefs.launcherWeatherCityJson) }
                var showStudioWeatherPicker by remember { mutableStateOf(false) }

                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Style d'horloge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LauncherClockStyle.entries, key = { it.name }) { style ->
                                FilterChip(
                                    selected = studioClockStyle == style,
                                    onClick = {
                                        scope.launch { prefsRepository.setLauncherClockStyle(style.name) }
                                    },
                                    label = { Text(style.labelFr) },
                                    modifier = Modifier.testTag("studio_clock_style_${style.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ville météo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(studioCity.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = { showStudioWeatherPicker = true },
                                modifier = Modifier.testTag("studio_pick_weather_city")
                            ) {
                                Text("Changer")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pastilles de notification", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = prefs.launcherNotificationBadgesEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { prefsRepository.setLauncherNotificationBadgesEnabled(enabled) }
                                },
                                modifier = Modifier.testTag("studio_notification_badges_switch")
                            )
                        }
                    }
                }

                if (showStudioWeatherPicker) {
                    LauncherWeatherCityPickerDialog(
                        currentCity = studioCity,
                        onSelectCity = { chosen ->
                            showStudioWeatherPicker = false
                            scope.launch { prefsRepository.setLauncherWeatherCityJson(chosen.toJson()) }
                        },
                        onDismiss = { showStudioWeatherPicker = false }
                    )
                }
            }

            // Fond du système (Blanc / Sombre Noire) & Motifs Africains BRAV HOME
            item {
                val activeAfricanWallpaper = remember(prefs.launcherAfricanWallpaperId) {
                    LauncherAfricanWallpaper.fromId(prefs.launcherAfricanWallpaperId)
                }
                BravLuxuryCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Fond du système", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ThemeMode.entries, key = { it.name }) { mode ->
                                FilterChip(
                                    selected = prefs.themeMode == mode,
                                    onClick = { scope.launch { prefsRepository.setThemeMode(mode) } },
                                    leadingIcon = {
                                        when (mode) {
                                            ThemeMode.LIGHT -> Icon(Icons.Rounded.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                            ThemeMode.DARK -> Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                            ThemeMode.SYSTEM -> Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("system_bg_${mode.key}")
                                )
                            }
                        }

                        Text("Motifs Africains BRAV HOME", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.testTag("studio_african_wallpaper_row")
                        ) {
                            items(LauncherAfricanWallpaper.entries, key = { it.id }) { preset ->
                                val isSelected = activeAfricanWallpaper == preset
                                val previewTop = Color(if (lux.isDark) preset.darkTopHex else preset.lightTopHex)
                                val previewBottom = Color(if (lux.isDark) preset.darkBottomHex else preset.lightBottomHex)
                                Surface(
                                    onClick = {
                                        scope.launch {
                                            prefsRepository.setLauncherAfricanWallpaperId(preset.id)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = lux.cardSurface,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 0.75.dp,
                                        color = if (isSelected) lux.champagneGold else lux.hairline
                                    ),
                                    modifier = Modifier
                                        .width(104.dp)
                                        .testTag("studio_african_wallpaper_${preset.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Brush.verticalGradient(listOf(previewTop, previewBottom)))
                                                .border(0.5.dp, lux.hairline, RoundedCornerShape(10.dp))
                                        ) {
                                            preset.pattern?.let { pat ->
                                                BravAfricanPatternCanvas(
                                                    pattern = pat,
                                                    primaryColor = Color(
                                                        if (lux.isDark) preset.patternPrimaryDarkHex
                                                        else preset.patternPrimaryLightHex
                                                    ),
                                                    secondaryColor = Color(
                                                        if (lux.isDark) preset.patternSecondaryDarkHex
                                                        else preset.patternSecondaryLightHex
                                                    ),
                                                    opacity = 0.22f,
                                                    scale = 0.75f
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Sélectionné",
                                                    tint = lux.titlePurple,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = preset.labelFr,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) lux.titlePurple else lux.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Text("Fond d'accueil par défaut", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (wallpaperBmp != null) {
                            Image(
                                bitmap = wallpaperBmp.asImageBitmap(),
                                contentDescription = "Aperçu du fond d'accueil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).testTag("launcher_pick_wallpaper_button")
                            ) {
                                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choisir un fond")
                            }
                            if (prefs.launcherWallpaperPath.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        LauncherRepository.clearWallpaper(context, prefs.launcherWallpaperPath)
                                        scope.launch { prefsRepository.setLauncherWallpaperPath("") }
                                    },
                                    modifier = Modifier.weight(1f).testTag("launcher_clear_wallpaper_button")
                                ) {
                                    Text("Par défaut")
                                }
                            }
                        }
                    }
                }
            }

            item {
                BravLuxuryCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Couleur uniforme des icônes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LauncherThemePalette.entries, key = { it.name }) { pal ->
                                FilterChip(
                                    selected = palette == pal,
                                    onClick = {
                                        scope.launch {
                                            prefsRepository.setLauncherStyle(pal.name, shape.name, prefs.launcherIconSizeDp)
                                        }
                                        onSyncCloudflare?.invoke(pal.name, shape.name, prefs.launcherIconSizeDp)
                                    },
                                    label = { Text(pal.displayName) }
                                )
                            }
                        }

                        Text("Forme des icônes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LauncherIconShape.entries, key = { it.name }) { sh ->
                                FilterChip(
                                    selected = shape == sh,
                                    onClick = {
                                        scope.launch {
                                            prefsRepository.setLauncherStyle(palette.name, sh.name, prefs.launcherIconSizeDp)
                                        }
                                        onSyncCloudflare?.invoke(palette.name, sh.name, prefs.launcherIconSizeDp)
                                    },
                                    label = { Text(sh.displayName) }
                                )
                            }
                        }

                        Text("Taille : ${prefs.launcherIconSizeDp} dp", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = prefs.launcherIconSizeDp.toFloat(),
                            onValueChange = { nextSize ->
                                scope.launch {
                                    prefsRepository.setLauncherStyle(palette.name, shape.name, nextSize.toInt())
                                }
                            },
                            valueRange = 48f..72f
                        )

                        OutlinedButton(
                            onClick = {
                                LauncherRepository.clearAllCustomIcons(context)
                                scope.launch { prefsRepository.setLauncherCustomIconsJson("{}") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restaurer les icônes par défaut")
                        }
                    }
                }
            }

            item {
                Text("Personnaliser chaque application (${apps.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            items(apps, key = { "studio_${it.packageName}" }) { app ->
                ElevatedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UniformBravAppTile(
                            app = app,
                            palette = palette,
                            shape = shape,
                            iconSize = 48.dp,
                            customIconPath = customIcons[app.packageName],
                            showLabel = false,
                            onClick = {},
                            onLongClick = {}
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(
                            onClick = {
                                iconPickerTargetPkg = app.packageName
                                iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Text("Icône")
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UniformBravAppTile(
    app: LauncherAppInfo,
    palette: LauncherThemePalette,
    shape: LauncherIconShape,
    iconSize: Dp,
    customIconPath: String?,
    badgeCount: Int = 0,
    showLabel: Boolean = true,
    textColorOverride: Color? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val bmp = remember(app.packageName, customIconPath) {
        LauncherRepository.loadAppIconBitmap(context, app.packageName, customIconPath)
    }
    val tileShape = RoundedCornerShape(percent = shape.cornerPercent)
    val accent = Color(palette.primaryHex)
    val container = Color(palette.iconContainerHex)
    val labelColor = textColorOverride ?: Color(palette.textHex)
    val monochromeTint = remember(palette.iconTintHex) {
        val bwMatrix = ColorMatrix().apply { setToSaturation(0.15f) }
        ColorFilter.colorMatrix(bwMatrix)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(tileShape)
                    .background(container)
                    .border(1.5.dp, accent.copy(alpha = 0.7f), tileShape),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (customIconPath.isNullOrBlank()) monochromeTint else null,
                        modifier = Modifier
                            .size(if (customIconPath.isNullOrBlank()) iconSize * 0.72f else iconSize)
                            .clip(tileShape)
                    )
                } else {
                    Icon(Icons.Rounded.Apps, contentDescription = app.label, tint = Color(palette.iconTintHex))
                }
                if (customIconPath.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accent.copy(alpha = 0.16f))
                    )
                }
            }
            if (badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE11D48),
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (badgeCount > 9) "9+" else "$badgeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DockQuickButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    textColor: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.22f)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.padding(8.dp).size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
    }
}
