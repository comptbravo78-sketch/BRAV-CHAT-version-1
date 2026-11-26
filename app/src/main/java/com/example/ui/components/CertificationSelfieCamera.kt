package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.LiveSelfieAssessment
import com.example.security.BravHumanVerificationService
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.StatusOnlineGreen

/**
 * Capture selfie caméra frontale EN DIRECT exclusivement (CameraX).
 * Aucune importation depuis la galerie ou les fichiers n'est possible.
 * Demande la permission CAMERA uniquement lorsque l'utilisateur lance la caméra.
 */
@Composable
fun CertificationSelfieCamera(
    capturedPreview: Bitmap?,
    assessment: LiveSelfieAssessment?,
    isProcessing: Boolean,
    onCaptured: (Bitmap, BravHumanVerificationService.AnalyzedSelfiePayload) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lux = BravTheme.colors

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraStarted by remember { mutableStateOf(hasCameraPermission) }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        cameraStarted = granted
        permissionDeniedExplicitly = !granted
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = lux.cardSurface,
            border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = lux.titlePurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Selfie en direct — Caméra frontale uniquement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple
                    )
                }

                if (capturedPreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = capturedPreview.asImageBitmap(),
                            contentDescription = "Aperçu du selfie en direct",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (assessment != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (assessment.passed) StatusOnlineGreen.copy(alpha = 0.92f) else BravRedSecondary.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (assessment.passed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = BravWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = assessment.feedbackFr,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BravWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onRetake,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cert_retake_selfie_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reprendre le selfie en direct")
                    }
                } else if (!hasCameraPermission || !cameraStarted) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BravPurplePrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    tint = lux.titlePurple,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = if (permissionDeniedExplicitly) {
                                "L'autorisation caméra est requise pour capturer votre selfie en direct (importation galerie interdite)."
                            } else {
                                "Aucune photo importée n'est acceptée. Placez votre visage dans le cadre avec un bon éclairage."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (permissionDeniedExplicitly) BravRedSecondary else lux.inkSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                if (hasCameraPermission) {
                                    cameraStarted = true
                                } else {
                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cert_open_front_camera_button")
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ouvrir la caméra frontale")
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF14111A))
                    ) {
                        val previewView = remember(context) {
                            PreviewView(context).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        }
                        DisposableEffect(lifecycleOwner, cameraStarted) {
                            val future = ProcessCameraProvider.getInstance(context)
                            val executor = ContextCompat.getMainExecutor(context)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                    imageCapture = capture
                                    cameraError = null
                                }.onFailure { err ->
                                    cameraError = "Caméra frontale indisponible : ${err.localizedMessage ?: "erreur matérielle"}"
                                }
                            }, executor)

                            onDispose {
                                runCatching { future.get().unbindAll() }
                            }
                        }

                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Cadre guide ovale Or champagne pour centrer le visage
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val ovalW = size.width * 0.66f
                            val ovalH = size.height * 0.68f
                            val left = (size.width - ovalW) / 2f
                            val top = (size.height - ovalH) / 2f
                            drawOval(
                                color = BravChampagneGold.copy(alpha = 0.92f),
                                topLeft = Offset(left, top),
                                size = Size(ovalW, ovalH),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.62f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                text = "Centrez votre visage dans l'ovale doré",
                                style = MaterialTheme.typography.labelSmall,
                                color = BravWhite,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    cameraError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = BravRedSecondary
                        )
                    }

                    Button(
                        onClick = {
                            val cap = imageCapture ?: return@Button
                            cap.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        try {
                                            val bmp = imageProxyToBitmap(image)
                                            if (bmp != null) {
                                                val analyzed = BravHumanVerificationService.analyzeAndEncodeSelfie(bmp)
                                                onCaptured(bmp, analyzed)
                                            } else {
                                                cameraError = "Impossible de décoder la capture."
                                            }
                                        } finally {
                                            image.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        cameraError = exception.localizedMessage ?: "Échec capture caméra."
                                    }
                                }
                            )
                        },
                        enabled = !isProcessing && imageCapture != null,
                        colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("cert_capture_selfie_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BravWhite, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyse présence humaine...")
                        } else {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Capturer le selfie en direct")
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        postScale(-1f, 1f, raw.width / 2f, raw.height / 2f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
