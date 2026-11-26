package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.viewmodel.BravViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    viewModel: BravViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val myLocations by viewModel.myLiveLocations.collectAsStateWithLifecycle()
    val sharedLocations by viewModel.sharedLiveLocations.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()

    var selectedDurationMin by remember { mutableIntStateOf(60) }
    var myCenter by remember { mutableStateOf(GeoPoint(5.3484, -4.0305)) }

    Configuration.getInstance().userAgentValue = context.packageName
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.5)
            controller.setCenter(myCenter)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    @SuppressLint("MissingPermission")
    fun shareCurrentGpsNow() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    myCenter = GeoPoint(loc.latitude, loc.longitude)
                    mapView.controller.animateTo(myCenter)
                    viewModel.publishLiveLocation(
                        recipients = savedContacts,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        acc = loc.accuracy,
                        durationMin = selectedDurationMin
                    )
                } else {
                    viewModel.showMessage("Signal GPS indisponible pour le moment.")
                }
            }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shareCurrentGpsNow()
        else viewModel.showMessage("Permission de localisation requise.")
    }

    // Refresh map markers whenever encrypted live locations change
    val allShares = remember(myLocations, sharedLocations) { (myLocations + sharedLocations).distinctBy { it.id } }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Carte GPS chiffrée",
                pattern = BravPatternType.WAX_TEXTILE,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = lux.headerOnColor)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { mapView },
                    update = { view ->
                        view.overlays.clear()
                        var firstPoint: GeoPoint? = null
                        allShares.forEach { share ->
                            val coords = viewModel.decryptPeerLocation(share)
                            if (coords != null) {
                                val pt = GeoPoint(coords.latitude, coords.longitude)
                                if (firstPoint == null) firstPoint = pt
                                val marker = Marker(view).apply {
                                    position = pt
                                    title = if (share.ownerId == viewModel.currentUserId) "Ma position partagée" else share.ownerName
                                    snippet = "Chiffré E2EE"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                view.overlays.add(marker)
                            }
                        }
                        firstPoint?.let { view.controller.setCenter(it) }
                        view.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                val lux = BravTheme.colors
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = lux.modalSurface,
                    border = BorderStroke(0.8.dp, lux.violetContour),
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = BravPurple)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${allShares.size} partage(s) GPS chiffré(s) actif(s)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Surface(
                color = lux.translucentCardSurface,
                border = BorderStroke(0.75.dp, lux.violetContour),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BravAfricanPatternCanvas(
                        pattern = BravPatternType.WAX_TEXTILE,
                        variant = BravPatternVariant.GOLD_ON_IVORY,
                        opacity = BravPatternIntensity.SUBTLE_WATERMARK,
                        modifier = Modifier.matchParentSize()
                    )
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Durée du partage en direct :", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15 to "15 min", 60 to "1 heure", 480 to "8 heures").forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedDurationMin == mins,
                                onClick = { selectedDurationMin = mins },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (granted) shareCurrentGpsNow()
                                else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Partager ma position")
                        }
                        if (myLocations.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.stopLiveLocation() },
                                colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Arrêter")
                                Spacer(Modifier.width(4.dp))
                                Text("Arrêter")
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
