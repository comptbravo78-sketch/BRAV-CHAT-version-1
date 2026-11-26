package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLogoMark
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.util.findActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleIdToken: (String) -> Unit,
    onSetLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    var manualTapInProgress by remember { mutableStateOf(false) }

    // Silent auto sign-in request using a single GetGoogleIdOption
    LaunchedEffect(Unit) {
        if (manualTapInProgress || isLoading) return@LaunchedEffect
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val req = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val cm = CredentialManager.create(activityContext)
            val result = cm.getCredential(context = activityContext, request = req)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                if (!manualTapInProgress) {
                    onGoogleIdToken(idToken)
                }
            }
        } catch (_: NoCredentialException) {
            // Normal when no authorized account is cached yet
        } catch (_: GetCredentialCancellationException) {
            // User dismissed silent prompt
        } catch (e: Exception) {
            Log.d("BravSignIn", "Silent Google sign-in skipped: ${e.message}")
        }
    }

    suspend fun tryGoogleIdFallback(cm: CredentialManager, webClientId: String): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val req = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = cm.getCredential(context = activityContext, request = req)
        val cred = result.credential
        return if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else null
    }

    fun launchInteractiveGoogleSignIn() {
        scope.launch {
            manualTapInProgress = true
            onSetLoading(true)
            onError(null)
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                val cm = CredentialManager.create(activityContext)

                val token: String? = try {
                    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                    val req = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()
                    val result = cm.getCredential(context = activityContext, request = req)
                    val cred = result.credential
                    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        GoogleIdTokenCredential.createFrom(cred.data).idToken
                    } else null
                } catch (e: NoCredentialException) {
                    Log.w("BravSignIn", "SignInWithGoogle returned NoCredentialException; trying GetGoogleIdOption", e)
                    tryGoogleIdFallback(cm, webClientId)
                } catch (e: GetCredentialUnsupportedException) {
                    Log.w("BravSignIn", "SignInWithGoogle unsupported; trying GetGoogleIdOption", e)
                    tryGoogleIdFallback(cm, webClientId)
                } catch (e: GetCredentialProviderConfigurationException) {
                    Log.w("BravSignIn", "Provider configuration exception; trying GetGoogleIdOption", e)
                    tryGoogleIdFallback(cm, webClientId)
                }

                if (!token.isNullOrBlank()) {
                    onGoogleIdToken(token)
                } else {
                    onSetLoading(false)
                    onError("Impossible de récupérer le jeton Google. Réessayez.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("BravSignIn", "Google Sign-In cancelled", e)
                onSetLoading(false)
                onError("Connexion annulée. Sélectionnez un compte Google pour continuer.")
            } catch (e: NoCredentialException) {
                Log.w("BravSignIn", "No Google credentials found on device", e)
                onSetLoading(false)
                onError("Aucun compte Google connecté sur cet appareil. Ajoutez un compte Google dans les paramètres Android.")
            } catch (e: GetCredentialException) {
                Log.e("BravSignIn", "CredentialManager error: ${e.type} - ${e.message}", e)
                onSetLoading(false)
                onError(e.localizedMessage ?: "Erreur Credential Manager lors de la connexion Google.")
            } catch (e: Exception) {
                Log.e("BravSignIn", "Interactive Google Sign-In error", e)
                onSetLoading(false)
                onError(e.localizedMessage ?: "Erreur lors de l'authentification Google.")
            } finally {
                manualTapInProgress = false
            }
        }
    }

    val lux = BravTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lux.ivoryCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        BravAfricanPatternCanvas(
            pattern = BravPatternType.SOLAR_ADINKRA,
            variant = BravPatternVariant.GOLD_ON_IVORY,
            opacity = BravPatternIntensity.LIGHT_DECOR,
            scale = 1.0f
        )

        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = lux.cardSurface,
                border = BorderStroke(1.25.dp, lux.champagneGold.copy(alpha = 0.72f)),
                tonalElevation = 1.dp,
                shadowElevation = if (lux.isDark) 0.dp else 3.dp
            ) {
                Box(modifier = Modifier.padding(22.dp)) {
                    BravLogoMark(size = 76.dp)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "BRAV Chat",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = lux.titlePurple
                )
                Text(
                    text = "Messagerie chiffrée, appels HD et SMS unifiés.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = lux.inkSecondary
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = { launchInteractiveGoogleSignIn() },
                enabled = !isLoading,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BravPurplePrimary,
                    contentColor = BravWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_signin_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BravWhite,
                        strokeWidth = 2.2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Connexion en cours...", fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        text = "Continuer avec Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = lux.softGray,
                border = BorderStroke(0.75.dp, lux.hairline)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = lux.titlePurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Chiffrement de bout en bout activé",
                        style = MaterialTheme.typography.labelMedium,
                        color = lux.inkSecondary
                    )
                }
            }
        }
    }
}
