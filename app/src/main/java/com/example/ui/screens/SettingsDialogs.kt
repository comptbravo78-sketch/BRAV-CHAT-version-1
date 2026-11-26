package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.media.VoiceAudioProcessor
import com.example.media.VoiceProcessingConfig
import com.example.model.BlockedEntry
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LiveMicrophoneTestDialog(
    processingConfig: VoiceProcessingConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var inputLevel by remember { mutableFloatStateOf(0f) }
    var noiseReductionDb by remember { mutableFloatStateOf(0f) }
    var speechActive by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPerm = granted
    }

    var hwNsEnabledNow by remember { mutableStateOf(false) }
    var hwAecEnabledNow by remember { mutableStateOf(false) }

    val hwNsSupported = remember { runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false) }
    val hwAecSupported = remember { runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false) }

    if (hasMicPerm) {
        LaunchedEffect(processingConfig) {
            withContext(Dispatchers.IO) {
                val sampleRate = 16000
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1280)

                val recorder = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
                }.getOrNull()

                if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        captureError = "Microphone indisponible sur cet appareil."
                    }
                    runCatching { recorder?.release() }
                    return@withContext
                }

                val hwNs = if (hwNsSupported) {
                    runCatching {
                        NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                            enabled = processingConfig.noiseSuppressionEnabled
                        }
                    }.getOrNull()
                } else null
                val hwAec = if (hwAecSupported) {
                    runCatching {
                        AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                            enabled = processingConfig.echoCancellationEnabled
                        }
                    }.getOrNull()
                } else null
                val hwAgc = if (runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false)) {
                    runCatching {
                        AutomaticGainControl.create(recorder.audioSessionId)?.apply {
                            enabled = processingConfig.autoGainControlEnabled || processingConfig.voicePriorityEnabled
                        }
                    }.getOrNull()
                } else null

                withContext(Dispatchers.Main) {
                    hwNsEnabledNow = runCatching { hwNs?.enabled == true }.getOrDefault(false)
                    hwAecEnabledNow = runCatching { hwAec?.enabled == true }.getOrDefault(false)
                }

                val processor = VoiceAudioProcessor(processingConfig).apply {
                    initialize(sampleRate, 1)
                }
                val frameBuf = ShortArray(160) // 10ms WebRTC frame size at 16 kHz

                try {
                    recorder.startRecording()
                    while (isActive) {
                        val read = recorder.read(frameBuf, 0, frameBuf.size)
                        if (read > 0) {
                            processor.processPcm16(frameBuf, read, 1)
                            val tel = processor.currentTelemetry()
                            withContext(Dispatchers.Main) {
                                inputLevel = tel.inputLevel
                                noiseReductionDb = tel.noiseReductionDb
                                speechActive = tel.speechDetected
                            }
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        captureError = t.localizedMessage ?: "Erreur d'écoute microphone"
                    }
                } finally {
                    runCatching { recorder.stop() }
                    runCatching { hwNs?.release() }
                    runCatching { hwAec?.release() }
                    runCatching { hwAgc?.release() }
                    runCatching { recorder.release() }
                }
            }
        }
    }

    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = lux.modalSurface,
        icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = BravPurple) },
        title = { Text("Test microphone & filtre vocal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasMicPerm) {
                    Text(
                        text = "Autorisez l'accès au microphone pour mesurer le filtre de réduction du bruit en temps réel.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Autoriser le microphone")
                    }
                } else if (captureError != null) {
                    Text(captureError.orEmpty(), color = BravRed, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        text = "Parlez normalement puis observez le comportement du filtre de priorité vocale :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { inputLevel.coerceIn(0f, 1f) },
                        color = if (speechActive) StatusOnlineGreen else BravPurple,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (speechActive) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            Text(
                                text = if (speechActive) "Voix détectée (prioritaire)" else "Bruit ambiant atténué",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (speechActive) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (processingConfig.noiseSuppressionEnabled) "-%.1f dB".format(noiseReductionDb) else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BravPurple
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.violetContour),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "NS Android : ${
                                    when {
                                        !processingConfig.noiseSuppressionEnabled -> "Désactivé"
                                        hwNsEnabledNow -> "Actif sur session micro + DSP BRAV"
                                        hwNsSupported -> "Disponible + DSP BRAV actif"
                                        else -> "DSP BRAV logiciel actif"
                                    }
                                }",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "AEC Android : ${
                                    when {
                                        !processingConfig.echoCancellationEnabled -> "Désactivé"
                                        hwAecEnabledNow -> "Actif sur session micro + WebRTC AEC"
                                        hwAecSupported -> "Disponible + WebRTC AEC"
                                        else -> "DSP WebRTC AEC actif"
                                    }
                                }",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
fun BlockedUsersDialog(
    blockedUsers: List<BlockedEntry>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = lux.modalSurface,
        icon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = BravRed) },
        title = { Text("Utilisateurs bloqués (${blockedUsers.size})") },
        text = {
            if (blockedUsers.isEmpty()) {
                Text(
                    "Aucun utilisateur bloqué pour le moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedUsers, key = { it.blockedUid }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = lux.translucentSubtleSurface,
                            border = BorderStroke(0.75.dp, lux.violetContour),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ID : ${entry.blockedUid.take(14)}...", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    if (entry.reason.isNotBlank()) {
                                        Text(entry.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { onUnblock(entry.blockedUid) }) {
                                    Text("Débloquer", color = BravPurple)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
fun ChatLockPinDialog(
    hasPinConfigured: Boolean,
    onSavePin: suspend (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = BravPurple) },
        title = { Text(if (hasPinConfigured) "Modifier le code PIN" else "Configurer un code PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Protégez vos discussions verrouillées avec un code PIN local de 4 à 8 chiffres.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("Code PIN (4 à 8 chiffres)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                onClick = {
                    scope.launch {
                        onSavePin(pin)
                        onDismiss()
                    }
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (hasPinConfigured) {
                    TextButton(onClick = {
                        scope.launch {
                            onSavePin("")
                            onDismiss()
                        }
                    }) { Text("Désactiver", color = BravRed) }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

@Composable
fun LegalDocumentDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("J'ai compris") }
        }
    )
}
